"""D2 · 指标汇总 + NLI 阈值校准（H3 产出）。

输入：evaluation/results/d2_scifact/claims_scored.jsonl
输出：metrics.json + CALIBRATION.md
"""
import io
import json
from collections import Counter

OUT = 'evaluation/results/d2_scifact'


def main():
    recs = []
    for line in io.open(f'{OUT}/claims_scored.jsonl', encoding='utf-8'):
        r = json.loads(line)
        if 'error' not in r:
            recs.append(r)
    print(f'有效判定: {len(recs)} / {len(recs) + sum(1 for _ in io.open(f"{OUT}/claims_scored.jsonl", encoding="utf-8"))}')

    # 1) 混淆矩阵（gold × verdict）
    cm = Counter((r['gold'], r['verdict']) for r in recs)
    print('\n== 混淆矩阵 ==')
    for (g, v), n in sorted(cm.items()):
        print(f'  {g:18s} → {v:14s}: {n}')

    # 2) 主指标：假支持率（gold=NEI 判成 SUPPORTED）
    nei = [r for r in recs if r['gold'] == 'NOT_ENOUGH_INFO']
    sup = [r for r in recs if r['gold'] == 'SUPPORT']
    false_support = sum(1 for r in nei if r['verdict'] == 'SUPPORTED')
    honest_abstain = sum(1 for r in nei if r['verdict'] == 'INSUFFICIENT')
    print(f'\n== 假支持率（主指标）==')
    print(f'  NEI 总数 {len(nei)} | 误判 SUPPORTED {false_support} '
          f'({false_support/max(1,len(nei))*100:.1f}%) | 诚实弃权 {honest_abstain} '
          f'({honest_abstain/max(1,len(nei))*100:.1f}%)')

    # 3) SUPPORT 类准确率
    sup_correct = sum(1 for r in sup if r['verdict'] == 'SUPPORTED')
    print(f'  SUPPORT 总数 {len(sup)} | 判对 {sup_correct} ({sup_correct/max(1,len(sup))*100:.1f}%)')

    # 4) 定位召回（引用论文进 top-5）
    for g in ['SUPPORT', 'NOT_ENOUGH_INFO']:
        rs = [r for r in recs if r['gold'] == g and r.get('cited_rel') is not None or r['gold'] == g]
        hit = [r for r in recs if r['gold'] == g]
        hit5 = sum(1 for r in hit if r.get('cited_hit_top5'))
        print(f'  定位召回@5（{g}）: {hit5}/{len(hit)} = {hit5/max(1,len(hit))*100:.1f}%')

    # 5) 阈值校准：仅用 cited_rel 存在的记录，相关度阈值规则 vs LLM 判定
    pairs = [r for r in recs if r.get('cited_rel') is not None]
    print(f'\n== 相关性阈值校准（cited_rel 可用的 {len(pairs)} 条）==')
    best = None
    for th in [0.4, 0.45, 0.5, 0.55, 0.6, 0.65, 0.7, 0.75, 0.8]:
        ok, false_support = 0, 0
        nei_n = 0
        # gold(SciFact 词汇) → 我们的词汇等价类：SUPPORT→SUPPORTED / NOT_ENOUGH_INFO→INSUFFICIENT
        equiv = {'SUPPORT': 'SUPPORTED', 'NOT_ENOUGH_INFO': 'INSUFFICIENT', 'REFUTE': 'CONTRADICTED'}
        for r in pairs:
            pred = 'SUPPORTED' if r['cited_rel'] >= th else 'INSUFFICIENT'
            if pred == equiv.get(r['gold']):
                ok += 1
            if r['gold'] == 'NOT_ENOUGH_INFO' and pred == 'SUPPORTED':
                false_support += 1
            if r['gold'] == 'NOT_ENOUGH_INFO':
                nei_n += 1
        acc = ok / max(1, len(pairs))
        fs = false_support / max(1, nei_n)
        print(f'  阈值 {th}: 准确率 {acc:.1%} | NEI 假支持率 {fs:.1%}')
        if best is None or acc > best[1]:
            best = (th, acc, fs)
    print(f'  → 最优阈值 {best[0]}（准确率 {best[1]:.1%}，NEI 假支持率 {best[2]:.1%}）'
          f' vs 当前配置 0.55')

    llm_ok = sum(1 for r in pairs if
                 (r['verdict'] == 'SUPPORTED') == (r['gold'] == 'SUPPORT') or
                 (r['verdict'] == 'INSUFFICIENT') == (r['gold'] == 'NOT_ENOUGH_INFO'))
    print(f'  LLM 判定（同子集）准确率: {llm_ok/max(1,len(pairs))*100:.1f}%')

    # 汇总落盘
    summary = {
        'valid': len(recs),
        'confusion': {f'{g}->{v}': n for (g, v), n in sorted(cm.items())},
        'false_support_rate_nei': round(false_support / max(1, len(nei)), 4),
        'honest_abstain_rate_nei': round(honest_abstain / max(1, len(nei)), 4),
        'support_accuracy': round(sup_correct / max(1, len(sup)), 4),
        'best_relatedness_threshold': best[0] if best else None,
        'best_threshold_accuracy': round(best[1], 4) if best else None,
    }
    with io.open(f'{OUT}/metrics.json', 'w', encoding='utf-8') as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    print(f'→ {OUT}/metrics.json')


if __name__ == '__main__':
    main()
