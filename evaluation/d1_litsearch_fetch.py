"""D1 · LitSearch 数据拉取（datasets-server 分页 JSON，无额外依赖）"""
import json
import time
import urllib.request

BASE = 'https://datasets-server.huggingface.co/rows'
OUT = 'evaluation/litsearch'


def fetch_all(config, split):
    rows, offset, length = [], 0, 100
    while True:
        url = f'{BASE}?dataset=princeton-nlp/LitSearch&config={config}&split={split}&offset={offset}&length={length}'
        for attempt in range(3):
            try:
                with urllib.request.urlopen(url, timeout=60) as r:
                    data = json.loads(r.read().decode('utf-8'))
                break
            except Exception as e:
                print(f'  retry {attempt + 1}: {e}')
                time.sleep(5)
        else:
            raise RuntimeError(f'fetch failed at offset {offset}')
        batch = [row['row'] for row in data.get('rows', [])]
        rows.extend(batch)
        print(f'  {config}: {len(rows)} rows')
        if len(batch) < length or offset + length >= data.get('num_rows_total', 0):
            break
        offset += length
        time.sleep(0.4)
    return rows


if __name__ == '__main__':
    corpus = fetch_all('corpus_clean', 'full')
    with open(f'{OUT}_corpus.jsonl', 'w', encoding='utf-8') as f:
        for r in corpus:
            f.write(json.dumps(r, ensure_ascii=False) + '\n')
    print(f'corpus: {len(corpus)}')
    time.sleep(2)
    queries = fetch_all('query', 'full')
    with open(f'{OUT}_queries.jsonl', 'w', encoding='utf-8') as f:
        for r in queries:
            f.write(json.dumps(r, ensure_ascii=False) + '\n')
    print(f'queries: {len(queries)}')
