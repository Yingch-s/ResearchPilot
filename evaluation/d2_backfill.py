"""D2 定位字段离线补算（verdict/reason 不变，只重算 cited_hit_top5 / cited_rel）。

根因：d2_run.py 里 cited（doc_id）与检索 ids（'SF-'+doc_id）前缀不匹配，
与 D1 的 paperId↔corpusid bug 同源。
"""
import io
import json
import time
import urllib.request

OUT = 'evaluation/results/d2_scifact'
API = 'http://localhost:8080'
SIDECAR = 'http://localhost:8100'
TOPIC = '96284057-9863-4c94-bf6a-8aae79b1cbf7'


def log(msg):
    print(time.strftime('%H:%M:%S'), msg, flush=True)


def post_json(url, body, timeout=120):
    r = urllib.request.urlopen(urllib.request.Request(url, data=json.dumps(body).encode(),
        headers={'Content-Type': 'application/json'}), timeout=timeout)
    return json.loads(r.read().decode('utf-8'))


def cosine(a, b):
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    return dot / (na * nb) if na and nb else 0.0


import math

recs = [json.loads(l) for l in io.open(f'{OUT}/claims_scored.jsonl', encoding='utf-8')]
recs = [r for r in recs if 'error' not in r and r.get('cited')]
corpus = {r['doc_id']: r for r in
          (json.loads(l) for l in io.open('evaluation/scifact_eval_corpus.jsonl', encoding='utf-8'))}
log(f'待补算 {len(recs)} 条')
done = 0
for r in recs:
    cited = r['cited']
    claim = None
    for line in io.open('evaluation/scifact_eval_claims.jsonl', encoding='utf-8'):
        c = json.loads(line)
        if c['id'] == r['id']:
            claim = c['claim']
            break
    ranked = post_json(f'{API}/api/rp/retrieve',
                       {'topic_id': TOPIC, 'query': claim, 'top_k': 5,
                        'channels': ['bm25', 'dense'], 'with_parents': False})
    cands = ranked.get('candidates', [])
    ids = [c.get('paperId', '').replace('SF-', '') for c in cands]
    hit = cited in ids
    cited_rel = None
    if cited in ids:
        idx = ids.index(cited)
        texts = [c.get('text', '')[:600] for c in cands]
        vecs = post_json(f'{SIDECAR}/v1/embeddings', {'input': [claim] + texts})
        vv = [item['embedding'] for item in vecs['data']]
        cited_rel = round(cosine(vv[0], vv[idx + 1]), 4)
    r['cited_hit_top5'] = hit
    r['cited_rel'] = cited_rel
    done += 1
    if done % 50 == 0:
        log(f'补算 {done}/{len(recs)}')

with io.open(f'{OUT}/claims_scored.jsonl', 'w', encoding='utf-8') as f:
    for r in recs:
        f.write(json.dumps(r, ensure_ascii=False) + '\n')
log('补算完成')
