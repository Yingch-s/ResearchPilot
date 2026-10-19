# D2 · full · round 3

这四篇论文的实验使用的操作系统和数据库软件是什么？

---

# 研究报告

## 直接回答

本报告汇总了 3 个已完成研究步骤的发现；逐项依据见下方证据与局限。

**研究问题**：这四篇论文的实验使用的操作系统和数据库软件是什么？

## 资料范围

- 专题内全部就绪资料（未限定单篇）

## 逐项发现

### 1. 各论文实验环境章节中声明的操作系统是什么？（COMPLETED）

## 问题陈述

本步骤要回答的问题是：这四篇论文在实验环境章节中**声明的操作系统**分别是什么？

## 取证归纳

**结论：在所给的全部原文片段中，没有任何一篇论文明确声明其实验所用的操作系统。**

逐篇核查如下：

1. **ColBERT 类论文（含 4.1 Methodology、4.1.1 Datasets & Metrics、4.1.3 Hardware & Time Measurements 的论文）**
   - 硬件章节仅报告了服务器配置："we use a single Tesla V100 GPU that has 32 GiBs of memory on a server with two Intel Xeon Gold 6132 CPUs, each with 14 physical cores (24 hyperthreads), and 469 GiBs of RAM"，以及另一台 "four Titan V GPUs attached, each with 12 GiBs of memory" 的服务器 [KB-1]。
   - 实现章节声明的软件栈为 "Our ColBERT models are implemented using Python 3 and PyTorch 1. We use the popular transformers 5 library for the pre-trained BERT model"。
   - **操作系统：论文中未报告**（原文只给出 CPU/GPU/内存与 Python/PyTorch 信息，未提及 OS）。

2. **ANCE / Dense Retrieval 类论文（含 A.1 MORE EXPERIMENTAL DETAILS 的论文）**
   - 该附录只描述了 TREC DL 基准、OpenQA 实验设置、基线系统与 BERT-Siamese 配置、LAMB 优化器等 [KB-2]。
   - **操作系统：论文中未报告**。

3. **含 "4 Experimental Setup" 的论文**
   - 原文仅有一句："In this section, we describe the data we used for experiments and the basic setup." [KB-3]
   - **操作系统：论文中未报告**（该片段未给出任何环境细节）。

4. **含 "B Alternative Similarity Functions & Triplet Loss" 的论文**
   - 该节讨论 L2 距离、triplet loss、margin 取值与超参数设置 [KB-4]。
   - **操作系统：论文中未报告**。

5. **含 "4.1.1 Datasets" 的论文**
   - 该节仅介绍 MSMARCO Passage Ranking 与 Natural Questions (NQ) 两个数据集 [KB-6]。
   - **操作系统：论文中未报告**。

## 结论

基于所给原文片段，**四篇论文均未在实验环境章节中声明操作系统**。可确认的相关信息仅有：
- 一篇论文报告了详细的硬件配置（Tesla V100、Titan V、Intel Xeon Gold 6132、469 GiB RAM）[KB-1]；
- 同一篇论文报告了软件栈为 Python 3 + PyTorch 1 + transformers 库（章节背景中的 4.1.2 Implementation，未在给定 [KB-n] 片段中单独编号，故仅作背景参考）；
- 其余论文的片段只涉及数据集、基线、相似度函数与超参数，未涉及操作系统。

如需确认操作系统信息，建议补充抓取各论文的 "Implementation Details" 或实验环境脚注/代码仓库说明部分。

## 参考文献

[KB-1] 4.1 Methodology, 4.1.3 Hardware & Time Measurements, p.6

[KB-2] A.1 MORE EXPERIMENTAL DETAILS, p.12

[KB-3] 4 Experimental Setup, p.4

[KB-4] B Alternative Similarity Functions & Triplet Loss, p.12

[KB-5] 4.1 Methodology, 4.1.1 Datasets & Metrics, p.6

[KB-6] 4.1.1 Datasets, p.6nn# 取证归纳：各论文实验环境中的操作系统与数据库软件

## 问题重述

本步骤需补充取证：四篇论文的实验环境或实现细节中声明的**操作系统**以及**数据库软件**（如 MySQL、PostgreSQL、MongoDB 等）分别是什么。

## 研究结论

### 一、操作系统（Operating System）

基于用户专题论文库中提供的原文片段，**四篇论文均未在实验环境或实现细节章节中明确声明所使用的操作系统**。

- 论文仅说明实验设置详见附录（"This section describes our experimental setups. More details can be found in Appendix A.1 and A.2." [KB-1]），但所给片段中并未包含附录内容，因此无法从现有材料中获知操作系统信息。
- 部分论文报告了硬件与实现环境，但未提及操作系统。例如，ANCE 论文报告了效率测量所用的硬件："We measured ANCE efficiency using a single 32GB V100 GPU, on a cloud VM with Intel(R) Xeon(R) Platinum 8168 CPU and 650GB of RAM memory."（章节背景 P-9558cde80da4，非 [KB-n] 原文，仅供参考）。该描述只涉及 GPU、CPU 与内存，**未声明操作系统**。
- ColBERT 论文报告了实现语言与框架："Our ColBERT models are implemented using Python 3 and PyTorch 1. We use the popular transformers 5 library for the pre-trained BERT model."（章节背景 P-f3d00876519a，非 [KB-n] 原文，仅供参考）。该描述只涉及编程语言与深度学习框架，**未声明操作系统**。

**结论：操作系统——论文中未报告。**

### 二、数据库软件（Database Software）

基于用户专题论文库中提供的原文片段，**四篇论文均未声明使用任何数据库软件（如 MySQL、PostgreSQL、MongoDB 等）**。

- 现有片段中涉及数据存储与检索的内容均为**数据集与检索索引**，而非数据库管理系统。例如：
  - MS MARCO 被描述为数据集："MS MARCO is a dataset (and a corresponding competition) introduced by Microsoft in 2016 for reading comprehension and adapted in 2018 for retrieval. It is a collection of 8.8M passages from Web pages..." [KB-2]。
  - 问答实验使用五个 QA 数据集："We use the same five QA datasets and training/dev/testing splitting method as in previous work (Lee et al., 2019)." [KB-3]。
  - ColBERT 的索引被描述为基于 BERT 的向量索引："ColBERT' s indexing-the only time it needs to feed documents through BERT-is also practical: it can index the MS MARCO collection of 9M passages in about 3 hours using a single server with four GPUs (§4.5), retaining its effectiveness with a space footprint of as little as few tens of GiBs." [KB-4]。
- 章节背景中提到的 "Faiss IndexFlatIP Index"、"ANN search" 等属于**向量检索库/近似最近邻索引**（如 Faiss），并非传统数据库软件（MySQL/PostgreSQL/MongoDB）。且该内容属于章节背景（P-9558cde80da4），并非用户指定的 [KB-n] 原文，不能作为已校验结论引用。

**结论：数据库软件——论文中未报告。**

## 说明

本次取证所依据的 [KB-1] 至 [KB-5] 原文片段仅覆盖实验方法论概述、数据集描述（MS MARCO、QA 数据集）及索引/效率等实现层面信息，**均未涉及操作系统或数据库软件**。要确认这两项信息，需进一步查阅各论文的附录（如 [KB-1] 所指的 Appendix A.1 与 A.2）或完整实现细节章节。在现有材料范围内，应如实标注为"论文中未报告"，不作推测。

## 参考文献

[KB-1] 用户专题论文库原文片段：5 EXPERIMENTAL METHODOLOGIES，第 5 页

[KB-2] 用户专题论文库原文片段：4.1 Methodology，第 6 页

[KB-3] 用户专题论文库原文片段：4.2 Question Answering Datasets，第 4 页

[KB-4] 用户专题论文库原文片段：1 INTRODUCTION，第 2 页

[KB-5] 用户专题论文库原文片段：1 Introduction，第 1 页

### 2. 各论文实验所用数据库软件的名称与版本是什么？（COMPLETED）

## 问题陈述

本步骤要回答的问题是：这四篇论文的实验所使用的**数据库软件**的名称与版本是什么？

## 研究结论（基于用户专题论文库原文片段）

### 1. 关于"数据库软件"的直接证据

在所提供的全部原文片段（[KB-1] 至 [KB-6]）中，**均未出现任何数据库软件（如 MySQL、PostgreSQL、MongoDB、Oracle、SQLite、Elasticsearch 等）的名称或版本信息**。

各片段涉及的内容分别为：

- [KB-1] 描述的是实验硬件配置：使用单块 Tesla V100 GPU（32 GiB 显存），服务器配有两颗 Intel Xeon Gold 6132 CPU（每颗 14 物理核、24 超线程）和 469 GiB 内存；另一台服务器 CPU 与内存规格相同，但配有四块 Titan V GPU（每块 12 GiB 显存）。这是**硬件**信息，不是数据库软件。
- [KB-2] 仅为实验设置章节的开头说明："In this section, we describe the data we used for experiments and the basic setup."（[KB-2]），未提及数据库软件。
- [KB-3] 讨论替代相似度函数与 triplet loss 的超参数设置（[KB-3]），未提及数据库软件。
- [KB-4] 说明使用 MS MARCO Ranking 与 TREC-CAR 两个**数据集**（[KB-4]），这是数据集而非数据库软件。
- [KB-5] 说明使用 MSMARCO Passage Ranking 与 Natural Questions (NQ) 两个 QA **数据集**（[KB-5]），同样是数据集而非数据库软件。
- [KB-6] 仅为在 NQ 上检验三种训练策略有效性的表格说明（[KB-6]），未提及数据库软件。

### 2. 需要澄清的易混淆点

论文中出现的 MS MARCO、TREC-CAR、MSMARCO Passage Ranking、Natural Questions (NQ) 均为**实验数据集/基准（datasets/benchmarks）**，而非数据库管理系统软件，例如 [KB-4] 明确指出"we conduct our experiments on the MS MARCO Ranking ... and TREC Complex Answer Retrieval (TREC-CAR) datasets"，[KB-5] 称"two popular QA benchmarks"。因此不能将其等同于数据库软件。

此外，[KB-1] 中提到的 Tesla V100、Titan V、Intel Xeon Gold 6132 属于**硬件/GPU/CPU**，也不属于数据库软件。

### 3. 材料不足之处

- 四篇论文实验所用**数据库软件的名称与版本**：**论文中未报告**（在所提供的 [KB-1]～[KB-6] 片段范围内无任何相关记载）。
- 若需确认是否存在数据库软件使用情况，需检索各论文的"Implementation / Experimental Setup"完整章节（例如 ColBERT 论文 4.1.2 Implementation 中提到使用 Python 3、PyTorch 1、transformers 库、faiss 索引，但这些属于编程语言、深度学习框架与向量检索库，并非数据库软件，且这些细节来自章节背景而非本步骤可引用的 [KB-n] 原文，故不作结论）。

## 结论

基于用户专题论文库所提供的原文片段 [KB-1]～[KB-6]，**无法确定这四篇论文实验所使用的数据库软件名称与版本**——相关片段仅报告了硬件配置（[KB-1]）、数据集/基准（[KB-4]、[KB-5]）与超参数设置（[KB-3]）等信息，**数据库软件的名称与版本在论文中未报告**。建议补充检索各论文的完整"Implementation"章节以进一步核实。

## 参考文献

[KB-1] 论文库片段：4.1 Methodology，页码 6（Hardware & Time Measurements）

[KB-2] 论文库片段：4 Experimental Setup，页码 4

[KB-3] 论文库片段：B Alternative Similarity Functions & Triplet Loss，页码 12

[KB-4] 论文库片段：4.1 Methodology，页码 6（Datasets & Metrics）

[KB-5] 论文库片段：4.1.1 Datasets，页码 6

[KB-6] 论文库片段：A.2 The Effectiveness of The Three Training Strategies on NQ，页码 13

# 步骤取证归纳：四篇论文实验所用数据库软件名称与版本

## 问题陈述

本步骤需补充取证：四篇专题论文在实验部分（Implementation / Experimental Setup 章节）所使用的**数据库软件名称与版本**（如 MySQL、PostgreSQL、MongoDB 等）。

## 研究结论

基于用户专题论文库提供的原文片段 [KB-1] 至 [KB-5]，**四篇论文均未报告其实验所使用的数据库软件名称与版本**。具体核查如下：

1. **RocketQA 相关论文**：[KB-1] 仅说明"本节描述我们的实验设置，更多细节见附录 A.1 和 A.2"（"This section describes our experimental setups. More details can be found in Appendix A.1 and A.2."），未提及任何数据库软件；[KB-2] 说明实验在 MSMARCO 数据集上进行（"we conduct the extensive experiments on MSMARCO dataset"），[KB-3] 仅概述实验目的，均未涉及数据库软件。[KB-4] 提到 ColBERT 索引 MS MARCO 的 9M passages 约需 3 小时（单服务器四 GPU），但未说明所用数据库软件。[KB-5] 描述 TREC CAR 数据集构成，同样未涉及数据库软件。

2. **补充说明**：现有材料中出现的 MSMARCO、NQ、TREC CAR、TREC 2019 DL 等均为**数据集/评测基准**，而非数据库软件；[KB-4] 提及的索引与 GPU 服务器属于检索系统实现细节，不能等同于数据库软件。

## 结论

**论文中未报告**四篇论文实验所使用的数据库软件名称与版本。所提供原文片段仅涉及数据集（如 MSMARCO、NQ、TREC CAR）与实验方法概述，未包含 MySQL、PostgreSQL、MongoDB 或任何数据库系统的名称或版本信息。若需确认，应进一步查阅各论文的 Implementation Details 完整段落或附录 A.1/A.2（[KB-1] 所指），但当前材料不足以回答该问题。

## 参考文献

- [KB-1] 章节 5 EXPERIMENTAL METHODOLOGIES，页码 5
- [KB-2] 章节 4.2.2 The Effectiveness of The Three Training Strategies in RocketQA，页码 8
- [KB-3] 章节 1 Introduction，页码 2
- [KB-4] 章节 1 INTRODUCTION，页码 2
- [KB-5] 章节 4.1 Methodology，页码 6

### 3. 四篇论文之间 OS 与数据库信息是否可直接横向比较？（COMPLETED）

## 步骤结论：四篇论文之间 OS 与数据库信息是否可直接横向比较？

**结论：无法进行横向比较。** 基于当前提供的原文片段，四篇论文中**没有任何一篇报告了操作系统（OS）信息**，也**没有任何一篇报告了数据库软件信息**。因此不存在可供横向比较的 OS 与数据库字段。

---

### 一、逐篇取证情况

**[KB-1]（ColBERT，SIGIR '20）**
- 提供的片段仅讨论 late interaction 架构、MaxSim 算子、向量相似度检索索引（如 [1, 15]）等模型设计内容，未涉及实验环境。
- OS：论文中未报告（本片段未报告）。
- 数据库软件：论文中未报告。片段中提到的 "vector-similarity search indexes (e.g., [1, 15])" 属于检索索引方法引用，**并非**数据库软件说明，不能据此推断使用了某数据库。

**[KB-2]（RocketQA，4.1.3 Implementation Details）**
- 片段涉及实现细节：使用深度学习框架 **PaddlePaddle**（Ma et al., 2019），最多 **八块 NVIDIA Tesla V100 GPU（32G RAM）**，预训练模型为 ERNIE 2.0 base/large，并使用 FleetX 分布式训练引擎、ADAM 优化器、混合精度与 gradient checkpoint 等。
- OS：论文中未报告（仅报告了框架与硬件）。
- 数据库软件：论文中未报告。

**[KB-3]（DPR，6.2 Results）**
- 片段讨论端到端 QA 的 Exact Match 结果、与 ORQA/REALM 的对比、joint training 消融、以及单块 32GB GPU 上的推理延迟（约 20ms）等。
- OS：论文中未报告。
- 数据库软件：论文中未报告。片段中提到的 "passages retrieved" 属于检索流程描述，**并非**数据库软件。

**[KB-4]（DPR，附录 B Alternative Similarity Functions & Triplet Loss）**
- 片段讨论替代相似度函数（L2、triplet loss）与超参数设置，并提及 "documented in our code release"。
- OS：论文中未报告。
- 数据库软件：论文中未报告。

---

### 二、为什么不能横向比较

1. **字段缺失**：四篇论文的上述片段均未报告 OS 与数据库软件，横向比较的前提（同一字段在四篇中均有取值）不成立。
2. **报告粒度不一致**：即便在唯一有环境信息的 [KB-2] 中，报告的是**深度学习框架（PaddlePaddle）+ GPU 硬件**，而非操作系统或数据库软件；这与"OS/数据库"并非同一维度，不能替代。
3. **片段范围有限**：[KB-1]、[KB-3]、[KB-4] 的片段属于方法/结果/附录讨论部分，本身不承载实验环境信息；仅凭这些片段无法判定全文是否在别处报告了 OS 或数据库，但**在本步骤可用的材料范围内，均属"论文中未报告"**。

---

### 三、建议的后续取证方向（不构成结论）

若要真正完成 OS 与数据库的横向比较，需要在四篇论文中定位**实验环境/实现细节章节**（如 ColBERT 的实现细节、DPR 的 Experimental Setup [KB-5] 所指的 "basic setup" 具体内容、RocketQA 的完整实现细节），检索关键词如 "operating system"、"Linux"、"Ubuntu"、"Anaconda"、"FAISS"、"Elasticsearch"、"database"、"index" 等。**注意：FAISS 等属于向量检索库，是否算作"数据库软件"需按研究口径界定，且必须由原文明确表述方可引用，不得推断。**

---

### 参考文献

[KB-1] ColBERT 论文 1 INTRODUCTION，第 2 页（用户专题论文库原文片段）

[KB-2] RocketQA 论文 4.1.3 Implementation Details，第 7 页（用户专题论文库原文片段）

[KB-3] DPR 论文 6.2 Results，第 8 页（用户专题论文库原文片段）

[KB-4] DPR 论文 B Alternative Similarity Functions & Triplet Loss，第 12 页（用户专题论文库原文片段）

[KB-5] DPR 论文 4 Experimental Setup，第 4 页（用户专题论文库原文片段，仅指出存在 "basic setup" 章节）

## 问题陈述

本步骤要补充取证的是：四篇论文在实验环境中使用的**操作系统**与**数据库/检索软件**（如 Linux、Ubuntu、FAISS、Elasticsearch、Anaconda 等）分别是什么，并判断这四篇论文之间的 OS 与数据库信息是否可以直接横向比较。

## 研究发现

### 一、关于操作系统（OS）

在所提供的原文片段中，**没有任何一篇论文报告了实验所使用的操作系统信息**（无论是 Linux、Ubuntu、Windows 还是其他系统）。原文中能够找到的硬件与计算环境描述仅有以下内容：

- 有一篇论文在“4.1.3 Hardware & Time Measurements”一节中详细描述了服务器硬件配置：延迟评测使用单块 Tesla V100 GPU（32 GiB 显存），服务器配有两颗 Intel Xeon Gold 6132 CPU（每颗 14 个物理核、24 个超线程）和 469 GiB RAM；CPU 密集型检索实验与索引实验则使用另一台 CPU 与内存规格相同、但配有四块 Titan V GPU（每块 12 GiB 显存）的服务器 [KB-5]。该段仅涉及 CPU、GPU 与内存，**未提及操作系统**。

因此，就操作系统而言：**论文中未报告**。

### 二、关于数据库/检索软件

1. **FAISS（近似最近邻检索库）**：有一篇论文在正文脚注中给出了 FAISS 的 GitHub 地址 `https://github.com/facebookresearch/faiss` [KB-6]。这间接表明该论文的实验使用了 FAISS 作为 ANN 检索组件（该脚注出现在“3.6 End-to-end Topk Retrieval with ColBERT”一节），但原文片段本身并未以文字形式明确陈述“我们使用 FAISS 进行检索”，仅提供了其代码仓库链接。

2. **传统稀疏检索模型（BM25、TF-IDF）**：多篇论文将 BM25、TF-IDF 作为基线或第一阶段检索方法加以讨论。例如，有论文指出第一阶段检索仍主要依赖离散词袋匹配，如 BM25 [KB-1]；另有论文指出 TF-IDF 或 BM25 等强稀疏向量空间模型已被广泛用作各类 QA 任务的标准方法 [KB-4]，并在介绍两阶段方法时提到传统 IR 检索器（如 TF-IDF 或 BM25）先选出相关段落（见章节背景 P-140621b255be，但该内容未在给定 [KB-n] 片段中直接对应，故仅作背景参考）。这些属于**方法/算法层面**的提及，并非对具体数据库软件产品的报告。

3. **Elasticsearch、Anaconda 等具体软件**：在所提供的全部原文片段中，**均未出现** Elasticsearch、Anaconda 或任何其他具体数据库/环境管理软件的名称。因此，就这些软件而言：**论文中未报告**。

### 三、可横向比较性判断

基于现有取证材料：

- **操作系统**：四篇论文均未报告 OS 信息，**无法进行任何横向比较**。
- **数据库/检索软件**：仅能确认其中一篇论文涉及 FAISS（通过脚注链接 [KB-6]），其余论文在给定片段中未报告具体检索软件产品；BM25/TF-IDF 属于通用基线方法而非特定软件产品 [KB-1][KB-4]。因此，**四篇论文之间在数据库/检索软件层面缺乏可对齐的、同维度的报告信息，无法直接横向比较**。

## 结论

在所提供的原文片段范围内，四篇论文**均未报告操作系统信息**，也**基本未报告具体的数据库软件产品**（仅一篇通过脚注间接涉及 FAISS [KB-6]）。硬件层面仅有服务器 CPU/GPU/内存的配置描述 [KB-5]，不含 OS。因此，**四篇论文之间的 OS 与数据库信息不具备直接横向比较的基础**；Elasticsearch、Anaconda 等具体软件在材料中均属“论文中未报告”。若需进行横向比较，需进一步查阅各论文的完整实验设置章节（如 Implementation Details / Experimental Setup），本步骤材料不足以支撑该比较。

## 参考文献

[KB-1] 用户专题论文库原文片段，章节 1 INTRODUCTION，页码 1

[KB-2] 用户专题论文库原文片段，章节 7 RELATED WORK，页码 8

[KB-3] 用户专题论文库原文片段，章节 1 Introduction，页码 1

[KB-4] 用户专题论文库原文片段，章节 7 RelatedWork，页码 8

[KB-5] 用户专题论文库原文片段，章节 4.1 Methodology，页码 6

[KB-6] 用户专题论文库原文片段，章节 3.6 End-to-end Topk Retrieval with ColBERT，页码 5（含 FAISS 仓库链接 https://github.com/facebookresearch/faiss）

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
- 有 4 条来源仍待引用复核，不能作为无条件结论。

## 覆盖度与缺口

- 已检查来源：12 条；已完成步骤：3 / 3。
- 当前已接纳来源没有标记为反向证据；这不等于已经证明不存在冲突。

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