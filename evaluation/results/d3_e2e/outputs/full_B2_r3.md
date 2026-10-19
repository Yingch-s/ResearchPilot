# B2 · full · round 3

DPR、ColBERT、ANCE、RocketQA 各自报告了怎样的资源需求（算力、索引体积的定性描述）？

---

# 研究报告

## 直接回答

本报告汇总了 4 个已完成研究步骤的发现；逐项依据见下方证据与局限。

**研究问题**：DPR、ColBERT、ANCE、RocketQA 各自报告了怎样的资源需求（算力、索引体积的定性描述）？

## 资料范围

- 专题内全部就绪资料（未限定单篇）

## 逐项发现

### 1. DPR 论文如何定性描述其训练算力与索引体积需求？（COMPLETED）

## 问题陈述

本步骤要回答的是：**DPR 论文如何定性描述其训练算力与索引体积需求？** 需基于用户专题论文库中的原文片段作答，并标注 [KB-n]。

## 取证归纳

**结论先行：所提供的 [KB-1]–[KB-6] 片段中，没有任何一条来自 DPR 论文本身对"训练算力"或"索引体积"的定性描述。** 这些片段均出自其他论文（RocketQA、ANCE、ColBERT），其中对 DPR 的提及仅限于以下间接信息：

### 1. 关于 DPR 的预训练语言模型规模（间接相关，非算力/索引描述）

片段 [KB-1] 在对比不同工作的预训练模型时提到："As shown in Table 6, DPR (Karpukhin et al., 2020) uses BERTbase. ANCE (Xiong et al., 2020) uses RoBERTabase, and ME-BERT (Luan et al., 2020) uses BERTlarge." [KB-1]

这说明 DPR 采用 BERTbase 作为编码器骨干，可视为对模型规模的一个侧面说明，但**并非**对训练算力（如 GPU 数量、训练时长、显存等）的定性描述。

### 2. 关于 DPR 的使用设置（与索引/检索数量相关，但非索引体积）

片段 [KB-6] 提到复现 DPR 阅读器设置时："we first re-use the released model 6 of the extractive reader in DPR (Karpukhin et al., 2020), and take 100 retrieved passages during inference (the same setting used in DPR)." [KB-6]

这描述的是 DPR 在推理时取前 100 个检索段落（top-100 passages）的设置，属于检索/阅读流程配置，**并非**索引体积（index size/footprint）或训练算力的描述。

### 3. 其他片段对 DPR 的提及

- [KB-3]、[KB-4] 仅涉及 ANCE 与 DPR 在效果上的比较（如 "It also outperforms DPR in passage retrieval in OpenQA" [KB-4]），与资源需求无关。
- [KB-2]、[KB-5] 分别讨论 ANCE 的异步索引刷新开销与 ColBERT 的索引吞吐/空间占用，均非 DPR 的资源描述。

## 材料不足之处

针对本步骤的核心问题——"DPR 论文如何定性描述其训练算力与索引体积需求"——**所提供的原文片段中未包含 DPR 论文对此的任何直接表述，论文中未报告**（就本片段集而言）。现有材料仅能确认 DPR 使用 BERTbase 骨干 [KB-1] 及推理取 top-100 段落的设置 [KB-6]，无法据此推断其训练算力或索引体积的定性描述，故不对该部分作任何猜测。

## 参考文献

[KB-1] 用户专题论文库片段，章节 A.1 The Effects of Pre-trained LMs，页码 13

[KB-2] 用户专题论文库片段，章节 4 APPROXIMATE NEAREST NEIGHBOR NOISE CONTRASTIVE ESTIMATION，页码 5

[KB-3] 用户专题论文库片段，章节 6.1 EFFECTIVENESS AND EFFICIENCY，页码 7

[KB-4] 用户专题论文库片段，章节 6.1 EFFECTIVENESS AND EFFICIENCY，页码 6

[KB-5] 用户专题论文库片段，章节 4.5 IndexingThroughput & Footprint，页码 9

[KB-6] 用户专题论文库片段，章节 4.2.3 Passage Reading with RocketQA，页码 9

## 问题陈述

本步骤要回答的是：**DPR 论文如何定性描述其训练算力需求与索引体积需求？** 具体而言，需取证 DPR 原文关于（1）训练所用 GPU 数量与训练时长、（2）索引体积 / 内存占用的定性描述。

## 研究取证

### 一、可用材料与归属说明

需先明确：本次提供的 [KB-1]～[KB-6] 原文片段中，**并非全部出自 DPR 论文**。据内容判断：

- [KB-1]（DPR 论文第 4.2 节 QA Datasets 与 Table 1）与 [KB-5]（DPR 论文第 5.4 节 Run-time Efficiency）属于 DPR 论文原文。
- [KB-2]（第 5 节 Experimental Methodologies，4 GPUs、Faiss IndexFlatIP、32GB V100、650GB RAM）从术语（ANCE、index refreshing per 10k batches）看属于 **ANCE 论文**。
- [KB-3]（第 4.1.3 节 Hardware & Time Measurements，Tesla V100、Titan V、469 GiBs RAM）从章节编号与内容看属于 **ColBERT 论文**。
- [KB-4]（第 6.1 节 End-to-end QA System，eight 32GB GPUs）属于含 DPR Reader 的端到端 QA 系统描述，训练算力指 reader 训练，非 DPR 检索器训练。
- [KB-6]（附录 A.1，ANCE 的 ANN 索引刷新实验）属于 **ANCE 论文**。

因此，严格属于 DPR 检索器本身的算力/索引描述，仅 [KB-1]、[KB-5] 可直接引用；[KB-4] 可作旁证但须说明其对象是 reader。

### 二、DPR 训练算力（GPU 数量、训练时长）

- **GPU 数量**：所给 DPR 原文片段中**未出现** DPR 检索器训练所用 GPU 数量的描述。[KB-1] 仅涉及数据集规模与过滤后训练问题数（Table 1 给出各数据集原始训练样本数与 DPR 实际训练问题数），不含硬件信息。[KB-5] 只讨论推理/检索速度。故就 DPR 检索器训练 GPU 数量而言，**论文（本材料范围内）未报告**。
- **训练时长**：材料中**无任何**关于 DPR 训练时长的定性或定量描述，**论文中未报告**（就本材料范围）。
- 需要区分的旁证：[KB-4] 提到 "All experiments were done on eight 32GB GPUs"，但其上下文是端到端 QA 系统（含 neural reader 的 batch size 设置、负样本采样 m̃=24），指的是 **reader/端到端系统训练**，不能等同于 DPR 检索器训练算力。

### 三、DPR 索引体积与内存占用

- **索引效率的定性描述**：[KB-5] 明确指出 DPR 借助 FAISS 内存索引（in-memory index for real-valued vectors）"can be made incredibly efficient"，在 Intel Xeon CPU E5-2698 v4 @ 2.20GHz、512GB 内存的服务器上，**每秒可处理 995.0 个问题**（每题返回 top 100 passages），而 BM25/Lucene（Java 文件索引）单 CPU 线程每秒仅 23.7 个问题。这是对 DPR 检索/索引运行效率的定性 + 定量描述，隐含"内存索引可承载全库向量"的可行性判断，但**未给出索引的具体体积（GB 数）或内存占用数值**。
- **索引体积的定量描述**：材料中**未给出** DPR 索引的字节体积、向量维度×文档数的估算或内存占用数字，**论文中未报告**（就本材料范围）。
- 可作对照的旁证：[KB-2] 中 ANCE 使用 "Faiss IndexFlatIP Index"，效率测量环境为 "a single 32GB V100 GPU … 650GB of RAM"；[KB-3] 中 ColBERT 索引实验使用 "four Titan V GPUs … 469 GiBs of RAM"，并说明 "we use up to all four GPUs during indexing"。这些是 ANCE / ColBERT 的资源描述，**不可**移用于 DPR。

### 四、小结表（仅限本材料）

| 维度 | DPR 原文是否报告 | 依据 |
|---|---|---|
| 训练 GPU 数量 | 本材料范围内未报告（[KB-1] 仅数据集规模；[KB-4] 的 8×32GB GPU 属 reader/端到端系统） | [KB-1]、[KB-4] |
| 训练时长 | 未报告 | — |
| 索引体积（GB/向量数×维度） | 未报告 | — |
| 索引/内存效率定性描述 | 有：FAISS 内存索引"incredibly efficient"，995.0 QPS vs BM25 23.7 QPS（512GB RAM 服务器） | [KB-5] |

## 结论

就所提供的论文片段而言，DPR 论文对**训练算力（GPU 数量、训练时长）与索引体积（字节体积、内存占用）均未给出可直接引用的定性或定量描述**：[KB-1] 只交代数据集与过滤后训练问题规模，[KB-5] 只从**运行效率**角度定性称 DPR 借助 FAISS 内存索引"can be made incredibly efficient"，并给出 995.0 QPS（对比 BM25/Lucene 23.7 QPS/线程）的实测数字，运行环境为 512GB 内存服务器。片段中出现的 "four GPUs / 1:1 Trainer:Inference"（[KB-2]）、"single 32GB V100 / 650GB RAM"（[KB-2]）、"four Titan V / 469 GiBs RAM / up to all four GPUs during indexing"（[KB-3]）、"eight 32GB GPUs"（[KB-4]）分别属于 ANCE、ColBERT 与端到端 reader 的设置，**不应记作 DPR 检索器的资源需求**。若需补齐 DPR 训练算力与索引体积的原始表述，应回到 DPR 论文正文（如第 4 节实现细节与附录）另行取证；在本材料范围内，相关项应标注为"论文中未报告"。

## 参考文献

[KB-1] DPR 论文，4.2 Question Answering Datasets，第 4 页（Table 1）

[KB-2] ANCE 论文，5 Experimental Methodologies，第 5 页

[KB-3] ColBERT 论文，4.1.3 Hardware & Time Measurements，第 6 页

[KB-4] 端到端 QA 系统论文，6.1 End-to-end QA System，第 7 页

[KB-5] DPR 论文，5.4 Run-time Efficiency，第 7 页

[KB-6] ANCE 论文，A.1 More Experimental Details，第 13 页（Figure 5）

### 2. ColBERT 论文如何定性描述其索引体积与检索算力需求？（COMPLETED）

## 问题陈述

本步骤聚焦于 ColBERT 论文对其**索引体积（space footprint）**与**检索算力需求（computational cost / FLOPs / latency）**的定性描述，并提取可引用的原文证据。

## 研究结果

### 一、检索算力需求（相对 BERT 类模型的定性对比）

ColBERT 论文的核心定性主张是：相较此前的 BERT 类排序模型，ColBERT 在保持竞争力的效果下大幅降低检索计算开销。

- 摘要层面给出总体判断：ColBERT "executing two orders-of-magnitude faster and requiring four orders-of-magnitude fewer FLOPs per query" [KB-1]。
- 引言中给出更具体的重排序场景数字：作为重排序器使用时，"it delivers over 170× speedup (and requires 14,000× fewer FLOPs) relative to existing BERT-based models" [KB-2]；结论章节重复了同一组数字 "more than 170× faster and requires 14,000× fewer FLOPs/query" [KB-6]。
- 论文将这一效率归因于 late interaction 架构：查询与文档用 BERT 独立编码，再用 "a cheap yet powerful interaction step" 建模细粒度相似度，从而可以离线预计算文档表示，"considerably speeding up query processing" [KB-1]；结论中同样强调通过 "cheap and pruning-friendly computations" 加速查询处理 [KB-6]。
- 引言还给出服务时延的定性描述："ColBERT can serve queries in tens or few hundreds of milliseconds" [KB-2]。
- 论文对比了基线的高成本：BERT 类排序模型需把每个 query-document 对送入庞大神经网络计算单一相关性分数，使计算成本比此前方法高数个数量级 [KB-1]；背景材料中亦提到 BERT 即使在高性能 GPU 上也会带来数十毫秒至数万毫秒的延迟上升，但该表述属于章节背景，未绑定 KB 片段，故仅作理解之用，不作为已校验结论。

### 二、索引体积（space footprint）

- 引言给出定性量级："retaining its effectiveness with a space footprint of as little as few tens of GiBs" [KB-2]。
- 索引吞吐方面：ColBERT "can index the MS MARCO collection of 9M passages in about 3 hours using a single server with four GPUs" [KB-2]；§4.5 复述 "ColBERT can index MS MARCO in about three hours" [KB-5]。
- 论文强调索引是 ColBERT 唯一需要把文档送入 BERT 的环节："ColBERT's indexing—the only time it needs to feed documents through BERT—is also practical" [KB-2]；并指出 "any BERT-based model must incur the computational cost of processing each document at least once. While ColBERT encodes each document with BERT exactly once, existing BERT-based rankers would repeat similar computations on possibly hundreds of documents for each query" [KB-5]。

### 三、材料覆盖范围的说明

- 关于索引体积的**具体数值**（如 27 GiBs、24 维向量、2 字节浮点等）出现在 §4.5 的 Table 4 讨论中，但该段落属于章节背景 P-89f60865927d，未包含在所给 [KB-n] 原文片段内；因此在本步骤的已校验证据中，索引体积仅有 "few tens of GiBs" 这一**定性**描述 [KB-2]，更细的定量数值**论文中未在所提供的 [KB-n] 片段中报告**。
- 关于算力需求，[KB-n] 片段提供了 FLOPs 倍数、加速倍数与毫秒级时延的定性/相对描述，但**未报告**具体的绝对 FLOPs 数值或硬件配置细节（除索引阶段的"单服务器四 GPU"外）[KB-2]。

## 结论

就本步骤问题而言，ColBERT 论文对资源需求的定性描述可归纳为两点：(1) **算力**——通过 late interaction 实现查询端"廉价"交互与文档表示离线预计算，使检索比 BERT 类模型快约两个数量级（重排序场景 >170×）、每查询 FLOPs 少约四个数量级（14,000×），并可在数十至数百毫秒内响应查询 [KB-1][KB-2][KB-6]；(2) **索引体积**——在保持效果的前提下，空间占用可低至"数十 GiB 量级"（few tens of GiBs），且仅需单台四 GPU 服务器约 3 小时即可索引 MS MARCO 的 9M 段落 [KB-2][KB-5]。更精确的体积数值与绝对算力数值未在所给 [KB-n] 片段中出现，本步骤不作推断。

## 参考文献

[KB-1] ColBERT 论文 ABSTRACT，第 1 页（用户专题论文库片段）

[KB-2] ColBERT 论文 1 INTRODUCTION，第 2 页（用户专题论文库片段）

[KB-3] ColBERT 论文 4 EXPERIMENTAL EVALUATION，第 6 页（用户专题论文库片段）

[KB-4] ColBERT 论文 1 INTRODUCTION，第 3 页（用户专题论文库片段）

[KB-5] ColBERT 论文 4.5 Indexing Throughput & Footprint，第 9 页（用户专题论文库片段）

[KB-6] ColBERT 论文 5 CONCLUSIONS，第 9 页（用户专题论文库片段）

### 3. ANCE 论文如何定性描述其训练算力与索引刷新（索引体积/重建）需求？（COMPLETED）

## 问题陈述

本步骤聚焦 ANCE 论文如何**定性描述**其训练算力需求与索引刷新（索引体积/重建）需求。以下归纳仅基于用户专题论文库中的 [KB-1] 至 [KB-6] 原文片段。

## 研究结论

### 1. 训练算力的定性描述：瓶颈在于"全语料重编码"

ANCE 明确将训练计算的主要开销定位为"用新 checkpoint 更新训练语料的编码"，而非索引重建本身：

- 论文指出："In ANCE training, the bulk of computing is to update the encodings of the training corpus using new checkpoints."（ANCE 训练中的主要计算量在于用新 checkpoint 更新训练语料的编码）[KB-5]。
- 其对索引更新的拆解为两步：①**Inference**——用更新后的 DR 模型刷新整个语料库所有文档的表示；②**Index**——用更新后的表示重建 ANN 索引。论文强调："Although Index is efficient (Johnson et al., 2019), Inference is too expensive to compute per batch as it requires a forward pass on the entire corpus which is much bigger than the training batch."（索引重建本身高效，但推理需对整个语料做前向计算，语料远大于训练 batch，因而每 batch 计算过于昂贵）[KB-3]。
- 论文进一步说明这种开销"inevitable but can be mitigated by asynchronous index refresh"（不可避免，但可通过异步索引刷新缓解）[KB-5]。

### 2. 索引刷新的定性描述：异步刷新机制

- 实现方式："We implement ANCE using an asynchronously updated ANN index of the corpus representation."（使用异步更新的语料表示 ANN 索引）[KB-1]。
- 机制细节：维护一个 Inferencer 并行地用近期 checkpoint 计算文档编码，并在完成后刷新用于负采样的 ANN 索引，以"catch up"模型训练；Trainer 则继续使用旧索引 `ANN_{f_{k-1}}` 进行随机学习 [KB-1][KB-3]。
- 刷新频率与资源配比（定性+具体设置）："We use 1:1 Trainer:Inference GPU allocation, index refreshing per 10k training batches, batch size 8, and gradient accumulation step 2 on 4 GPUs."（1:1 的 Trainer 与 Inference GPU 配比，每 1 万训练 batch 刷新一次索引，batch size 8，梯度累积步 2，共 4 块 GPU）[KB-2]。
- 刷新频率的重要性：附录图 5 展示了不同 ANN 索引刷新（如 per 10k Batch）、Trainer:Inferencer GPU 配比与学习率下的训练损失与测试 NDCG 曲线 [KB-6]。
- 效率测量环境："We measured ANCE efficiency using a single 32GB V100 GPU, on a cloud VM with Intel(R) Xeon(R) Platinum 8168 CPU and 650GB of RAM memory."（在单块 32GB V100 GPU、Xeon 8168 CPU、650GB 内存的云 VM 上测量效率）[KB-2]。
- ANN 索引类型："The ANN search uses the Faiss IndexFlatIP Index (Johnson et al., 2019)."（使用 Faiss IndexFlatIP 索引）[KB-2]；MaxP 设置下 passage 级 max-pooling 在 ANN 中原生支持 [KB-2]。

### 3. 索引体积的定性描述

- 论文中**未报告**索引体积的具体数值或定性规模描述（如 GiB 级别、压缩比等）。KB 片段仅说明索引基于全语料文档表示构建、使用 Faiss IndexFlatIP，并需随模型更新重建，但未对索引体积作任何定量或定性刻画。
- 相较之下，同批材料中 ColBERT 提到其"space footprint of as little as few tens of GiBs"（低至数十 GiB 的空间占用）[KB-4]，可作为对照，但该表述属于 ColBERT 而非 ANCE。

## 材料不足说明

- ANCE 索引体积的定性/定量描述：论文中未报告（KB 片段内无相关表述）。
- ANCE 训练总算力（如 GPU 小时、总 FLOPs）的量化数值：论文中未报告（仅给出 4 GPU、1:1 配比、每 10k batch 刷新等配置性描述）。

## 参考文献

[KB-1] ANCE 论文 1 INTRODUCTION, p.2

[KB-2] ANCE 论文 5 EXPERIMENTAL METHODOLOGIES, p.5

[KB-3] ANCE 论文 4 APPROXIMATE NEAREST NEIGHBOR NOISE CONTRASTIVE ESTIMATION, p.5

[KB-4] ColBERT 论文 1 INTRODUCTION, p.2（对照材料）

[KB-5] ANCE 论文 6.1 EFFECTIVENESS AND EFFICIENCY, p.6

[KB-6] ANCE 论文 A.1 MORE EXPERIMENTAL DETAILS, p.13

### 4. RocketQA 论文如何定性描述其训练算力与索引/负采样相关资源需求？（COMPLETED）

## 问题陈述

本步骤要回答：RocketQA 论文如何**定性描述**其训练算力与索引/负采样相关的资源需求？

## 研究取证

### 1. 与训练算力相关的定性表述

在用户专题论文库提供的 RocketQA 原文片段中，**没有**任何关于 GPU 数量、训练时长、显存占用等训练算力资源需求的定性或定量描述。可检索到的相关原文仅有：

- 论文将三大贡献概括为 cross-batch negatives、denoised hard negatives 与 data augmentation [KB-2][KB-3][KB-4][KB-5]。
- 关于 cross-encoder 生成伪标签用于数据增强，原文仅定性指出"Though inefficient, the cross-encoder architecture has been found to be more capable than the dual-encoder architecture in both theory and practice (Luan et al., 2020)"，即承认 cross-encoder 架构"低效（inefficient）"，但这是相对能力/效率的定性比较，并未给出任何算力规模、训练时长或硬件资源的具体描述 [KB-2]。

因此，关于"训练算力"的定性资源描述，**论文中未报告**（至少在所提供的 [KB-1]–[KB-6] 材料范围内未出现）。

### 2. 与索引/负采样相关的定性表述

- RocketQA 的第一项策略是 cross-batch negatives：相对 in-batch negatives，"it increases the number of available negatives for each question during training, and alleviates the discrepancy between training and inference" [KB-2]。这里只是定性说明扩大了每个问题的可用负样本数量，**未**描述索引体积或负采样所需的存储/计算成本。
- 第二项策略是 denoised hard negatives："It aims to remove false negatives from the top-ranked results retrieved by a retriever, and derive more reliable hard negatives" [KB-2]。这涉及从检索器 top-ranked 结果中采样硬负例，但同样**未**给出索引规模或负采样资源开销的定性描述。
- 在对比实验中，原文提到 "Both DRP and ME-BERT use in-batch random sampling and hard negative sampling from the results retrieved by BM25, while ANCE enhances the hard negative sampling by using the dense retriever" [KB-6]。这只是方法层面的负采样来源对比，**不构成**对 RocketQA 自身索引/负采样资源需求的定性描述。

因此，关于"索引体积/负采样资源需求"的定性描述，**论文中未报告**（在 [KB-1]–[KB-6] 范围内无对应原文）。

### 3. 材料边界说明

需要特别指出：[KB-1] 实际上是 **ANCE** 论文的片段（描述其使用异步更新的 ANN 索引与 Inferencer 并行计算文档编码、定期刷新用于负采样的 ANN 索引），并非 RocketQA 的资源描述 [KB-1]。它不能用于回答 RocketQA 的资源需求问题，仅可作为 ANCE 一侧的对照材料。

## 结论

基于所提供的 [KB-1]–[KB-6] 原文片段，**RocketQA 论文未对其训练算力（硬件规模、训练时长、显存等）以及索引体积/负采样相关的资源需求作出任何定性描述**。可用的定性表述仅限于：cross-encoder 架构"inefficient"但能力更强 [KB-2]，以及 cross-batch negatives 扩大了每个问题的可用负样本数量、缓解训练-推理差异 [KB-2]。若需补齐 RocketQA 的资源需求定性描述，应查阅论文正文中关于实验设置/实现细节（如 4.x 节）的段落，而这些内容不在当前材料范围内。

## 参考文献

[KB-1] ANCE 论文片段（1 INTRODUCTION，第2页）：异步更新 ANN 索引与 Inferencer 并行计算文档编码、刷新负采样索引。

[KB-2] RocketQA 论文片段（1 Introduction，第2页）：三大贡献与 cross-encoder "inefficient" 表述。

[KB-3] RocketQA 论文片段（Abstract，第1页）：三大贡献概述。

[KB-4] RocketQA 论文片段（5 Conclusions，第9页）：贡献总结。

[KB-5] RocketQA 论文片段（1 Introduction，第2页）：三项训练策略概述。

[KB-6] RocketQA 论文片段（4.2.1 Dense Passage Retrieval，第7页）：各基线负采样方式对比。

## 问题陈述

本步骤聚焦于：RocketQA 论文如何**定性**描述其训练算力资源需求，以及与索引/负采样相关的资源需求。用户论文库中可用的原文片段有限（[KB-1] 至 [KB-5]），以下归纳严格基于这些片段，材料未覆盖的部分明确标注"论文中未报告"。

## 研究发现

### 1. 训练算力资源（硬件）

论文库提供的片段中，[KB-5]（4.1.3 Implementation Details）包含与硬件相关的原文，但**该片段仅摘录了训练轮数（Training epochs）与优化器（Optimizers）两段**，并未摘录硬件配置段落。因此，就用户提供的 [KB-n] 原文而言：

- 关于 GPU 型号、GPU 数量、显存等硬件配置：**论文中未报告**（在所提供的 [KB-n] 片段范围内）。
- 关于训练时长（wall-clock time）：**论文中未报告**（在所提供的 [KB-n] 片段范围内）。

需要说明的是，章节背景 P-31f29f303789 中确实出现了"up to eight NVIDIA Tesla V100 GPUs (with 32G RAM)"等表述，但按任务要求，背景不得当作已校验结论引用，故此处不将其作为 [KB-n] 证据。

### 2. 与"负采样/批大小"相关的训练资源权衡

[KB-3] 提供了 RocketQA 关于跨批负采样（cross-batch negatives）与算力之间权衡的定性描述：

> "Note that cross-batch negatives can be applied in both settings of single-GPU and multi-GPUs. When there is only a single GPU available, it can be implemented in an accumulation way while trading off training time." [KB-3]

即：跨批负采样在单 GPU 与多 GPU 场景均可使用；当**仅有单 GPU** 时，需以"累积（accumulation）"方式实现，并**以牺牲训练时间为代价**（trading off training time）。这是论文对负采样策略所带来算力/时间成本的一处明确**定性**描述。

### 3. 训练流程对资源的间接要求

[KB-1] 与 [KB-2] 仅说明论文将介绍 RocketQA 的整体训练流程：

> "Next, we will describe the details of the whole training procedure of RocketQA." [KB-1]

> "Lastly, we present the whole training procedure of RocketQA." [KB-2]

这两处为流程性表述，未直接给出算力或索引体积的定量/定性资源数据。

### 4. 训练轮数与优化器（资源相关的训练配置）

[KB-5] 摘录了训练配置中的轮数与优化器：

> "The dual-encoders are trained on MSMARCO for 40, 10 and 10 epochs in three steps of RocketQA, respectively. The dualencoders are trained on NQ for 30 epochs in all steps of RocketQA. The cross-encoders are trained for 2 epochs on both MSMARCO and NQ." [KB-5]

> "We use ADAM optimizer." [KB-5]

这些属于训练配置，可反映训练规模（多步、多轮），但**并非**对算力或索引体积的直接定性描述。

### 5. 索引体积

在所提供的 [KB-1] 至 [KB-5] 片段中，**没有**关于索引体积（index size）的任何定性或定量描述。因此：索引体积的资源需求——**论文中未报告**（在所提供的片段范围内）。

### 6. 实验有效性（非资源信息）

[KB-4] 仅涉及三种训练策略在 NQ 上的有效性实验：

> "Table 7: The experiments to examine the effectiveness of the three proposed training strategies in RocketQA on NQ." [KB-4]

与资源需求无直接关系。

## 结论

基于用户论文库提供的 [KB-1] 至 [KB-5] 原文，RocketQA 对资源需求的**定性**描述可归纳为：

1. **负采样与算力的权衡**：跨批负采样支持单/多 GPU；单 GPU 时需以累积方式实现，并"以训练时间为代价"（[KB-3]）。这是片段中对算力成本最直接的一处定性表述。
2. **训练规模**：双编码器在 MSMARCO 上分三步分别训练 40/10/10 轮，在 NQ 上各步训练 30 轮；交叉编码器在两者上均训练 2 轮；使用 ADAM 优化器（[KB-5]）。这体现训练的计算量级，但非直接的算力定性描述。
3. **训练流程**：[KB-1]、[KB-2] 仅表明论文会详述整体训练流程，未给出资源数据。

**材料不足、明确"论文中未报告"的部分**（在所提供的 [KB-n] 片段范围内）：
- 具体 GPU 型号/数量/显存等硬件算力配置；
- 训练时长（wall-clock）；
- 索引体积（index size）的任何定性或定量描述。

（注：章节背景 P-31f29f303789 中出现的"up to eight NVIDIA Tesla V100 GPUs (with 32G RAM)"等信息，因未纳入 [KB-n] 校验原文，本步骤不作为已校验结论引用；如需纳入，建议补充该段的 [KB-n] 摘录后再行取证。）

## 参考文献

[KB-1] 章节 3.4 The Training Procedure，页码 5（用户专题论文库原文片段）

[KB-2] 章节 3 Approach，页码 3（用户专题论文库原文片段）

[KB-3] 章节 3.3 Optimized Training Approach，页码 4（用户专题论文库原文片段）

[KB-4] 章节 A.2 The Effectiveness of The Three Training Strategies on NQ，页码 13（用户专题论文库原文片段）

[KB-5] 章节 4.1.3 Implementation Details，页码 7（用户专题论文库原文片段）

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