"""P1-D/V3：检索接口性能压测（与生成分离）。

三通道 × 并发 1/10/20/50；查询取自 LitSearch（D1 语料 5574 篇专题）。
输出：evaluation/results/perf_retrieval/bench.json
"""
import io
import json
import statistics
import sys
import threading
import time
import urllib.error
import urllib.request
from collections import defaultdict

API = 'http://localhost:8080'
TOPIC = 'aea33cf2-e4cb-4517-93ce-7161179fd847'  # D1 子集专题（5574 chunks）
CHANNELS = {'bm25': ['bm25'], 'dense': ['dense'], 'hybrid': ['bm25', 'dense']}
CONCURRENCIES = [1, 10, 20, 50]
N_PER_COMBO = 200  # 每组合请求数

queries = [json.loads(l)['query'] for l in io.open('evaluation/litsearch_queries.jsonl', encoding='utf-8')][:100]
queries = queries * (N_PER_COMBO // len(queries) + 1)

import http.cookiejar
jar = http.cookiejar.CookieJar()
op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
try:
    import os
    op.open(urllib.request.Request(f'{API}/api/rp/auth/login',
        data=json.dumps({'username': os.environ.get('RP_EVAL_USER', 'jinkai'),
                         'password': os.environ.get('RP_EVAL_PASSWORD', '')}).encode(),
        headers={'Content-Type': 'application/json'}), timeout=15).read()
    print('auth ok')
except Exception as e:
    print('auth skip:', str(e)[:60])


def one(channel, q):
    t0 = time.time()
    try:
        op.open(urllib.request.Request(f'{API}/api/rp/retrieve',
            data=json.dumps({'topic_id': TOPIC, 'query': q, 'top_k': 5,
                             'channels': CHANNELS[channel], 'with_parents': False}).encode(),
            headers={'Content-Type': 'application/json'}), timeout=120).read()
        return (time.time() - t0) * 1000, None
    except Exception as e:
        return (time.time() - t0) * 1000, str(e)[:60]


def pct(xs, p):
    xs = sorted(xs)
    return xs[min(len(xs) - 1, int(len(xs) * p / 100))] if xs else 0


results = {}
for channel in CHANNELS:
    for conc in CONCURRENCIES:
        lat, errs = [], defaultdict(int)
        idx = [0]
        lock = threading.Lock()
        def worker():
            while True:
                with lock:
                    if idx[0] >= N_PER_COMBO:
                        return
                    i = idx[0]; idx[0] += 1
                # 唯一查询后缀：击穿缓存（channelsKey 不在缓存键内的产品 bug 会使跨通道命中缓存）
                ms, err = one(channel, f"{queries[i % len(queries)]} #u{i}-{channel}")
                lat.append(ms)
                if err:
                    errs[err[:40]] += 1
        threads = [threading.Thread(target=worker) for _ in range(conc)]
        t0 = time.time()
        for t in threads: t.start()
        for t in threads: t.join()
        wall = time.time() - t0
        key = f'{channel}@{conc}'
        results[key] = {'p50': round(pct(lat, 50), 1), 'p95': round(pct(lat, 95), 1),
                        'p99': round(pct(lat, 99), 1), 'mean': round(statistics.mean(lat), 1),
                        'qps': round(len(lat) / wall, 1), 'errors': sum(errs.values())}
        print(f"{key:14s} P50 {results[key]['p50']:7.1f}ms  P95 {results[key]['p95']:7.1f}ms  "
              f"P99 {results[key]['p99']:7.1f}ms  QPS {results[key]['qps']:6.1f}  err {sum(errs.values())}")

json.dump(results, io.open('evaluation/results/perf_retrieval/bench.json', 'w', encoding='utf-8'),
          ensure_ascii=False, indent=2)
print('→ bench.json')
