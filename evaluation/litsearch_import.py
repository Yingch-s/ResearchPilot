"""D1 · LitSearch 导入 v3 —— 大批量写入（绕过 ES 写请求 ~21s 固定开销）。

策略：先 GPU 全量 embed（本地缓存 JSONL，断点续），再每 2,000 篇一次大 bulk
（写入请求级固定 ~21s × 3 次 ≈ 1 分钟，而非 87 次 × 21s = 30 分钟）。
根因记录：ES 在 Docker bind mount 上，写入请求有 ~21s 固定延迟
（单条恒定 21.36s ×3 实测，与 payload 大小无关），数据在 D 盘 61% 用量。
"""
import io
import json
import os
import time
import urllib.request

TOPIC = 'aea33cf2-e4cb-4517-93ce-7161179fd847'
SUBSET = 'evaluation/litsearch/subset_corpus.jsonl'
STATE = 'evaluation/litsearch/import_state.json'
LOG = 'evaluation/litsearch/import.log'
EMBED_CACHE = 'evaluation/litsearch/embed_cache.jsonl'
BULK_SIZE = 2000
ES = 'http://localhost:9200'
SIDECAR = 'http://localhost:8100'


def log(msg):
    line = f'{time.strftime("%H:%M:%S")} {msg}'
    print(line, flush=True)
    with io.open(LOG, 'a', encoding='utf-8') as f:
        f.write(line + '\n')


def post_json(url, body, timeout=600):
    r = urllib.request.urlopen(urllib.request.Request(url, data=json.dumps(body).encode(),
        headers={'Content-Type': 'application/json'}), timeout=timeout)
    return json.loads(r.read().decode('utf-8'))


def load_subset():
    return [json.loads(line) for line in io.open(SUBSET, encoding='utf-8')]


def load_cache():
    cache = {}
    if os.path.exists(EMBED_CACHE):
        for line in io.open(EMBED_CACHE, encoding='utf-8'):
            r = json.loads(line)
            cache[r['cid']] = r['vec']
    return cache


def save_cache_item(cid, vec):
    with io.open(EMBED_CACHE, 'a', encoding='utf-8') as f:
        f.write(json.dumps({'cid': cid, 'vec': vec}) + '\n')


def embed(texts):
    d = post_json(f'{SIDECAR}/v1/embeddings', {'input': texts})
    return [item['embedding'] for item in d['data']]


def embed_all(subset, cache):
    pending = [r for r in subset if r['corpusid'] not in cache]
    log(f'embedding: 待嵌 {len(pending)} / {len(subset)}')
    batch = 128
    for i in range(0, len(pending), batch):
        rows = pending[i:i + batch]
        texts = [f"{r['title']}\n\n{r['abstract']}"[:600] for r in rows]
        vecs = embed(texts)
        for r, v in zip(rows, vecs):
            save_cache_item(r['corpusid'], v)
        log(f'embed 进度 {min(i + batch, len(pending))}/{len(pending)}')
        time.sleep(0.1)


def bulk_all(subset, cache):
    docs = []
    for r in subset:
        docs.append({
            '_id': f'LS-{r["corpusid"]}',
            'text': f"{r['title']}\n\n{r['abstract']}"[:600],
            'content_type': 'text',
            'topic_id': TOPIC,
            'paper_id': f'LS-{r["corpusid"]}',
            'document_version_id': 'litsearch-corpus',
            'parent_id': f'P-LS-{r["corpusid"]}',
            'section_path': 'title+abstract',
            'page': 1,
            'embedding': cache[r['corpusid']],
        })
    total = 0
    for i in range(0, len(docs), BULK_SIZE):
        part = docs[i:i + BULK_SIZE]
        lines = []
        for d in part:
            lines.append(json.dumps({'index': {'_index': 'rp_chunks_v1', '_id': d['_id']}}))
            lines.append(json.dumps({k: v for k, v in d.items() if k != '_id'}, ensure_ascii=False))
        t = time.time()
        r = urllib.request.urlopen(urllib.request.Request(f'{ES}/rp_chunks_v1/_bulk',
            data='\n'.join(lines).encode('utf-8') + b'\n',
            headers={'Content-Type': 'application/x-ndjson'}), timeout=600)
        resp = json.loads(r.read().decode('utf-8'))
        if resp.get('errors'):
            raise RuntimeError(f'bulk {i} 部分错误')
        total += len(part)
        log(f'bulk [{i}..{i+len(part)}] 完成 {total}/{len(docs)} ({time.time()-t:.1f}s)')
    urllib.request.urlopen(urllib.request.Request(f'{ES}/rp_chunks_v1/_refresh', data=b'{}',
        headers={'Content-Type': 'application/json'}))
    log(f'导入完成: ES 共 {total} 篇')


if __name__ == '__main__':
    subset = load_subset()
    cache = load_cache()
    log(f'启动: 子集 {len(subset)}, 缓存向量 {len(cache)}')
    embed_all(subset, cache)
    cache = load_cache()
    bulk_all(subset, cache)
