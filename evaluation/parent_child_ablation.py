"""V3 报告一 · Parent-Child 消融（生成层）：chunk 直供 vs 父窗口扩展。

同查询两组上下文 → 同一 LLM 回答 → 比较：上下文 Token、答案长度、
数字保真率（答案中的数字是否都能在上下文中找到——事实保真的代理指标）。
"""
import io
import json
import os
import re
import time
import urllib.request

import http.cookiejar

API = 'http://localhost:8080'
TOPIC = 'e22e83f7-77b4-47fa-aa12-ce8a1ef203b8'
KEY = os.environ.get('EVAL_LLM_KEY', '')

jar = http.cookiejar.CookieJar()
op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
op.open(urllib.request.Request(f'{API}/api/rp/auth/login',
    data=json.dumps({'username': 'jinkai', 'password': 'rp-jinkai-2026'}).encode(),
    headers={'Content-Type': 'application/json'}), timeout=15).read()


def retrieve(query, with_parents):
    # 已知产品 bug：hybrid 间歇返回空（dense 瞬断 + 空结果被缓存）——重试 + 换查询后缀绕过
    for attempt in range(4):
        q = query if attempt == 0 else f'{query} （{attempt}）'
        rr = op.open(urllib.request.Request(f'{API}/api/rp/retrieve',
            data=json.dumps({'topic_id': TOPIC, 'query': q, 'top_k': 5,
                             'channels': ['bm25', 'dense'], 'with_parents': True}).encode(),
            headers={'Content-Type': 'application/json'}), timeout=60)
        d = json.loads(rr.read().decode())
        cands = d.get('candidates', [])
        if cands:
            texts = [c.get('text', '') for c in cands]
            if with_parents:
                # parent 组：用父窗口文本（rp_section_contexts.content）替换 chunk 文本
                parents = d.get('parents', {}) or {}
                texts = [parents.get(c.get('parentId', '')) or t
                         for c, t in zip(cands, texts)]
            return texts
        time.sleep(1)
    return []


def llm(query, context):
    body = json.dumps({'model': 'deepseek-chat', 'temperature': 0.1,
                       'messages': [{'role': 'user',
                                     'content': f'仅依据以下资料回答研究问题。资料：\n\n{context}\n\n问题：{query}\n\n要求：中文回答，引用资料中的具体数字与事实。'}],
                       'max_tokens': 800}).encode()
    r = urllib.request.urlopen(urllib.request.Request(
        'http://localhost:18090/v1/chat/completions', data=body,
        headers={'Content-Type': 'application/json', 'Authorization': f'Bearer {KEY}'}), timeout=180)
    d = json.loads(r.read().decode())
    usage = d.get('usage', {})
    return d['choices'][0]['message']['content'], usage


QUERIES = [
    'DPR 的负样本策略具体是怎么做的？',
    'ColBERT 的 late interaction 与 MaxSim 是什么关系？',
    'ANCE 如何用 ANN 刷新全局难负例？',
    'RocketQA 的去噪策略具体做了什么？',
    '四篇论文分别用什么训练数据集？',
    'RocketQA 与 DPR 在跨批负例上的区别？',
]

rows = []
for qi, q in enumerate(QUERIES):
    for mode, wp in [('chunk', False), ('parent', True)]:
        ctxs = retrieve(q, wp)
        context = '\n\n'.join(f'[片段{i+1}] {t}' for i, t in enumerate(ctxs))
        answer, usage = llm(q, context)
        # 数字保真率：答案中出现的数字（去掉常见噪声）应能在上下文中找到
        ans_nums = set(re.findall(r'\d+\.?\d*', answer)) - {'1', '2', '3', '4', '5'}
        ctx_text = context
        faithful = sum(1 for n in ans_nums if n in ctx_text)
        fid = faithful / len(ans_nums) if ans_nums else None
        rows.append({'query': q, 'mode': mode, 'context_chars': len(context),
                     'answer_chars': len(answer),
                     'llm_tokens': usage.get('total_tokens'),
                     'numbers_in_answer': len(ans_nums),
                     'numeric_fidelity': round(fid, 3) if fid is not None else None})
        print(f"{q[:14]:16s} {mode:6s} ctx={len(context):6d} ans={len(answer):4d} "
              f"tokens={usage.get('total_tokens')} 数字保真={fid}")

summary = {}
for mode in ('chunk', 'parent'):
    rs = [r for r in rows if r['mode'] == mode]
    fids = [r['numeric_fidelity'] for r in rs if r['numeric_fidelity'] is not None]
    summary[mode] = {'context_chars': round(sum(r['context_chars'] for r in rs) / len(rs)),
                     'answer_chars': round(sum(r['answer_chars'] for r in rs) / len(rs)),
                     'numeric_fidelity': round(sum(fids) / len(fids), 3) if fids else None}
print(json.dumps(summary, ensure_ascii=False, indent=1))
json.dump({'summary': summary, 'rows': rows},
          io.open('evaluation/results/parent_child_ablation.json', 'w', encoding='utf-8'),
          ensure_ascii=False, indent=1)
print('→ parent_child_ablation.json')
