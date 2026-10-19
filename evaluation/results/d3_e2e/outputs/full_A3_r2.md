# A3 · full · round 2

DPR、ColBERT、ANCE、RocketQA 各自的负样本（hard negative）策略是什么？

---

# 研究报告

## 直接回答

本报告汇总了 4 个已完成研究步骤的发现；逐项依据见下方证据与局限。

**研究问题**：DPR、ColBERT、ANCE、RocketQA 各自的负样本（hard negative）策略是什么？

## 资料范围

- 专题内全部就绪资料（未限定单篇）

## 逐项发现

### 1. DPR 原文如何描述其负样本来源与采样方式（如 in-batch negatives、BM25 top-k hard negatives 等），并给出了哪些关键实现细节？（COMPLETED）

## 问题陈述

本步骤聚焦 DPR 原文对负样本来源与采样方式的描述，包括 in-batch negatives、BM25 top-k hard negatives 等机制，以及关键实现细节。

## 研究发现

### 1. 知识库材料中关于 DPR 负样本策略的直接记载

在用户专题论文库中，关于 DPR 负样本策略的直接描述主要出现在 RocketQA 论文对 DPR 的转述中：

- RocketQA 论文在对比稀疏与稠密检索基线时指出："Both DRP and ME-BERT use in-batch random sampling and hard negative sampling from the results retrieved by BM25, while ANCE enhances the hard negative sampling by using the dense retriever." [KB-6]。即 DPR（原文写作 DRP，应为 DPR 的笔误）采用 **in-batch 随机采样 + 从 BM25 检索结果中采样 hard negative** 的组合方式，而 ANCE 则用稠密检索器本身来增强 hard negative 采样。

- 在实验方法部分，RocketQA 论文将 "BM25 + Rand Neg"（BM25 负样本与随机负样本 1:1 组合）标注为引自 Karpukhin et al. (2020) 与 Luan et al. (2020) 的做法 [KB-3]，这与 DPR 的负样本构造方式相呼应，但该片段本身并未展开 DPR 原文的具体描述。

- 同一部分还提到 "BM25 negatives can help train dense retrieval (Karpukhin et al., 2020; Luan et al., 2020)"，并据此设置了 "BM25 Warm Up" 训练策略 [KB-2]，说明 DPR 使用 BM25 负样本辅助训练这一事实被后续工作引用与沿用。

### 2. 知识库材料对 DPR 负样本策略的间接评价

- ANCE 论文指出："as observed in recent research (Karpukhin et al., 2020), the in-batch local negatives, though effective in learning word or visual representations, are not significantly better than spare-retrieved negatives in representation learning for dense retrieval." [KB-4]。这说明 DPR 原文曾观察到 in-batch 局部负样本在稠密检索表示学习中并不显著优于稀疏检索得到的负样本。

- ANCE 论文还批评 "uninformative negatives sampled locally in batch" 会导致梯度范数衰减、随机梯度方差大、收敛缓慢 [KB-5]，这从侧面反映了 DPR 所依赖的 in-batch negatives 的局限性。

- RocketQA 论文亦指出，从 top-k 检索结果中选取少量 hard negative 的机制存在 false negative 问题 [KB-6 所在章节背景]，但该论述是 RocketQA 自身的动机陈述，并非对 DPR 原文实现细节的转述。

### 3. 材料不足之处

关于 DPR 原文对负样本策略的**具体实现细节**，例如：

- BM25 top-k 中 k 的具体取值（如 top-100）；
- 每个问题正负样本的具体配比（如 1 正 : 1 BM25 负 : n 随机负）；
- in-batch negatives 与 BM25 hard negatives 的具体混合比例与采样流程；
- 训练 batch size、迭代轮数等超参数；

上述知识库材料中**论文中未报告**。现有 [KB-n] 片段仅能确认 DPR 采用"in-batch 随机采样 + BM25 检索结果 hard negative 采样"的组合框架 [KB-6]，以及 BM25 负样本被用于训练稠密检索 [KB-2][KB-3]，但未给出 DPR 原文中的具体数值与流程细节。

## 结论

根据用户专题论文库材料，DPR 的负样本策略可归纳为：**in-batch 随机负采样与从 BM25 检索结果中采样 hard negative 相结合** [KB-6]，其中 BM25 负样本被认为有助于稠密检索训练 [KB-2][KB-3]。ANCE 论文进一步指出，DPR 原文观察到 in-batch 局部负样本在稠密检索中并不显著优于稀疏检索负样本 [KB-4]，且这类局部负样本存在梯度范数衰减、方差大、收敛慢的问题 [KB-5]。然而，关于 DPR 原文中 BM25 top-k 的 k 值、正负样本配比、batch 内混合方式等**关键实现细节，现有材料中论文未报告**，需回到 DPR 原文（Karpukhin et al., 2020）进一步核实，不宜在此推测。

## 参考文献

[KB-1] RocketQA 论文，1 Introduction，第 2 页（用户专题论文库）

[KB-2] RocketQA 论文，5 EXPERIMENTAL METHODOLOGIES，第 5 页（用户专题论文库）

[KB-3] RocketQA 论文，5 EXPERIMENTAL METHODOLOGIES，第 5 页（用户专题论文库）

[KB-4] ANCE 论文，1 INTRODUCTION，第 1 页（用户专题论文库）

[KB-5] ANCE 论文，ABSTRACT，第 1 页（用户专题论文库）

[KB-6] RocketQA 论文，4.2.1 Dense Passage Retrieval，第 7 页（用户专题论文库）

# DPR 负样本策略取证归纳

## 问题陈述

本步骤聚焦：DPR 原文（Karpukhin et al., 2020）如何描述其负样本来源与采样方式（in-batch negatives、BM25 top-k hard negatives 等），并给出了哪些关键实现细节（如 BM25 top-k 的 k 取值、正负样本配比等）。

## 研究结论

### 1. 现有材料能确认的内容

**（1）DPR 使用了 in-batch（局部小批量）负样本，且效果并不显著优于 BM25 负样本。**
材料明确指出：另一种采样方式是在局部 mini-batch 内采样负样本（如对比学习中的做法），然而这些局部负样本并没有显著超越 BM25 负样本，并直接引用了 Karpukhin et al., 2020 作为依据 [KB-1]。这说明 DPR 的负样本来源之一确实是 in-batch negatives。

**（2）DPR 使用了从 BM25 top 检索文档中采样的负样本（hard negatives）。**
材料指出，一个自然的选择是从 BM25 检索到的 top 文档中采样负样本，但可能使 DR 模型偏向学习稀疏检索、难以大幅超越 BM25 [KB-1]。这印证了 DPR 采用 BM25 top-k hard negatives 作为负样本来源的做法。

**（3）DPR 属于"从 top-k 检索候选中选取少量 hard negatives"的既有工作之一。**
材料在讨论训练/推理差异时指出，先前工作尝试设计特定机制从 top-k 检索候选中选取少量 hard negatives，并将 Karpukhin et al., 2020 列为其中之一 [KB-2 所在章节背景亦提及]。这从侧面确认 DPR 的 hard negative 来自 top-k 检索候选。

**（4）false negative 风险与未标注正例问题。**
材料以 MSMARCO 为例指出：每个问题平均只有 1.1 个标注正例，而全集有 8.8M 段落；人工检查发现 top 检索中未被标为正例的段落有 70% 实际是正例，因此从 top-k 检索段落中采样 hard negatives 很可能引入 false negatives [KB-2]。这一批评针对的正是包括 DPR 在内的"从 top-k 采样 hard negatives"的做法。

**（5）BM25 负样本与随机负样本在表示空间中的差异。**
Figure 1 的 T-SNE 展示了 query、相关文档、来自 BM25 的负样本（BM25 Neg）、随机采样负样本（Rand Neg）以及测试负样本（DR Neg）在稠密检索中的表示分布 [KB-3]。这说明论文区分了 BM25 Neg 与 Rand Neg 两类训练负样本。

### 2. 材料不足以回答的部分（论文中未报告）

用户特别要求补充的以下 DPR 原文实现细节，**在上述 [KB-n] 材料中均未报告**，不能据此给出结论：

- **BM25 top-k 的 k 具体取值**：材料仅泛称 "top documents / top-K / top-k"，未给出具体数值 → 论文中未报告。
- **正负样本配比（如 1 正 : N 负的具体 N）**：材料未涉及 → 论文中未报告。
- **in-batch negatives 与 BM25 hard negatives 的具体组合方式与数量**：材料仅提及两类来源存在，未给出组合细节 → 论文中未报告。
- **DPR 原文对采样方式的逐条自述**：材料多为其他论文（如 ANCE、RocketQA 相关工作）对 DPR 的转述与引用，而非 DPR 原文的直接表述 → 需以 DPR 原文核实，当前材料不足以确认。

## 说明

上述材料主要来自对 DPR 的**二手转述与批评性引用**（例如 ANCE 论文的 Preliminaries/Introduction、RocketQA 的 Introduction），可确认 DPR 负样本的两大来源为 **in-batch negatives** 与 **BM25 top-k hard negatives**，但 DPR 原文关于 k 取值、正负配比等关键实现细节，当前论文库片段中**未报告**，建议后续直接检索 DPR 原文（Karpukhin et al., 2020, "Dense Passage Retrieval for Open-Domain Question Answering"）以补齐。

## 参考文献

[KB-1] 章节 2 PRELIMINARIES, 页码 3（负样本采样方式：BM25 top 文档 vs. 局部 mini-batch，引用 Karpukhin et al., 2020）

[KB-2] 章节 1 Introduction, 页码 2（MSMARCO 未标注正例与 false negative 问题）

[KB-3] 章节 1 INTRODUCTION, 页码 2（Figure 1：BM25 Neg、Rand Neg、DR Neg 的 T-SNE 表示）

### 2. ColBERT 原文如何构造与使用 hard negatives（如从哪个检索器采样、每个 query 取多少条、训练时如何组合），原文对其效果的说明是什么？（COMPLETED）

# ColBERT 原文的 Hard Negative 构造与使用（取证归纳）

## 问题重述

本步骤要回答：ColBERT 原文如何构造与使用 hard negatives（从哪个检索器采样、每个 query 取多少条、训练时如何组合），以及原文对其效果的说明。

## 取证结果

**重要说明：当前提供的用户专题论文库片段（[KB-1] 至 [KB-6]）全部来自 RocketQA 与 ANCE 两篇论文，不包含 ColBERT 原文的任何内容。** 因此，关于 ColBERT 的具体构造细节（采样检索器、每 query 负样本数量、训练组合方式、效果说明），在现有材料中均**无法取证**，属于"论文中未报告"（更准确地说，是本步骤所给材料中未提供）。

## 材料中可间接参考的相关信息（非 ColBERT）

材料中与"hard negative 采样"相关的论述均出自 RocketQA 和 ANCE，可作为对照背景，但不能作为 ColBERT 的结论：

- RocketQA 指出，此前工作尝试"从 top-k 检索候选中选取少量 hard negatives"来缓解训练-推理差异，但因存在大量未标注正例而易产生假阴性问题；为此 RocketQA 提出"denoised hard negatives"，旨在从检索器返回的 top-ranked 结果中去除假阴性、获得更可靠的 hard negatives [KB-2]。这表明"从检索器 top-k 采样 hard negatives"是当时通用做法，但材料并未指明 ColBERT 是否采用此法。
- ANCE 指出，已有研究探索了多种为稠密检索构造负训练实例的方式，例如用对比学习在当前或近期 mini-batch 内选取 hard negatives；但 in-batch 局部负样本对稠密检索表征学习并不显著优于稀疏检索得到的负样本 [KB-6]。ANCE 自身则主张从全语料库全局选取 hard negatives，并使用异步更新的 ANN 索引 [KB-5]。

以上均为 RocketQA/ANCE 的立场，**不能**推断为 ColBERT 的做法。

## 结论

- 关于 ColBERT 的 hard negative 策略（采样来源检索器、每 query 负样本条数、训练时的组合方式、原文对其效果的说明），**所给 [KB-n] 材料中未包含 ColBERT 原文，故论文中未报告（本材料无法回答）**。
- 若需完成该子问题，应补充 ColBERT 原文（Khattab & Zaharia, SIGIR 2020）的相关片段，尤其是其关于从检索器采样 top-k 文档作为 hard negatives、每 query 取若干条（如 top-k）并在训练中与 in-batch 负样本组合的描述，以及消融实验中对效果的说明。在补充材料前，不应给出具体数值或机制细节。

## 参考文献

[KB-1] RocketQA, 1 Introduction, p.2（用户专题论文库片段）

[KB-2] RocketQA, 1 Introduction, p.2（用户专题论文库片段）

[KB-3] RocketQA, Abstract, p.1（用户专题论文库片段）

[KB-4] RocketQA, 5 Conclusions, p.9（用户专题论文库片段）

[KB-5] ANCE, Abstract, p.1（用户专题论文库片段）

[KB-6] ANCE, 1 Introduction, p.1（用户专题论文库片段）

# ColBERT 的 Hard Negative 构造与使用（基于论文库原文的取证归纳）

## 问题陈述

本步骤需回答：ColBERT 原文如何构造与使用 hard negatives——从哪个检索器采样、每个 query 取多少条、训练时如何组合，以及原文对其效果的说明。

## 研究说明（重要）

经核查用户专题论文库中提供的全部原文片段 [KB-1] 至 [KB-6]，**这些片段并未直接包含 ColBERT 论文中关于 hard negative 构造细节的原文**（即未出现"从 BM25 采样""每个 query 取 top-k 条""训练时与 in-batch negatives 如何组合"等具体表述）。[KB-1] 仅涉及 ANCE 论文语境下的负样本 T-SNE 可视化，[KB-3] 是 RocketQA 论文对 hard negative 去噪的讨论，[KB-2]/[KB-4]/[KB-5]/[KB-6] 均为 ColBERT 的检索/重排/摘要内容，均未描述 ColBERT 的 hard negative 采样流程。

因此，**基于给定材料，ColBERT 的 hard negative 采样来源、每 query 采样条数、训练组合方式均"论文中未报告"（在所提供的片段范围内无法取证）**。以下仅归纳材料中确实可得的相关信息。

## 可取证的相关信息

### 1. ColBERT 的负样本相关线索（间接）

- [KB-1] 的图 1 说明展示了 query、相关文档、来自 BM25 的负训练样本（"BM25 Neg"）与随机采样负样本（"Rand Neg"），以及测试阶段的负样本（"DR Neg"）的 T-SNE 表示。该图属于 ANCE 论文语境下的对比可视化，说明 BM25 检索结果与随机采样是 dense retrieval 中常见的负样本来源，但**该片段本身并非 ColBERT 论文对自身 hard negative 策略的描述**，不能据此断言 ColBERT 采用了 BM25 采样。

### 2. RocketQA 对"从 dense retriever 顶层结果采 hard negatives"的讨论（旁证，非 ColBERT）

[KB-3] 明确指出：直接从 dense retriever 检索出的 top-k passages 中采样 hard negatives 是"前人研究中广泛采用的策略"（引用了 Gillick et al., 2019; Wu et al., 2020; Xiong et al., 2020），但这样做会引入噪声——作者人工检查发现约 70% 未被标为正例的顶层结果实际上是正例或高度相关。因此 RocketQA 提出用强 cross-encoder 做"denoised hard negatives"，并报告去噪后性能提升；且"在较低排名处被过滤（去噪）的段落更多，因为较低排名处更可能有更多假阴性" [KB-3]。这为"从检索器采样 hard negatives"的通用做法提供了背景，但**不是 ColBERT 自身的策略描述**。

### 3. ColBERT 的检索/重排设置（与训练负样本无关）

- [KB-4] 说明 ColBERT 可用于对另一检索模型（通常是 term-based 模型）的输出做重排，或直接做端到端检索；重排时对 k（如 k=1000）个文档做批量打分。
- [KB-2] 说明端到端全检索时，每个 query 直接从 MS MARCO 的 8.8M 文档中检索 top-1000。
- [KB-6] 说明重排实验中对 Anserini 的 BM25 输出做重排，k=1000 时 MRR@10 略高于 Table 1。
- [KB-5] 为摘要，介绍 late interaction 架构与效率优势。

以上均为**推理/检索阶段**的设置，**不涉及训练阶段 hard negative 的采样来源、条数与组合方式**。

## 结论

在所提供的 [KB-1]–[KB-6] 片段范围内：

- **ColBERT 从哪个检索器采样 hard negatives**：论文中未报告（片段未涉及）。
- **每个 query 取多少条 hard negatives**：论文中未报告（片段未涉及）。
- **训练时如何组合 hard negatives（如与 in-batch negatives 的配比）**：论文中未报告（片段未涉及）。
- **原文对 hard negative 效果的说明**：论文中未报告（片段未涉及 ColBERT 自身的 hard negative 消融/效果说明）。

可确认的相关旁证仅为：[KB-1] 显示 BM25 采样与随机采样是 dense retrieval 常见负样本来源；[KB-3]（RocketQA 论文）讨论并批评了"从 dense retriever 顶层结果直接采 hard negatives"的做法并主张去噪。这些均**不能等同于 ColBERT 的策略**。若要完成本步骤，需补充 ColBERT 论文中训练章节（如 §3.2/§4 训练细节、Table 2 附近的 hard negative 消融）的原文片段。

## 参考文献

[KB-1] ColBERT 论文 1 INTRODUCTION，页码 2（用户论文库片段）

[KB-2] ColBERT 论文 4.3 End-to-end Topk Retrieval，页码 8（用户论文库片段）

[KB-3] RocketQA 论文 4.2.2 The Effectiveness of The Three Training Strategies in RocketQA，页码 8（用户论文库片段）

[KB-4] ColBERT 论文 3.5 Topk Re-ranking with ColBERT，页码 5（用户论文库片段）

[KB-5] ColBERT 论文 ABSTRACT，页码 1（用户论文库片段）

[KB-6] ColBERT 论文 4.2 Quality-Cost Tradeoff: Topk Re-ranking，页码 8（用户论文库片段）

### 3. ANCE 原文如何定义并动态更新 hard negatives（如从异步索引中按 ANCE 准则采样、索引刷新频率、负样本数量），其与静态 hard negative 的差异表述是什么？（COMPLETED）

# ANCE 的 Hard Negative 定义与动态更新机制

## 问题陈述

本步骤聚焦 ANCE 原文对 hard negatives 的定义方式、动态更新机制（异步索引、索引刷新频率、负样本数量），以及其与静态 hard negative 的差异表述。

## 研究发现

### 1. ANCE 对 hard negative 的定义：全局负样本，而非批内局部负样本

ANCE 的核心出发点是反对"随机或批内局部负样本"（random or in-batch local negatives），主张"使用正在优化的 DR 模型从整个语料库中检索来构造全局负样本"（constructs global negatives using the being-optimized DR model to retrieve from the entire corpus），并强调这"从根本上使训练中负样本的分布与测试中需要区分的无关文档分布对齐"（fundamentally aligns the distribution of negative samples in training and of irrelevant documents to separate in testing）[KB-3]。

摘要中同样将其定义为"从整个语料库全局地选择 hard 训练负样本，使用异步更新的 ANN 索引"（selects hard training negatives globally from the entire corpus, using an asynchronously updated ANN index）[KB-2]。

### 2. 负样本的采样准则：从 ANN 索引的 top 检索结果中取

ANCE 从 ANN 索引中由 DR 模型检索到的 top 文档里采样负样本，负样本集合定义为 `DANCE = ANN_f(q,d) \ D+`，即 top 检索文档中除去正样本的部分；原文称"按定义，DANCE 是当前 DR 模型最难的负样本（the hardest negatives for the current DR model）"，并指出这些更具信息量的负样本有更高的训练损失、更高的梯度范数上界，从而改善训练收敛 [KB-5]。

### 3. 动态更新机制：异步刷新 ANN 索引

**为何需要异步**：随机训练中 DR 模型 f() 每个 minibatch 都更新，维持实时 ANN 索引很困难，因为索引更新需要两步——(1) Inference：用更新后的 DR 模型刷新全语料文档表示；(2) Index：用更新后的表示重建 ANN 索引。虽然 Index 高效，但 Inference 需要对远大于训练 batch 的整个语料做前向计算，无法每 batch 执行 [KB-5]。

**实现方式**：ANCE 采用"每 m 个 batch 更新一次 ANN 索引"的异步索引刷新（update the ANN index once every m batches），使用检查点 fk。除 Trainer 外另设一个 Inferencer，取最新检查点重算全语料编码；与此同时 Trainer 继续用来自 `ANN_{fk-1}` 的 `D_{fk-1}` 做随机学习；语料重编码完成后 Inferencer 更新 ANN 索引（`ANN_{fk}`）并交给 Trainer [KB-5]。这一过程使 ANCE 负样本被异步更新以"追赶"随机训练，索引与模型优化之间的差距取决于 Trainer 与 Inferencer 的计算资源分配（附录 A.3 显示 1:1 GPU 划分足以最小化该差距）[KB-5]。

引言中表述一致：ANCE 使用异步更新的语料表示 ANN 索引，类似 Guu et al. (2020) 维护一个 Inferencer，用正在优化的 DR 模型的近期检查点并行计算文档编码，并在完成后刷新用于负采样 的 ANN 索引，以跟上模型训练 [KB-1]。

### 4. 与静态/局部 hard negative 的差异表述

- **对照对象**：ANCE 明确与"随机或批内局部负样本"对立 [KB-3]；摘要指出 DR 的学习瓶颈源于"批内局部采样的无信息负样本占主导，导致梯度范数递减、随机梯度方差大、收敛慢"（the domination of uninformative negatives sampled locally in batch, which yield diminishing gradient norms, large stochastic gradient variances, and slow learning convergence）[KB-2]。
- **理论收益**：从方差缩减角度，ANCE 负样本"提升每个实例梯度范数的上界，降低随机梯度估计的方差，带来更快的学习收敛" [KB-3]；结论部分重申局部负样本"无信息、梯度范数低、对学习收敛贡献很小"，而 ANCE 负样本"梯度范数大得多、降低随机梯度方差、改善训练收敛" [KB-6]。
- **实验佐证**：实验"经验性地验证了梯度范数在 ANCE 采样的负样本上远大于局部负样本，从而改善稠密检索模型收敛"这一理论 [KB-1]。

### 5. 关于负样本数量的说明

关于每次采样多少个负样本（负样本数量）的具体数值，上述原文片段中**论文中未报告**；材料仅给出"每 m 个 batch 刷新一次索引"以及"从 top 检索文档中除去正样本"的集合定义，未给出 m 的具体取值或每查询负样本条数。此外，ANCE 与 ColBERT、DPR、RocketQA 的横向对比不在本步骤材料范围内；材料中仅出现 RocketQA 提出"cross-batch negatives、denoised hard negatives、data augmentation"三项训练策略的概述 [KB-4]，未涉及其与 ANCE 的细节比较。

## 结论

ANCE 将 hard negative 定义为"用正在优化的 DR 模型从整个语料库检索出的全局负样本"（`DANCE = ANN_f(q,d) \ D+`），即当前模型最难区分的样本 [KB-3][KB-5]。其动态更新依赖异步 ANN 索引：Inferencer 用最新检查点重编码全语料并每 m 个 batch 刷新一次索引，Trainer 在此期间使用上一版本索引的负样本继续训练，使负样本分布持续"追赶"模型演化 [KB-1][KB-5]。与静态/批内局部负样本相比，ANCE 强调后者梯度范数递减、方差大、收敛慢，而全局动态负样本提升梯度范数上界、降低方差、加速收敛 [KB-2][KB-3][KB-6]。负样本数量的具体取值，论文中未报告。

## 参考文献

[KB-1] 用户专题论文库片段，章节 1 INTRODUCTION，页码 2

[KB-2] 用户专题论文库片段，章节 ABSTRACT，页码 1

[KB-3] 用户专题论文库片段，章节 1 INTRODUCTION，页码 2

[KB-4] 用户专题论文库片段，章节 1 Introduction，页码 2

[KB-5] 用户专题论文库片段，章节 4 APPROXIMATE NEAREST NEIGHBOR NOISE CONTRASTIVE ESTIMATION，页码 5

[KB-6] 用户专题论文库片段，章节 8 CONCLUSION，页码 8

# DPR 负样本策略取证归纳（in-batch negatives / BM25 hard negatives）

## 问题陈述

本次步骤的取证目标是：DPR 的负样本策略——即 in-batch negatives 与 BM25 hard negatives 的具体做法——在用户专题论文库的原文片段中是如何被描述或引用的。需注意：所给材料主要来自 ANCE 论文（其将 DPR 作为基线/对照引用），因此对 DPR 做法的直接描述有限，下文严格区分"原文明确表述"与"论文中未报告"。

## 研究结果

### 1. in-batch negatives 的定位与已知局限

原文在综述负样本构造方式时提到，已有研究探索了多种为稠密检索构造负训练实例的方法，例如使用对比学习在"当前或最近的 mini-batch 中选取 hard negatives" [KB-1]。随后原文给出关键判断：如近期研究（Karpukhin et al., 2020）所观察到的，in-batch 局部负样本虽然在词或视觉表示学习中有效，但在稠密检索的表示学习中并不显著优于稀疏检索得到的负样本 [KB-1]。这一表述说明 DPR（Karpukhin et al., 2020）被视为"in-batch 局部负样本"路线的代表，但原文并未展开 DPR 的 in-batch 实现细节（如 batch 内如何复用正/负样本、batch size 设置等）。

原文进一步从理论上指出，在稠密检索常见条件下，局部 in-batch 负样本会导致梯度范数递减，产生较高的随机梯度方差和较慢的训练收敛，即局部负采样是稠密检索效果的瓶颈 [KB-4]。这是 ANCE 对"静态/局部负样本"路线的批评性表述，间接界定了 DPR 式 in-batch 负样本的类别归属。

### 2. BM25 hard negatives 与 1:1 组合

在基线设置中，原文明确列出了与 DPR 相关的负样本构造方式：包括批内随机采样（Rand Neg）、从 BM25 top 100 中随机采样（BM25 Neg），以及"BM25 与随机负样本的 1:1 组合"（BM25 + Rand Neg），并将后者标注为来自 Karpukhin et al., 2020 与 Luan et al., 2020 [KB-2]。这是材料中对 DPR 负样本策略最直接的一句描述——即 DPR 采用 BM25 负样本与随机负样本按 1:1 混合的做法。

关于 BM25 负样本的作用，原文在实现细节中指出：近期研究发现 BM25 负样本有助于训练稠密检索（Karpukhin et al., 2020; Luan et al., 2020），因此设置了"BM25 Warm Up"（BM25 → ∗）方案，即先用 MARCO 官方 BM25 负样本训练 DR 模型；ANCE 同样以 BM25 负样本进行 warm up [KB-5]。在 OpenQA 实验中，原文则使用 DPR 发布的 checkpoint 对 ANCE 做 warm up [KB-5]。

### 3. 与 ANCE 动态负样本的对照（背景性差异）

作为对照，原文描述了 ANCE 的做法：不使用随机或批内局部负样本，而是用正在优化的 DR 模型从整个语料中检索以构造全局负样本，从而使训练中负样本分布与测试中需区分的无关文档分布对齐 [KB-4 所在章节背景]。在实现上，ANCE 采用异步更新的 ANN 索引、Faiss IndexFlatIP、1:1 的 Trainer:Inference GPU 分配、每 10k 个训练 batch 刷新一次索引、batch size 8、梯度累积步长 2（4 卡），并对每个正样本从 ANN top 200 中均匀采样一个负样本 [KB-5 所在章节背景]。需要说明：上述 ANCE 实现参数出自章节背景，非 [KB-n] 直接引文，仅用于理解"动态 vs 静态"的差异语境。

### 4. 消融实验中"额外 BM25 负样本"的表述

原文消融部分描述了 in-batch 负样本训练：使用相似配置（7 个 gold 负样本）的 in-batch 训练显著提升结果，其与标准 1-of-N 训练的关键区别在于 gold 负样本来自同一 batch 还是整个训练集；in-batch 训练本质上是复用 batch 中已有负样本的省内存方式，随 batch size 增大准确率持续提升 [KB-6 所在章节背景]。此外，原文探索了在 in-batch 训练中加入"hard"负样本——即对问题有高 BM25 分数但不含答案串的段落，这些段落作为同一 batch 中所有问题的负样本；结果显示加入 1 个 BM25 负样本提升明显，加入 2 个则不再有进一步帮助 [KB-6 所在章节背景，对应 Table 3 说明]。Table 3 的说明明确指出 G.+BM25(1) 与 G.+BM25(2) 表示 in-batch 训练分别附加 1 或 2 个 BM25 负样本，作为 batch 内所有问题的负段落 [KB-6]。

### 5. 材料未覆盖的部分

- DPR 原文中 in-batch negatives 的具体机制（如是否使用 gold passages 作为批内负样本、batch size、每问题负样本数）在给定 [KB-n] 中**论文中未报告**；仅能从 [KB-1] 推断其属于"in-batch 局部负样本"类别。
- DPR 中 BM25 hard negatives 的具体数量（如 top-k 取值、每问题采样几个）在 [KB-n] 中**论文中未报告**；[KB-2] 仅说明其为"BM25 top 100 随机采样"与"1:1 组合"两种基线设定，且该 1:1 组合被归于 Karpukhin et al., 2020。
- DPR 的训练超参（batch size、warm-up 步数等）在本材料中**论文中未报告**。

## 结论

依据所给原文，可确认的 DPR 负样本策略要点为：(1) DPR 被归入"in-batch 局部负样本"路线，原文指出该类负样本在稠密检索表示学习中并不显著优于稀疏检索负样本，且理论分析认为局部 in-batch 负样本会导致梯度方差高、收敛慢 [KB-1][KB-4]；(2) DPR 的 BM25 相关负样本做法在基线中被表述为"从 BM25 top 100 随机采样"以及"BM25 与随机负样本 1:1 组合"（后者明确归于 Karpukhin et al., 2020）[KB-2]；(3) 原文确认 BM25 负样本有助于训练稠密检索，并据此设计 BM25 warm-up，ANCE 亦以 BM25 负样本或 DPR checkpoint 做 warm up [KB-5]；(4) 消融显示在 in-batch 训练中额外加入 1 个高 BM25 分数但不含答案的 hard 负样本可显著提升，加入 2 个无进一步收益 [KB-6]。至于 DPR 的批内负样本具体配置与 BM25 负样本数量，材料中**论文中未报告**，需回到 DPR 原文补充取证。

## 参考文献

[KB-1] 用户专题论文库片段，章节 1 INTRODUCTION，页码 1

[KB-2] 用户专题论文库片段，章节 5 EXPERIMENTAL METHODOLOGIES，页码 5

[KB-3] 用户专题论文库片段，章节 1 INTRODUCTION，页码 2（Figure 1 说明）

[KB-4] 用户专题论文库片段，章节 1 INTRODUCTION，页码 2

[KB-5] 用户专题论文库片段，章节 5 EXPERIMENTAL METHODOLOGIES，页码 5

[KB-6] 用户专题论文库片段，章节 5.2 Ablation Study on Model Training，页码 6（Table 3 说明）

### 4. RocketQA 原文如何描述其负样本策略（如跨 batch negatives、去偏 hard negative 采样、denoised negatives 等），各阶段训练分别使用了什么负样本配置？（COMPLETED）

## 问题陈述

本步骤聚焦一个问题：RocketQA 原文如何描述其负样本（hard negative）策略（如 cross-batch negatives、denoised hard negatives 等），以及各阶段训练分别使用了什么负样本配置？

## 研究取证

### 1. 三项核心技术贡献中的负样本策略

RocketQA 明确提出三项训练策略，其中两项直接与负样本相关：cross-batch negatives（跨 batch 负样本）与 denoised hard negatives（去噪 hard negatives），第三项为 data augmentation（数据增强）[KB-1][KB-3][KB-4]。

原文对三项贡献的定位表述为："The proposed RocketQA introduces three novel training strategies to improve dense passage retrieval for open-domain QA, namely cross-batch negatives, denoised hard negatives, and data augmentation." [KB-1]

### 2. Cross-batch negatives（跨 batch 负样本）

原文对该策略的描述是：与 in-batch negatives 相比，cross-batch negatives 增加了训练中每个问题可用的负样本数量，并缓解了训练与推理之间的差异（discrepancy）。原文写道："First, RocketQA introduces cross-batch negatives. Comparing to inbatch negatives, it increases the number of available negatives for each question during training, and alleviates the discrepancy between training and inference." [KB-2]

其动机来自 dual-encoder 训练与推理不一致的挑战：推理时需从数百万候选中识别正样本，而训练时受单 GPU 内存限制只能在很小的候选集上估计概率 [KB-2]（该挑战在引言背景中亦有阐述）。

### 3. Denoised hard negatives（去噪 hard negatives）

原文描述该策略旨在从检索器返回的 top-ranked 结果中移除假负样本（false negatives），从而获得更可靠的 hard negatives。原文写道："Second, RocketQA introduces denoised hard negatives. It aims to remove false negatives from the top-ranked results retrieved by a retriever, and derive more reliable hard negatives." [KB-2]

其动机是"未标注正样本"问题：仅检查 top-K 检索结果容易漏掉相关段落，从 top-k 检索段落中采样 hard negatives 很可能引入 false negatives [KB-2]（引言背景中以 MSMARCO 为例说明人工检查发现 70% 未标注的 top 检索段落实际为正样本）。

### 4. 与 data augmentation 的关系

第三项贡献是利用 cross-encoder 为大规模无监督数据生成高质量伪标签，用于训练 dual-encoder 检索器 [KB-2]。原文指出 cross-encoder 在理论与实践上均比 dual-encoder 更强，故用其为无标注数据生成伪标签 [KB-2]。此项属于训练数据层面的策略，而非直接的负样本采样策略。

## 材料不足之处（明确说明）

基于所提供的 [KB-n] 原文片段，以下内容**论文中未报告**，无法归纳：

- **各阶段训练的具体负样本配置**：材料仅给出三项策略的高层描述，未包含分阶段（如 stage-1/stage-2/stage-3 或多阶段 pipeline）训练时各自的负样本数量、来源、采样比例等具体配置。原文片段中未出现分阶段负样本配置的细节。
- **cross-batch negatives 的具体实现机制**（如跨 batch 的队列/缓存大小、如何跨 batch 共享负样本等）在给定片段中未报告。
- **denoised hard negatives 的具体去噪方法**（如使用 cross-encoder 打分的阈值、过滤规则等）在给定片段中未报告。
- 材料中出现的 [KB-5][KB-6] 属于 ANCE 论文引言（讨论 in-batch 局部负样本的局限与全局负样本），并非 RocketQA 原文，故不用于归纳 RocketQA 的负样本策略。

## 结论

依据所给原文，RocketQA 的负样本相关策略可归纳为两条核心：**cross-batch negatives**（相较 in-batch negatives 扩大每个问题的可用负样本数量并缓解训练-推理差异）[KB-2]，以及 **denoised hard negatives**（从 top-ranked 检索结果中去除 false negatives 以获得更可靠 hard negatives）[KB-2]；二者与 data augmentation 共同构成三项主要技术贡献 [KB-1][KB-3][KB-4]。至于**各阶段训练的具体负样本配置**，所提供的原文片段中未报告，需查阅论文方法章节（如第 3 节训练流程）的完整内容方可补充。

## 参考文献

[KB-1] RocketQA 原文，1 Introduction，页码 2

[KB-2] RocketQA 原文，1 Introduction，页码 2

[KB-3] RocketQA 原文，Abstract，页码 1

[KB-4] RocketQA 原文，5 Conclusions，页码 9

[KB-5] ANCE 论文，1 INTRODUCTION，页码 2（非 RocketQA 原文，仅作区分说明）

[KB-6] ANCE 论文，1 INTRODUCTION，页码 1（非 RocketQA 原文，仅作区分说明）

# RocketQA 负样本策略取证归纳

## 问题陈述

本步骤需依据论文原文，梳理 RocketQA 在**各训练阶段（STEP 1–4）**的具体负样本配置，包括负样本来源、数量、采样比例以及 cross-batch negatives 的使用情况，并核对其"去噪 hard negative 采样（denoised hard negative sampling）"的原文描述。

## 研究结果

### 1. 总体负样本框架：cross-batch negatives 贯穿所有阶段

RocketQA 的训练流程类比"多级火箭"，双编码器性能在 STEP 1、3、4 逐步提升。原文明确指出 cross-batch negative 策略用于双编码器训练的**所有阶段**：

> "Note that the cross-batch negative strategy is applied through all the steps for training the dualencoder." [KB-3]

### 2. 各阶段负样本配置

**STEP 1：cross-batch negatives 训练初始双编码器**

> "STEP1: Train a dual-encoder M (0) D by using crossbatch negatives on DL." [KB-3]

即初始双编码器 M(0)D 在人工标注数据 DL 上使用 cross-batch negatives 训练。

**STEP 2：训练 cross-encoder，负样本从 top-k 检索结果随机采样**

> "STEP 2: Train a cross-encoder MC on DL. The positives used for training the cross-encoder are from the original training set DL, while the negatives are randomly sampled from the top-k passages (excluding the labeled positive passages) retrieved by M (0) D fromC for each question q ∈ QL." [KB-3]

即 cross-encoder 的正样本来自原始训练集，负样本从 M(0)D 为每个问题检索出的 top-k passages 中**随机采样**（排除标注正样本），目的是让 cross-encoder 适应双编码器检索结果的分布。

**STEP 3：denoised hard negative sampling 训练双编码器 M(1)D**

> "STEP 3: Train a dual-encoder M (1) D by further introducing denoised hard negative sampling on DL. Regarding to each question q ∈ QL, the hard negatives are sampled from the top passages retrieved by M (0) D from C, and only the passages that are predicted as negatives by the cross-encoder MC with high confidence scores will be selected." [KB-3]

即从 M(0)D 检索出的 top passages 中采样 hard negatives，且**仅保留被 cross-encoder MC 以高置信度判定为负样本的段落**——这就是"去噪（denoised）"的核心机制。

**STEP 4：用 cross-encoder 标注伪数据，联合训练 M(2)D**

> "STEP 4: Construct pseudo training data DU by usingMC to label the top-k passages retrieved by M (1) D fromC for each question q ∈ QU, and then train a dual-encoder M (2) D on both the manually labeled training data DL and the automatically augmented training data DU ." [KB-3]

即用 MC 为无标注问题 QU 的 top-k 检索段落打标签，构建伪训练数据 DU，并在 DL 与 DU 上联合训练 M(2)D。cross-batch negatives 同样适用于此阶段（见第 1 点）。

### 3. 关于 in-batch / cross-batch 负样本的消融证据（补充背景）

论文 5.2 节消融实验（针对 DPR 式训练方案）讨论了 in-batch 负样本的效果，可作为理解 cross-batch 思路的佐证：

> "The middle bock is the in-batch negative training (Section 3.2) setting. We find that using a similar configuration (7 gold negative passages), in-batch negative training improves the results substantially." [KB-2]

> "Finally, we explore in-batch negative training with additional 'hard' negative passages that have high BM25 scores given the question, but do not contain the answer string (the bottom block). These additional passages are used as negative passages for all questions in the same batch. We find that adding a single BM25 negative passage improves the result substantially while adding two does not help further." [KB-2]

Table 3 说明中亦定义了 IB 与 G.+BM25(1)/(2) 的含义：

> "G.+BM25(1) and G.+BM25(2) denote in-batch training with 1 or 2 additional BM25 negatives, which serve as negative passages for all questions in the batch." [KB-1]

## 结论

依据所给原文，RocketQA 的负样本策略可归纳为：

- **cross-batch negatives 贯穿双编码器训练的全部阶段（STEP 1、3、4）** [KB-3]；
- **STEP 2** 的 cross-encoder 负样本为从 M(0)D 检索的 top-k 段落中**随机采样**（排除正样本）[KB-3]；
- **STEP 3** 采用**去噪 hard negative 采样**：hard negatives 来自 M(0)D 的 top passages，仅保留被 cross-encoder 高置信度判为负的段落 [KB-3]；
- **STEP 4** 通过 cross-encoder 对无标注问题的 top-k 检索结果打标签生成伪数据，与人工标注数据联合训练，并沿用 cross-batch negatives [KB-3]。

**论文中未报告的部分**：所给原文片段**未给出**各阶段具体的负样本**数量**（如每问题 n 个 negatives 的确切数值）、**采样比例**（如 top-k 的 k 值、去噪保留比例、高置信度阈值）等定量细节；这些实现细节原文指向 "Section 4"（"The implementation details of denoising hard negatives and data augmentation can be found in Section 4" [KB-3]），但该节内容不在本次提供的材料范围内，故不作推测。

## 参考文献

[KB-1] 用户专题论文库片段：RocketQA, Section 5.2 Ablation Study on Model Training, Table 3 说明, p.6

[KB-2] 用户专题论文库片段：RocketQA, Section 5.2 Ablation Study on Model Training（In-batch negative training 段落）, p.6

[KB-3] 用户专题论文库片段：RocketQA, Section 3.4 The Training Procedure（STEP 1–4 及 cross-batch 说明）, p.5

## 证据与分歧

- 2004.04906（ACCEPTED）
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
- 2010.08191（ACCEPTED）
- 检出 3 条存在反向支持关系的来源，需要单独核对条件和适用范围。
- 有 4 条来源仍待引用复核，不能作为无条件结论。

## 覆盖度与缺口

- 已检查来源：15 条；已完成步骤：4 / 4。

## 来源清单

- 2004.04906
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
- 2010.08191