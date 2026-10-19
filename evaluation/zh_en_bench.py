"""V3 报告一 · 中文查英文论文专项：四组对照。

A 原始中文-Dense ｜ B 中文-Hybrid（现网默认，含关键词扩展链路）
C 英文-Dense（上界参照）｜ D 英文-Hybrid（现网英文行为）
指标：R@5/R@10、MRR@10 vs D1 refs；核心输出 = 中文相对英文上界的召回保持率。
"""
import io
import json
import os
import re
import time
import urllib.request

API = 'http://localhost:8080'
KEY = None
TOPIC = 'aea33cf2-e4cb-4517-93ce-7161179fd847'

import http.cookiejar
jar = http.cookiejar.CookieJar()
op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


def login():
    import os
    op.open(urllib.request.Request(f'{API}/api/rp/auth/login',
        data=json.dumps({'username': os.environ.get('RP_EVAL_USER', 'jinkai'),
                         'password': os.environ.get('RP_EVAL_PASSWORD', '')}).encode(),
        headers={'Content-Type': 'application/json'}), timeout=15).read()


def llm_translate(en):
    """经本地中继调 DeepSeek 做查询翻译（temperature 0）。"""
    body = json.dumps({'model': 'deepseek-flash', 'temperature': 0,
                       'messages': [{'role': 'user',
                                     'content': '将下面的学术检索查询翻译成自然的中文研究问题，只输出译文：\n' + en}],
                       'max_tokens': 200}).encode()
    r = urllib.request.urlopen(urllib.request.Request(
        'http://localhost:18090/v1/chat/completions', data=body,
        headers={'Content-Type': 'application/json',
                 'Authorization': 'Bearer ' + os.environ.get('EVAL_LLM_KEY', '')}), timeout=120)
    d = json.loads(r.read().decode())
    txt = d['choices'][0]['message']['content'].strip()
    # 清洗：单行、限长（retrieve 端 400 = 空/超长查询）
    txt = re.sub(r'\s+', ' ', txt).strip('" ')
    return txt[:200]


def retrieve(query, channels, top_k=10):
    r = op.open(urllib.request.Request(f'{API}/api/rp/retrieve',
        data=json.dumps({'topic_id': TOPIC, 'query': query, 'top_k': top_k,
                         'channels': channels, 'with_parents': False}).encode(),
        headers={'Content-Type': 'application/json'}), timeout=60)
    d = json.loads(r.read().decode())
    ids = []
    for c in d.get('candidates', []):
        pid = c.get('paperId', '').replace('LS-', '').replace('SF-', '')
        try:
            ids.append(int(pid))
        except ValueError:
            ids.append(pid)
    return ids


def metrics(ranked, refs, k):
    refs = set(refs)
    hits = [i for i, doc in enumerate(ranked[:k]) if doc in refs]
    r_at_k = len(hits) / len(refs) if refs else 0.0
    mrr = 1.0 / (hits[0] + 1) if hits else 0.0
    return r_at_k, mrr


def mean(xs):
    return sum(xs) / len(xs) if xs else 0.0


if __name__ == '__main__':
    login()
    rows = [json.loads(l) for l in io.open('evaluation/zh_en_queries_draft.jsonl', encoding='utf-8')]
    groups = {'A_zh_dense': [], 'B_zh_hybrid': [], 'C_en_dense': [], 'D_en_hybrid': []}
    for i, row in enumerate(rows):
        zh = row['query_zh'] or llm_translate(row['query_en'])
        row['query_zh'] = zh
        en, refs = row['query_en'], row['refs']
        for name, q, ch in [('A_zh_dense', zh, ['dense']), ('B_zh_hybrid', zh, ['bm25', 'dense']),
                            ('C_en_dense', en, ['dense']), ('D_en_hybrid', en, ['bm25', 'dense'])]:
            try:
                ranked = retrieve(q, ch)
            except Exception as e:
                print(f'{row["qid"]} {name} ERR {str(e)[:50]}')
                ranked = []
            r5, mrr = metrics(ranked, refs, 10)
            r10, _ = metrics(ranked, refs, 10)
            groups[name].append({'qid': row['qid'], 'r5': r5, 'r10': r10, 'mrr': mrr})
        if (i + 1) % 10 == 0:
            print(f'{i+1}/{len(rows)}', flush=True)
        time.sleep(0.3)

    summary = {}
    for name, items in groups.items():
        summary[name] = {'r5': round(mean([x['r5'] for x in items]), 4),
                         'r10': round(mean([x['r10'] for x in items]), 4),
                         'mrr': round(mean([x['mrr'] for x in items]), 4)}
    # 核心输出：召回保持率 = zh / en（同通道；dense 用 A/C，hybrid 用 B/D）
    for zk, ek, tag in [('A_zh_dense', 'C_en_dense', 'dense'), ('B_zh_hybrid', 'D_en_hybrid', 'hybrid')]:
        if summary[zk] and summary[ek]:
            summary[f'keep_{tag}'] = {
                'r5': round(summary[zk]['r5'] / max(1e-9, summary[ek]['r5']), 4) if summary[ek]['r5'] else None,
                'r10': round(summary[zk]['r10'] / max(1e-9, summary[ek]['r10']), 4) if summary[ek]['r10'] else None,
                'mrr': round(summary[zk]['mrr'] / max(1e-9, summary[ek]['mrr']), 4) if summary[ek]['mrr'] else None}
    print(json.dumps(summary, ensure_ascii=False, indent=1))
    json.dump({'summary': summary, 'per_query': groups},
              io.open('evaluation/results/zh_en_bench.json', 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    # 保存带翻译的查询集（供人工审阅修订）
    with io.open('evaluation/zh_en_queries_translated.jsonl', 'w', encoding='utf-8') as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + '\n')
    print('→ zh_en_bench.json + zh_en_queries_translated.jsonl')
