# ResearchPilot Evaluation Protocol · v1.0（冻结）

> **冻结规则**：本协议在开始任何正式评测运行之前提交。数据集划分、指标、对照条件、
> 模型配置一经冻结不得修改；若确需修改，版本号递增并在结果中同时报告新旧两版数字。
> **禁止**在看到结果后挑选样本或调整阈值再重跑。

## 0. 环境披露（每次运行记录）

- 模型：DeepSeek（api.deepseek.com），各角色模型名见 `model-config.json` 当时快照
- Embedding：BGE-M3 1024 维 L2（sidecar `/embedding-identity` 返回的 revision 一并记录）
- 温度：研究/回答 0.7，NLI/意图 0.1（代码内常量）
- 运行日期、git commit（fork 与产品仓）、Tavily 启用状态

## 1. D1 · 检索质量（LitSearch）

- **语料**：LitSearch 语料库导入为独立专题（Query→CS/SS 域分别导入）
- **指标**：Recall@5、Recall@10、MRR@10
- **对照**：BM25-only / Dense-only / Hybrid+RRF（现网实现）/ Hybrid+RRF+Rerank
- **协议转换声明**：LitSearch 原始协议为全库检索；本实现为专题内检索
  （导入该数据集全部语料到单一专题，Scope 等价于全库），转换方式随结果披露
- **运行**：597 查询全量，无抽样

### v1.1 修订（任何结果产生之前，2026-10-05）

- **语料子集**：官方 corpus_clean 为 64,183 篇（含引用网络干扰项）。本机 CPU
  embedding 吞吐 0.4 篇/s 不可行，故采用子集协议：**574 目标论文全量（597 查询
  去重共享）+ 5,000 随机干扰（冻结种子 20261005）= 5,574 篇**。
- **embedding 截断**：title+abstract 截断至 600 字符（查询为短自然语言）。
- 四个对照组在同一子集、同一截断下比较——**变体间结论有效**，但绝对数值
  不可与 LitSearch 官方榜单直比，报告时随数字披露本节。

## 2. D2 · 证据验证（SciFact + 跨语言）

- **语料**：SciFact claims（140 条）映射到导入的对应论文；跨语言对使用
  `evaluation/citation_cross_language_cases.json`
- **指标**：证据定位 Recall@10；Claim Support 混淆矩阵（vs SciFact 标注）；
  **错误确认率**（False SUPPORTED / 全部判定）；Quote 定位准确率
- **校准产出**：NLI 相关性阈值（当前 0.55 占位）的 PR 曲线与建议值
- **对照**：单层 n-gram（L1 only）/ 纯 embedding / 双层（现网实现）

## 3. D3 · 端到端研究（自建任务套件）

### 3.1 语料（冻结）

稠密检索家族 4 篇 open-access 论文（`evaluation/d3_corpus/`，已验签 %PDF）：

| ID | 论文 | 年份 |
|---|---|---|
| 2004.04906 | DPR: Dense Passage Retrieval (Karpukhin et al.) | 2020 |
| 2004.12832 | ColBERT: Late Interaction (Khattab et al.) | 2020 |
| 2007.00808 | ANCE: Global Hard Negatives (Xiong et al.) | 2020 |
| 2010.08191 | RocketQA: Cross-Batch Negatives + Denoising (Qu et al.) | 2020 |

导入为单一专题 `Evaluation · Dense Retrieval Family`，全部 READY 后冻结语料。

### 3.2 任务套件（12 条，类型分层）

**A. 单维对比（4 条）**——每条产出 4×1 矩阵列

| # | 任务 | 预期覆盖 |
|---|---|---|
| A1 | 各论文的检索/建模核心方法 | DPR=双编码器+最大内积；ColBERT= late interaction+MaxSim；ANCE=ANN 全局难负例；RocketQA=跨批负例+去噪+数据增强 |
| A2 | 训练数据与标注需求 | DPR=NQ/TriviaQA/WQ/TREC；ColBERT=MS MARCO+SQuAD；ANCE=MS MARCO；RocketQA=MS MARCO+人工去噪标注 |
| A3 | 负样本策略 | DPR=in-batch+BM25 难负例；ColBERT=BM25 挖掘+蒸馏；ANCE=异步索引全局难负例；RocketQA=跨批+去噪（人工验证） |
| A4 | 索引结构与检索效率特征 | DPR=FAISS 精确/近似；ColBERT=延迟交互免精确 NN；ANCE=定期刷新 ANN 索引；RocketQA=两阶段 |

**B. 多维对比（2 条）**——每条产出 4×2 矩阵

| # | 任务 | 列 |
|---|---|---|
| B1 | 各论文的训练配置全貌（数据+负样本+蒸馏/增强） | 训练数据、负样本策略 |
| B2 | 各论文报告的资源需求（算力/索引体积的定性描述） | 算力需求、索引体积 |

**C. 汇总（1 条）**

| # | 任务 | 预期 |
|---|---|---|
| C1 | 从 DPR 到 RocketQA 的负样本策略演进主线 | in-batch → BM25 难例 → 全局难例(ANN) → 跨批+去噪；需覆盖全部 4 篇 |

**D. 负例（3 条）**——正确行为：输出"未找到"且**不编造**

| # | 任务 | 设计依据 |
|---|---|---|
| D1 | 各论文分别使用什么缓存/消息队列框架部署在线服务？ | 4 篇均未报告部署中间件 |
| D2 | 这些实验使用的操作系统和数据库软件是什么？ | 均未报告 OS/DB |
| D3 | 各论文的通讯作者所属大学的建校年份？ | 传记信息不在论文中 |

**E. 定位（2 条）**——验证引用定位

| # | 任务 | 预期 |
|---|---|---|
| E1 | RocketQA 的去噪（denoising）策略在论文哪一部分？ | 定位到对应 section/页 |
| E2 | ANCE 如何刷新全局难负例的索引？ | 定位到 index refresh 机制段落 |

### 3.3 Ground Truth 规则

- 上表"预期覆盖"由模型知识起草，**打分前必须逐条对照 4 篇原文人工核验**
  （核验后定稿 `d3_ground_truth.json`，此后不再改动）
- 核验人：项目所有者（一次性，预计 1-2 小时）
- 任何 ground truth 修正记录在案（json 内 `revised: true` 标记）

### 3.4 评分规则

| 指标 | 定义 |
|---|---|
| 矩阵覆盖率 | 有"已支持"证据的应答单元格 / 全部应答单元格 |
| 无依据结论占比 | 被判 SUPPORTED 但 ground truth 不支持的单元格 / 全部 SUPPORTED 单元格 |
| 负例正确率 | 负例任务正确输出"未找到/未报告"的比例（编造即 0） |
| 引用抽验正确率 | 每任务抽 3 条引用人工核验"引用确实支持该句"的比例 |
| 运行健康度 | 进入终态比例；PARTIAL 时报告含未完成项披露的比例 |

### 3.5 对照组（每组 3 次取均值）

| 组 | 配置 |
|---|---|
| B0 | 无知识库，仅通用知识回答 |
| Basic RAG | 朴素切片 Top-5 问答（不走双层验证/矩阵/多步骤） |
| Full | ResearchPilot 深度研究全流程 |

### 3.6 运行约束

- 每次 Full 运行预算走现网配置（1200s 全局）；三次运行间不修改代码与配置
- 负例任务同样给 Full 预算——考察的是诚实停止，不是快速放弃
- 每次运行记录 token（usage/ 模块）与耗时

## 4. D4 · 可靠性清单（汇编，不重跑）

来自既有自动化验证：HITL 中断恢复、进程重启续跑、重复审批幂等、
优雅停止部分成果、断点续传上传、隔离无串扰——汇编为 README 可靠性清单。

## 5. D5 · 成本效率（随 D1-D3 采集）

- usage/ 模块输出：每 ask/run 的 prompt/completion tokens、耗时
- 报告 P50/P95；网络搜索触发率（两段式判断的触发比例）

## 6. 结果记录

- 每维度结果写入 `evaluation/results/<Dx>_<date>/`（原始输出 + 汇总表）
- 汇总表字段：指标、数值、对照组、运行日期、git commit、环境披露差异
