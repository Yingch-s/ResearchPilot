"""D3 端到端评分（PROTOCOL §3.4）：GT 定稿后对 108 份报告 LLM 辅助判分。

指标：
- 矩阵覆盖率：报告覆盖 GT 预期事实点的比例（A/B/C/E 任务）
- 无依据结论占比：报告含 GT 与资料均不支持的具体断言（LLM 判）
- 负例正确率：D 任务明确表达「未找到/未报告」且无编造
- 运行健康度：终态比例（runs.jsonl 状态，纯代码）

输出：evaluation/results/d3_e2e/scores.jsonl + d3_scores.md
"""
import io
import json
import os
import re
import time
import urllib.error
import urllib.request

KEY = os.environ.get('EVAL_LLM_KEY', '')
OUT = 'evaluation/results/d3_e2e'

GT = json.load(io.open('evaluation/d3_ground_truth.json', encoding='utf-8'))
runs = [json.loads(l) for l in io.open(f'{OUT}/runs.jsonl', encoding='utf-8')]


def judge(report, gt_entry, task_type):
    """LLM 判分：覆盖率 + 无依据 + 负例正确性。返回 dict。"""
    points = gt_entry.get('expected', [])
    points_text = '\n'.join(f'{i+1}. {p}' for i, p in enumerate(points))
    negative = task_type == 'D'
    prompt = f"""你是严格的评测判分员。研究报告应当覆盖以下 {len(points)} 个「预期事实点」（每个点涉及一篇论文）：

{points_text}

{'特别注意：这是一道负例任务——正确行为是明确说明资料中未找到/未报告相关信息，且不得编造。' if negative else ''}
{'=' * 30}
研究报告全文：
{report[:60000]}
{'=' * 30}

只输出 JSON（不要 markdown）：
{{"covered": [已覆盖的事实点编号,如[1,2]], "partial": [部分覆盖的编号], "unsupported_claims": 报告中无法从研究过程得出的具体断言数量, "fabricated": 报告是否编造了明显不存在于资料的信息(true/false), "negative_correct": {'报告是否正确表达未找到/未报告(true/false，若编造则 false)' if negative else 'null'}}}"""
    body = json.dumps({'model': 'deepseek-chat', 'temperature': 0,
                       'messages': [{'role': 'user', 'content': prompt}], 'max_tokens': 500}).encode()
    r = urllib.request.urlopen(urllib.request.Request(
        'http://localhost:18090/v1/chat/completions', data=body,
        headers={'Content-Type': 'application/json', 'Authorization': f'Bearer {KEY}'}), timeout=180)
    d = json.loads(r.read().decode())
    raw = d['choices'][0]['message']['content'].strip()
    raw = re.sub(r'^```[a-zA-Z]*\s*', '', raw).replace('```', '').strip()
    return json.loads(raw), len(points)


def pct(xs, p):
    xs = sorted(xs)
    return xs[min(len(xs) - 1, int(len(xs) * p / 100))] if xs else 0


rows = []
out = io.open(f'{OUT}/scores.jsonl', 'w', encoding='utf-8')
for i, r in enumerate(runs):
    task_type = r['task'][0]
    gt = GT['tasks'].get(r['task'], {})
    rec = {'task': r['task'], 'round': r['round'], 'config': r['config'], 'status': r.get('status')}
    rep = (r.get('answer') or '')
    if r['config'] == 'full' and not rep:
        rep = (io.open(f"{OUT}/outputs/full_{r['task']}_r{r['round']}.md", encoding='utf-8').read()
               if __import__('os').path.exists(f"{OUT}/outputs/full_{r['task']}_r{r['round']}.md") else '')
    try:
        if not rep:
            rec.update({'error': 'empty answer'})
        else:
            j, total = judge(rep, gt, task_type)
            covered = len(j.get('covered', []))
            partial = len(j.get('partial', []))
            rec.update({
                'covered': covered, 'partial': partial, 'total': total,
                'coverage': round(covered / max(1, total), 3),
                'coverage_partial': round((covered + 0.5 * partial) / max(1, total), 3),
                'unsupported_claims': j.get('unsupported_claims', 0),
                'fabricated': j.get('fabricated'),
                'negative_correct': j.get('negative_correct'),
            })
    except Exception as e:
        rec.update({'error': str(e)[:200]})
    out.write(json.dumps(rec, ensure_ascii=False) + '\n')
    rows.append(rec)
    if (i + 1) % 12 == 0:
        print(f'{i+1}/{len(runs)}', flush=True)
out.close()

# ── 汇总 ──
def agg(rs, key_fn, flt):
    sel = [r for r in rs if flt(r) and 'error' not in r]
    if not sel:
        return None
    return key_fn(sel)


print('\n===== D3 评分汇总 =====')
for cfg in ('full', 'basic', 'b0'):
    rs = [r for r in rows if r['config'] == cfg and 'error' not in r]
    abe = [r for r in rs if r['task'][0] in 'ABCE']
    neg = [r for r in rs if r['task'][0] == 'D']
    cov = [r['coverage_partial'] for r in abe]
    unsup = sum(r.get('unsupported_claims', 0) for r in rs)
    fab = sum(1 for r in rs if r.get('fabricated'))
    neg_ok = sum(1 for r in neg if r.get('negative_correct') is True)
    term = sum(1 for r in rs if r.get('status') in ('COMPLETED', 'PARTIAL_COMPLETED'))
    print(f"\n[{cfg}] n={len(rs)}")
    if cov:
        print(f"  矩阵覆盖率(部分加权): mean {sum(cov)/len(cov):.3f}  P50 {pct(cov,50):.3f}")
    print(f"  无依据断言总数 {unsup}  编造报告数 {fab}")
    if neg:
        print(f"  负例正确率 {neg_ok}/{len(neg)}")
    print(f"  运行健康度(终态) {term}/{len(rs)}")

# 分任务 × full
print('\n[full 分任务]')
per_task = {}
for r in rows:
    if r['config'] == 'full' and 'error' not in r and r['task'][0] in 'ABCE':
        per_task.setdefault(r['task'], []).append(r['coverage_partial'])
for t in sorted(per_task):
    v = per_task[t]
    print(f"  {t}: 覆盖率 mean {sum(v)/len(v):.3f} ({len(v)} 轮)")
print('→ scores.jsonl')
