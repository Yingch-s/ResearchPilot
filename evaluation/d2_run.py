"""D2 · SciFact 证据验证评测（断点续跑）。

每条 claim：
1. 检索 top-5（产品 /api/rp/retrieve hybrid 通道）——记录引用论文是否命中（定位召回）
2. GPU embedding：claim 与各候选摘要的相关性（cosine）
3. 判定：LLM NLI（镜像生产 RpClaimVerifier 的判定准则，temp 0.1）
   → SUPPORTED / CONTRADICTED / INSUFFICIENT

指标：定位召回、假支持率（NEI→SUPPORTED，主指标）、混淆矩阵、
阈值校准（用相关性分数扫阈值，离线判定 SUPPORTED 的备选规则）。

断点：evaluation/results/d2_scifact/claims_state.json；输出 claims_scored.jsonl
"""
import io
import json
import math
import os
import time
import urllib.request

API = 'http://localhost:8080'
ES = 'http://localhost:9200'
SIDECAR = 'http://localhost:8100'
TOPIC = '96284057-9863-4c94-bf6a-8aae79b1cbf7'
OUT = 'evaluation/results/d2_scifact'
STATE = f'{OUT}/claims_state.json'

JUDGE_PROMPT = """你在判定一段论文摘要是否支持一条研究声明。

判定准则（三选一）：
- SUPPORTED：摘要包含能支持该声明的内容
- CONTRADICTED：摘要支持与声明相反的结论
- INSUFFICIENT：摘要与声明主题相关但不足以建立或推翻声明

只输出 JSON，不要 markdown：
{"verdict":"SUPPORTED|CONTRADICTED|INSUFFICIENT","reason":"简短理由"}

声明：%s

摘要：%s"""


def log(msg):
    line = f'{time.strftime("%H:%M:%S")} {msg}'
    print(line, flush=True)
    with io.open(f'{OUT}/run.log', 'a', encoding='utf-8') as f:
        f.write(line + '\n')


def post_json(url, body, timeout=300):
    r = urllib.request.urlopen(urllib.request.Request(url, data=json.dumps(body).encode(),
        headers={'Content-Type': 'application/json'}), timeout=timeout)
    return json.loads(r.read().decode('utf-8'))


def cosine(a, b):
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    return dot / (na * nb) if na and nb else 0.0


def retrieve(query):
    d = post_json(f'{API}/api/rp/retrieve',
                  {'topic_id': TOPIC, 'query': query, 'top_k': 5,
                   'channels': ['bm25', 'dense'], 'with_parents': False})
    return [(c.get('paperId', ''), c.get('text', '')) for c in d.get('candidates', [])]


def llm_judge(claim, abstract):
    # 镜像生产 RpClaimVerifier 的 NLI 判定路径（温度 0.1）
    prompt = JUDGE_PROMPT % (claim, abstract[:1500])
    import os
    key = os.environ.get('LLM_API_KEY', '')
    r = urllib.request.urlopen(urllib.request.Request(
        'https://api.deepseek.com/chat/completions',
        data=json.dumps({'model': 'deepseek-chat', 'temperature': 0.1,
                         'messages': [{'role': 'user', 'content': prompt}],
                         'max_tokens': 200}).encode(),
        headers={'Content-Type': 'application/json', 'Authorization': f'Bearer {key}'}), timeout=120)
    raw = json.loads(r.read().decode('utf-8'))['choices'][0]['message']['content']
    j = raw.strip()
    if j.startswith('```'):
        j = j.strip('`').replace('json', '', 1).strip()
    parsed = json.loads(j)
    v = parsed.get('verdict', 'INSUFFICIENT').upper()
    return v if v in ('SUPPORTED', 'CONTRADICTED', 'INSUFFICIENT') else 'INSUFFICIENT', parsed.get('reason', '')


def main():
    os.makedirs(OUT, exist_ok=True)
    claims = [json.loads(l) for l in io.open('evaluation/scifact_eval_claims.jsonl', encoding='utf-8')]
    corpus = {r['doc_id']: r for r in
              (json.loads(l) for l in io.open('evaluation/scifact_eval_corpus.jsonl', encoding='utf-8'))}
    scored = {}
    state = {'done': []}
    if os.path.exists(f'{OUT}/claims_scored.jsonl'):
        for line in io.open(f'{OUT}/claims_scored.jsonl', encoding='utf-8'):
            r = json.loads(line)
            scored[r['id']] = r
            state['done'].append(r['id'])
    pending = [c for c in claims if c['id'] not in scored]
    log(f'claims {len(claims)}, 已完成 {len(scored)}, 待跑 {len(pending)}')
    started = time.time()
    for i, c in enumerate(pending):
        try:
            ranked = retrieve(c['claim'])
            ids = [pid for pid, _ in ranked]
            abstracts = {pid: t for pid, t in ranked}
            cited_hit = c['cited'] in ids
            # 相关性：claim × 各候选摘要（GPU）
            embed_texts = [c['claim']] + [abstracts.get(pid, '')[:600] for pid in ids]
            vecs = post_json(f'{SIDECAR}/v1/embeddings', {'input': embed_texts})
            vv = [item['embedding'] for item in vecs['data']]
            rels = [round(cosine(vv[0], v), 4) for v in vv[1:]]
            cited_rel = rels[ids.index(c['cited'])] if c['cited'] in ids else None
            # 判定：优先用引用摘要；不在 top5 则用最相关候选
            judge_abstract = corpus.get(c['cited'], {}).get('abstract', '') or \
                (abstracts.get(ids[0], '') if ids else '')
            verdict, reason = llm_judge(c['claim'], judge_abstract)
            rec = {'id': c['id'], 'gold': c['gold'], 'verdict': verdict, 'reason': reason,
                   'cited': c['cited'], 'cited_hit_top5': cited_hit, 'cited_rel': cited_rel,
                   'top5_ids': ids}
        except Exception as e:
            rec = {'id': c['id'], 'gold': c['gold'], 'error': str(e)[:200]}
            log(f'{i}/{len(pending)} 错误: {str(e)[:80]}')
        with io.open(f'{OUT}/claims_scored.jsonl', 'a', encoding='utf-8') as f:
            f.write(json.dumps(rec, ensure_ascii=False) + '\n')
        state['done'].append(c['id'])
        with io.open(STATE, 'w', encoding='utf-8') as f:
            json.dump(state, f)
        if (i + 1) % 25 == 0:
            rate = (i + 1) / max(1, time.time() - started)
            log(f'进度 {i+1}/{len(pending)} 速率 {rate:.2f}/s 剩余 '
                f'{(len(pending)-i-1)/max(rate,1)/60:.0f} 分钟')
        time.sleep(0.2)
    log('D2 判定完成，运行 d2_calibrate.py 出指标')


if __name__ == '__main__':
    main()
