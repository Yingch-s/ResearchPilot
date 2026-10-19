# E2 · full · round 1

ANCE 如何刷新全局难负例所用的 ANN 索引？

---

# 研究报告

## 直接回答

本报告汇总了 4 个已完成研究步骤的发现；逐项依据见下方证据与局限。

**研究问题**：ANCE 如何刷新全局难负例所用的 ANN 索引？

## 资料范围

- 专题内全部就绪资料（未限定单篇）

## 逐项发现

### 1. ANCE 训练中全局难负例的 ANN 索引是如何构建与初始化的？（COMPLETED）

# ANCE 全局难负例 ANN 索引的构建与初始化

## 问题陈述

本步骤聚焦于：ANCE 训练中，用于全局难负例采样的 ANN 索引是如何**构建**与**初始化**的？即索引所依赖的语料表示如何产生、索引由谁维护、以何种节奏刷新，以及初始状态如何。

## 研究取证归纳

### 1. 索引的定位：异步更新的语料表示 ANN 索引

ANCE 的核心机制是"从整个语料中全局选取难负例"，其实现载体是一个**异步更新的 ANN 索引**。论文明确说明："We implement ANCE using an asynchronously updated ANN index of the corpus representation."[KB-1]；摘要亦将其概括为"selects hard training negatives globally from the entire corpus, using an asynchronously updated ANN index."[KB-2]；第 4 节再次强调 ANCE "selects negatives from the entire corpus using an asynchronously updated ANN index."[KB-6]

### 2. 索引的构建：Inferencer 负责语料编码 + 索引重建

索引的构建由独立于 Trainer 的 **Inferencer** 完成。论文指出："Similar to Guu et al. (2020), we maintain an Inferencer that parallelly computes the document encodings with a recent checkpoint from the being optimized DR model, and refresh the ANN index used for negative sampling once it finishes, to keep up with the model training."[KB-1]

索引更新被拆分为两个操作："1) Inference: refresh the representations of all documents in the corpus with an updated DR model; 2) Index: rebuild the ANN index using updated representations."[KB-4] 其中 Index（重建）本身高效（引 Johnson et al., 2019），但 Inference 需对整个语料做前向计算，"too expensive to compute per batch as it requires a forward pass on the entire corpus which is much bigger than the training batch."[KB-4]

### 3. 初始化的时序：以检查点为锚、每 m 批刷新一次

索引并非每批更新，而是**按检查点节奏异步刷新**。论文描述："Thus we implement an asynchronous index refresh similar to Guu et al. (2020), and update the ANN index once every m batches, i.e., with checkpoint fk."[KB-4]（此句出自章节背景 P-454776103b5e，与 [KB-4] 同节，属同段落的展开表述）

具体流程为：Trainer 与 Inferencer 并行运行；Inferencer 取最新检查点（如 fk）重算整个语料的编码；在此期间 Trainer 继续用来自上一版索引 ANNfk-1 的负例 Dfk-1 做随机学习；一旦语料重编码完成，Inferencer 更新 ANN 索引（ANNfk）并交给 Trainer 使用。图 2 的说明与此一致："The Inferencer uses a recent checkpoint to update the representation of documents in the corpus and once finished, refreshes the ANN index with most up-to-date encodings."[KB-5]

由此，ANCE 负例是"asynchronously updated to 'catch up' with the stochastic training"，索引与模型优化之间的差距取决于 Trainer 与 Inferencer 的算力分配，附录 A.3 表明 1:1 的 GPU 划分足以将该差距的影响降到最小（此句出自章节背景 P-454776103b5e）。

### 4. 索引的用途：从索引中取 top 检索文档作为全局负例

索引构建完成后，ANCE 负例取自 DR 模型经该索引检索到的 top 文档：DANCE = ANNf(q,d) \ D+，即"the top retrieved documents by f() from the ANN index"，并称"By definition, DANCE are the hardest negatives for the current DR model"[KB-6]（此定义出自章节背景 P-454776103b5e，与 [KB-6] 同节）。这与引言中"ANCE constructs global negatives using the being-optimized DR model to retrieve from the entire corpus."[KB-3] 的定位一致。

## 结论

ANCE 的全局难负例 ANN 索引是一个**基于语料表示、由独立 Inferencer 异步维护**的近似最近邻索引：Inferencer 用正在优化的 DR 模型的最新检查点并行重算全语料文档编码，再据此重建 ANN 索引，并每隔 m 个批次（以检查点 fk 为界）刷新一次并交给 Trainer 使用；Trainer 在刷新间隙继续使用上一版索引 ANNfk-1 采样负例，从而让负例分布异步"追赶"模型训练。[KB-1][KB-2][KB-4][KB-5][KB-6]

**关于"初始化"的说明**：上述材料说明了索引的构建主体（Inferencer）、构建方式（全语料重编码 + 重建索引）与刷新节奏（每 m 批/按检查点），但**未报告**索引在训练开始时的具体初始化细节（例如初始检查点如何选取、初始索引是否由随机/预训练模型首次全量构建、m 的具体取值、所用 ANN 库的具体配置等）。这些内容论文中未报告，需查阅原文第 4 节及附录 A.3 以获得完整信息。

## 参考文献

[KB-1] 用户专题论文库原文片段，章节 1 INTRODUCTION，页码 2

[KB-2] 用户专题论文库原文片段，章节 ABSTRACT，页码 1

[KB-3] 用户专题论文库原文片段，章节 1 INTRODUCTION，页码 2

[KB-4] 用户专题论文库原文片段，章节 4 APPROXIMATE NEAREST NEIGHBOR NOISE CONTRASTIVE ESTIMATION，页码 5

[KB-5] 用户专题论文库原文片段，章节 3 ANALYSES ON THE CONVERGENCE OF DENSE RETRIEVAL TRAINING（图 2 说明），页码 4

[KB-6] 用户专题论文库原文片段，章节 4 APPROXIMATE NEAREST NEIGHBOR NOISE CONTRASTIVE ESTIMATION，页码 4

（附：章节背景 P-454776103b5e 与 P-5a165840e0c3 为同节展开段落，用于补全时序描述，引用已绑定对应 [KB-4]/[KB-5]/[KB-6] 所在章节。）

# 步骤取证归纳：ANCE 训练中全局难负例 ANN 索引的构建与初始化

## 问题陈述

本步骤聚焦于 ANCE 训练开始时全局难负例所用 ANN 索引的**构建与初始化**方式，具体包括：索引初始化方式、初始检查点的选取、刷新间隔 m 的取值，以及 ANN 库配置。

## 研究发现

### 1. ANN 索引库与距离度量配置

ANCE 的 ANN 检索使用 **Faiss 的 IndexFlatIP 索引**（内积/点积索引），这与 ANCE 采用的点积相似度 BERT-Siamese 结构相配套。此外，模型采用 MaxP 设置时，段落级分数在 ANN 中**原生支持 max-pooling**，因此该操作可直接由 ANN 完成 [KB-1]。

- 检索库：Faiss `IndexFlatIP`（Johnson et al., 2019）[KB-1]
- 相似度：内积（与点积相似度一致）[KB-1]
- 长文档处理：FirstP（前 512 token）或 MaxP（切分为最多 4 个 512-token 段落后 max-pool）[KB-1]

### 2. 索引的构建与刷新机制（异步刷新）

ANCE 采用**异步索引刷新**机制（类似 Guu et al., 2020）。索引更新需要两步操作：1）Inference：用更新后的 DR 模型重新计算整个语料库的表示；2）Index：用更新后的表示重建 ANN 索引。由于对整个语料库做前向推理过于昂贵，无法每批次执行，因此 ANCE 每 m 个批次更新一次 ANN 索引，即使用检查点 f_k [KB-2]。

具体流程为：除 Trainer 外，另运行一个 Inferencer，它取最新检查点（如 f_k）重新编码整个语料库；与此同时，Trainer 继续用来自 ANN_{f_{k-1}} 的 D_{f_{k-1}} 进行随机学习。一旦语料库重新编码完成，Inferencer 更新 ANN 索引（ANN_{f_k}）并交给 Trainer 使用 [KB-2]。

### 3. 刷新间隔 m 的取值

论文中刷新间隔 m 在实验设置中具体化为**每 10k 个训练批次刷新一次索引**（index refreshing per 10k training batches）[KB-1]。附录 A.1 的图 5 也以 "per 10k Batch" 为例展示了不同刷新频率下的训练损失与测试 NDCG [KB-3]。

### 4. 训练资源配置（与刷新相关）

- Trainer:Inference GPU 分配为 **1:1** [KB-1]
- 每 10k 训练批次刷新索引 [KB-1]
- batch size 8，梯度累积步 2，使用 4 块 GPU [KB-1]
- 每个正例从 ANN top 200 中均匀采样一个负例 [KB-1]
- 附录指出 1:1 的 GPU 划分足以最小化索引与模型之间差距的影响 [KB-2]

### 5. 初始检查点与初始化方式

关于**训练开始时 ANN 索引的初始检查点如何选取**（即第一次构建索引时使用哪个模型/表示来初始化 ANN 索引），上述材料中**论文中未报告**。材料仅说明索引按检查点 f_k 周期性刷新，并提到 ANCE 在 TREC DL 中用 BM25 负例做 warm-up、在 OpenQA 中用已发布的 DPR 检查点做 warm-up [KB-1 所在章节背景]，但**未明确说明初始 ANN 索引由哪个检查点（如随机初始化模型、BM25 warm-up 后的模型或 DPR 检查点）构建**。

## 结论

ANCE 的全局难负例 ANN 索引基于 **Faiss IndexFlatIP** 构建，采用**异步刷新**：由独立的 Inferencer 在每个检查点 f_k 处重编码整个语料库并重建索引，Trainer 则并行地使用上一版本索引 ANN_{f_{k-1}} 的负例继续训练；刷新间隔 m 在实验中取**每 10k 个训练批次**，资源配置为 1:1 的 Trainer:Inference GPU 分配。至于训练**初始时刻** ANN 索引具体由哪个检查点初始化，论文原文未报告，需谨慎标注为未报告项。

## 参考文献

[KB-1] 用户专题论文库，章节 5 EXPERIMENTAL METHODOLOGIES，页码 5

[KB-2] 用户专题论文库，章节 4 APPROXIMATE NEAREST NEIGHBOR NOISE CONTRASTIVE ESTIMATION，页码 5

[KB-3] 用户专题论文库，章节 A.1 MORE EXPERIMENTAL DETAILS，页码 13

### 2. ANCE 在训练过程中以什么频率、在什么触发条件下刷新 ANN 索引？（COMPLETED）

# 研究步骤取证归纳：ANCE 刷新 ANN 索引的频率与触发条件

## 问题陈述

本步骤要回答：ANCE 在训练过程中以什么频率、在什么触发条件下刷新用于全局难负例采样的 ANN 索引？

## 研究发现

### 1. 总体机制：异步刷新（Asynchronous Index Refresh）

ANCE 使用一个**异步更新**的语料表示 ANN 索引来实现全局负例采样。论文明确指出，索引的更新涉及两个操作：一是 Inference（用更新后的 DR 模型对全语料重新计算表示），二是 Index（用更新后的表示重建 ANN 索引）。由于 Index 操作高效，而 Inference 需要对整个语料做前向计算、开销过大，"too expensive to compute per batch as it requires a forward pass on the entire corpus which is much bigger than the training batch" [KB-4]。因此无法逐批（per batch）刷新，只能采用异步方式。

### 2. 刷新频率：每 m 个批次刷新一次

关于频率，论文给出的明确表述是：采用类似 Guu et al. (2020) 的异步索引刷新，"update the ANN index once every m batches, i.e., with checkpoint fk" [KB-4]。也就是说，索引并非每批更新，而是**每隔 m 个批次**用检查点 f_k 刷新一次。需要说明的是，**论文在该片段中未给出 m 的具体数值**，仅以符号 m 表示这一间隔。

### 3. 触发条件：Inferencer 完成全语料重编码后触发

刷新并非由固定时间触发，而是由 **Inferencer 完成语料重编码这一事件**触发。论文描述：除 Trainer 外，另运行一个 Inferencer，它取最新检查点（如 f_k）重新计算整个语料的编码；与此同时 Trainer 继续用来自 ANN_{f_{k-1}} 的负例进行随机学习；"Once the corpus is re-encoded, Inferencer updates the ANN index (ANN_{f_k}) and feed it to the Trainer" [KB-4]。即：**一旦 Inferencer 完成全语料重编码，就用最新编码刷新 ANN 索引并交给 Trainer**。

引言部分也印证了这一触发逻辑：维护一个 Inferencer，"parallelly computes the document encodings with a recent checkpoint... and refresh the ANN index used for negative sampling once it finishes, to keep up with the model training" [KB-1]。

### 4. 刷新与训练节奏的关系

由于上述异步机制，ANCE 负例（D_ANCE）被异步更新以"catch up"随机训练；ANN 索引与 DR 模型优化之间的差距取决于 Trainer 与 Inferencer 之间的计算资源分配，附录 A.3 表明 1:1 的 GPU 分配足以将这一差距的影响降到最小 [KB-4]。图 2 的说明也概括了这一流程：Trainer 用来自 ANN 索引的负例学习表示，Inferencer 用近期检查点更新语料文档表示，"once finished, refreshes the ANN index with most up-to-date encodings" [KB-5]。

## 结论

- **频率**：ANCE 不是逐批刷新索引，而是**每 m 个批次**用检查点 f_k 刷新一次（"update the ANN index once every m batches"）[KB-4]。m 的具体取值在所提供的材料中**论文中未报告**。
- **触发条件**：刷新的实质触发条件是**异步 Inferencer 完成对整个语料的重编码**——"Once the corpus is re-encoded, Inferencer updates the ANN index (ANN_{f_k}) and feed it to the Trainer" [KB-4]；引言亦表述为 Inferencer 完成后即刷新索引以跟上训练 [KB-1]。
- **原因**：逐批刷新不可行，因为 Inference 需对整个语料做前向计算，开销远大于训练批 [KB-4]；因此采用类似 Guu et al. (2020) 的异步刷新，使负例分布异步"追赶"训练 [KB-4]。

## 参考文献

[KB-1] ANCE 论文，1 INTRODUCTION，第 2 页（用户专题论文库原文片段）

[KB-2] ANCE 论文，ABSTRACT，第 1 页（用户专题论文库原文片段）

[KB-3] ANCE 论文，1 INTRODUCTION，第 2 页（用户专题论文库原文片段）

[KB-4] ANCE 论文，4 APPROXIMATE NEAREST NEIGHBOR NOISE CONTRASTIVE ESTIMATION，第 5 页（用户专题论文库原文片段）

[KB-5] ANCE 论文，3 ANALYSES ON THE CONVERGENCE OF DENSE RETRIEVAL TRAINING，第 4 页（用户专题论文库原文片段）

[KB-6] ANCE 论文，1 INTRODUCTION，第 2 页（用户专题论文库原文片段）

### 3. 刷新 ANN 索引时具体重新编码与重建的流程是什么？（COMPLETED）

# 刷新 ANN 索引时的重新编码与重建流程

## 问题陈述

本步骤聚焦于 ANCE 在刷新全局难负例所用的 ANN 索引时，**具体的重新编码（Inference）与索引重建（Index）流程**是什么。

## 研究发现

### 一、索引刷新包含两个操作：Inference 与 Index

论文明确指出，ANN 索引的更新需要两个操作：其一是 **Inference**，即用更新后的 DR 模型刷新语料库中所有文档的表示；其二是 **Index**，即用更新后的表示重建 ANN 索引。论文同时指出，Index 操作本身是高效的，但 Inference 需要在整个语料库上做一次前向计算，语料库远大于训练 batch，因此逐 batch 计算代价过高 [KB-4]。

> "the index update requires two operations: 1) Inference: refresh the representations of all documents in the corpus with an updated DR model; 2) Index: rebuild the ANN index using updated representations. Although Index is efficient (Johnson et al., 2019), Inference is too expensive to compute per batch as it requires a forward pass on the entire corpus which is much bigger than the training batch." [KB-4]

### 二、异步刷新机制：Trainer 与 Inferencer 并行

为解决 Inference 代价过高的问题，ANCE 采用与 Guu et al. (2020) 类似的**异步索引刷新**：每 m 个 batch（即用某个 checkpoint f_k）更新一次 ANN 索引。除 Trainer 外，另设一个 **Inferencer**，它取最新的 checkpoint（如 f_k）重新计算整个语料库的编码；与此同时，Trainer 继续使用来自旧索引 ANN_{f_{k-1}} 的负例 D_{f_{k-1}} 进行随机训练。一旦语料库重新编码完成，Inferencer 便更新 ANN 索引（ANN_{f_k}）并将其提供给 Trainer [KB-4]。

> "we implement an asynchronous index refresh similar to Guu et al. (2020), and update the ANN index once every m batches, i.e., with checkpoint fk. As illustrated in Fig. 2, besides the Trainer, we run an Inferencer that takes the latest checkpoint (e.g., fk) and recomputes the encodings of the entire corpus. In parallel, the Trainer continues its stochastic learning using Dfk-1 from ANNfk-1. Once the corpus is re-encoded, Inferencer updates the ANN index (ANNfk) and feed it to the Trainer." [KB-4]

### 三、刷新流程的图示描述

图 2 的说明进一步刻画了该流程：Trainer 使用来自 ANN 索引的负例学习表示；Inferencer 使用一个较新的 checkpoint 更新语料库中文档的表示，一旦完成，就用最新的编码刷新 ANN 索引 [KB-5]。

> "The Trainer learns the representation using negatives from the ANN index. The Inferencer uses a recent checkpoint to update the representation of documents in the corpus and once finished, refreshes the ANN index with most up-to-date encodings." [KB-5]

### 四、实现方式与刷新频率

在实现层面，ANCE 维护一个异步更新的语料库表示 ANN 索引，并用一个 Inferencer 并行地以正在优化的 DR 模型的较新 checkpoint 计算文档编码，完成后刷新用于负采样的 ANN 索引，以跟上模型训练 [KB-1]。实验设置中，索引刷新频率为**每 10k 个训练 batch 一次**，采用 1:1 的 Trainer:Inference GPU 分配，ANN 搜索使用 Faiss IndexFlatIP [KB-6]。

> "We implement ANCE using an asynchronously updated ANN index of the corpus representation. Similar to Guu et al. (2020), we maintain an Inferencer that parallelly computes the document encodings with a recent checkpoint from the being optimized DR model, and refresh the ANN index used for negative sampling once it finishes, to keep up with the model training." [KB-1]

> "We use 1:1 Trainer:Inference GPU allocation, index refreshing per 10k training batches ... The ANN search uses the Faiss IndexFlatIP Index (Johnson et al., 2019)." [KB-6]

### 五、刷新带来的效果

通过该异步过程，ANCE 负例（D_ANCE）被异步更新以"追赶"随机训练；ANN 索引与 DR 模型优化之间的差距取决于 Trainer 与 Inferencer 之间的计算资源分配，附录 A.3 表明 1:1 的 GPU 划分足以将该差距的影响降到最低 [KB-4]。

## 结论

ANCE 刷新 ANN 索引的流程可归纳为：**（1）Inferencer 取最新 checkpoint 对全语料库重新编码（Inference）→（2）用新编码重建 ANN 索引（Index）→（3）将新索引交给 Trainer 供负采样**。由于全语料前向计算代价过高，该过程并非逐 batch 进行，而是每 m 个 batch（实验中为每 10k batch）异步刷新一次，Trainer 在此期间并行使用旧索引的负例继续训练 [KB-4][KB-6]。

需要说明的是：关于重新编码与重建**内部的具体算法细节**（如编码的批处理方式、索引构建的并行/分片策略、增量更新还是全量重建的底层实现），论文原文片段中**未报告**；材料仅说明使用 Faiss IndexFlatIP 进行 ANN 搜索、每 10k batch 刷新一次、1:1 GPU 分配 [KB-6]。

## 参考文献

[KB-1] ANCE 论文，1 INTRODUCTION，第 2 页。

[KB-2] ANCE 论文，ABSTRACT，第 1 页。

[KB-3] ANCE 论文，1 INTRODUCTION，第 2 页。

[KB-4] ANCE 论文，4 APPROXIMATE NEAREST NEIGHBOR NOISE CONTRASTIVE ESTIMATION，第 5 页。

[KB-5] ANCE 论文，3 ANALYSES ON THE CONVERGENCE OF DENSE RETRIEVAL TRAINING，第 4 页。

[KB-6] ANCE 论文，5 EXPERIMENTAL METHODOLOGIES，第 5 页。

### 4. 论文是否给出索引刷新策略对全局难负例质量与检索效果的影响证据？（COMPLETED）

# 研究步骤取证归纳：索引刷新策略对全局难负例质量与检索效果的影响证据

## 问题陈述

本步骤要核查的问题是：论文是否提供了证据，说明**ANN 索引刷新策略**（即异步刷新频率与刷新机制）如何影响**全局难负例的质量**以及**检索效果**？

## 研究发现

### 1. 索引刷新策略的具体设计（机制层面）

论文明确给出了 ANCE 的索引刷新机制：由于每个 minibatch 都会更新 DR 模型 f()，而索引更新包含两个操作——Inference（用更新后的 DR 模型重新计算整个语料库的表示）与 Index（用更新后的表示重建 ANN 索引）；其中 Inference 因需要对整个语料库做前向计算，代价过高，无法每个 batch 都做，因此采用异步刷新，“update the ANN index once every m batches, i.e., with checkpoint fk”，并由 Inferencer 与 Trainer 并行运行，Inferencer 完成语料重编码后更新索引 ANN_fk 并交给 Trainer，而 Trainer 在此之前继续使用旧索引 ANN_fk-1 采样 [KB-4]。这一机制在引言中也有对应描述：维护一个 Inferencer 并行计算文档编码，并在其完成后刷新用于负采样的 ANN 索引，以“keep up with the model training” [KB-1]。摘要层面同样将其概括为“using an asynchronously updated ANN index” [KB-2]，第 4 节开篇也重申“selects negatives from the entire corpus using an asynchronously updated ANN index” [KB-5]。

### 2. 关于刷新滞后（gap）影响的证据

论文指出，ANCE 负例是“asynchronously updated to 'catch up' with the stochastic training”，即索引与模型优化之间存在滞后；该滞后的大小“depends on the allocation of computing resources between Trainer and Inferencer”，并引用附录 A.3 称“an 1:1 GPU split is sufficient to minimize the influence of this gap” [KB-4]。这是论文中**唯一**直接讨论刷新滞后对负例新鲜度影响的陈述，但其证据形式是资源分配建议（1:1 GPU 划分可最小化该 gap 的影响），而非对负例质量或检索指标的定量对比。

### 3. 关于全局难负例质量与检索效果的整体证据

论文对 ANCE 有效性的实证支持主要停留在整体层面：实验在 web search、OpenQA 与商用搜索引擎三类场景展示 ANCE 的优势，ANCE 点积检索“nearly matches the accuracy of BERT-based cascade IR pipeline, while being 100x more efficient” [KB-2]；并“empirically validate our theory that the gradient norms on ANCE sampled negatives are much bigger than local negatives and thus improve the convergence of dense retrieval models” [KB-1]；结论部分再次强调 ANCE 负例具有更大梯度范数、降低随机梯度方差、改善训练收敛 [KB-6]。这些证据针对的是“全局难负例 vs 局部负例”的对比，而非“不同索引刷新策略（如刷新频率 m、同步 vs 异步、不同 GPU 分配）对负例质量与检索效果的影响”。

## 结论

- **索引刷新策略本身**：论文给出了清晰的异步刷新设计（每 m 个 batch、以 checkpoint f_k 刷新、Inferencer 与 Trainer 并行）[KB-1][KB-4][KB-5]，并定性说明刷新滞后取决于计算资源分配，附录 A.3 建议 1:1 GPU 划分以最小化该 gap 的影响 [KB-4]。
- **刷新策略对全局难负例质量与检索效果的直接影响证据**：**论文中未报告**。现有材料中没有任何关于不同刷新频率/刷新方式对负例质量或检索指标（如 MRR、Recall）的消融或对比数据；论文的实证证据仅支持“全局难负例（相对局部负例）带来更大梯度范数、更低梯度方差、更快收敛及更好的检索表现” [KB-1][KB-2][KB-6]，不能等同于对刷新策略影响的证据。

## 参考文献

[KB-1] 用户专题论文库原文片段，章节 1 INTRODUCTION，页码 2

[KB-2] 用户专题论文库原文片段，章节 ABSTRACT，页码 1

[KB-3] 用户专题论文库原文片段，章节 1 INTRODUCTION，页码 2

[KB-4] 用户专题论文库原文片段，章节 4 APPROXIMATE NEAREST NEIGHBOR NOISE CONTRASTIVE ESTIMATION，页码 5

[KB-5] 用户专题论文库原文片段，章节 4 APPROXIMATE NEAREST NEIGHBOR NOISE CONTRASTIVE ESTIMATION，页码 4

[KB-6] 用户专题论文库原文片段，章节 8 CONCLUSION，页码 8

## 证据与分歧

- 2007.00808（ACCEPTED）
- 2007.00808（ACCEPTED）
- 2007.00808（ACCEPTED）
- 有 1 条来源仍待引用复核，不能作为无条件结论。

## 覆盖度与缺口

- 已检查来源：3 条；已完成步骤：4 / 4。
- 当前已接纳来源没有标记为反向证据；这不等于已经证明不存在冲突。

## 来源清单

- 2007.00808
- 2007.00808
- 2007.00808