# B2 · full · round 1

DPR、ColBERT、ANCE、RocketQA 各自报告了怎样的资源需求（算力、索引体积的定性描述）？

---

# 研究报告

## 直接回答

本报告汇总了 4 个已完成研究步骤的发现；逐项依据见下方证据与局限。

**研究问题**：DPR、ColBERT、ANCE、RocketQA 各自报告了怎样的资源需求（算力、索引体积的定性描述）？

## 资料范围

- 专题内全部就绪资料（未限定单篇）

## 逐项发现

### 1. DPR 论文报告了哪些算力与索引体积相关的资源需求？（COMPLETED）

# DPR 论文报告了哪些算力与索引体积相关的资源需求？

## 问题陈述

本步骤聚焦于：在 DPR、ColBERT、ANCE、RocketQA 四者的资源需求对比中，**DPR 论文本身**报告了哪些与算力（离线编码/训练计算量）和索引体积（空间占用）相关的资源需求定性描述。

## 研究发现的核查结果

基于用户专题论文库中的原文片段逐条核查，结论如下：

### 1. 关于 DPR 自身的算力需求

论文库中**没有**任何片段直接报告 DPR 自身的训练算力、GPU 数量、编码耗时等资源需求。原文中 DPR 仅作为**对比基线和设置参照**出现：

- ANCE 论文的实验方法部分说明 OpenQA 实验"following the exact settings from Karpukhin et al. (2020)"，即沿用 DPR 的实验设置，但未复述 DPR 的资源开销 [KB-1]。
- RocketQA 论文在阅读器实验中"take 100 retrieved passages during inference (the same setting used in DPR)"，同样只是引用 DPR 的推理设置（100 篇检索段落），并非 DPR 的资源需求报告 [KB-4]。

因此，**DPR 论文报告的算力需求在本材料中未报告**。

### 2. 关于 DPR 的索引体积需求

论文库中同样**没有**片段报告 DPR 的索引体积或空间占用。相关片段报告的是其他系统的索引资源：

- ColBERT 报告其索引 MS MARCO（9M 段落）约需 3 小时、单台四 GPU 服务器，空间足迹可低至"数十 GiB"级别（"a space footprint of as little as few tens of GiBs"）[KB-3]；其 §4.5 进一步讨论索引吞吐与空间足迹，并指出任何 BERT 类模型都必须承担每篇文档至少一次编码的计算成本 [KB-6]。这是 **ColBERT 自身的资源报告**，不涉及 DPR。
- ANCE 论文报告了其效率测量条件（单块 32GB V100 GPU、650GB 内存的云 VM 等），并指出训练中主要计算量在于用新检查点刷新训练语料的编码 [KB-5]（章节背景中提及）。这同样是 **ANCE 的资源描述**，而非 DPR。

因此，**DPR 论文报告的索引体积需求在本材料中未报告**。

## 结论

在当前提供的论文库片段范围内，**DPR 论文所报告的算力需求与索引体积需求均未报告**。所有片段中 DPR 仅以"实验设置参照"（Karpukhin et al., 2020 的设置、推理取 100 篇段落）的形式被引用 [KB-1][KB-4]，没有任何关于 DPR 训练算力、编码耗时、GPU 配置或索引空间占用的原始描述。若需回答"DPR 报告了怎样的资源需求"，需补充 DPR 原论文（Karpukhin et al., 2020）中相关章节的原文片段，现有材料不足以支撑该结论，不应作任何推测。

## 参考文献

[KB-1] 用户专题论文库片段，章节 5 EXPERIMENTAL METHODOLOGIES，页码 5

[KB-2] 用户专题论文库片段，章节 4 EXPERIMENTAL EVALUATION，页码 6

[KB-3] 用户专题论文库片段，章节 1 INTRODUCTION，页码 2

[KB-4] 用户专题论文库片段，章节 4.2.3 Passage Reading with RocketQA，页码 9

[KB-5] 用户专题论文库片段，章节 6.1 EFFECTIVENESS AND EFFICIENCY，页码 7

[KB-6] 用户专题论文库片段，章节 4.5 Indexing Throughput & Footprint，页码 9

## 问题陈述

本步骤要回答：DPR（Karpukhin et al., 2020）原论文报告的**算力需求**与**索引体积/索引空间占用**的定性描述是什么？

## 研究发现的归纳

### 1. 语料规模与索引体积（可间接推知索引规模）

用户论文库中唯一直接涉及 DPR 索引规模的材料来自对 NQ 数据集的描述：在 Karpukhin et al. (2020) 的设定下，约 62,000 条 factoid 问题被选出，全部维基百科文章被切分为 passage 集合，语料中"有超过 2100 万个 passage（more than 21 million passages in the corpus）"[KB-2]。

- 这给出了 DPR 需要建立索引的**文档/passage 数量级**（>21M passages），是索引体积的直接规模依据。
- 但原文片段**未给出**每条 passage 的向量维度、每向量字节数、索引总字节数或磁盘占用等定量/定性的索引体积描述。这些内容在所提供的 [KB-n] 材料中**论文中未报告**。

### 2. 算力需求

所提供的材料中与"算力/效率"相关的仅有：

- 稠密检索（DR，含 DPR）的一个理想特性是"可借助近似最近邻（ANN）搜索获得效率支持（efficiency support from approximate nearest neighbor (ANN) search）"[KB-4]。这属于对检索阶段效率机制的定性说明，并非 DPR 论文报告的算力开销。
- DPR 被描述为"简单而有效"的解决方案，"不依赖额外预训练或复杂的联合训练方案（without relying on additional pretraining or complex joint training schemes）"[KB-3]，这是对训练复杂度的间接定性表述，但并非具体的算力（GPU 数量、训练时长、编码开销等）报告。

需要说明：材料中出现的 GPU 配置、Trainer:Inference 分配、V100 32GB、650GB RAM 等细节属于 **ANCE 论文**的实验设置（见章节背景 P-9558cde80da4），**不是 DPR 原论文的报告**，不能归为 DPR 的资源需求。

## 结论

基于用户论文库的原文：

1. **索引体积/规模**：DPR 在 NQ 设定下对全部维基百科建立的 passage 语料"超过 2100 万个 passage"[KB-2]，这是索引规模的唯一可引用依据；索引的具体体积（维度、字节、存储占用）在材料中**论文中未报告**。
2. **算力需求**：材料仅提供定性旁证——DR 可依托 ANN 搜索获得效率支持[KB-4]，且 DPR 不依赖额外预训练或复杂联合训练[KB-3]；DPR 原论文报告的具体算力（GPU 数量、训练/编码时长、索引构建开销等）在所提供的 [KB-n] 材料中**论文中未报告**，不应推测。

如需补齐 DPR 的算力与索引体积定量描述，需补充 DPR 原论文（Karpukhin et al., 2020）正文中关于索引规模（如 passage 向量维度、索引大小）与硬件/训练时长的原文片段。

## 参考文献

[KB-2] 用户专题论文库片段，章节 4.1.1 Datasets，页码 6

[KB-4] 用户专题论文库片段，章节 1 INTRODUCTION，页码 1

[KB-3] 用户专题论文库片段，章节 7 RelatedWork，页码 9

### 2. ColBERT 论文报告了哪些算力与索引体积相关的资源需求？（COMPLETED）

## 问题陈述

本步骤要回答的问题是：**ColBERT 论文报告了哪些与算力和索引体积相关的资源需求？** 以下归纳只依据用户专题论文库中的原文片段 [KB-1] 至 [KB-6]，以及各章节背景中已提供的原文；论文未报告的内容将明确标注“论文中未报告”。

---

## ColBERT 报告的资源需求

### 一、查询侧算力：相比 BERT 类模型大幅降低

- 在重排序场景（“ColBERT (re-rank)”）下，ColBERT 相对现有 BERT 类模型**提速超过 170 倍，且所需 FLOPs 少 14,000 倍**，同时效果优于所有非 BERT 基线 [KB-1]。
- 摘要中给出了同一量级的表述：ColBERT“执行速度快两个数量级，每查询所需 FLOPs 少四个数量级” [KB-2]；结论部分再次强调“比现有 BERT 类模型快 170 倍以上、每查询 FLOPs 少 14,000 倍” [KB-6]。
- 延迟方面，ColBERT 可在**数十毫秒到数百毫秒**内完成查询服务 [KB-1]。

其算力节省的机制在于：查询与文档分别独立编码，再通过廉价且利于剪枝的交互计算评估相关性，从而可以离线预计算文档表示 [KB-2][KB-4]。

### 二、索引（离线）算力需求

- ColBERT 的索引阶段是它**唯一需要让文档通过 BERT** 的环节；该过程“实用”：**在单台配备四块 GPU 的服务器上，约 3 小时即可索引 MS MARCO 的 900 万段落** [KB-1]。
- §4.5 进一步说明：基于图 6 的索引吞吐量，ColBERT 可在**约三小时**内完成 MS MARCO 的索引；并指出任何 BERT 类模型都至少要对每个文档计算一次，而 ColBERT 对每个文档**仅用 BERT 编码一次**，现有 BERT 类排序器则可能要为每个查询在数百个文档上重复类似计算 [KB-5]。
- 论文将索引相关成本明确列为研究问题 RQ4：“ColBERT 在离线计算与内存开销方面的索引相关成本是什么？” [KB-3]。

### 三、索引体积（空间占用）

- 引言中给出定性描述：ColBERT 在保持效果的同时，**空间占用可低至数十 GiB（few tens of GiBs）** [KB-1]。
- §4.5 给出更具体的数字：在降低嵌入维度或每维字节数的各种设置中，**最省空间的设置（使用 24 维向量、以 2 字节浮点存储、采用余弦相似度重排序）仅比最耗空间的设置低 1% 的 MRR@10，而前者只需 27 GiB 来表示 MS MARCO 集合** [KB-5 所属章节背景，Table 4 段落]。

### 四、材料未覆盖或论文未报告的部分

- 关于**具体 GPU 型号、显存占用、训练成本、能耗**等硬件层面的细化指标：上述片段中**论文中未报告**。
- 关于索引体积随不同配置变化的**完整表格数值**：片段仅给出“最省空间设置 = 27 GiB”与“最耗空间设置”的对比结论，其余各档具体数值**论文片段中未报告**。

---

## 结论

ColBERT 论文从三个维度报告了资源需求：**查询侧算力**上，相比 BERT 类模型提速 170 倍以上、每查询 FLOPs 少 14,000 倍（即快两个数量级、FLOPs 少四个数量级），延迟为数十至数百毫秒 [KB-1][KB-2][KB-6]；**索引算力**上，单台四 GPU 服务器约 3 小时可索引 MS MARCO 的 900 万段落，且每个文档仅需 BERT 编码一次 [KB-1][KB-5]；**索引体积**上，空间占用可低至数十 GiB，最省空间配置（24 维、2 字节浮点）仅需 27 GiB 表示 MS MARCO 集合，且质量仅下降 1% MRR@10 [KB-1][KB-5 背景 Table 4]。更细的硬件规格与训练成本等则论文中未报告。

---

## 参考文献

[KB-1] ColBERT 论文，1 INTRODUCTION，第 2 页。

[KB-2] ColBERT 论文，ABSTRACT，第 1 页。

[KB-3] ColBERT 论文，4 EXPERIMENTAL EVALUATION，第 6 页。

[KB-4] ColBERT 论文，1 INTRODUCTION，第 2 页。

[KB-5] ColBERT 论文，4.5 Indexing Throughput & Footprint，第 9 页。

[KB-6] ColBERT 论文，5 CONCLUSIONS，第 9 页。

## 问题陈述

本步骤需归纳 DPR、ANCE、RocketQA 三篇论文所报告的**算力需求**与**索引体积**的定性描述（ColBERT 部分由其他步骤负责）。要求仅依据提供的论文片段 [KB-1] 至 [KB-6] 归纳，材料未覆盖之处明确标注“论文中未报告”。

## 研究发现

### 一、ANCE 的资源需求

**索引维护与算力机制（定性）**

ANCE 使用一个**异步更新的 ANN 索引**来存储语料表示：与 Guu et al. (2020) 类似，它维护一个 Inferencer，用当前正在优化的 DR 模型的最新 checkpoint 并行计算文档编码，并在完成后刷新用于负采样的 ANN 索引，以跟上模型训练 [KB-1]。这一定性描述了 ANCE 的核心算力开销来源——即“用新 checkpoint 重新编码训练语料”是训练中的主要计算量，但可通过异步索引刷新来缓解 [KB-4]。

**具体硬件与配置**

在实验设置中，ANCE 给出了较为具体的算力配置：使用 **1:1 的 Trainer:Inference GPU 分配**、每 10k 训练批次刷新一次索引、batch size 8、梯度累积步数 2、共 **4 块 GPU**；ANN 搜索使用 Faiss IndexFlatIP 索引 [KB-4]。效率测量在**单块 32GB V100 GPU**、配备 Intel Xeon Platinum 8168 CPU 与 **650GB RAM** 的云虚拟机上完成 [KB-4]。此外，为适配长文档，ANCE 采用 FirstP（取文档前 512 tokens）与 MaxP（切分为最多 4 个 512-token 段落并做 max-pooling）两种设置，其中 max-pooling 由 ANN 原生支持 [KB-4]。

**索引体积**

关于索引体积（如索引大小、向量维度对存储的影响），提供的片段中**论文中未报告**具体的索引体积数值或定性描述。仅可间接看到：ANCE 在商业搜索引擎生产系统中评估了“不同语料规模、编码维度以及精确/近似搜索”下的离线收益 [KB-2 所属章节背景]，但片段本身未给出索引体积的定性结论。

### 二、DPR 的资源需求

提供的 [KB-1]–[KB-6] 片段中，DPR 仅作为**对比基线**被提及：DPR 使用 in-batch 随机采样与来自 BM25 结果的硬负采样 [KB-5]；OpenQA 实验沿用 DPR 的设置（Karpukhin et al., 2020）[KB-2]；RocketQA 复用 DPR 的抽取式 reader，并在推理时取 100 篇检索段落（与 DPR 相同设置）[KB-3]。

关于 DPR 论文自身报告的**算力需求**与**索引体积**定性描述，**论文中未报告**（现有片段未包含 DPR 原文的相关陈述，不得据此推断）。

### 三、RocketQA 的资源需求

片段 [KB-3]、[KB-5]、[KB-6] 主要描述 RocketQA 的训练策略（cross-batch negatives、denoised hard negatives、data augmentation）[KB-6]、与稀疏/稠密检索基线的对比 [KB-5]，以及其检索结果对阅读器的影响（推理取 100 篇、训练取 top 50 篇）[KB-3]。

关于 RocketQA 论文自身报告的**算力需求**与**索引体积**定性描述，**论文中未报告**（现有片段未包含相关陈述）。

## 结论

在所提供的材料范围内，只有 **ANCE** 报告了可辨识的资源需求信息：其核心算力开销是用新 checkpoint 重新编码训练语料以刷新 ANN 索引，采用异步索引刷新缓解，并在实验中给出 4 GPU、1:1 Trainer:Inference 分配、32GB V100、650GB RAM 等具体配置 [KB-1][KB-4]。关于**索引体积**的定性描述，ANCE 片段未报告。

**DPR 与 RocketQA** 在现有片段中仅作为对比基线或设置参照出现，其论文自身报告的算力需求与索引体积描述**论文中未报告**，需补充相应原文片段后方可归纳，此处不作猜测。

## 参考文献

[KB-1] ANCE 论文，1 INTRODUCTION，第 2 页（异步 ANN 索引与 Inferencer 描述）

[KB-2] ANCE 论文，5 EXPERIMENTAL METHODOLOGIES，第 5 页（OpenQA 沿用 DPR 设置）

[KB-3] RocketQA 论文，4.2.3 Passage Reading with RocketQA，第 9 页（复用 DPR reader 与检索设置）

[KB-4] ANCE 论文，6.1 EFFECTIVENESS AND EFFICIENCY，第 7 页（Table 5 效率、硬件与训练配置）

[KB-5] RocketQA 论文，4.2.1 Dense Passage Retrieval，第 7 页（DPR/ANCE 作为基线的采样方式）

[KB-6] RocketQA 论文，1 Introduction，第 2 页（三项训练策略）

### 3. ANCE 论文报告了哪些算力与索引体积相关的资源需求？（COMPLETED）

## 问题陈述

本步骤聚焦于 ANCE 论文，梳理其报告的与**算力**和**索引体积**相关的资源需求。

## 研究发现

### 一、算力需求（训练与索引刷新）

ANCE 的核心资源开销来自“异步索引刷新”机制：它需要并行运行一个 Inferencer，用最新 checkpoint 重新编码整个语料库并重建 ANN 索引。

- 论文明确指出该机制的必要性：索引更新包含两步——Inference（用更新后的 DR 模型刷新全语料表示）与 Index（用新表示重建 ANN 索引）；其中“Although Index is efficient (Johnson et al., 2019), **Inference is too expensive to compute per batch as it requires a forward pass on the entire corpus** which is much bigger than the training batch” [KB-3]。也就是说，全语料前向推理是主要的昂贵开销，无法逐 batch 执行，因而必须异步、按间隔刷新。
- 实现上采用与 Guu et al. (2020) 类似的异步刷新，并维护一个 Inferencer 并行计算文档编码 [KB-1]：“we maintain an Inferencer that parallelly computes the document encodings with a recent checkpoint from the being optimized DR model, and refresh the ANN index used for negative sampling once it finishes” [KB-1]。
- **具体配置**：论文报告“We use **1:1 Trainer:Inference GPU allocation**, index refreshing per 10k training batches, batch size 8, and gradient accumulation step 2 on **4 GPUs**” [KB-2]。即训练与推理各占一半 GPU，共 4 块 GPU；每 10k 训练 batch 刷新一次索引。
- **效率测量环境**：“We measured ANCE efficiency using a **single 32GB V100 GPU**, on a cloud VM with **Intel(R) Xeon(R) Platinum 8168 CPU and 650GB of RAM memory**” [KB-2]。这给出了测量效率时所使用的硬件规格（单块 32GB V100、指定 CPU 与 650GB 内存）。

> 说明：关于 1:1 GPU 分配是否“足够”，正文提到“Appendix A.3 shows an 1:1 GPU split is sufficient to minimize the influence of this gap”（见章节背景 P-454776103b5e），但该结论位于附录 A.3，用户论文库 [KB-n] 片段中未直接收录该附录原文，故此处不作为 [KB-n] 已校验结论引用。

### 二、索引体积

- 关于**索引体积的定性/定量描述**：在所给的 [KB-n] 原文片段中，ANCE 论文**未报告**索引体积（如 GB 数、向量维度对应的存储规模等）的具体描述。相关片段仅涉及 ANN 索引的类型与刷新机制（Faiss IndexFlatIP 索引 [KB-2]；异步刷新流程 [KB-1][KB-3]），但未给出索引占用空间的定性或定量说明。
- 因此，就“索引体积”这一维度而言，基于当前材料应标注为：**论文（所给片段）中未报告**。

## 结论

就 ANCE 论文所报告的**算力需求**而言，材料给出了明确信息：其核心开销是“对整个语料库做前向推理以刷新 ANN 索引”，该操作过于昂贵无法逐 batch 执行，故采用异步刷新；实现上使用 1:1 的 Trainer:Inference GPU 分配、共 4 块 GPU、batch size 8、梯度累积 2、每 10k 训练 batch 刷新一次索引，效率测量在单块 32GB V100、Intel Xeon Platinum 8168 CPU、650GB RAM 的云 VM 上完成 [KB-1][KB-2][KB-3]。

就**索引体积**而言，所给 [KB-n] 原文片段**未报告**任何关于索引体积的定性或定量描述，故本步骤无法据此归纳 ANCE 的索引体积需求，需明确标注为“论文（所给片段）中未报告”。

## 参考文献

[KB-1] ANCE 论文，1 INTRODUCTION，第 2 页（用户专题论文库片段）

[KB-2] ANCE 论文，5 EXPERIMENTAL METHODOLOGIES，第 5 页（用户专题论文库片段）

[KB-3] ANCE 论文，4 APPROXIMATE NEAREST NEIGHBOR NOISE CONTRASTIVE ESTIMATION，第 5 页（用户专题论文库片段）

[KB-4] ColBERT 论文，1 INTRODUCTION，第 2 页（用户专题论文库片段，背景对照）

[KB-5] ColBERT 论文，4 EXPERIMENTAL EVALUATION，第 6 页（用户专题论文库片段，背景对照）

[KB-6] ANCE 论文，5 EXPERIMENTAL METHODOLOGIES，第 5 页（用户专题论文库片段）

### 4. RocketQA 论文报告了哪些算力与索引体积相关的资源需求？（COMPLETED）

## 问题陈述

本步骤需要回答：RocketQA 论文报告了哪些与算力（计算资源）和索引体积（存储/内存开销）相关的资源需求？

## 研究结论

基于用户专题论文库提供的原文片段，**RocketQA 论文并未报告任何量化的算力需求或索引体积数据**，也未对索引体积做定性描述。可提取的相关信息仅有以下几点间接线索：

**1. 训练阶段受显存限制的定性说明（挑战层面）**

论文在阐述 dual-encoder 训练困难时提到，训练时只能在小候选集上估计正例概率，原因是"due to the limited memory of a single GPU (or other device)"，即单个 GPU 内存有限，无法在训练中使用大规模候选集，从而造成训练与推理之间的差异 [KB-1]。这是论文中对训练资源约束的唯一直接定性描述，但未给出具体显存数值、GPU 型号或训练时长。

**2. 推理阶段检索规模与候选集大小**

论文指出推理时需从"a large collection containing millions of candidates"（数百万候选段落的大集合）中为每个问题识别正例 [KB-1]；以 MSMARCO 为例，整个集合包含"8.8M passages"（880 万段落）[KB-1]。这属于对检索语料规模的描述，可间接反映索引所需覆盖的数据量，但论文未报告由此产生的索引体积、向量维度或内存占用。

**3. 端到端 QA 推理时读取的段落数量**

在 passage reading 实验中，RocketQA 沿用 DPR 的设置，推理时取 100 篇检索段落（"take 100 retrieved passages during inference"），训练新 reader 时则取 top 50 篇 [KB-3]。这属于推理流程的配置参数，而非算力或索引体积报告。

**4. 其他相关但非资源类信息**

- 论文主实验使用 ERNIEbase，并在附录中用 BERTbase 做替换对比，性能略降（MSMARCO MRR@10 约 0.6、NQ R@100 约 1.6）[KB-5]。这涉及预训练模型规模，但论文未据此报告算力开销。
- 论文提到 cross-encoder 架构"Though inefficient"（效率较低），用于生成伪标签 [KB-1]。这是对 cross-encoder 计算效率的定性描述，但未量化。

## 明确缺失项

以下内容在提供的原文片段中**论文中未报告**：

- 训练所需的 GPU 数量、型号、显存、训练时长或 FLOPs；
- 索引的体积（GB 级大小）、向量维度、索引文件大小或内存占用；
- 索引构建（离线编码）的耗时或算力开销；
- 任何与 DPR、ColBERT、ANCE 的资源需求横向对比数据（片段中出现的 DPR/ANCE/ColBERT 均为性能或预训练模型对比，非资源对比）[KB-4][KB-5][KB-6]。

## 参考文献

[KB-1] RocketQA 论文，1 Introduction，页码 2

[KB-2] RocketQA 论文，1 Introduction，页码 2

[KB-3] RocketQA 论文，4.2.3 Passage Reading with RocketQA，页码 9

[KB-4] RocketQA 论文，4 EXPERIMENTAL EVALUATION，页码 6

[KB-5] RocketQA 论文，A.1 The Effects of Pre-trained LMs，页码 13

[KB-6] RocketQA 论文，4.2.1 Dense Passage Retrieval，页码 7

## 证据与分歧

- 2004.04906（ACCEPTED）
- 2004.04906（ACCEPTED）
- 2004.12832（ACCEPTED）
- 2004.12832（ACCEPTED）
- 2004.12832（ACCEPTED）
- 2004.12832（ACCEPTED）
- 2007.00808（ACCEPTED）
- 2007.00808（ACCEPTED）
- 2007.00808（ACCEPTED）
- 2010.08191（ACCEPTED）
- 2010.08191（ACCEPTED）
- 2010.08191（ACCEPTED）
- 检出 1 条存在反向支持关系的来源，需要单独核对条件和适用范围。
- 有 4 条来源仍待引用复核，不能作为无条件结论。

## 覆盖度与缺口

- 已检查来源：12 条；已完成步骤：4 / 4。

## 来源清单

- 2004.04906
- 2004.04906
- 2004.12832
- 2004.12832
- 2004.12832
- 2004.12832
- 2007.00808
- 2007.00808
- 2007.00808
- 2010.08191
- 2010.08191
- 2010.08191