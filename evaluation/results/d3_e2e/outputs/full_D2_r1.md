# D2 · full · round 1

这四篇论文的实验使用的操作系统和数据库软件是什么？

---

# 研究报告

## 直接回答

本报告汇总了 3 个已完成研究步骤的发现；逐项依据见下方证据与局限。

**研究问题**：这四篇论文的实验使用的操作系统和数据库软件是什么？

## 资料范围

- 专题内全部就绪资料（未限定单篇）

## 逐项发现

### 1. 四篇论文分别是哪些，各自在实验章节中报告了怎样的操作系统环境？（COMPLETED）

## 问题陈述

本步骤要确认：用户专题论文库中的四篇论文分别是哪些，以及它们在实验章节中报告了怎样的操作系统环境（并为后续回答“操作系统和数据库软件”问题做铺垫）。

## 研究结论

### 一、四篇论文的识别

根据原文片段，可以识别出以下四篇论文（依据其数据集、方法与引用特征）：

1. **ColBERT 论文**（对应 [KB-1]、[KB-4] 及章节背景 P-f3d00876519a）：实验使用 MS MARCO 与 TREC-CAR 数据集，模型用 Python 3 与 PyTorch 实现 [KB-4]。该片段明确写有"our ColBERT models are implemented using Python 3 and PyTorch 1"（见章节背景，但该句未出现在给定的 [KB-n] 片段中，故不作为已校验引用）。

2. **DPR 相关 / OpenQA 实验论文**（对应 [KB-3] 及章节背景 P-ce93f2e110a1）：涉及 DPR、RAG-Token、ANCE 等系统，实验基于 DPR 开源库与 HuggingFace 上的 RAG-Token 发布版本 [KB-3]。

3. **ANCE 论文**（对应章节背景 P-ce93f2e110a1 中"Implementation Details"部分）：描述了 ANCE 训练、LAMB 优化器等细节（该内容仅出现在背景中，未在 [KB-n] 片段中直接呈现）。

4. **另一篇使用 MSMARCO Passage Ranking 与 Natural Questions (NQ) 的 QA 检索论文**（对应 [KB-6] 及章节背景 P-ba32f5570add）：在两个 QA 基准上开展实验 [KB-6]。

> 说明：由于提供的 [KB-n] 片段均为节选，四篇论文的完整标题、作者等信息在材料中**未直接给出**，上述识别主要依据数据集与方法描述推断。

### 二、各论文实验章节报告的操作系统环境

逐条核对给定原文片段：

- [KB-1] 仅报告了**硬件配置**：Tesla V100 GPU（32 GiB）、两颗 Intel Xeon Gold 6132 CPU（各 14 物理核/24 超线程）、469 GiB 内存；另一台服务器 CPU 与内存规格相同但配有四块 Titan V GPU（各 12 GiB）。**未提及操作系统**。
- [KB-2] 讨论替代相似度函数与三元组损失的超参数设置，**未涉及操作系统**。
- [KB-3] 说明 OpenQA 实验基于 DPR 开源库、HuggingFace 上的 RAG-Token 等软件依赖，**未提及操作系统**。
- [KB-4] 仅介绍数据集（MS MARCO、TREC-CAR）与评价指标，**未提及操作系统**。
- [KB-5] 仅为实验设置章节的开头一句，**未提及操作系统**。
- [KB-6] 仅介绍 MSMARCO Passage Ranking 与 NQ 两个数据集，**未提及操作系统**。

此外，章节背景 P-f3d00876519a 中提到实现使用 "Python 3 and PyTorch 1"、transformers 库、faiss 索引等，但**同样未报告操作系统**。

### 三、数据库软件

给定原文片段中**未报告任何数据库软件**。相关系统均基于文件式索引/向量检索库（如 faiss，仅在背景中出现）与深度学习框架，未涉及传统数据库。

## 结论

1. 四篇论文可大致识别为：ColBERT 论文、DPR/OpenQA 实验论文、ANCE 论文，以及一篇在 MSMARCO Passage Ranking 与 NQ 上做 QA 检索的论文；但材料中**未给出完整标题与作者**，无法完全确证。
2. 就**操作系统环境**而言，所提供的 [KB-n] 原文片段中**四篇论文均未报告操作系统**（[KB-1] 只报告了 CPU/GPU/内存等硬件规格）。因此本步骤无法从现有材料中得出各论文的操作系统结论，需补充实验章节中关于软件环境的原文。
3. 关于**数据库软件**，材料中同样**未报告**。

## 参考文献

[KB-1] 章节 4.1 Methodology，页码 6（硬件与时间测量）

[KB-2] 章节 B Alternative Similarity Functions & Triplet Loss，页码 12

[KB-3] 章节 A.1 MORE EXPERIMENTAL DETAILS，页码 12

[KB-4] 章节 4.1 Methodology，页码 6（数据集与指标）

[KB-5] 章节 4 Experimental Setup，页码 4

[KB-6] 章节 4.1.1 Datasets，页码 6

## 问题陈述

本步骤要回答的问题：四篇论文分别是哪些？各自在实验章节中报告了怎样的操作系统环境和数据库软件（如 Ubuntu Linux、MySQL、PostgreSQL 等）。

## 研究取证与归纳

### 1. 关于"四篇论文分别是哪些"

用户专题论文库提供的原文片段 [KB-1]～[KB-5] 实际指向的论文主题如下：

- **[KB-1]、[KB-3]、[KB-4]、[KB-5]** 主题为开放域问答的稠密检索（dense retrieval / dual-encoder），涉及 TREC CAR、MS MARCO 等数据集，与 DPR/ColBERT 系列工作相关。
- **[KB-2]** 为 ColBERT 论文的结论与致谢部分（"we introduced ColBERT, a novel ranking model…"）。

需要说明：**材料本身并未明确列出"四篇论文"的完整标题清单**。片段中可辨识的至少包括 ColBERT（[KB-2]）以及一篇基于 dual-encoder、使用 TREC CAR 的稠密检索论文（[KB-5]）。其余论文的完整身份在提供的原文中**论文中未报告**，无法据此确认四篇论文的准确名单。

### 2. 关于操作系统环境

- [KB-1] 仅说明："This section describes our experimental setups. More details can be found in Appendix A.1 and A.2."——即实验细节被指向附录 A.1 和 A.2，但**附录原文未在材料中提供**。
- 章节背景（P-9558cde80da4）提到的硬件环境为："We measured ANCE efficiency using a single 32GB V100 GPU, on a cloud VM with Intel(R) Xeon(R) Platinum 8168 CPU and 650GB of RAM memory."——这里只报告了 **GPU、CPU 型号与内存**，**并未报告操作系统**。

因此，就所提供的 [KB-n] 原文而言，**各论文实验章节报告的操作系统（如 Ubuntu Linux 等）论文中未报告**。

### 3. 关于数据库软件

- [KB-3]、[KB-4] 提到检索实现使用 TF-IDF / BM25、倒排索引、Lucene-BM25（"a strong Lucene-BM25 system"）、MIPS 等，但这些属于**检索/索引技术**，并非数据库软件（MySQL、PostgreSQL）环境说明。
- [KB-5] 只涉及 TREC CAR 数据集规模与划分（"about 29M passages"、"2,254 queries"），不涉及数据库软件。
- [KB-2] 为致谢与资助信息，与实验环境无关。

因此，**数据库软件（MySQL、PostgreSQL 等）在所提供的原文中论文中未报告**。

## 结论

1. **四篇论文的完整名单**：材料中可明确辨识的仅有 ColBERT（[KB-2]）及一篇使用 TREC CAR 的稠密检索论文（[KB-5]），其余论文身份**论文中未报告**，无法确认"四篇"的准确构成。
2. **操作系统**：[KB-1] 将实验细节指向附录 A.1、A.2，但附录内容未提供；可见的硬件描述仅为 GPU（32GB V100）、CPU（Xeon Platinum 8168）与 650GB 内存。**操作系统（Ubuntu Linux 等）论文中未报告**。
3. **数据库软件**：原文只涉及 TF-IDF/BM25、Lucene、倒排索引、Faiss（"Faiss IndexFlatIP Index"）等检索与索引组件，**MySQL、PostgreSQL 等数据库软件论文中未报告**。

综上，基于所提供的 [KB-n] 原文，**无法回答四篇论文实验所用的操作系统与数据库软件**，需补充附录 A.1/A.2 原文或其他论文的完整实验章节方可取证。

## 参考文献

[KB-1] 用户专题论文库片段：5 EXPERIMENTAL METHODOLOGIES，第 5 页

[KB-2] 用户专题论文库片段：5 CONCLUSIONS，第 9 页

[KB-3] 用户专题论文库片段：1 Introduction，第 1 页

[KB-4] 用户专题论文库片段：Abstract，第 1 页

[KB-5] 用户专题论文库片段：4.1 Methodology，第 6 页

### 2. 四篇论文各自在实验中使用了哪种数据库软件及其版本？（COMPLETED）

## 问题陈述

本步骤要回答的问题是：**四篇论文各自在实验中使用了哪种数据库软件及其版本？**

## 取证归纳

基于用户提供的原文片段 [KB-1] 至 [KB-6]，逐一核查如下：

### 1. 关于"数据库软件"的直接证据

在所提供的全部原文片段中，**没有任何一篇论文明确报告了所使用的数据库软件名称或版本**。具体而言：

- [KB-1] 仅描述了硬件配置（Tesla V100、Titan V GPU、Intel Xeon Gold 6132 CPU、内存容量等）与时间测量方法，未提及数据库软件。
- [KB-2] 仅为实验设置的概述句（"we describe the data we used for experiments and the basic setup"），未涉及数据库软件。
- [KB-4]、[KB-6] 描述的是**数据集**（MS MARCO、TREC-CAR、NQ、MSMARCO Passage Ranking），属于评测数据而非数据库管理系统软件。
- [KB-3]、[KB-5] 讨论的是相似度函数、三元组损失、训练策略与超参数，与数据库软件无关。

### 2. 与"检索/索引软件"相关的间接线索

虽然原文未出现"数据库软件"字样，但 [KB-1] 与章节背景（P-f3d00876519a）中出现了与检索索引相关的工具信息，可作为**间接线索**（但需注意：这些属于背景片段，且并非"数据库软件"）：

- [KB-1] 提到"the mostly CPU-based retrieval experiments in §4.3 and the indexing experiments in §4.5"，说明实验涉及检索与索引环节，但未指明所用数据库/索引软件名称与版本。
- 章节背景 P-f3d00876519a 提及使用 **faiss 索引**（"our faiss index was faster at L2-based retrieval"，并设置了分区数 P=2,000）以及 **Python 3、PyTorch 1、transformers 库**。但需强调：
  - 这些内容来自章节背景，而非 [KB-n] 已校验原文，不能作为本步骤的已核验结论；
  - 即便计入，faiss 是向量相似度检索库、PyTorch 是深度学习框架，均**不属于数据库软件**，且原文片段中也**未给出其版本号**。

### 3. 结论性判断

| 论文 | 实验所用数据库软件 | 版本 |
|------|------------------|------|
| 论文一（对应 [KB-1][KB-3][KB-4]，ColBERT 类工作） | 论文中未报告 | 论文中未报告 |
| 论文二（对应 [KB-2]） | 论文中未报告 | 论文中未报告 |
| 论文三（对应 [KB-5]，RocketQA） | 论文中未报告 | 论文中未报告 |
| 论文四（对应 [KB-6]，NQ/MSMARCO 类工作） | 论文中未报告 | 论文中未报告 |

## 结论

在所提供的 [KB-1] 至 [KB-6] 原文片段范围内，**四篇论文均未报告其实验所使用的数据库软件及其版本**。现有材料仅涉及硬件配置 [KB-1]、数据集 [KB-4][KB-6]、训练策略与超参数 [KB-3][KB-5] 等内容，均不构成对"数据库软件及版本"的回答。章节背景中出现的 faiss、PyTorch、Python 3 等属于检索库/框架，既非数据库软件，也未在已校验原文中给出完整版本信息，故不能据此下结论。因此，本步骤应明确标注为**"论文中未报告"**，不作任何猜测。

## 参考文献

[KB-1] 用户专题论文库 · 章节 4.1 Methodology · 页码 6
[KB-2] 用户专题论文库 · 章节 4 Experimental Setup · 页码 4
[KB-3] 用户专题论文库 · 章节 B Alternative Similarity Functions & Triplet Loss · 页码 12
[KB-4] 用户专题论文库 · 章节 4.1 Methodology · 页码 6
[KB-5] 用户专题论文库 · 章节 A.2 The Effectiveness of The Three Training Strategies on NQ · 页码 13
[KB-6] 用户专题论文库 · 章节 4.1.1 Datasets · 页码 6

## 问题陈述

本步骤要求补充取证：四篇论文在实验中使用的**操作系统**（如 Linux Ubuntu 版本）以及**数据库软件**（如 MySQL、PostgreSQL、SQLite、MongoDB 等）的**名称与版本**。

## 研究取证结论

基于用户专题论文库中提供的原文片段，逐一核查后结论如下：

### 1. 关于操作系统

- [KB-1]（ColBERT 论文引言）仅提及硬件配置与效率："it can index the MS MARCO collection of 9M passages in about 3 hours using a single server with four GPUs (§4.5)" [KB-1]。此处只说明使用"配备四块 GPU 的单台服务器"，**未报告**该服务器的操作系统名称或版本。
- [KB-2]（RocketQA 论文 4.2.1 节及 Table 3 说明）仅描述对比的检索基线（BM25、doc2query、DeepCT、docTTTTTquery、GAR、DPR、ME-BERT、ANCE）及数据集（MSMARCO、NQ），**未报告**任何操作系统信息。

→ **四篇论文实验所用操作系统：论文中未报告。**

### 2. 关于数据库软件

- [KB-1] 中提及的 "index the MS MARCO collection" 指的是 ColBERT 的**索引（indexing）过程**，即把文档通过 BERT 编码并存储为向量表示，并非指使用某种数据库软件；原文未出现 MySQL、PostgreSQL、SQLite、MongoDB 等任何数据库名称 [KB-1]。
- [KB-2] 中提及的 "MSMARCO Passage Ranking"、"NQ datasets" 均为**评测数据集**名称，而非数据库软件；同样未出现任何数据库软件名称或版本 [KB-2]。

→ **四篇论文实验所用数据库软件及版本：论文中未报告。**

## 结论

在本次提供的原文片段 [KB-1]、[KB-2] 中，**没有任何关于操作系统（如 Linux Ubuntu 版本）或数据库软件（如 MySQL、PostgreSQL、SQLite、MongoDB 及其版本）的记载**。文中出现的 "index"、"MS MARCO"、"NQ" 等术语分别指向量索引过程与评测数据集，不能等同于数据库软件。因此，本步骤所需信息**论文中未报告**，需查阅四篇论文的完整实验设置章节（如 Implementation Details / Experimental Setup）方能进一步确认，现有材料不足以支撑结论，不予猜测。

## 参考文献

[KB-1] ColBERT 论文，1 INTRODUCTION，页码 2（用户专题论文库原文片段）

[KB-2] RocketQA 论文，4.2.1 Dense Passage Retrieval，页码 8（用户专题论文库原文片段）

### 3. 论文中操作系统与数据库信息是明确写出还是需从部署描述中间接推断？（COMPLETED）

# 步骤取证归纳：操作系统与数据库信息是明确写出还是间接推断？

## 问题陈述

本步骤需要判定：这四篇论文的实验所用**操作系统（OS）**与**数据库软件**信息，是论文中**明确写出**，还是需要从部署/实现描述中**间接推断**。

## 研究结论

### 一、操作系统信息：论文中未报告

在所给的全部原文片段中，**没有任何一处明确提及实验所使用的操作系统**（如 Linux、Ubuntu、Windows 等），也没有可供间接推断操作系统的部署描述。

- 论文在实验设置部分仅笼统说明"描述了实验所用数据和基本设置"——"In this section, we describe the data we used for experiments and the basic setup." [KB-2]；
- 另一篇论文同样仅说明"本节描述我们的实验设置，更多细节见附录 A.1 和 A.2"——"This section describes our experimental setups. More details can be found in Appendix A.1 and A.2." [KB-5]；
- 涉及实现细节的表述也停留在模型与数据层面，例如实验基于 MS MARCO 与 TREC-CAR 数据集——"we conduct our experiments on the MS MARCO Ranking [24] ... and TREC Complex Answer Retrieval (TREC-CAR) [6] datasets" [KB-6]，以及超参数设置记录于代码发布中——"a better hyper-parameter setting used in these analysis experiments, which is documented in our code release." [KB-1]。

上述内容均未涉及操作系统，故**操作系统信息在论文中未报告**，也无法从这些片段间接推断。

### 二、数据库软件信息：论文中未报告

原文片段中**没有出现任何数据库软件**（如 MySQL、PostgreSQL、MongoDB、Elasticsearch 等）的明确表述，同样缺乏可据以推断的部署描述。

- 文中出现的是检索模型与数据集（如 BM25、MS MARCO、TREC-CAR）——"By only examining the the top-K passages retrieved by a specific retrieval approach (e.g. BM25)" [KB-3]；"the first stage retrieval still mainly relies on matching discrete bag-of-words, e.g., BM25" [KB-4]；以及数据集描述 [KB-6]。这些都是**检索算法/语料数据集**，而非数据库软件，不能作为数据库软件的推断依据。

因此，**数据库软件信息在论文中未报告**。

## 结论

基于所给 [KB-1] 至 [KB-6] 原文片段，四篇论文对**操作系统**与**数据库软件**均**未明确写出**，且现有片段中也不存在足以**间接推断**二者的部署描述（片段仅涉及数据集、检索算法、超参数与代码发布等）[KB-1][KB-2][KB-5][KB-6]。故本步骤应判定为：**论文中未报告**操作系统与数据库软件信息，现有材料无法支撑任何具体推断，需回到论文正文/附录（如附录 A.1、A.2 所指的更多细节）或代码仓库进一步查证。

## 参考文献

[KB-1] 章节 B Alternative Similarity Functions & Triplet Loss，页码 12

[KB-2] 章节 4 Experimental Setup，页码 4

[KB-3] 章节 1 Introduction，页码 2

[KB-4] 章节 1 INTRODUCTION，页码 1

[KB-5] 章节 5 EXPERIMENTAL METHODOLOGIES，页码 5

[KB-6] 章节 4.1 Methodology，页码 6

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