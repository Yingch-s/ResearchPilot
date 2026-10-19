# D2 评测器校准 · 独立二次判定一致率（V3 报告二）

> 2026-10-19 ｜ 方法：从 1108 条已判定样本分层抽样 60 条（SUPPORTED 30 / INSUFFICIENT 20 / CONTRADICTED 10），
> 由独立核验员**不看生产判定**逐条重判（只依据 claim + 被引摘要原文），再对比一致率。
> 口径声明：二次判定者为独立 LLM 核验会话（非项目所有者人工）——它校准的是「生产判定管线 vs
> 仔细独立判定」的一致性，不是绝对真值；绝对真值需项目所有者抽验。

## 结果

| 指标 | 数值 |
|---|---|
| **总体一致率** | **57/60 = 95.0%** |
| 二次判定分布 | {'SUPPORTED': 32, 'INSUFFICIENT': 20, 'CONTRADICTED': 8} |
| 生产判定分布 | {'SUPPORTED': 30, 'INSUFFICIENT': 20, 'CONTRADICTED': 10} |

## 分层一致率

| gold 分层 | 一致率 |
|---|---|
| NOT_ENOUGH_INFO | 25/26 = 96% |
| SUPPORT | 32/34 = 94% |

## 3 条分歧（全部是二次判定给出更细致的推理）

- `43` gold=NOT_ENOUGH_INFO：生产判 CONTRADICTED → 独立判 INSUFFICIENT；摘要仅证明CD34+ HPC可被HIV感染并建立潜伏感染，未给出易感细胞比例的高低，无法判定低比例易感的说法。
- `1393` gold=SUPPORT：生产判 INSUFFICIENT → 独立判 SUPPORTED；摘要表明miRNA建立靶mRNA阈值且below which protein production is highly repressed并充当开关与微调器，支
- `717` gold=SUPPORT：生产判 CONTRADICTED → 独立判 SUPPORTED；摘要指出跨物种聚集的高密度甲基化特异位于核小体连接区（低核小体占据）并直接排斥核小体，支持低核小体占据与高甲基化相关。

## 结论

1. **95% 一致率**支持生产判定管线的可信度：D2 主指标（假支持率 4.0%）的测量误差在 ±5% 量级
2. 3 条分歧均为边界 case（量词未被支持、相关≠支持、相关关系方向性）——不是系统性偏差
3. 待办：项目所有者对 60 条做最终人工抽验，可将「独立判定」升级为「人类真值」
