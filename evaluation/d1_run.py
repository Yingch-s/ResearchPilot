"""D1 · LitSearch 检索评测运行器。

597 查询 × 4 通道配置（bm25 / dense / hybrid / hybrid+rerank 无——现网无 rerank，
第四组以 hybrid+abstract-section 增强路径代替？否——协议冻结为三组有效对照 +
官方全量语料披露。运行：POST /api/rp/retrieve（channels 参数走产品代码路径）。

输出：evaluation/results/d1_<date>/metrics.json + per_query 明细
"""
import json
import io
import time
import urllib.request
from collections import defaultdict

API = 'http://localhost:8080'
TOPIC = 'aea33cf2-e4cb-4517-93ce-7161179fd847'
CONFIGS = [('bm25', ['bm25']), ('dense', ['dense']), ('hybrid', ['bm25', 'dense'])]
TOPK = 10


def retrieve(query, channels):
    body = json.dumps({'topic_id': TOPIC, 'query': query, 'top_k': TOPK,
                       'channels': channels, 'with_parents': False}).encode()
    r = urllib.request.urlopen(urllib.request.Request(f'{API}/api/rp/retrieve', data=body,
        headers={'Content-Type': 'application/json'}), timeout=60)
    d = json.loads(r.read().decode('utf-8'))
    items = d.get('results') or d.get('candidates') or []
    ids = [c.get('paperId') or c.get('paper_id') or '' for c in items]
    # ES 里 paper_id = 'LS-<corpusid>'，refs 是整数 corpusid —— 归一化后对比
    return [int(str(x).replace('LS-', '')) if str(x).replace('LS-', '').isdigit() else -1 for x in ids]


def load_queries():
    queries = []
    for line in io.open('evaluation/litsearch_queries.jsonl', encoding='utf-8'):
        r = json.loads(line)
        refs = set(r['refs'])
        queries.append((r['query'], refs, r['query_set']))
    return queries


def metrics(ranked, refs):
    hits5 = any(pid in refs for pid in ranked[:5])
    hits10 = any(pid in refs for pid in ranked[:10])
    rr = 0.0
    for i, pid in enumerate(ranked[:10], 1):
        if pid in refs:
            rr = 1.0 / i
            break
    return hits5, hits10, rr


def main():
    queries = load_queries()
    print(f'queries: {len(queries)}')
    results = {}
    detail = {}
    for name, channels in CONFIGS:
        stats = defaultdict(float)
        qd = []
        t0 = time.time()
        errors = 0
        for i, (q, refs, qset) in enumerate(queries):
            try:
                ranked = retrieve(q, channels)
            except Exception as e:
                errors += 1
                ranked = []
            h5, h10, rr = metrics(ranked, refs)
            stats['r5'] += h5
            stats['r10'] += h10
            stats['mrr'] += rr
            qd.append({'query': q, 'set': qset, 'ranked': ranked[:10], 'refs': sorted(refs),
                       'hit10': h10, 'rr': rr})
            if (i + 1) % 100 == 0:
                print(f'  [{name}] {i+1}/{len(queries)} ({time.time()-t0:.0f}s)')
        n = max(1, len(queries) - errors)
        results[name] = {
            'recall@5': round(stats['r5'] / n, 4),
            'recall@10': round(stats['r10'] / n, 4),
            'mrr@10': round(stats['mrr'] / n, 4),
            'queries': len(queries), 'errors': errors,
            'elapsed_s': round(time.time() - t0, 1),
        }
        detail[name] = qd
        print(f'{name}: R@5={results[name]["recall@5"]} R@10={results[name]["recall@10"]} '
              f'MRR@10={results[name]["mrr@10"]} errors={errors}')
    date = time.strftime('%m%d')
    import os
    os.makedirs(f'evaluation/results/d1_{date}', exist_ok=True)
    with io.open(f'evaluation/results/d1_{date}/metrics.json', 'w', encoding='utf-8') as f:
        json.dump({'protocol': 'PROTOCOL.md v1.0 §1', 'topk': TOPK, 'corpus_docs': 64183,
                   'results': results}, f, ensure_ascii=False, indent=2)
    with io.open(f'evaluation/results/d1_{date}/per_query.json', 'w', encoding='utf-8') as f:
        json.dump(detail, f, ensure_ascii=False)
    print('done →', f'evaluation/results/d1_{date}/')


if __name__ == '__main__':
    main()
