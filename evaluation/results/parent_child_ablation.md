# Parent-Child 上下文消融（V3 报告一 · 生成层）

> 2026-10-19 ｜ 6 查询 × 2 组（chunk 直供 / 父窗口扩展）× deepseek-chat temp 0.1

## 结果（均值）

| 口径 | chunk | parent 窗口 |
|---|---|---|
| 上下文字符 | 3,623 | **19,175（5.3×）** |
| 答案字符 | 614 | 890 |
| LLM tokens | ~1,100 | ~3,300（3×） |
| 数字保真率 | 0.900 | **0.952** |

## 结论

1. Parent 窗口用 **3× token 换 +5pp 数字保真**——事实保真收益真实但边际；
   对引用密集的对比型任务值得，对摘要型任务需权衡成本
2. 方法学教训：with_parents API 只附带 parents 字典（不改 text）——
   消融必须手动用父文本替换 chunk 文本；初版两组同上下文的「差异」是采样噪声
3. 单条答案含编造数字的 case（fid<1）集中在中文查询的 bm25 通道——与中文专项结论互证

## 复现

```bash
EVAL_LLM_KEY=... python evaluation/parent_child_ablation.py
```
