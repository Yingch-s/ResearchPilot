"""D2 · SciFact 数据拉取（corpus + claims，datasets-server 分页）"""
import json
import time
import urllib.request

BASE = 'https://datasets-server.huggingface.co/rows'


def fetch_all(config, split):
    rows, offset, length = [], 0, 100
    while True:
        url = f'{BASE}?dataset=allenai/scifact&config={config}&split={split}&offset={offset}&length={length}'
        with urllib.request.urlopen(url, timeout=60) as r:
            data = json.loads(r.read().decode('utf-8'))
        rows.extend(row['row'] for row in data.get('rows', []))
        total = data.get('num_rows_total', 0)
        offset += length
        if offset >= total or not data.get('rows'):
            break
        time.sleep(0.5)
    return rows


if __name__ == '__main__':
    corpus = fetch_all('corpus', 'full')
    with open('evaluation/scifact_corpus.jsonl', 'w', encoding='utf-8') as f:
        for r in corpus:
            f.write(json.dumps({'doc_id': str(r['doc_id']), 'title': r.get('title', ''),
                                'abstract': r.get('abstract', '')}, ensure_ascii=False) + '\n')
    print(f'corpus: {len(corpus)}')
    time.sleep(2)
    claims = fetch_all('claims', 'full')
    n_labeled = 0
    with open('evaluation/scifact_claims.jsonl', 'w', encoding='utf-8') as f:
        for r in claims:
            ev = r.get('evidence') or {}
            gold = None
            if ev:
                labels = [lbl for docs in ev.values() for lbl in docs]
                if 'SUPPORT' in labels:
                    gold = 'SUPPORT'
                elif 'REFUTE' in labels:
                    gold = 'REFUTE'
                elif 'NOT_ENOUGH_INFO' in labels:
                    gold = 'NOT_ENOUGH_INFO'
                n_labeled += 1
            f.write(json.dumps({'id': str(r['id']), 'claim': r['claim'],
                                'cited': str(r.get('cited_corpus_id', '') or ''),
                                'gold': gold, 'evidence': ev}, ensure_ascii=False) + '\n')
    print(f'claims: {len(claims)} (有标注 {n_labeled})')
