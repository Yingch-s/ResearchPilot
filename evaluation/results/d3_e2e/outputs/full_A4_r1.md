# A4 · full · round 1

DPR、ColBERT、ANCE、RocketQA 各自的索引结构与检索效率特征是什么？

---

# 研究报告

## 直接回答

本报告汇总了 4 个已完成研究步骤的发现；逐项依据见下方证据与局限。

**研究问题**：DPR、ColBERT、ANCE、RocketQA 各自的索引结构与检索效率特征是什么？

## 资料范围

- 专题内全部就绪资料（未限定单篇）

## 逐项发现

### 1. DPR 的索引结构与检索效率特征是什么？（COMPLETED）

## 问题陈述

本步骤聚焦于 **DPR 的索引结构与检索效率特征**。需要说明的是，用户提供的原文片段主要来自 ANCE 论文（以 DPR 为基线/对比对象）和 RocketQA 论文，其中对 DPR 的**直接描述**非常有限。以下归纳严格基于 [KB-n] 原文，并明确标注哪些内容"论文中未报告"。

## 研究发现

### 1. DPR 在原文中的定位：作为对比基线

在所提供的材料中，DPR 主要作为 ANCE 的对比对象出现。原文指出 ANCE 在 OpenQA 段落检索任务上优于 DPR："It also outperforms DPR in passage retrieval in OpenQA (Table 2)" [KB-4]。此外，ANCE 通过替换 DPR 的检索器、保留相同阅读器（NQ 上为 RAG-Token，TQA 上为 DPR Reader）来验证检索改进能否传导到答案准确率 [KB-2][KB-5]。这说明原文将 DPR 视为"检索器 + 阅读器"级联系统的基线。

### 2. DPR 的检索效率特征（间接证据）

原文在讨论 ANCE 效率时给出了与 DPR 同属的稠密检索（DR）架构的效率特征，可作为理解 DPR 效率来源的间接依据：

- **Siamese 网络 + 文档编码可预计算带来的效率优势**：原文指出 "DR with standard batching provides a 100x speed up compared to BERT Rerank, a natural benefit from the Siamese network and pre-computable document encoding" [KB-3 背景/章节背景 P-15176114bf92]。由于 DPR 正是典型的 BERT-Siamese 稠密检索模型（原文多次将 DPR 与 BERT-Siamese 架构并置讨论，如 "It also outperforms DPR in passage retrieval" [KB-4]），这一"100x 效率"特征在架构层面同样适用于 DPR。
- 需要强调：上述 100x 数据是原文针对 ANCE/DR 报告的，**原文并未单独报告 DPR 的具体延迟数值**。

### 3. DPR 的索引结构

关于 DPR 的索引结构（如是否使用 ANN 索引、索引类型、刷新机制等），所提供的 [KB-n] 片段中**论文中未报告** DPR 的具体索引实现细节。原文仅在 ANCE 语境下描述了其自身的索引机制："We implement ANCE using an asynchronously updated ANN index of the corpus representation" [KB-1]，以及 "The ANN search uses the Faiss IndexFlatIP Index" [KB-5 背景]。这些描述均针对 ANCE，而非 DPR，因此不能直接归于 DPR。

唯一可间接关联的是：原文提到 ANCE 在 OpenQA 中"warm up ANCE using the released DPR checkpoints" [KB-5 背景]，说明 DPR 提供了可复用的预训练检索模型，但**未说明 DPR 的索引构建方式**。

### 4. DPR 作为阅读器/下游组件的角色

原文还显示 DPR 不仅作为检索器被对比，其阅读器也被复用："we first re-use the released model 6 of the extractive reader in DPR (Karpukhin et al., 2020), and take 100 retrieved passages during inference (the same setting used in DPR)" [KB-6]。这侧面反映 DPR 系统采用"检索 Top-100 段落 + 抽取式阅读器"的流水线设定，但属于阅读阶段而非索引结构本身。

## 结论

基于所提供的原文片段，可以确认的 DPR 相关特征仅限于：

1. **定位**：DPR 是 ANCE 在 OpenQA 段落检索中的对比基线，ANCE 在其之上取得更优检索效果 [KB-4]，并可复用 DPR 的预训练 checkpoint 与阅读器 [KB-5 背景][KB-6]。
2. **效率特征的间接推断**：DPR 所属的稠密检索（BERT-Siamese）架构因"Siamese 网络 + 文档编码可预计算"而相对 BERT 重排有约 100x 效率优势 [KB-3 背景]，但该数值是原文对 DR/ANCE 报告，非 DPR 专属。
3. **索引结构**：**论文中未报告** DPR 的索引结构细节（ANN 索引类型、刷新机制等）；原文中关于"asynchronously updated ANN index" [KB-1] 与 "Faiss IndexFlatIP" [KB-5 背景] 的描述均针对 ANCE，不可移用于 DPR。

因此，若要完整刻画 DPR 的索引结构与检索效率，现有材料不足，需补充 DPR 原论文（Karpukhin et al., 2020）作为证据来源。

## 参考文献

[KB-1] ANCE 论文 1 INTRODUCTION（用户专题论文库片段）

[KB-2] ANCE 论文 6.1 EFFECTIVENESS AND EFFICIENCY（Table 4 说明，用户专题论文库片段）

[KB-3] ANCE 论文 ABSTRACT（用户专题论文库片段）

[KB-4] ANCE 论文 6.1 EFFECTIVENESS AND EFFICIENCY（用户专题论文库片段）

[KB-5] ANCE 论文 5 EXPERIMENTAL METHODOLOGIES（用户专题论文库片段）

[KB-6] RocketQA 论文 4.2.3 Passage Reading with RocketQA（用户专题论文库片段）

# DPR 的索引结构与检索效率特征（取证归纳）

## 问题陈述

本步骤聚焦 DPR（Dense Passage Retriever）论文，考察其索引结构（是否使用 FAISS、索引类型、文档向量预计算与离线建库方式）以及 Top-k 检索流程与检索效率特征。

## 研究结论

### 1. 索引结构与建库方式

- **使用 FAISS 索引**：DPR 在推理阶段使用 FAISS 对全部段落的稠密表示建立索引 [KB-2]。另一处表述进一步说明，推理时对所有段落应用段落编码器 EP，并**离线**用 FAISS 建立索引，且 FAISS 可扩展到数十亿向量 [KB-6]。
- **索引类型为 IndexFlatIP**：DPR 明确使用 `IndexFlatIP` 进行索引，并采用**精确最大内积搜索**（exact maximum inner product search）进行查询 [KB-2]。这说明其索引为扁平（flat）精确检索结构，而非压缩/近似结构。
- **文档向量预计算**：论文强调问题编码与段落编码分离是"理想的"，因为这样所有段落的稠密表示可以被**预计算**以支持高效检索 [KB-2]（章节背景）。结合 [KB-6]，段落向量在离线阶段全部编码并建库。
- **关于倒排文件/IVFPQ**：需要特别指出，本步骤材料中提到的"倒排文件（inverted file）"与"IVFPQ"索引结构，出自 **ColBERT** 的相关原文 [KB-5]（"we use an IVFPQ index…partitions the embedding space into P cells…"），以及 [KB-4] 中关于 ColBERT 端到端检索的 faiss 索引设置（P=2,000、p=10、s=16 子向量等）。这些**并非 DPR 的索引结构**。就 DPR 而言，原文仅报告使用 `IndexFlatIP` 精确内积搜索 [KB-2]，**论文中未报告 DPR 使用倒排文件或乘积量化索引**。

### 2. Top-k 检索流程

- 运行时给定问题 q，先用问题编码器 EQ 得到其嵌入 vq，然后检索嵌入与 vq 最接近的 top-k 段落 [KB-6]。相似度以向量点积计算 [KB-2]（章节背景）。
- 在效率测试中，DPR 返回的是"每问题 top 100 段落" [KB-3]。

### 3. 检索效率特征

- **查询吞吐**：在 Intel Xeon CPU E5-2698 v4 @ 2.20GHz、512GB 内存的服务器上，借助 FAISS 内存索引，DPR 可达到**每秒处理 995.0 个问题**（每题返回 top 100 段落）[KB-3]。
- **对比 BM25/Lucene**：相比之下，BM25/Lucene（Java 实现、文件索引）每 CPU 线程每秒仅处理 **23.7 个问题** [KB-3]。
- **建库成本**：为稠密向量建索引耗时远高于稀疏索引。在 2100 万段落上计算稠密嵌入资源密集但易于并行，8 块 GPU 上约需 **8.8 小时**；在单台服务器上为 2100 万向量构建 FAISS 索引约需 **8.5 小时**；而用 Lucene 构建倒排索引总共只需约 **30 分钟** [KB-3]（章节背景）。
- **设计动机**：引入检索组件是为了减少 reader 需处理的候选段落数量，这对实时回答用户问题至关重要 [KB-3]；同时传统基于词的稀疏检索（如 BM25）虽有倒排索引高效，但在语义匹配上存在词不匹配（term mismatch）的局限 [KB-1]，这正是稠密检索的动机所在。

## 材料不足说明

- DPR 索引的**具体压缩/量化参数**、内存占用细节：**论文中未报告**（本步骤材料未涉及）。
- DPR 是否使用 GPU 加速检索、不同 top-k 取值下的延迟曲线：**论文中未报告**（材料仅给出 top 100 场景下的整体吞吐）。

## 参考文献

[KB-1] DPR 论文 Introduction（章节 1，第 1 页）

[KB-2] DPR 论文 3.2 The Dual-Encoder Architecture（第 4 页）

[KB-3] DPR 论文 5.4 Run-time Efficiency（第 7 页）

[KB-4] ColBERT 论文 4.1 Methodology（第 6 页）

[KB-5] ColBERT 论文 3.6 End-to-end Top-k Retrieval with ColBERT（第 6 页）

[KB-6] DPR 论文 3.1 Overview（第 3 页）

### 2. ColBERT 的索引结构与检索效率特征是什么？（COMPLETED）

# ColBERT 的索引结构与检索效率特征

## 问题陈述

本步骤聚焦于 ColBERT 的**索引结构**与**检索效率**特征。需要说明的是，所提供的原文片段主要覆盖 ColBERT 的架构思想、效率收益与索引成本，对索引结构的**底层实现细节**（如具体的向量索引类型、量化方式、压缩编码等）报告有限，相关部分将明确标注"论文中未报告"。

## 研究发现

### 一、索引结构特征

**1. 文档表示离线预计算（late interaction 架构）**

ColBERT 采用"延迟交互"（late interaction）架构：查询与文档**分别独立**经 BERT 编码为两组上下文嵌入，相关性通过两组嵌入之间"廉价且利于剪枝"的计算来评估 [KB-2]。这一设计的关键在于，文档表示可以**离线预计算**，从而显著加速查询处理 [KB-4]。换言之，索引中存储的是**预先计算好的文档上下文嵌入集合**，而非在线联合编码 query-document 对。

**2. 支持基于向量相似度索引的端到端检索**

由于交互机制"利于剪枝"（pruning-friendly），ColBERT 可以利用**向量相似度索引**（vector-similarity indexes）直接在大规模文档集合上进行端到端检索 [KB-4]。论文明确将其用于两种场景：一是作为基于词项检索模型之上的重排序（re-ranking），二是借助向量相似度索引在完整集合上检索 [KB-3]。这说明其索引结构是为**向量相似度检索**服务的，且可支撑全集合端到端检索 [KB-6]。

**3. 索引的空间占用（footprint）**

索引的空间开销被报告为"可低至数十 GiB（few tens of GiBs）"，同时保持有效性 [KB-1]。这是原文对索引存储规模最具体的量化描述。

> 说明：关于索引内部的具体数据结构（例如倒排/向量索引的具体类型、嵌入维度压缩、量化或残差编码方案等）在所提供的片段中**论文中未报告**。

### 二、检索效率特征

**1. 查询延迟：数十至数百毫秒**

ColBERT 可在"数十到数百毫秒"内完成查询服务 [KB-1]。

**2. 相对 BERT 类模型的速度与算力优势**

- 作为重排序使用时（"ColBERT (re-rank)"），相较现有 BERT 类模型实现 **170 倍以上加速**，且所需 FLOPs 减少 **14,000 倍**，同时效果优于所有非 BERT 基线 [KB-1]。
- 摘要层面给出的是"执行速度快两个数量级、每查询 FLOPs 少四个数量级"的表述 [KB-4]；结论部分再次强调"快 170 倍以上、每查询 FLOPs 少 14,000 倍" [KB-6]。

**3. 索引构建（离线）成本**

索引是 ColBERT 唯一需要将文档送入 BERT 的环节；用**单台四 GPU 服务器**索引 MS MARCO 的 **900 万段落**约需 **3 小时** [KB-1]。这体现了其"重离线、轻在线"的效率取舍。

**4. 效率来源的机制解释**

效率提升源于：独立编码 query 与 document 后，交互步骤"廉价而强大" [KB-4]；以及延迟交互使得文档表示可离线预计算、避免对每个 query-document 对都跑一遍大型网络 [KB-4][KB-6]。

## 结论

ColBERT 的索引结构核心是**离线预计算的文档上下文嵌入集合**，配合**向量相似度索引**支持端到端检索，空间占用可低至数十 GiB [KB-1][KB-3][KB-4]。其检索效率特征表现为：查询延迟数十至数百毫秒、重排序场景下 170 倍以上加速与 14,000 倍 FLOPs 节省 [KB-1][KB-6]，以及单台四 GPU 服务器约 3 小时完成 900 万段落索引的离线成本 [KB-1]。效率的根本来源是 late interaction 架构使文档表示可离线预计算、并采用廉价且利于剪枝的交互计算 [KB-2][KB-4]。

需说明：关于索引的**具体底层实现细节**（向量索引类型、压缩/量化方案、嵌入维度等），所提供的原文片段**论文中未报告**，故不作推断。

## 参考文献

[KB-1] ColBERT 论文，1 INTRODUCTION，第 2 页（用户专题论文库原文片段）

[KB-2] ColBERT 论文，1 INTRODUCTION，第 2 页（用户专题论文库原文片段）

[KB-3] ColBERT 论文，1 INTRODUCTION，第 3 页（用户专题论文库原文片段）

[KB-4] ColBERT 论文，ABSTRACT，第 1 页（用户专题论文库原文片段）

[KB-5] ColBERT 论文，4 EXPERIMENTAL EVALUATION，第 6 页（用户专题论文库原文片段）

[KB-6] ColBERT 论文，5 CONCLUSIONS，第 9 页（用户专题论文库原文片段）

### 3. ANCE 的索引结构与检索效率特征是什么？（COMPLETED）

# ANCE 的索引结构与检索效率特征

## 问题陈述

本步骤聚焦 ANCE（Approximate nearest neighbor Negative Contrastive Learning）的**索引结构**与**检索效率**特征：它如何构建/维护索引，索引在训练与检索中扮演什么角色，以及其效率表现与瓶颈来自何处。

## 研究结果

### 1. 索引结构：异步更新的 ANN 索引

ANCE 的核心索引是一个对**全语料文档表示**构建的 ANN 索引，并采用**异步刷新**方式维护：

- "We implement ANCE using an asynchronously updated ANN index of the corpus representation." [KB-1]
- 具体机制上，ANCE 维护一个 Inferencer，用正在优化的 DR 模型的近期 checkpoint 并行计算文档编码，编码完成后刷新用于负采样的 ANN 索引，以跟上模型训练进度："we maintain an Inferencer that parallelly computes the document encodings with a recent checkpoint from the being optimized DR model, and refresh the ANN index used for negative sampling once it finishes, to keep up with the model training." [KB-1]
- 摘要层面同样强调该索引用于**从全语料全局选取困难负样本**："ANCE, a learning mechanism that selects hard training negatives globally from the entire corpus, using an asynchronously updated ANN index." [KB-2]

因此，索引结构并非静态的检索索引，而是**与训练过程耦合、周期性重建的全局负采样索引**。

### 2. 索引与检索的具体配置（来自实验设置）

关于索引/检索的具体实现参数，材料给出了以下细节（位于实验方法部分）：

- 为适配长文档，ANCE 采用 FirstP（取文档前 512 token）与 MaxP（文档切分为最多 4 个 512-token 段落，段落级分数做 max-pooling）两种设置，且 **max-pooling 操作在 ANN 中原生支持**。
- ANN 检索使用 **Faiss IndexFlatIP**（内积/Flat 索引）。
- 训练时采用 1:1 的 Trainer:Inference GPU 分配，**每 10k 个训练 batch 刷新一次索引**，batch size 8，4 块 GPU 上梯度累积步长 2；每个正样本从 **ANN top 200** 中均匀采样一个负样本。
- 效率测量使用单块 32GB V100 GPU，云 VM 配 Intel Xeon Platinum 8168 CPU 与 650GB 内存。

（以上要点出自章节背景 P-9558cde80da4，即论文第 5 节实验方法；该背景仅用于理解条件与限定，未作为已校验结论引用。）

### 3. 检索效率特征

- **总体效率定位**：ANCE 的点积检索在精度上几乎匹敌基于 BERT 的级联 IR 流水线，同时**效率高 100 倍**："showing ANCE dot-product retrieval nearly matches the accuracy of BERT-based cascade IR pipeline, while being 100x more efficient." [KB-2]
- **效率来源**：Table 5 测量了 ANCE（FirstP）在 TREC DL 文档检索上的效率，在线延迟针对单条 query 检索 100 篇文档。DR 采用标准 batching 相比 BERT Rerank 带来 **100x 加速**，这源于 **Siamese 网络结构与可预计算的文档编码**："DR with standard batching provides a 100x speed up compared to BERT Rerank, a natural benefit from the Siamese network and pre-computable document encoding." [KB-6]
- **训练侧的效率瓶颈**：ANCE 训练中主要计算开销在于**用新 checkpoint 更新训练语料的编码**；在负采样模型与待学习模型相同的假设下，这一开销不可避免，但可通过**异步索引刷新**缓解："the bulk of computing is to update the encodings of the training corpus using new checkpoints… this is inevitable but can be mitigated by asynchronous index refresh." [KB-6]

### 4. 效率与效果的关联背景

ANCE 的检索效果优势在多场景得到验证（TREC DL 文档检索、OpenQA 段落检索、商业搜索引擎生产环境约 15% 相对提升），且更好的检索能传导为更好的答案准确率 [KB-5]。这为"索引结构带来的效率—效果权衡"提供了效果侧佐证，但材料未给出具体的延迟/吞吐数值。

## 结论

ANCE 的索引结构是**对全语料文档表示构建、随训练异步刷新的 ANN 索引**：由 Inferencer 用近期 checkpoint 并行编码文档，编码完成后刷新索引以支撑全局困难负采样 [KB-1][KB-2]。检索侧依赖 Siamese 结构与可预计算文档编码，实现相比 BERT Rerank 约 **100x** 的加速，且点积检索精度接近 BERT 级联流水线 [KB-2][KB-6]。其主要效率代价在于训练中反复更新语料编码，通过**异步索引刷新**缓解 [KB-6]。具体索引构建参数（Faiss IndexFlatIP、每 10k batch 刷新、ANN top 200 负采样、FirstP/MaxP 设置）见论文第 5 节实验方法。

**材料不足之处**：论文片段**未报告** Table 5 的具体延迟/吞吐数值、索引规模与内存占用、以及不同语料规模/编码维度下的效率对比数据，故这些量化细节无法从现有材料归纳。

## 参考文献

[KB-1] ANCE 论文，1 INTRODUCTION，第 2 页（异步更新 ANN 索引与 Inferencer 机制）

[KB-2] ANCE 论文，ABSTRACT，第 1 页（全局困难负采样与 100x 效率）

[KB-3] ANCE 论文，6.1 EFFECTIVENESS AND EFFICIENCY，第 7 页（Table 4 OpenQA 结果、Table 5 效率表标题）

[KB-4] ANCE 论文，5 EXPERIMENTAL METHODOLOGIES，第 5 页（OpenQA 评测设置）

[KB-5] ANCE 论文，6.1 EFFECTIVENESS AND EFFICIENCY，第 6 页（多场景效果与生产环境提升）

[KB-6] ANCE 论文，6.1 EFFECTIVENESS AND EFFICIENCY，第 6 页（Table 5 效率分析与异步索引刷新）

章节背景（仅用于理解条件与限定，未作为已校验结论引用）：P-ed0e1d2d326b（1 INTRODUCTION）、P-3297b0e9cc65（ABSTRACT）、P-15176114bf92（6.1 EFFECTIVENESS AND EFFICIENCY）、P-9558cde80da4（5 EXPERIMENTAL METHODOLOGIES）

### 4. RocketQA 的索引结构与检索效率特征是什么？（COMPLETED）

# RocketQA 的索引结构与检索效率特征

## 问题陈述

本步骤聚焦 RocketQA 的**索引结构**与**检索效率**特征。需说明的是，RocketQA 本身是一套**双编码器（dual-encoder）密集段落检索的优化训练方法**，其核心贡献在训练策略层面，而非索引结构或检索效率的专门设计。以下归纳严格依据论文库原文。

## 研究结论

### 1. 索引结构：论文中未报告

在所提供的原文片段中，**没有任何一处描述 RocketQA 的索引结构**（如是否使用 ANN 索引、倒排索引、向量索引类型、索引更新机制等）。RocketQA 被定位为"一种优化的训练方法"（an optimized training approach），其技术贡献明确限定为三项训练策略：

- 跨批次负例（cross-batch negatives）
- 去噪难负例（denoised hard negatives）
- 数据增强（data augmentation）[KB-1][KB-4][KB-5]

因此，就现有材料而言，**RocketQA 的索引结构在论文中未报告**。

（注：材料中唯一涉及 ANN 索引异步更新的描述出现在 ANCE 的段落 [KB-3]，属于 ANCE 而非 RocketQA，不能作为 RocketQA 的证据。）

### 2. 检索效率特征：论文中未报告直接指标

所提供的原文片段**未报告 RocketQA 的检索效率指标**（如延迟、吞吐、QPS、索引规模、检索耗时等）。材料中与"效率/性能"相关的内容可归纳为以下几点，但均非索引层面的效率数据：

- **检索质量优于前人**：实验表明 RocketQA 在 MSMARCO 与 Natural Questions 上显著优于此前的最先进模型 [KB-1]。
- **端到端 QA 性能提升**：将提取式阅读器叠加于 RocketQA 检索器之上，其检索结果能够提升最终 QA 性能 [KB-1][KB-6]。
- **推理时检索段落数**：为公平比较，推理时沿用 DPR 设置取 100 个检索段落；训练新阅读器时改用 top 50 段落 [KB-6]。这属于实验设置，而非效率测量。

### 3. 与效率相关的间接线索（谨慎说明）

论文在问题背景中提到双编码器在推理时需从"包含数百万候选的大集合"中为每个问题识别正例段落，并指出训练与推理之间存在差异 [KB-4]。这一表述揭示了密集检索在**大规模候选集上推理**的固有场景，但原文**并未**据此报告 RocketQA 的索引实现或效率优化手段。因此不能推断 RocketQA 采用了某种特定索引或效率机制。

## 结论

基于所提供的 [KB-1]–[KB-6] 原文，**RocketQA 的索引结构与检索效率特征在论文中均未报告**。RocketQA 的定位是密集段落检索的**训练方法优化**（跨批次负例、去噪难负例、数据增强），其已验证的收益体现在**检索质量与端到端 QA 性能**上，而非索引结构或检索效率指标。若需回答索引与效率问题，需补充论文中方法/实验章节关于索引构建与效率测量的原文，当前材料不足以支撑。

## 参考文献

[KB-1] Abstract, p.1 — RocketQA 定位与三项贡献、MSMARCO/NQ 结果、端到端 QA 提升

[KB-2] 1 Introduction, p.2 — 三项策略有效性

[KB-3] 1 INTRODUCTION, p.2 — ANCE 的异步 ANN 索引（非 RocketQA）

[KB-4] 1 Introduction, p.2 — 三项贡献详述、训练/推理差异与大规模候选

[KB-5] 1 Introduction, p.2 — 三项训练策略总结

[KB-6] 4.2.3 Passage Reading with RocketQA, p.9 — 端到端 QA、推理取 100 段落、训练取 top 50

# RocketQA 索引结构与检索效率特征 — 取证归纳

## 结论先行

**用户提供的原文材料 [KB-1] 中并未包含任何关于 RocketQA 的内容。** [KB-1] 讨论的是 Anserini BM25、doc2query、DeepCT、docTTTTTquery 以及 ColBERT（ColBERTL2 + faiss）的端到端 Top-k 检索延迟测量方式，与 RocketQA 的索引结构或检索效率无关。

因此，就本步骤要求回答的“RocketQA 的索引结构与检索效率特征（ANN 向量索引、延迟、吞吐）”而言：

- **索引结构**：论文中未报告（所给材料未涉及 RocketQA）。
- **检索效率（延迟/吞吐）**：论文中未报告（所给材料未涉及 RocketQA）。

## 材料实际覆盖内容（供对照，均非 RocketQA）

为避免混淆，下面列出 [KB-1] 实际报告的、与“索引/延迟”相关但**不属于 RocketQA** 的信息：

- 关于 BM25 类模型的延迟测量方式：Anserini 实现“only utilizes parallelism across different queries”，故报告单线程延迟，并指出按索引分片并行可进一步降低延迟 [KB-1]。
- 关于 DeepCT：因仅重加权 BM25 词频而不改动索引，其延迟由 BM25 延迟估计得出 [KB-1]。
- 关于 ColBERT 端到端检索：采用负平方 L2 距离作为向量相似度函数，延迟测量包含“faiss-based candidate filtering and the subsequent re-ranking”，且 faiss 使用全部可用 CPU 核 [KB-1]。

以上均为 ColBERT/BM25 系模型的描述，**不能**作为 RocketQA 的结论引用。

## 说明

由于现有 [KB-n] 材料不包含 RocketQA 的任何原文，本步骤无法基于论文库完成对 RocketQA 索引结构与检索效率的取证归纳。若需回答该问题，需补充包含 RocketQA 相关章节（如索引构建、ANN/向量检索、延迟或吞吐实验）的原文片段。在此之前，对 RocketQA 的相关特征应标注为“论文中未报告”，不作推断。

## 参考文献

- [KB-1] 用户专题论文库原文片段，章节 4.3 End-to-end Topk Retrieval，页码 8（无外部 URL）

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
- 2010.08191（ACCEPTED）
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
- 2010.08191
- 2010.08191
- 2010.08191
- 2010.08191