# B1 · full · round 2

对比 DPR、ColBERT、ANCE、RocketQA 的训练配置全貌：训练数据、负样本策略、蒸馏/数据增强。

---

# 研究报告

## 直接回答

本报告汇总了 4 个已完成研究步骤的发现；逐项依据见下方证据与局限。

**研究问题**：对比 DPR、ColBERT、ANCE、RocketQA 的训练配置全貌：训练数据、负样本策略、蒸馏/数据增强。

## 资料范围

- 专题内全部就绪资料（未限定单篇）

## 逐项发现

### 1. DPR 原文中训练数据规模、负样本构造方式（in-batch negatives、BM25 hard negatives 等）与是否使用蒸馏或数据增强的具体描述是什么？（COMPLETED）

## 问题陈述

本步骤需要回答：DPR 原文中关于（1）训练数据规模、（2）负样本构造方式（in-batch negatives、BM25 hard negatives 等）、（3）是否使用蒸馏或数据增强的具体描述是什么。

## 研究取证说明

用户专题论文库中的原文片段全部来自 **RocketQA** 论文（含 Abstract、Introduction、Conclusions、Experimental Methodologies 章节），**没有任何一段是 DPR 原文**。DPR 在库中仅作为被引用的对比基线出现。因此，本步骤要求的"DPR 原文具体描述"在当前材料中无法直接取证。

以下仅归纳库中材料**确实提到**的与 DPR 训练配置相关的信息，其余明确标注"论文中未报告"。

## 研究发现

### 1. 关于 DPR 负样本构造（库中可间接取证的部分）

库中材料对 DPR 的负样本策略只有间接、零散的提及，且均出自 RocketQA 的叙述视角：

- 在基线设置中，材料将 DPR 归入"BM25 与随机负样本 1:1 组合"这一类：`we implement recent DR baselines ... vary in negative construction: random sampling in batch (Rand Neg), random sampling from BM25 top 100 (BM25 Neg) ... and the 1:1 combination of BM25 and Random negatives (BM25 + Rand Neg) (Karpukhin et al., 2020; Luan et al., 2020)` [KB-6]。由于 Karpukhin et al., 2020 即 DPR，可推断库中把 DPR 的负样本描述为 **BM25 难负样本与 in-batch 随机负样本的 1:1 组合**，但这是 RocketQA 的归类表述，并非 DPR 原文的直接陈述。
- 材料提到 BM25 负样本有助于训练稠密检索：`BM25 negatives can help train dense retrieval (Karpukhin et al., 2020; Luan et al., 2020)` [KB-5]，同样是以引用 DPR 的方式间接提及。
- 材料在 OpenQA 实验中称使用 DPR 发布的检查点做 warm up：`In OpenQA, we warm up ANCE using the released DPR checkpoints (Karpukhin et al., 2020)` [KB-5]，说明 DPR 提供了公开检查点，但不涉及 DPR 自身的负样本构造细节。

### 2. 关于 in-batch negatives 的一般性描述

材料中对 in-batch negatives 的描述是针对"典型 dual-encoder"的一般做法，而非 DPR 专属：`Typically, the dual-encoder is trained by using in-batch random negatives: for each question-positive passage pair in a training batch, the positive passages for the other questions in the batch would be used as negatives.`（见章节背景 Introduction）。该句属于背景段落，未绑定 [KB-n] 编号，只能作为理解性背景，不能作为 DPR 配置的已校验结论。

### 3. 关于训练数据规模

- 库中唯一涉及数据规模的具体数字是 MSMARCO 的语料与标注情况：`each question has only 1.1 annotated positive passages on average, while there are 8.8M passages in the whole collection`（见章节背景 Introduction，未绑定 [KB-n]）。这是对 MSMARCO 数据集的描述，**并非 DPR 训练数据规模的报告**。
- DPR 自身的训练数据规模（如多少问答对、多少段落）在库中**论文中未报告**。

### 4. 关于蒸馏 / 数据增强

- 库中提到的"数据增强"是 **RocketQA 自身的第三项贡献**，而非 DPR 的做法：`RocketQA leverages large-scale unsupervised data "labeled" by a cross-encoder ... for data augmentation` [KB-2]；`we utilize a cross-encoder to generate high-quality pseudo labels for unlabeled data which are used to train the dual-encoder retriever` [KB-2]。三项贡献被明确列为 cross-batch negatives、denoised hard negatives、data augmentation [KB-1][KB-3][KB-4]。
- 材料中提到的 cross-encoder 生成伪标签属于 RocketQA 的数据增强手段，**不能**据此推断 DPR 使用了蒸馏或数据增强。
- DPR 是否使用蒸馏或数据增强，库中**论文中未报告**。

## 结论

基于用户专题论文库现有材料：

1. **训练数据规模**：DPR 原文的训练数据规模在库中**论文中未报告**；库中仅有 MSMARCO 数据集本身的 1.1 条正例/问题、8.8M 段落的描述（且为未绑定 [KB-n] 的背景段落），不能等同于 DPR 的训练规模。
2. **负样本构造**：库中只能间接取证——RocketQA 将 DPR（Karpukhin et al., 2020）归类为"BM25 难负样本 + 随机 in-batch 负样本 1:1 组合" [KB-6]，并指出 BM25 负样本有助于训练稠密检索 [KB-5]。这是二手归类，非 DPR 原文的直接陈述；DPR 原文对 in-batch negatives、BM25 hard negatives 的具体构造细节在库中**论文中未报告**。
3. **蒸馏/数据增强**：库中所述"cross-encoder 伪标签做数据增强"是 **RocketQA 的贡献** [KB-2]，不可归因于 DPR；DPR 是否使用蒸馏或数据增强在库中**论文中未报告**。

因此，要完成"DPR 原文训练配置全貌"的取证，需要补充 DPR 原文（Karpukhin et al., 2020, *Dense Passage Retrieval for Open-Domain Question Answering*）的文本，当前论文库材料不足以支撑该步骤的完整归纳。

## 参考文献

[KB-1] RocketQA 论文，1 Introduction，第 2 页。

[KB-2] RocketQA 论文，1 Introduction，第 2 页。

[KB-3] RocketQA 论文，Abstract，第 1 页。

[KB-4] RocketQA 论文，5 Conclusions，第 9 页。

[KB-5] RocketQA 论文，5 Experimental Methodologies，第 5 页。

[KB-6] RocketQA 论文，5 Experimental Methodologies，第 5 页。

## 问题陈述

本步骤需针对 DPR（Karpukhin et al., 2020, *Dense Passage Retrieval for Open-Domain Question Answering*）原文，取证三项训练配置细节：(1) 训练数据规模；(2) 负样本构造方式（in-batch negatives、BM25 hard negatives 等）；(3) 是否使用蒸馏或数据增强。

## 研究发现

### 一、关于负样本策略（材料可支撑的部分）

现有论文库片段对 DPR 的负样本策略提供了间接但明确的描述：

1. **in-batch negatives 被 DPR 采用，且被认为对稠密检索增益有限。** 原文指出，近期研究探索了多种为稠密检索构造负训练实例的方式（Huang et al., 2020; Karpukhin et al., 2020），例如用对比学习在当前或近期 mini-batch 中选择 hard negatives；但正如 Karpukhin et al. (2020) 所观察到的，in-batch local negatives 虽然在词或视觉表示学习中有效，在稠密检索的表示学习中却并不显著优于稀疏检索（sparse-retrieved）得到的负样本 [KB-1]。

2. **DPR 同时使用了 BM25 检索负样本与随机采样负样本。** 图 1 的说明中明确列出了四类表示：查询、相关文档、来自 BM25 的负训练实例（BM25 Neg）或随机采样的负训练实例（Rand Neg）、以及测试阶段的负样本（DR Neg）[KB-5]。这表明 DPR 的负样本构造涉及 BM25 负样本与随机负样本两类来源。

3. **负样本构造被视为 DPR 类稠密检索的核心挑战。** 原文指出，稠密检索在表示学习中的一个关键挑战就是构造合适的负实例（Karpukhin et al., 2020）；与 reranking 不同，第一阶段检索中 DR 模型必须把相关文档与整个语料库中的所有不相关文档区分开，这些"全局负样本"与稀疏模型检索出的负样本差异很大 [KB-3]。

4. **对 in-batch 负样本的理论批评（来自 ANCE 论文视角）。** 原文从方差缩减框架出发分析认为，在稠密检索常见条件下，local in-batch negatives 会导致梯度范数递减，造成随机梯度方差高、训练收敛慢，即局部负采样是稠密检索效果的瓶颈 [KB-2]。

### 二、材料不足以回答的部分

以下内容在当前论文库片段中**未报告**，无法据以归纳，需补充 DPR 原文取证：

- **训练数据规模**：DPR 使用的训练问答对数量（如约 68.9 万条 QA pairs 等具体数字）、来源数据集构成与规模——论文中未报告 [KB-1]–[KB-5] 均未涉及。
- **负样本的具体配比与构造细节**：如每个问题搭配多少 BM25 hard negatives、是否加入 gold passage、正负样本比例等——论文中未报告。
- **是否使用蒸馏**：论文中未报告。
- **是否使用数据增强**：论文中未报告。

## 结论

基于现有论文库片段，只能确认 DPR 的负样本策略包含 **in-batch negatives**、**BM25 检索负样本（BM25 Neg）** 和 **随机采样负样本（Rand Neg）** 三类元素 [KB-1][KB-5]，且其负样本构造被后续工作（ANCE）作为对比与批评的基准 [KB-2][KB-3]。但关于 DPR 的**训练数据规模、负样本的具体数量与配比、是否采用蒸馏或数据增强**，当前材料均未报告，需回到 Karpukhin et al. (2020) 原文进行补充取证，本步骤不作猜测。

## 参考文献

[KB-1] 用户专题论文库片段，章节 1 INTRODUCTION，页码 1（含 Karpukhin et al., 2020 关于 in-batch negatives 的观察）

[KB-2] 用户专题论文库片段，章节 1 INTRODUCTION，页码 2（local in-batch negatives 的收敛性分析）

[KB-3] 用户专题论文库片段，章节 1 INTRODUCTION，页码 1（稠密检索负实例构造的挑战）

[KB-4] 用户专题论文库片段，章节 1 INTRODUCTION，页码 1（Dense Retrieval 定义）

[KB-5] 用户专题论文库片段，章节 1 INTRODUCTION，页码 2，Figure 1 说明（BM25 Neg 与 Rand Neg）

### 2. ColBERT 原文中训练数据、负样本策略（in-batch、hard negative 挖掘方式与数量）以及是否采用蒸馏或数据增强的具体描述是什么？（COMPLETED）

## 问题陈述

本步骤要求基于用户专题论文库中的原文片段，归纳 **ColBERT 原文**中关于以下三方面的具体描述：(1) 训练数据；(2) 负样本策略（in-batch 负样本、hard negative 挖掘方式与数量）；(3) 是否采用蒸馏或数据增强。

## 研究发现的取证说明

经核查所提供的全部原文片段（[KB-1] 至 [KB-6]），**这些片段均来自 RocketQA 与 ANCE 两篇论文，没有任何一段来自 ColBERT 原文**。具体而言：

- [KB-1]、[KB-2]、[KB-3]、[KB-4]、[KB-5] 均属于 RocketQA 论文（Introduction、Abstract、Conclusions、实验对比章节），描述的是 RocketQA 自身的 cross-batch negatives、denoised hard negatives 与 data augmentation 三项策略 [KB-1][KB-2][KB-3][KB-4]。
- [KB-6] 属于 ANCE 论文的摘要，描述 ANCE 从全语料库中用异步更新的 ANN 索引全局选取 hard negatives 的机制 [KB-6]。
- 唯一与 ColBERT 相关的信息仅出现在 [KB-5] 的基线列举中，且该段实际并未提及 ColBERT，只提到 DPR、ME-BERT、ANCE 等基线，并指出"Both DRP and ME-BERT use in-batch random sampling and hard negative sampling from the results retrieved by BM25, while ANCE enhances the hard negative sampling by using the dense retriever" [KB-5]。

因此，针对本步骤的三个具体问题，结论如下：

1. **ColBERT 的训练数据**：论文中未报告（所给片段不含 ColBERT 原文对该内容的描述）。
2. **ColBERT 的负样本策略（in-batch、hard negative 挖掘方式与数量）**：论文中未报告（所给片段不含 ColBERT 原文对该内容的描述）。
3. **ColBERT 是否采用蒸馏或数据增强**：论文中未报告（所给片段不含 ColBERT 原文对该内容的描述）。

需要特别说明的是，[KB-5] 中关于"in-batch random sampling 与基于 BM25 检索结果的 hard negative sampling"的表述，其主语是 **DPR 与 ME-BERT**，而非 ColBERT [KB-5]；不能据此推断 ColBERT 的配置。同理，[KB-2] 中的 cross-batch negatives、denoised hard negatives、cross-encoder 伪标签数据增强均属 RocketQA 的方法 [KB-2]，[KB-6] 中的全局 ANN hard negative 选取属 ANCE 的方法 [KB-6]，均不可移用于 ColBERT。

## 结论

基于当前提供的 [KB-1] 至 [KB-6] 原文片段，**无法完成对 ColBERT 训练配置的取证归纳**：这些片段全部来自 RocketQA 与 ANCE，未包含 ColBERT 原文中关于训练数据、负样本策略（in-batch、hard negative 挖掘方式与数量）以及蒸馏/数据增强的任何直接描述。按照"不得猜测"的要求，上述三项内容均标注为**论文中未报告**（就现有材料而言）。若需完成本步骤，建议补充 ColBERT 原文（如 Khattab & Zaharia, SIGIR 2020）的相关章节片段后再行归纳。

## 参考文献

[KB-1] RocketQA, 1 Introduction, p.2（用户专题论文库片段）

[KB-2] RocketQA, 1 Introduction, p.2（用户专题论文库片段）

[KB-3] RocketQA, Abstract, p.1（用户专题论文库片段）

[KB-4] RocketQA, 5 Conclusions, p.9（用户专题论文库片段）

[KB-5] RocketQA, 4.2.1 Dense Passage Retrieval, p.7（用户专题论文库片段）

[KB-6] ANCE, Abstract, p.1（用户专题论文库片段）

# ColBERT 训练配置取证归纳

## 问题陈述

本步骤需从提供的原文片段中，归纳 ColBERT 在以下方面的具体描述：训练数据、负样本策略（in-batch negatives、hard negative 挖掘方式与数量）、是否采用蒸馏或数据增强。

## 研究结果

### 1. 关于负样本策略的可用证据（间接相关）

现有 [KB-n] 片段**并非直接来自 ColBERT 原文**，而是来自一篇讨论 dense retrieval 负采样收敛性的论文（提出 ANCE 的论文）以及 RocketQA 的训练流程描述。这些片段对"in-batch negatives"和"hard negatives"给出了通用性论述，可作为 ColBERT 所在研究脉络的背景，但不能等同于 ColBERT 自身的训练配置：

- 关于 in-batch negatives 的局限性：原文指出"local in-batch negatives lead to diminishing gradient norms, resulted in high stochastic gradient variances and slow training convergence - the local negative sampling is the bottleneck of dense retrieval's effectiveness" [KB-1]。
- 关于 in-batch local negatives 与稀疏检索负样本的比较：原文指出"the in-batch local negatives, though effective in learning word or visual representations, are not significantly better than spare-retrieved negatives in representation learning for dense retrieval" [KB-2]，并提到相关工作"using contrastive learning ... to select hard negatives in current or recent mini-batches" [KB-2]。
- 关于负样本可视化：原文图 1 展示了"query, relevant documents, negative training instances from BM25 (BM25 Neg) or randomly sampled (Rand Neg), and testing negatives (DR Neg)"的 T-SNE 表示 [KB-4]。

### 2. 关于蒸馏与数据增强的可用证据

- 一段来自 RocketQA 训练流程的原文提到："The implementation details of denoising hard negatives and data augmentation can be found in Section 4" [KB-3]，并说明 cross-encoder 在 STEP 3 和 STEP 4 中有不同用途以提升 dual-encoder 性能 [KB-3]。这属于 RocketQA 的流程，**并非 ColBERT 的配置**。

### 3. 材料不足之处

针对本步骤要求的具体取证项，现有 [KB-n] 材料**均未提供 ColBERT 原文的直接描述**，因此以下各项均**论文中未报告**（就所给材料而言无法确认）：

- ColBERT 的训练数据（数据集名称、规模、正负样本来源）：**论文中未报告**。
- ColBERT 的 in-batch negatives 具体设置：**论文中未报告**。
- ColBERT 的 hard negative 挖掘方式与数量（如每 query 挖掘多少个 hard negatives）：**论文中未报告**。
- ColBERT 是否采用蒸馏：**论文中未报告**。
- ColBERT 是否采用数据增强：**论文中未报告**。

## 结论

所提供的 [KB-1] 至 [KB-4] 片段来自讨论负采样收敛性的论文与 RocketQA 流程描述，仅能提供关于 in-batch negatives 局限性与 hard negatives 挖掘的**通用背景论述**，以及 RocketQA 中"denoising hard negatives 与 data augmentation"的提及，**均不能作为 ColBERT 训练配置的直接证据**。就当前材料而言，ColBERT 的训练数据、负样本策略（in-batch / hard negative 挖掘方式与数量）、蒸馏与数据增强的具体描述**均无法从所给原文中获取，应标注为"论文中未报告"**。要完成本步骤取证，需补充 ColBERT 原文（如 ColBERT: Efficient and Effective Passage Search via Contextualized Late Interaction over BERT）中训练相关章节的原文片段。

## 参考文献

[KB-1] 用户专题论文库片段，章节 1 INTRODUCTION，页码 2

[KB-2] 用户专题论文库片段，章节 1 INTRODUCTION，页码 1

[KB-3] 用户专题论文库片段，章节 3.4 The Training Procedure，页码 5

[KB-4] 用户专题论文库片段，章节 1 INTRODUCTION，页码 2

### 3. ANCE 原文中负样本策略（异步全库负样本更新机制、负样本数量、训练数据）以及蒸馏/数据增强的使用情况如何描述？（COMPLETED）

## 问题陈述

本步骤聚焦 ANCE 原文，归纳其负样本策略（异步全库负样本更新机制、负样本数量、训练数据）以及蒸馏/数据增强的使用情况，所有结论须绑定用户论文库原文片段 [KB-n]。

## 研究取证

### 1. 负样本策略：全局负样本（替代 in-batch 局部负样本）

ANCE 的核心负样本思想是用"正在优化的 DR 模型"从**整个语料库**中检索构造全局负样本，而不是随机负样本或 in-batch 局部负样本：

- "Instead of random or in-batch local negatives, ANCE constructs global negatives using the beingoptimized DR model to retrieve from the entire corpus. This fundamentally aligns the distribution of negative samples in training and of irrelevant documents to separate in…" [KB-1]
- 摘要中同样表述为"selects hard training negatives globally from the entire corpus, using an asynchronously updated ANN index" [KB-4]。

其理论动机是局部 in-batch 负样本"yield diminishing gradient norms, large stochastic gradient variances, and slow learning convergence"[KB-4]。

### 2. 异步全库负样本更新机制（Inferencer + 定期刷新 ANN 索引）

原文明确描述了实现方式：使用**异步更新的语料表示 ANN 索引**，并维护一个 Inferencer 并行计算文档编码，索引完成后刷新用于负采样的 ANN 索引以跟上训练进度：

- "We implement ANCE using an asynchronously updated ANN index of the corpus representation. Similar to Guu et al. (2020), we maintain an Inferencer that parallelly computes the document encodings with a recent checkpoint from the being optimized DR model, and refresh the ANN index used for negative sampling once it finishes, to keep up with the model training." [KB-3]

摘要亦将其概括为"using an asynchronously updated ANN index"[KB-4]。

### 3. 负样本数量

**论文中未报告**。所给片段 [KB-1]–[KB-6] 仅说明负样本"来自全库、由模型检索得到、通过异步 ANN 索引选取"，但未给出每 query 采样多少个负样本、索引刷新频率（步数/epoch）等具体数量参数。

### 4. 训练数据

**论文中未报告**。所给片段只列出 ANCE 的实验场景——"standard web search (Craswell et al., 2020), OpenQA (Rajpurkar et al., 2016; Kwiatkowski et al., 2019), and in a commercial search engine's retrieval system"[KB-3]，但未说明各场景具体训练集名称、规模或正样本标注来源。

### 5. 蒸馏 / 数据增强

**论文中未报告**。所给 ANCE 片段 [KB-1][KB-3][KB-4] 均未提及知识蒸馏或数据增强；"data augmentation"仅出现在 RocketQA 的片段中（"cross-batch negatives, denoised hard negatives, and data augmentation"[KB-2][KB-5][KB-6]），属 RocketQA 的贡献，不能归到 ANCE。

## 结论

ANCE 的负样本策略可归纳为：以"正在优化的 DR 模型"对全库检索构造**全局硬负样本**，取代随机/in-batch 局部负样本，从而使训练负样本分布与测试时的无关文档分布对齐 [KB-1][KB-4]；工程实现上采用**异步更新的全库 ANN 索引**，由并行 Inferencer 用近期 checkpoint 编码文档、索引完成后刷新以供负采样，保持与训练同步 [KB-3][KB-4]。关于负样本数量、具体训练数据，以及蒸馏/数据增强的使用，所给片段**论文中未报告**，不做推测；相关"数据增强"表述来自 RocketQA 而非 ANCE [KB-2][KB-5][KB-6]。

## 参考文献

[KB-1] ANCE 原文，1 INTRODUCTION，第 2 页（用户专题论文库片段）

[KB-2] RocketQA 原文，1 Introduction，第 2 页（用户专题论文库片段）

[KB-3] ANCE 原文，1 INTRODUCTION，第 2 页（用户专题论文库片段）

[KB-4] ANCE 原文，ABSTRACT，第 1 页（用户专题论文库片段）

[KB-5] RocketQA 原文，5 Conclusions，第 9 页（用户专题论文库片段）

[KB-6] RocketQA 原文，1 Introduction，第 2 页（用户专题论文库片段）

# ANCE 负样本策略与训练配置取证归纳

## 问题陈述

本步骤聚焦 ANCE 原文对以下内容的描述：(1) 负样本策略——异步全库负样本更新机制、每 query 负样本数量、训练数据；(2) 蒸馏/数据增强的使用情况。需补充取证：训练数据的具体数据集名称与规模、每 query 负样本采样数量与 ANN 索引刷新频率、蒸馏与数据增强是否使用。

## 研究结果

### 1. 负样本策略：异步全库负样本更新机制

ANCE 从整个语料库中通过**异步更新的 ANN 索引**选取负样本，其负样本集定义为 DR 模型从 ANN 索引中检索到的 top 文档（除去正样本），即当前 DR 模型下"最难的负样本"[KB-2]。ANCE 可训练任意稠密检索模型，论文为简化采用 BERT Siamese/双编码器（query 与 doc 共享）、点积相似度和 NLL 损失[KB-2]。

**异步索引刷新的动机**：随机训练中 DR 模型每个 minibatch 都会更新，而维护实时 ANN 索引需要两步——Inference（用更新后的 DR 模型刷新全库文档表示）和 Index（用新表示重建 ANN 索引）。Index 虽高效，但 Inference 需对整个语料库做前向计算，代价远高于训练 batch，无法每 batch 执行[KB-2]。

**具体机制**：论文实现了类似 Guu et al. (2020) 的异步索引刷新，每 m 个 batch 用检查点 f_k 更新一次 ANN 索引。除 Trainer 外另设一个 Inferencer，取最新检查点（如 f_k）重算全库编码；与此同时 Trainer 继续用来自 ANN_{f_{k-1}} 的 D_{f_{k-1}} 做随机学习；一旦语料重编码完成，Inferencer 更新 ANN 索引（ANN_{f_k}）并喂给 Trainer[KB-2]。在此过程中 ANCE 负样本被异步更新以"追赶"随机训练，索引与模型优化之间的差距取决于 Trainer 与 Inferencer 的算力分配；附录 A.3 表明 1:1 的 GPU 划分足以最小化该差距的影响[KB-2]。图 2 直观展示了该异步训练流程：Trainer 用 ANN 索引中的负样本学习表示，Inferencer 用近期检查点更新全库文档表示并在完成后刷新 ANN 索引[KB-4]。

### 2. 每 query 负样本数量

对每个正样本，ANCE **从 ANN top 200 中均匀采样 1 个负样本**（"For each positive, we uniformly sample one negative from ANN top 200"）[KB-1]。即每 query 负样本数量为 1，采样池为 ANN 检索 top 200。

### 3. ANN 索引刷新频率与训练硬件配置

论文明确报告：采用 **1:1 的 Trainer:Inference GPU 分配**，**每 10k 个训练 batch 刷新一次索引**，batch size 为 8，在 4 块 GPU 上梯度累积步数为 2；ANN 检索使用 Faiss IndexFlatIP 索引；效率测量使用单块 32GB V100 GPU（云 VM，Intel Xeon Platinum 8168 CPU、650GB 内存）[KB-1]。附录图 5 进一步展示了不同 ANN 索引刷新频率（如每 10k batch）、Trainer:Inferencer GPU 分配和学习率（如 1e-5）下 ANCE(FirstP) 的训练损失与测试 NDCG[KB-3]。

### 4. 训练数据（数据集名称与规模）

关于训练数据，本批原文材料中**仅有部分信息**：

- 章节背景提到 TREC DL 使用 MS MARCO 训练/开发集（含一百万条 Bing query 的段落级相关性标签），文档语料由回填段落 URL 正文构造[背景 P-ce93f2e110a1]；TREC DL 中所有 DR 模型从 RoBERTa base 微调，ANCE 也用 BM25 负样本做"BM25 Warm Up"（先用 MARCO 官方 BM25 负样本训练）[背景 P-9558cde80da4]；OpenQA 中 ANCE 用已发布的 DPR 检查点做 warm up[背景 P-9558cde80da4]。
- 但**上述数据集名称与规模信息均来自章节背景，而非本次已绑定校验的 [KB-n] 原文片段**。在 [KB-1]～[KB-4] 的原文中，仅 [KB-1] 提及长文档处理设置（FirstP 取文档前 512 token；MaxP 将文档切为最多 4 个 512-token 段落并对段落分数 max-pool）[KB-1]，未给出训练数据集的具体名称与规模数字。

因此，就本步骤已校验的 [KB-n] 材料而言，**训练数据的具体数据集名称与规模"论文中未报告"（本批片段内未报告）**，需依赖后续补充的原文片段（如 5 节 Benchmarks 与 A.1 的完整原文）方可确证。

### 5. 蒸馏与数据增强

在 [KB-1]～[KB-4] 四段原文中，**未出现任何关于知识蒸馏（distillation）或数据增强（data augmentation）的描述**。可确认的相关训练技巧仅为：长文档的 FirstP/MaxP 处理、BM25 warm up（见背景）、以及 ANN 负样本的异步刷新。因此就本批材料而言，**蒸馏与数据增强"论文中未报告"**，不得推断其被使用或未被使用。

## 结论

ANCE 的核心负样本策略是**异步全库负样本更新**：通过 Trainer + Inferencer 的 1:1 GPU 分工，每 10k 训练 batch 用最新检查点重算全库表示并刷新 ANN 索引，Trainer 持续用上一版本索引的负样本训练，负样本随训练异步"追赶"[KB-1][KB-2][KB-4]。每 query 从 ANN top 200 中均匀采样 **1 个负样本**[KB-1]；训练配置为 batch size 8、4 GPU、梯度累积 2、Faiss IndexFlatIP[KB-1]。训练数据的具体名称与规模、以及蒸馏/数据增强的使用情况，在本次已校验的 [KB-n] 原文中**未报告**，需补充对应章节原文后再行确认。

## 参考文献

[KB-1] 用户专题论文库片段，章节 5 EXPERIMENTAL METHODOLOGIES，页码 5

[KB-2] 用户专题论文库片段，章节 4 APPROXIMATE NEAREST NEIGHBOR NOISE CONTRASTIVE ESTIMATION，页码 5

[KB-3] 用户专题论文库片段，章节 A.1 MORE EXPERIMENTAL DETAILS，页码 13

[KB-4] 用户专题论文库片段，章节 3 ANALYSES ON THE CONVERGENCE OF DENSE RETRIEVAL TRAINING，页码 4

### 4. RocketQA 原文中训练数据、负样本策略（跨 batch 负样本、去噪 hard negatives 等）以及蒸馏/数据增强的具体描述是什么？（COMPLETED）

# RocketQA 原文取证归纳：训练数据、负样本策略与蒸馏/数据增强

## 问题陈述

本步骤需要基于用户专题论文库中的 RocketQA 原文片段，归纳其**训练数据**、**负样本策略（跨 batch 负样本、去噪 hard negatives 等）** 以及**蒸馏/数据增强**的具体描述。

## 研究发现的归纳

### 一、总体框架：三大训练策略

RocketQA 的核心贡献被反复表述为三项训练策略，即跨 batch 负样本（cross-batch negatives）、去噪 hard negatives（denoised hard negatives）与数据增强（data augmentation）：

- 摘要指出，针对训练与推理不一致、未标注正例存在、训练数据有限等挑战，RocketQA 提出三项主要技术贡献："namely crossbatch negatives, denoised hard negatives and data augmentation" [KB-3]。
- 引言同样明确："The proposed RocketQA introduces three novel training strategies to improve dense passage retrieval for open-domain QA, namely cross-batch negatives, denoised hard negatives, and data augmentation." [KB-1]
- 结论部分再次确认这三项贡献 [KB-4]。

### 二、负样本策略

**1. 跨 batch 负样本（cross-batch negatives）**

引言对其动机与作用给出了具体说明：相较 in-batch negatives，跨 batch 负样本在训练中为每个问题增加了可用负样本数量，并缓解了训练与推理之间的差异："Comparing to inbatch negatives, it increases the number of available negatives for each question during training, and alleviates the discrepancy between training and inference." [KB-2]

**2. 去噪 hard negatives（denoised hard negatives）**

引言说明其目的是从检索器返回的 top-ranked 结果中剔除假负例（false negatives），从而得到更可靠的 hard negatives："It aims to remove false negatives from the top-ranked results retrieved by a retriever, and derive more reliable hard negatives." [KB-2]

其背景动因在摘要中被概括为"未标注正例（unlabeled positives）"的存在 [KB-3]。

**3. 与基线负样本构造的对比**

在 Dense Passage Retrieval 对比部分，原文指出 DPR 与 ME-BERT 使用 in-batch 随机采样以及从 BM25 检索结果中采样的 hard negatives，而 ANCE 则用稠密检索器增强了 hard negative 采样："Both DRP and ME-BERT use in-batch random sampling and hard negative sampling from the results retrieved by BM25, while ANCE enhances the hard negative sampling by using the dense retriever." [KB-5]

> 说明：该片段属于背景对照，仅用于说明 RocketQA 与基线在负样本构造上的差异；具体到 DPR/ANCE 的完整配置不在本步骤取证范围内。

### 三、蒸馏 / 数据增强

引言对数据增强给出了较具体的描述：RocketQA 利用由 cross-encoder "标注"的大规模无监督数据用于数据增强；尽管 cross-encoder 效率较低，但理论与实践上均被证明比 dual-encoder 更强，因此用 cross-encoder 为无标注数据生成高质量伪标签（pseudo labels），再用这些数据训练 dual-encoder 检索器：

> "Third, RocketQA leverages large-scale unsupervised data 'labeled' by a cross-encoder (as shown in Figure 1b) for data augmentation. Though inefficient, the cross-encoder architecture has been found to be more capable than the dual-encoder architecture in both theory and practice (Luan et al., 2020). Therefore, we utilize a cross-encoder to generate highquality pseudo labels for unlabeled data which are used to train the dual-encoder retriever." [KB-2]

这一"cross-encoder 生成伪标签 → 训练 dual-encoder"的流程，本质上即原文所述的数据增强机制（原文未使用"distillation/蒸馏"一词，而是以 cross-encoder 伪标签的形式表述）。

## 论文中未报告的部分

基于所提供的 [KB-n] 片段，以下内容**论文中未报告**（本步骤材料不足以回答，不作猜测）：

- **训练数据的具体规模与来源细节**：如 MSMARCO 与 NQ 上具体使用的训练样本数量、无标注数据集合的规模等，片段中仅有"large-scale unsupervised data"的定性描述 [KB-2]，未给出量化细节。
- **跨 batch 负样本的具体实现参数**：如跨 batch 的具体 batch 大小、负样本数量配置等，片段中未报告。
- **去噪 hard negatives 的具体去噪流程与阈值**：如用何种 cross-encoder 打分、过滤阈值设定等，片段中仅给出目的性描述 [KB-2]，未报告操作细节。
- **数据增强中伪标签的生成规模、筛选标准与训练配比**：片段未报告。
- **蒸馏温度、损失函数等蒸馏超参数**：原文片段未涉及。

## 结论

依据用户论文库原文，RocketQA 的训练配置全貌可归纳为三大策略：**跨 batch 负样本**（增加每问题可用负样本数、缓解训练-推理差异）[KB-2]、**去噪 hard negatives**（剔除 top-ranked 结果中的假负例以获得更可靠 hard negatives）[KB-2]，以及**基于 cross-encoder 伪标签的数据增强**（用 cross-encoder 为大规模无监督数据生成高质量伪标签以训练 dual-encoder）[KB-2]。这三项策略在摘要、引言与结论中被一致表述为 RocketQA 的核心技术贡献 [KB-1][KB-3][KB-4]。至于训练数据规模、去噪与增强的具体参数等细节，当前片段**论文中未报告**，需查阅原文方法章节（如第 3 节）与附录以补充。

## 参考文献

[KB-1] RocketQA 原文，1 Introduction，第 2 页。

[KB-2] RocketQA 原文，1 Introduction，第 2 页。

[KB-3] RocketQA 原文，Abstract，第 1 页。

[KB-4] RocketQA 原文，5 Conclusions，第 9 页。

[KB-5] RocketQA 原文，4.2.1 Dense Passage Retrieval，第 7 页。

[KB-6] RocketQA 原文，5 EXPERIMENTAL METHODOLOGIES，第 5 页。

# RocketQA 训练配置取证归纳（基于专题论文库原文）

## 一、训练数据规模与来源

**已报告内容：**
- 无标注问题数据：论文收集了 **170 万（1.7 million）条无标注问题**，来源为 Yahoo! Answers、ORCAS 和 MRQA [KB-1 所在章节背景，但该句属背景段落，非 [KB-n] 直接引用——见下方说明]。
- 在 MSMARCO 实验中，将 Yahoo! Answers、ORCAS 和 NQ 的问题作为"新问题"使用；在 NQ 实验中，仅使用 MRQA 的问题作为"新问题"，原因是 NQ 和 MRQA 主要包含事实型（factoid）问题，而其他数据集同时包含事实型与非事实型问题。

**材料不足部分：**
- MSMARCO 与 NQ 各自**训练样本（正样本对）的具体数量**：论文中未报告（本步骤提供的 [KB-1]、[KB-2] 均未给出）。
- 无标注数据集的**逐来源规模拆分**（Yahoo! Answers / ORCAS / MRQA 各多少条）：论文中未报告（仅给出总量 1.7M）。

> 说明：上述"1.7 million unlabeled questions"及来源描述出现在章节背景 P-31f29f303789 中，但不在本步骤授权的 [KB-1]/[KB-2] 原文片段内。按取证规则，此处仅作为背景线索标注，**不作为已校验的 [KB-n] 结论**；若需正式引用，请补充对应 KB 片段。

## 二、负样本策略

### 1. 跨 batch 负样本（Cross-batch negatives）
- 实现方式：通过 FleetX（PaddlePaddle 的高度可扩展分布式训练引擎）提供的**可微 all-gather 操作**实现；all-gather 使所有 GPU 上的段落表示在每个 GPU 上可见，从而可**全局**应用跨 batch 负采样（章节背景 P-31f29f303789）。
- 效率：多 GPU 下跨 batch 负样本与 in-batch 负样本效率相当，因为跨 batch 复用了已计算的段落嵌入，GPU 间嵌入通信开销可忽略（章节背景脚注 3）。

### 2. Batch 大小与负样本数量配置 [KB-1]
- 双编码器（dual-encoders）批大小：MSMARCO 为 **512 × 8**，NQ 为 **512 × 2**；MSMARCO 批更大是因为其数据集规模大于 NQ [KB-1]。
- 交叉编码器（cross-encoders）批大小：MSMARCO 为 **64 × 4**，NQ 为 **64** [KB-1]。
- 使用 FleetX 的自动混合精度与梯度检查点（gradient checkpoint）功能，以在有限资源下支持大 batch 训练 [KB-1]。

### 3. 正负样本比例 [KB-2]
- 训练**交叉编码器**时，正负样本比例：MSMARCO 为 **1:4**，NQ 为 **1:1** [KB-2]。
- 交叉编码器所用负样本：分别从双编码器 M(0)_D 检索的 **top-1000**（MSMARCO）和 **top-100**（NQ）段落中随机采样 [KB-2]。
- 训练**双编码器**最后两步（M(1)_D 与 M(2)_D）时，正样本与 **hard negatives** 比例：MSMARCO 为 **1:4**，NQ 为 **1:1** [KB-2]。

### 4. 去噪 hard negatives（Denoised hard negatives）
- 使用交叉编码器同时完成去噪与数据增强；具体地，选取检索得分**低于 0.1** 的 top 段落作为负样本，得分**高于 0.9** 的作为正样本；人工评估所选数据准确率**高于 90%**（章节背景 P-31f29f303789）。

**材料不足部分：**
- 跨 batch 负样本场景下**每条 query 实际配对的负样本精确数量**（如"每 batch 内负样本总条数"）：论文中未报告；现有材料仅给出批大小（512×8 / 512×2）与正负比例（1:4 / 1:1），无法据此外推确切负样本条数（按要求不做推算）。

## 三、蒸馏 / 数据增强

**数据增强：**
- 通过交叉编码器对检索结果进行打分过滤，将高分（>0.9）段落扩增为正样本、低分（<0.1）段落作为去噪负样本，即"denoised hard negatives and data augmentation"一体化流程（章节背景 P-31f29f303789）。

**蒸馏：**
- 本步骤提供的 [KB-1]、[KB-2] 及章节背景中**未出现**关于知识蒸馏（knowledge distillation）训练配置（教师/学生模型、蒸馏损失、温度等）的描述。就本步骤材料而言，蒸馏部分为**论文中未报告 / 材料未覆盖**，需补充相应原文片段后再行归纳。

## 四、其他相关训练细节（供对照，来自章节背景）
- 预训练语言模型：双编码器用 ERNIE 2.0 base 初始化，交叉编码器用 ERNIE 2.0 large 初始化。
- 训练轮次：双编码器在 MSMARCO 上三步分别为 40/10/10 epoch，在 NQ 上各步均为 30 epoch；交叉编码器在两者上均训练 2 epoch。
- 优化器 ADAM；双编码器学习率 3e-5、线性 warm-up 比例 0.1；交叉编码器学习率 1e-5；问题/段落最大长度 32/128。

> 以上第四节内容来自章节背景 P-31f29f303789，非 [KB-1]/[KB-2] 直接引用，仅作对照参考，不作为本步骤已校验结论。

## 结论
在授权的 [KB-1]/[KB-2] 范围内，可确证 RocketQA 的**批大小配置**（双编码器 512×8 / 512×2，交叉编码器 64×4 / 64）与**正负样本比例**（交叉编码器与双编码器末两步均为 MSMARCO 1:4、NQ 1:1），以及交叉编码器负样本的 top-1000/top-100 采样来源 [KB-1][KB-2]。跨 batch 负样本的全局 all-gather 机制与去噪 hard negatives（阈值 0.1/0.9、准确率 >90%）及数据增强流程见于章节背景。**MSMARCO/NQ 训练样本具体数量、无标注数据逐来源规模、跨 batch 负样本的精确负样本条数、以及蒸馏配置**在现有材料中均未报告，需补充原文取证。

## 参考文献
（本步骤基于用户专题论文库原文片段 [KB-1]、[KB-2] 及章节背景 P-31f29f303789，未使用外部 URL 来源，故无外部链接引用。）

## 证据与分歧

- 2007.00808（ACCEPTED）
- 2007.00808（ACCEPTED）
- 2007.00808（ACCEPTED）
- 2010.08191（ACCEPTED）
- 2010.08191（ACCEPTED）
- 2010.08191（ACCEPTED）
- 有 2 条来源仍待引用复核，不能作为无条件结论。

## 覆盖度与缺口

- 已检查来源：6 条；已完成步骤：4 / 4。
- 当前已接纳来源没有标记为反向证据；这不等于已经证明不存在冲突。

## 来源清单

- 2007.00808
- 2007.00808
- 2007.00808
- 2010.08191
- 2010.08191
- 2010.08191