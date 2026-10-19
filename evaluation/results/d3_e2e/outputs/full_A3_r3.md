# A3 · full · round 3

DPR、ColBERT、ANCE、RocketQA 各自的负样本（hard negative）策略是什么？

---

# 研究报告

## 直接回答

本报告汇总了 4 个已完成研究步骤的发现；逐项依据见下方证据与局限。

**研究问题**：DPR、ColBERT、ANCE、RocketQA 各自的负样本（hard negative）策略是什么？

## 资料范围

- 专题内全部就绪资料（未限定单篇）

## 逐项发现

### 1. DPR 如何构造与使用硬负样本？（COMPLETED）

# DPR 硬负样本策略：取证归纳

## 问题陈述

本步骤聚焦 DPR（Karpukhin et al., 2020）如何构造与使用硬负样本（hard negative）。需基于用户专题论文库原文片段 [KB-1] 至 [KB-6] 归纳，材料不足处明确标注“论文中未报告”。

## 研究结果

### 1. DPR 的负样本构造方式：BM25 检索结果 + in-batch 随机采样

论文库中最直接的表述来自 RocketQA 论文对基线方法的描述：**“Both DRP and ME-BERT use in-batch random sampling and hard negative sampling from the results retrieved by BM25”**——即 DPR（原文拼作 DRP）与 ME-BERT 均采用“批内随机采样”与“从 BM25 检索结果中进行硬负样本采样”两种方式 [KB-4]。这说明 DPR 的硬负样本来源是 **BM25 的检索结果**，而非稠密检索器自身的检索结果（后者是 ANCE 的改进点，原文对比称 “ANCE enhances the hard negative sampling by using the dense retriever” [KB-4]）。

### 2. 硬负样本的使用：与随机负样本配合，并可用 BM25 负样本预热

- 在实验设置中，论文库记载 “BM25 negatives can help train dense retrieval (Karpukhin et al., 2020; Luan et al., 2020)”，并据此设置了 “BM25 Warm Up” 训练方式（先用 MARCO 官方 BM25 负样本训练 DR 模型）[KB-5]。这佐证了 DPR 类方法使用 BM25 负样本进行训练的做法。
- 在 OpenQA 实验中，“we warm up ANCE using the released DPR checkpoints (Karpukhin et al., 2020)” [KB-5]，说明 DPR 检查点本身即承载了其负样本训练成果，但该句只涉及 ANCE 的预热，未进一步说明 DPR 内部负样本配比。
- 关于 DPR 同时使用随机负样本与 BM25 硬负样本的组合方式（如每问题配比数量、采样 top-k 的具体 k 值），论文库中**未报告**。

### 3. 硬负样本策略的局限：假阴性问题

论文库指出，从 top-k 检索结果中选取少量硬负样本的既有机制（引用了包括 Karpukhin et al., 2020 在内的工作）存在假阴性风险：“previous work tried to design specific mechanisms for selecting a few hard negatives from the top-k retrieved candidates (Gillick et al., 2019; Wu et al., 2020; Karpukhin et al., 2020; Luan et al., 2020; Xiong et al., 2020). However, it suffers from the false negative issue” [KB-1 章节背景，对应引言论述]。其根源是大量未标注正例：MSMARCO 中每题平均仅 1.1 个标注正例，而候选段落达 8.8M，人工检查发现 top 检索结果中 70% 未标注者实为正例 [章节背景 P-140621b255be]。RocketQA 正是针对此提出 “denoised hard negatives”，以“remove false negatives from the top-ranked results retrieved by a retriever, and derive more reliable hard negatives” [KB-2]。

### 4. 与 ANCE 的对照（理解 DPR 策略的边界）

ANCE 论文摘要指出稠密检索的学习瓶颈源于“uninformative negatives sampled locally in batch”，并提出“selects hard training negatives globally from the entire corpus, using an asynchronously updated ANN index” [KB-3]。这一对照反衬出 DPR 的负样本是**批内局部采样 + BM25 检索结果采样**，而非全语料全局选取 [KB-3][KB-4]。

## 结论

根据论文库材料，DPR 的硬负样本策略可归纳为：**硬负样本取自 BM25 的检索结果，并与批内随机负样本（in-batch random negatives）共同用于双编码器训练** [KB-4]；BM25 负样本被证实有助于稠密检索训练，并可作为预热手段 [KB-5]。该策略的已知缺陷是 top-k 检索结果中存在大量假阴性（未标注正例），这也是 RocketQA 提出“去噪硬负样本”的动因 [KB-2][KB-1 章节背景]。

需说明的空白：DPR 硬负样本的具体数量配比、BM25 top-k 的 k 值、以及每问题负样本采样细节，**论文库中未报告**，本步骤不作推测。

## 参考文献

[KB-1] 用户专题论文库片段，章节 1 Introduction，页码 2（RocketQA 论文）

[KB-2] 用户专题论文库片段，章节 1 Introduction，页码 2（RocketQA 论文）

[KB-3] 用户专题论文库片段，章节 ABSTRACT，页码 1（ANCE 论文）

[KB-4] 用户专题论文库片段，章节 4.2.1 Dense Passage Retrieval，页码 7（RocketQA 论文）

[KB-5] 用户专题论文库片段，章节 5 EXPERIMENTAL METHODOLOGIES，页码 5（ANCE 论文）

[KB-6] 用户专题论文库片段，章节 Abstract，页码 1（RocketQA 论文）

# ColBERT 的负样本（hard negative）策略：基于论文库原文的取证归纳

## 问题重述

本步骤要求补充取证 ColBERT 的负样本（hard negative）构造与使用策略。以下归纳严格基于用户专题论文库中的原文片段 [KB-1] 至 [KB-6]，凡原文未报告的内容均明确标注"论文中未报告"。

## 研究发现的归纳

### 1. 论文库中与 ColBERT 负样本直接相关的原文

首先需要说明：在所给 [KB-1] 至 [KB-6] 六段原文中，**没有任何一段直接描述 ColBERT 的负样本（hard negative）构造方法**。与 ColBERT 相关的原文 [KB-1]、[KB-2]、[KB-4]、[KB-5] 均聚焦于其"late interaction"架构、效率优势与端到端检索能力，而非训练时的负样本采样策略。具体而言：

- [KB-2] 仅说明 ColBERT 采用 late interaction 架构、可离线预计算文档表示、并借助向量相似度索引实现端到端检索，未涉及训练负样本。
- [KB-5] 为结论段，强调 ColBERT 相比既有 BERT 类模型"more than 170× faster and requires 14,000× fewer FLOPs/query"，同样未报告负样本策略。
- [KB-1] 与 [KB-4] 说明 ColBERT 可从整个集合直接检索 top-1000 结果、可用于全集合向量相似度检索，但这是检索范式，而非负样本构造。

因此，**ColBERT 自身的 hard negative 策略在所提供的论文库原文中未报告**。

### 2. 论文库中确实涉及"硬负样本"的原文（但属于其他方法）

论文库中真正描述 hard negative 采样机制的是 [KB-6]，但其归属为 **RocketQA** 的多阶段训练流程，而非 ColBERT：

> "STEP 3: Train a dual-encoder M (1) D by further introducing denoised hard negative sampling on DL. Regarding to each question q ∈ QL, the hard negatives are sampled from the top passages retrieved by M (0) D from C, and only the passages that are predicted as negatives by the cross-encoder MC with high confidence scores will be selected." [KB-6]

即：对每个问题 q，硬负样本取自上一阶段双编码器 M(0)D 从整个语料 C 中检索出的 top passages，并且只有被交叉编码器 MC 以高置信度判定为负的段落才会被选中（去噪硬负采样）。

此外，[KB-3] 讨论的是"dense retrieval 训练中负采样"的理论分析（in-batch 局部负样本导致梯度范数衰减、收敛变慢），这属于 ANCE 一文的论述背景，同样**不是 ColBERT 的负样本策略**。

### 3. 章节背景的限定作用

章节背景（如 INTRODUCTION、ABSTRACT、CONCLUSIONS 及 RocketQA 的 3.4 节）仅用于理解上下文条件，不能作为 ColBERT 负样本策略的已校验结论。背景中出现的 ColBERT 相关文字（late interaction、top-1000 全集合检索等）与 [KB-1][KB-2][KB-4][KB-5] 一致，均不涉及负样本采样。

## 结论

基于所提供的论文库原文，**无法归纳出 ColBERT 的 hard negative 策略**——[KB-1] 至 [KB-6] 中没有任何片段报告 ColBERT 训练时如何构造或使用硬负样本。论文库中唯一明确描述"denoised hard negative sampling"的原文 [KB-6] 属于 RocketQA 的训练流程（从 M(0)D 检索的 top passages 中，经交叉编码器 MC 高置信度判负后选取），不可移用于 ColBERT。

若需回答"ColBERT 的负样本策略"，需补充 ColBERT 原论文（SIGIR '20）中训练章节的相关原文；在现有材料范围内，该问题应标注为"论文中未报告"。

## 参考文献

[KB-1] ColBERT 论文，1 INTRODUCTION，页码 2（用户专题论文库）

[KB-2] ColBERT 论文，ABSTRACT，页码 1（用户专题论文库）

[KB-3] 1 INTRODUCTION，页码 2（用户专题论文库）

[KB-4] ColBERT 论文，1 INTRODUCTION，页码 3（用户专题论文库）

[KB-5] ColBERT 论文，5 CONCLUSIONS，页码 9（用户专题论文库）

[KB-6] RocketQA 论文，3.4 The Training Procedure，页码 5（用户专题论文库）

### 2. ColBERT 的负样本策略是什么？（COMPLETED）

## 问题陈述

本步骤要回答的问题是：**ColBERT 的负样本（hard negative）策略是什么？**

## 研究取证与归纳

### 1. 论文库中与 ColBERT 相关的原文

在提供的专题论文库片段中，唯一直接涉及 ColBERT 的原文是 [KB-5]，其内容为 ColBERT 的摘要，主要介绍 ColBERT 的**延迟交互（late interaction）架构**、用 BERT 独立编码查询与文档、可离线预计算文档表示、以及借助向量相似度索引实现端到端检索等效率与效果特性。原文明确写道：ColBERT "introduces a late interaction architecture that independently encodes the query and the document using BERT and then employs a cheap yet powerful interaction step that models their fine-grained similarity" [KB-5]。

### 2. 关于 ColBERT 负样本策略的核查结果

通读 [KB-1] 至 [KB-6] 全部片段后可以确认：**这些材料均未报告 ColBERT 的负样本（hard negative）构造策略**。

- [KB-5]（ColBERT 摘要）通篇聚焦于架构设计与检索效率/效果，**没有提及任何负样本采样方式**（如 in-batch negatives、BM25 hard negatives 等）。
- 其余片段 [KB-1]、[KB-2]、[KB-3]、[KB-4]、[KB-6] 讨论的是 **ANCE**（[KB-1]、[KB-3]、[KB-6]）与 **RocketQA**（[KB-2]、[KB-4]）的负样本机制，而非 ColBERT。
- 其中 [KB-4] 在列举稠密检索基线时提到 "Both DRP and ME-BERT use in-batch random sampling and hard negative sampling from the results retrieved by BM25, while ANCE enhances the hard negative sampling by using the dense retriever" [KB-4]，但该句**只涉及 DPR、ME-BERT 与 ANCE，并未提及 ColBERT**。

因此，就本步骤所问的"ColBERT 的负样本策略"，**论文库中未报告**，无法据现有材料给出有原文依据的归纳。

### 3. 材料中可对照的负样本机制（供后续对比参考，非 ColBERT）

为避免混淆，明确区分材料中实际报告的负样本策略归属：

- **ANCE**：不用随机或批内局部负样本，而是用正在优化的稠密检索模型从整个语料库中检索来构造**全局负样本**（"Instead of random or in-batch local negatives, ANCE constructs global negatives using the beingoptimized DR model to retrieve from the entire corpus"）[KB-1]；并使用**异步更新的 ANN 索引**从全语料中选择硬负样本（"selects hard training negatives globally from the entire corpus, using an asynchronously updated ANN index"）[KB-3]。
- **RocketQA**：提出三种训练策略，包括**跨批负样本（cross-batch negatives）、去噪硬负样本（denoised hard negatives）与数据增强（data augmentation）**（"cross-batch negatives, denoised hard negatives, and data augmentation"）[KB-2]。
- **DPR / ME-BERT**：使用批内随机采样，以及从 BM25 检索结果中进行硬负样本采样（"Both DRP and ME-BERT use in-batch random sampling and hard negative sampling from the results retrieved by BM25"）[KB-4]。

以上均**不属于 ColBERT**，仅作为对照说明。

## 结论

针对本步骤"ColBERT 的负样本策略是什么"，**所提供的论文库材料中未报告**。现有片段中唯一涉及 ColBERT 的 [KB-5] 仅介绍其延迟交互架构与效率特性，未涉及任何负样本构造方式；其余片段讨论的是 ANCE、RocketQA、DPR/ME-BERT 的负样本机制。若要回答 ColBERT 的负样本策略，需补充 ColBERT 原文中关于训练（如使用 BM25 top-k 作为 hard negatives、in-batch negatives 等）的相关章节片段，本步骤在现有材料下不作推测。

## 参考文献

[KB-1] ANCE 论文 1 INTRODUCTION（页码 2）

[KB-2] RocketQA 论文 1 Introduction（页码 2）

[KB-3] ANCE 论文 ABSTRACT（页码 1）

[KB-4] RocketQA 论文 4.2.1 Dense Passage Retrieval（页码 7）

[KB-5] ColBERT 论文 ABSTRACT（页码 1）

[KB-6] ANCE 论文 6.1 EFFECTIVENESS AND EFFICIENCY（页码 6）

## 问题陈述

本步骤聚焦于 **ColBERT 的负样本（hard negative）策略**，需要基于用户专题论文库中的原文片段，归纳 ColBERT 在训练时如何构造负样本，尤其是 BM25 top-k 作为 hard negatives、in-batch negatives 的具体做法。

## 研究发现

### 1. 关于负样本构造方式的总体讨论

论文库原文指出，近期研究探索了多种为稠密检索构造负训练实例的方式，并提到用对比学习在当前或近期 mini-batch 中选择 hard negatives：

> "Recent research explored various ways to construct negative training instances for dense retrieval (Huang et al., 2020; Karpukhin et al., 2020)., e.g., using contrastive learning (Faghri et al., 2017; Oord et al., 2018; He et al., 2019; Chen et al., 2020a) to select hard negatives in current or recent mini-batches." [KB-1]

但需注意，该段落同时指出 in-batch local negatives 对稠密检索的表征学习并不显著优于稀疏检索得到的负样本：

> "the in-batch local negatives, though effective in learning word or visual representations, are not significantly better than spare-retrieved negatives in representation learning for dense retrieval." [KB-1]

### 2. 作为对比基线出现的负样本类型

在实验方法部分，原文列举了基于同一 BERT-Siamese 架构、仅负样本构造方式不同的稠密检索基线，包括：批内随机采样（Rand Neg）、从 BM25 top 100 随机采样（BM25 Neg），以及 BM25 与随机负样本 1:1 组合（BM25 + Rand Neg），还有使用批内最难负样本的对比学习/NCE（NCE Neg）：

> "We implement recent DR baselines that use the same BERT-Siamese, but vary in negative construction: random sampling in batch (Rand Neg), random sampling from BM25 top 100 (BM25 Neg) (Lee et al., 2019; Gao et al., 2020b) and the 1:1 combination of BM25 and Random negatives (BM25 + Rand Neg) (Karpukhin et al., 2020; Luan et al., 2020). We also compare with contrastive learning/Noise Contrastive Estimation, which uses hardest negatives in batch (NCE Neg) (Gutmann & Hyvärinen, 2010; Oord et al., 2018; Chen et al., 2020a)." [KB-2]

### 3. In-batch negatives 的训练机制

原文对 in-batch negatives 技巧作了说明：假设单 GPU 上一个 mini-batch 有 B 个问题、每个问题一个正段落，则每个问题可额外与 B-1 个负样本（即其余问题的正段落）配对，无需额外采样：

> "Assume that there are B questions in a mini-batch on a single GPU, and each question has one positive passage. With the in-batch negative trick, each question can be further paired with B-1 negatives (i.e., positive passages of the rest questions) without sampling additional negatives. In-batch negative training is a memory-efficient way to reuse the examples already loaded in a mini-batch rather than sampling new negatives, which increases the number of negatives for each question." [KB-6]

### 4. In-batch 训练下不同负样本选择的影响

消融实验显示，在标准 1-of-N 训练设置下，负样本的选择（随机、BM25 或 gold passages）在 k ≥ 20 时对 top-k 准确率影响不大：

> "The top block is the standard 1-of-N training setting, where each question in the batch is paired with a positive passage and its own set of n negative passages (Eq. (2)). We find that the choice of negatives - random, BM25 or gold passages (positive passages from other questions) - does not impact the top-k accuracy much in this setting when k ≥ 20." [KB-4]

### 5. 加入额外 BM25 "hard" negatives

消融实验进一步探索了在 in-batch negative 训练中加入额外的 "hard" 负段落——即对问题有较高 BM25 分数但不含答案字符串的段落，这些段落被用作同一批次中所有问题的负样本。结果显示加入 1 个 BM25 负样本显著提升，但加入 2 个则不再有进一步帮助：

> "Finally, we explore in-batch negative training with additional 'hard' negative passages that have high BM25 scores given the question, but do not contain the answer string (the bottom block). These additional passages are used as negative passages for all questions in the same batch. We find that adding a single BM25 negative passage improves the result substantially while adding two does not help further." [KB-4]

对应的表注也说明 G.+BM25(1) 和 G.+BM25(2) 分别表示 in-batch 训练中加入 1 或 2 个额外 BM25 负样本，作为批次中所有问题的负段落：

> "G.+BM25(1) and G.+BM25(2) denote in-batch training with 1 or 2 additional BM25 negatives, which serve as negative passages for all questions in the batch." [KB-3]

### 6. 关于负样本分布的图示

原文图 1 展示了 query、相关文档、来自 BM25 的负训练实例（BM25 Neg）或随机采样（Rand Neg）、以及测试负样本（DR Neg）的 T-SNE 表示：

> "Figure 1: T-SNE (Maaten & Hinton, 2008) representations of query, relevant documents, negative training instances from BM25 (BM25 Neg) or randomly sampled (Rand Neg), and testing negatives (DR Neg) in dense retrieval." [KB-5]

## 结论

综合论文库原文，可归纳出与 ColBERT 负样本策略相关的以下要点：

1. **BM25 top-k 作为 hard negatives**：原文将"从 BM25 top 100 随机采样"（BM25 Neg）列为一种负样本构造基线 [KB-2]；并在 in-batch 训练中引入"对问题有高 BM25 分数但不含答案"的额外 hard 负段落，作为批次中所有问题的负样本，加入 1 个即显著提升 [KB-4][KB-3]。
2. **In-batch negatives**：通过复用 mini-batch 内其他问题的正段落作为负样本，无需额外采样，是一种内存高效地增加负样本数量的方式 [KB-6]；在 1-of-N 设置下负样本类型（随机/BM25/gold）对 top-k 准确率影响不大 [KB-4]。
3. **对比学习/NCE 选取批内最难负样本**也被作为一种负样本策略提及 [KB-1][KB-2]。

**需特别说明的局限**：上述原文片段来自 ANCE 相关论文的实验与引言部分，其中 ColBERT 并未作为独立的负样本策略被单独描述；原文仅以引用形式（如 Huang et al., 2020; Karpukhin et al., 2020 等）提及负样本构造方法，并未明确将"BM25 top-k 作为 hard negatives"或"in-batch negatives"的具体配置归属于 ColBERT 本身。因此，**ColBERT 论文中关于其自身训练负样本构造的原文（如具体使用 BM25 top-k 作为 hard negatives、in-batch negatives 的配置细节）在当前提供的材料中未报告**，无法据此确认 ColBERT 的具体负样本策略，需补充 ColBERT 原论文的相应原文后方可定论。

## 参考文献

[KB-1] 用户专题论文库片段，章节 1 INTRODUCTION，页码 1

[KB-2] 用户专题论文库片段，章节 5 EXPERIMENTAL METHODOLOGIES，页码 5

[KB-3] 用户专题论文库片段，章节 5.2 Ablation Study on Model Training，页码 6

[KB-4] 用户专题论文库片段，章节 5.2 Ablation Study on Model Training，页码 6

[KB-5] 用户专题论文库片段，章节 1 INTRODUCTION，页码 2

[KB-6] 用户专题论文库片段，章节 3.3 Optimized Training Approach，页码 4

### 3. ANCE 如何动态选择硬负样本？（COMPLETED）

# ANCE 如何动态选择硬负样本？

## 问题陈述

本步骤聚焦于 ANCE（Approximate nearest neighbor Negative Contrastive Learning）方法，考察其如何**动态地**从整个语料库中选择硬负样本（hard negatives），而非依赖批内（in-batch）随机负样本。

## 研究归纳

### 1. 核心动机：批内局部负样本是瓶颈

ANCE 的理论出发点在于指出端到端稠密检索（DR）常弱于基于词项的稀疏检索，其学习瓶颈源于**批内局部采样的无信息负样本占据主导地位**，这些负样本导致梯度范数递减、随机梯度方差增大、收敛缓慢 [KB-1]。因此需要一种能从全局语料中选取硬负样本的机制。

### 2. 动态选择机制：用被优化的 DR 模型从全语料检索

ANCE 的关键在于**用"正在被优化的 DR 模型"自身从整个语料库中检索，从而构建全局负样本**，取代随机或批内局部负样本 [KB-3]。具体而言：

- ANCE 使用**异步更新的 ANN 索引**来选取硬训练负样本，从整个语料库中全局地（globally）选择 [KB-1]。
- 实现上，ANCE 维护一个 **Inferencer**，它用被优化 DR 模型的**近期检查点（recent checkpoint）并行计算文档编码**，并在完成后**刷新用于负样本采样的 ANN 索引**，以跟上模型训练的进度 [KB-5]。

也就是说，随着 DR 模型不断更新，ANN 索引被异步刷新，负样本随之"动态"地反映当前模型的检索结果——这正是其"动态选择"的本质：负样本分布与训练过程同步演进，并与推理时需区分的无关文档分布相对齐 [KB-3]。

### 3. 动态选择带来的理论收益

从方差缩减（variance reduction）视角看，这些 ANCE 负样本**抬高了每个实例梯度范数的上界、降低了随机梯度估计的方差，从而带来更快的收敛** [KB-3]（该点亦在 [KB-5] 中被提及为经验验证的结论）。

### 4. 预热（Warm Up）阶段的负样本来源

在训练初期，ANCE 并非一开始就从全局 ANN 索引取负样本，而是采用预热策略：

- 在 TREC DL 中，ANCE **用 BM25 负样本进行预热** [KB-4]。
- 在 OpenQA 中，ANCE **用已发布的 DPR 检查点进行预热** [KB-4]。

## 结论

ANCE 的动态硬负样本策略可概括为：**不用批内随机负样本，而是用当前正在优化的 DR 模型从整个语料库中检索来构造全局负样本，并通过异步维护与刷新 ANN 索引（由 Inferencer 用近期检查点并行编码文档）使负样本随模型训练动态更新** [KB-1][KB-3][KB-5]。其理论依据是批内局部负样本导致梯度范数递减与高方差，而全局动态负样本能抬高梯度范数上界、降低方差、加快收敛 [KB-1][KB-3]。训练初期则借助 BM25 负样本（TREC DL）或 DPR 检查点（OpenQA）进行预热 [KB-4]。

## 材料局限说明

- 关于 ANCE 动态选择的具体**超参数**（如每次采样负样本数量、ANN 索引刷新频率、Inferencer 并行细节等），上述原文片段**论文中未报告**，故不作推测。
- 本步骤仅针对 ANCE；DPR、ColBERT、RocketQA 的负样本策略不在本步骤范围内（RocketQA 相关片段 [KB-2][KB-6] 仅提及其"cross-batch negatives、denoised hard negatives、data augmentation"三项策略，未展开细节）。

## 参考文献

[KB-1] ABSTRACT（用户专题论文库，页码 1）

[KB-2] 1 Introduction（用户专题论文库，页码 2，RocketQA 片段）

[KB-3] 1 INTRODUCTION（用户专题论文库，页码 2，ANCE 片段）

[KB-4] 5 EXPERIMENTAL METHODOLOGIES（用户专题论文库，页码 5）

[KB-5] 1 INTRODUCTION（用户专题论文库，页码 2，ANCE 实现片段）

[KB-6] Abstract（用户专题论文库，页码 1，RocketQA 片段）

## 问题陈述

本步骤聚焦于 **ANCE 如何动态选择硬负样本**，并补充取证 DPR（in-batch negatives 与 BM25 hard negatives）、ColBERT 负样本策略、RocketQA cross-batch negatives 等内容。以下归纳严格基于用户论文库原文片段 [KB-1]～[KB-5]。

---

## 研究发现

### 1. ANCE 的动态硬负样本选择机制

**核心理念：用"正在优化的 DR 模型本身"从全语料库中检索全局负样本，使训练期负样本分布与测试期需区分的无关文档分布对齐。**

原文依据：ANCE "constructs global negatives using the being-optimized DR model to retrieve from the entire corpus. This fundamentally aligns the distribution of negative samples in training and of irrelevant documents to separate in testing"（该表述见于章节背景 P-ed0e1d2d326b 的引言部分；该段未在 [KB-n] 片段中完整收录，故此处仅作背景理解，不作为已校验引用结论）。

**可被 [KB-n] 直接支撑的要点：**

- 对比对象：ANCE 不采用 random 或 in-batch local negatives，而采用全局负样本（该对比关系在 [KB-1] 中被间接确立——[KB-1] 指出 in-batch local negatives "are not significantly better than spare-retrieved negatives in representation learning for dense retrieval"，这正是 ANCE 转向全局检索负样本的动机）。
- 具体动态采样实现（来自 [KB-3]）："For each positive, we uniformly sample one negative from ANN top 200." 即对每个正样本，从 ANN 检索的 top 200 中**均匀采样一个负样本**。
- 索引动态刷新（来自 [KB-3]）："index refreshing per 10k training batches"，即每 1 万个训练 batch 刷新一次 ANN 索引，从而让负样本随模型更新而动态演化。
- 异步更新：ANCE 使用"an asynchronously updated ANN index"（见章节背景 P-ed0e1d2d326b，未在 [KB-n] 片段中收录）。
- 预热设置（来自 [KB-3]）："ANCE is also warmed up by BM25 negatives"，在 OpenQA 中"we warm up ANCE using the released DPR checkpoints"。

> 说明：ANCE 的"全局负样本 + 用被优化模型检索"这一核心定义出现在章节背景 P-ed0e1d2d326b 中，但**未被列入 [KB-n] 可引用片段**。因此严格按取证要求，本步骤只能引用 [KB-1]（in-batch 负样本效果有限的动机）与 [KB-3]（top 200 均匀采样、索引每 10k batch 刷新、BM25/DPR 预热）作为已校验结论；"用被优化模型检索全语料"的完整表述属于背景理解，不作已校验引用。

### 2. DPR 的负样本策略（in-batch negatives 与 BM25 hard negatives）

[KB-3] 明确列出 DPR 相关的负样本构造基线：

- **Random sampling in batch（Rand Neg）**：批内随机采样负样本。
- **Random sampling from BM25 top 100（BM25 Neg）**：从 BM25 top 100 中随机采样负样本，引用 (Lee et al., 2019; Gao et al., 2020b)。
- **BM25 与 Random 的 1:1 组合（BM25 + Rand Neg）**：引用 (Karpukhin et al., 2020; Luan et al., 2020)——其中 Karpukhin et al., 2020 即 DPR。
- 此外还对比了 **NCE Neg**："contrastive learning/Noise Contrastive Estimation, which uses hardest negatives in batch"（[KB-3]）。

[KB-1] 亦指出："the in-batch local negatives, though effective in learning word or visual representations, are not significantly better than spare-retrieved negatives in representation learning for dense retrieval"，并援引 Karpukhin et al., 2020 的观察。

> 说明：DPR 原文中"1:1 组合 BM25 与 in-batch negatives"的具体比例细节，本库仅通过 [KB-3] 的 "1:1 combination of BM25 and Random negatives" 体现，其对应引用为 (Karpukhin et al., 2020; Luan et al., 2020)。DPR 论文内部对负样本的完整描述**论文中未报告**（本库未收录 DPR 原文）。

### 3. ColBERT 的负样本策略

在当前提供的 [KB-1]～[KB-5] 片段中，**未出现任何关于 ColBERT 负样本策略的内容**。因此本步骤对 ColBERT 的负样本策略**论文中未报告**，不做猜测。

### 4. RocketQA 的 cross-batch negatives 与 denoised hard negatives

[KB-4] 明确列出 RocketQA 的三项技术贡献，其中与负样本直接相关的两项：

- **Cross-batch negatives**："Comparing to inbatch negatives, it increases the number of available negatives for each question during training, and alleviates the discrepancy between training and inference."（[KB-4]）
- **Denoised hard negatives**："It aims to remove false negatives from the top-ranked results retrieved by a retriever, and derive more reliable hard negatives."（[KB-4]）
- 第三项为 **data augmentation**：利用 cross-encoder 为无监督数据生成高质量伪标签以训练 dual-encoder（[KB-4]）。

[KB-5] 总结确认："three major technical contributions in RocketQA, namely cross-batch negatives, denoised hard negatives and data augmentation. Extensive experiments have shown the effectiveness of the proposed approach by incorporating the three optimization strategies."（[KB-5]）

关于 false negative 问题的动机，[KB-4] 所在引言亦指出从 top-k 检索结果采样硬负样本会带来 false negative 问题（该完整论述见章节背景 P-140621b255be，其中提到"70% of them are actually positives"；此句未在 [KB-n] 片段中完整收录，故仅作背景，不作已校验引用）。

### 5. 负样本策略对比总览（基于 [KB-3]）

[KB-3] 在同一 BERT-Siamese 框架下对比了多种负样本构造方式：

| 策略 | 构造方式 | 出处 |
|------|----------|------|
| Rand Neg | 批内随机采样 | [KB-3] |
| BM25 Neg | 从 BM25 top 100 随机采样 | [KB-3] |
| BM25 + Rand Neg | BM25 与随机负样本 1:1 组合 | [KB-3] |
| NCE Neg | 批内最难负样本 | [KB-3] |
| ANCE | 从 ANN top 200 均匀采样一个负样本，索引每 10k batch 刷新 | [KB-3] |

---

## 结论

就本步骤核心问题"ANCE 如何动态选择硬负样本"而言，可被 [KB-n] 直接支撑的已校验结论是：ANCE 对每个正样本从 **ANN 检索的 top 200 中均匀采样一个负样本**，并**每 1 万个训练 batch 刷新一次 ANN 索引**，同时以 BM25 负样本（TREC DL）或 DPR checkpoint（OpenQA）进行预热（均见 [KB-3]）。其动机源于 [KB-1] 指出的 in-batch local negatives 在稠密检索表示学习中并不显著优于稀疏检索负样本。"用被优化模型检索全语料以对齐训练/测试分布"的完整表述见于章节背景，未列入 [KB-n]，故本步骤不将其作为已校验引用。

补充取证方面：DPR 的负样本策略在本库中以 Rand Neg、BM25 Neg、BM25 + Rand Neg（1:1，Karpukhin et al., 2020）及 NCE Neg 的形式出现（[KB-3]）；RocketQA 采用 cross-batch negatives 与 denoised hard negatives（[KB-4]、[KB-5]）；**ColBERT 的负样本策略在本库片段中未报告**，需另行取证。

---

## 参考文献

[KB-1] 用户专题论文库片段，章节 1 INTRODUCTION，页码 1。

[KB-2] 用户专题论文库片段，章节 1 INTRODUCTION，页码 2（Figure 1）。

[KB-3] 用户专题论文库片段，章节 5 EXPERIMENTAL METHODOLOGIES，页码 5。

[KB-4] 用户专题论文库片段，章节 1 Introduction，页码 2。

[KB-5] 用户专题论文库片段，章节 5 Conclusions，页码 9。

### 4. RocketQA 的负样本策略包含哪些环节？（COMPLETED）

## 问题陈述

本步骤聚焦于 **RocketQA 的负样本（hard negative）策略包含哪些环节**，基于用户专题论文库中的原文片段 [KB-1] 至 [KB-6] 进行取证归纳。

## 研究发现

### 一、总体框架：三项训练策略，其中两项与负样本直接相关

RocketQA 针对稠密段落检索提出了三项新的训练策略，即 **cross-batch negatives（跨批次负样本）**、**denoised hard negatives（去噪硬负样本）** 和 **data augmentation（数据增强）** [KB-1][KB-5]。实验结果表明这三项策略均能有效提升稠密段落检索的性能 [KB-2]。

在负样本层面，RocketQA 的硬负样本策略可归纳为两个核心环节：**跨批次负样本** 与 **去噪硬负样本**；此外，**数据增强** 作为第三项策略与负样本训练协同发挥作用。

### 二、环节一：跨批次负样本（Cross-batch Negatives）

RocketQA 首先引入跨批次负样本。与 in-batch negatives（批内负样本）相比，它在训练时为每个问题增加了可用负样本的数量，并缓解了训练与推理之间的差异（discrepancy）[KB-6]。

原文表述为："First, RocketQA introduces cross-batch negatives. Comparing to inbatch negatives, it increases the number of available negatives for each question during training, and alleviates the discrepancy between training and inference." [KB-6]

### 三、环节二：去噪硬负样本（Denoised Hard Negatives）

RocketQA 引入去噪硬负样本，其目的是 **从检索器返回的 top-ranked 结果中去除假负样本（false negatives）**，从而得到更可靠的硬负样本 [KB-6]。

原文表述为："Second, RocketQA introduces denoised hard negatives. It aims to remove false negatives from the top-ranked results retrieved by a retriever, and derive more reliable hard negatives." [KB-6]

这一环节针对的正是从 top-k 检索结果中采样硬负样本时容易引入假负样本的问题（背景章节亦提及此挑战，但此处仅以 [KB-6] 的原文结论为准）。

### 四、环节三（协同）：数据增强（Data Augmentation）

RocketQA 还利用由 cross-encoder "标注" 的大规模无监督数据进行数据增强 [KB-6]。虽然 cross-encoder 效率较低，但它在理论与实践中均被证明比 dual-encoder 更强，因此 RocketQA 用 cross-encoder 为无标注数据生成高质量伪标签，用于训练 dual-encoder 检索器 [KB-6]。

原文表述为："Third, RocketQA leverages large-scale unsupervised data 'labeled' by a cross-encoder ... to generate highquality pseudo labels for unlabeled data which are used to train the dual-encoder retriever." [KB-6]

### 五、与基线方法的对比定位

在对比实验中，论文指出 DPR 与 ME-BERT 均使用 in-batch 随机采样以及从 BM25 检索结果中采样硬负样本，而 ANCE 则通过使用稠密检索器来增强硬负样本采样 [KB-3]。这从侧面界定了 RocketQA 负样本策略相对于 DPR/ANCE 的差异背景：RocketQA 以跨批次负样本与去噪硬负样本作为其核心改进。

## 结论

基于论文库原文，RocketQA 的负样本策略主要包含以下环节：

1. **跨批次负样本（cross-batch negatives）**：相比批内负样本，增加每个问题在训练时的可用负样本数量，并缓解训练与推理之间的差异 [KB-6]。
2. **去噪硬负样本（denoised hard negatives）**：从检索器 top-ranked 结果中剔除假负样本，获得更可靠的硬负样本 [KB-6]。
3. **数据增强（data augmentation）**：用 cross-encoder 为大规模无监督数据生成高质量伪标签，以训练 dual-encoder 检索器，与上述负样本策略协同 [KB-6]。

以上三项被论文明确称为 RocketQA 的三大技术贡献 [KB-5]，并经实验验证均有效 [KB-2]。

**说明**：关于各环节的具体实现细节（如跨批次负样本的批次组织方式、去噪所用的 cross-encoder 过滤阈值、数据增强的规模与采样比例等），所给 [KB-n] 片段中 **论文中未报告**，故不作推测。

## 参考文献

[KB-1] RocketQA 论文库片段，章节 1 Introduction，页码 2。

[KB-2] RocketQA 论文库片段，章节 1 Introduction，页码 2。

[KB-3] RocketQA 论文库片段，章节 4.2.1 Dense Passage Retrieval，页码 7。

[KB-4] ANCE 论文库片段，章节 1 INTRODUCTION，页码 2。

[KB-5] RocketQA 论文库片段，章节 Abstract，页码 1。

[KB-6] RocketQA 论文库片段，章节 1 Introduction，页码 2。

## 证据与分歧

- 2004.04906（ACCEPTED）
- 2004.04906（ACCEPTED）
- 2004.04906（ACCEPTED）
- 2004.12832（ACCEPTED）
- 2004.12832（ACCEPTED）
- 2004.12832（ACCEPTED）
- 2007.00808（ACCEPTED）
- 2007.00808（ACCEPTED）
- 2007.00808（ACCEPTED）
- 2007.00808（ACCEPTED）
- 2010.08191（ACCEPTED）
- 2010.08191（ACCEPTED）
- 2010.08191（ACCEPTED）
- 检出 1 条存在反向支持关系的来源，需要单独核对条件和适用范围。
- 有 4 条来源仍待引用复核，不能作为无条件结论。

## 覆盖度与缺口

- 已检查来源：13 条；已完成步骤：4 / 4。

## 来源清单

- 2004.04906
- 2004.04906
- 2004.04906
- 2004.12832
- 2004.12832
- 2004.12832
- 2007.00808
- 2007.00808
- 2007.00808
- 2007.00808
- 2010.08191
- 2010.08191
- 2010.08191