# D2 · full · round 2

这四篇论文的实验使用的操作系统和数据库软件是什么？

---

# 研究报告

## 直接回答

本报告汇总了 4 个已完成研究步骤的发现；逐项依据见下方证据与局限。

**研究问题**：这四篇论文的实验使用的操作系统和数据库软件是什么？

## 资料范围

- 专题内全部就绪资料（未限定单篇）

## 逐项发现

### 1. 四篇论文分别是哪四篇，各自的实验章节在何处？（COMPLETED）

## 本步骤归纳：四篇论文分别是哪四篇，各自的实验章节在何处？

### 一、材料可确认的信息

根据提供的原文片段，可以确认论文库中至少涉及以下**四个不同的研究/论文**（通过章节标题与内容特征区分）：

**1. ANCE 相关论文（章节 "5 EXPERIMENTAL METHODOLOGIES"）**
- 实验章节为 **第 5 节 "EXPERIMENTAL METHODOLOGIES"**，原文说明："This section describes our experimental setups. More details can be found in Appendix A.1 and A.2." [KB-1]
- 该节涉及 TREC 2019 Deep Learning Track、Natural Questions、TriviaQA 等基准（见章节背景 P-9558cde80da4）。

**2. ColBERT 相关论文（章节 "4.1 Methodology"，含 4.1.3 Hardware & Time Measurements）**
- 实验设置位于 **第 4 节 "4 Experimental Setup"**，原文："In this section, we describe the data we used for experiments and the basic setup." [KB-3]
- 其方法/实验细节在 **4.1 Methodology**（含 4.1.1 Datasets & Metrics、4.1.2 Implementation、4.1.3 Hardware & Time Measurements）[KB-2]，并涉及 §4.2、§4.3、§4.5 等实验小节 [KB-2]。

**3. RocketQA 相关论文（章节 "4.2.2 The Effectiveness of The Three Training Strategies in RocketQA"）**
- 实验在 **4.2.2 节** 展开，原文："In this part, we conduct the extensive experiments on MSMARCO dataset to examine the effectiveness of the three strategies in RocketQA. Results on NQ dataset has shown the similar findings (see in Section A.2 in Appendix)." [KB-5]
- 同论文还包含 4.2.1 Dense Passage Retrieval 小节 [KB-6]。

**4. 含 "Appendix B Alternative Similarity Functions & Triplet Loss" 的论文**
- 该论文的分析实验见 **附录 B**，原文："All these additional experiments are conducted using the same hyperparameters tuned for the baseline (DP, NLL)." [KB-4]
- 其正文实验章节在提供的片段中未直接给出标题，仅能从附录 B 反推存在正文实验（如 Table 3、Table 5 所在章节）。

### 二、材料不足、需明确说明的部分

- **四篇论文的完整标题、作者与发表信息**：提供的 [KB-n] 片段中**未报告**任何论文标题、作者或出处，无法据此确定四篇论文的确切名称。
- **第 4 篇论文的正文实验章节位置**：片段仅提供附录 B [KB-4]，其正文实验章节标题**论文中未报告**（在给定材料范围内）。
- **各论文实验章节的完整页码范围**：仅有零散页码（如 [KB-1] 页码 5、[KB-2] 页码 6、[KB-3] 页码 4、[KB-4] 页码 12、[KB-5] 页码 8、[KB-6] 页码 8），不足以完整界定各论文实验章节的起止范围。

### 三、小结

在给定材料范围内，可辨识出四篇论文分别对应：①第 5 节 EXPERIMENTAL METHODOLOGIES 的论文 [KB-1]；②第 4 节 Experimental Setup / 4.1 Methodology 的论文 [KB-3][KB-2]；③含 4.2.2 RocketQA 实验小节的论文 [KB-5][KB-6]；④含附录 B 相似度函数与 Triplet Loss 分析的论文 [KB-4]。但**论文标题、作者、出处及第 4 篇正文实验章节位置，论文中未报告**，需后续补充检索或查阅全文确认。

### 参考文献

[KB-1] 章节 5 EXPERIMENTAL METHODOLOGIES，页码 5

[KB-2] 章节 4.1 Methodology，页码 6

[KB-3] 章节 4 Experimental Setup，页码 4

[KB-4] 章节 B Alternative Similarity Functions & Triplet Loss，页码 12

[KB-5] 章节 4.2.2 The Effectiveness of The Three Training Strategies in RocketQA，页码 8

[KB-6] 章节 4.2.1 Dense Passage Retrieval，页码 8

# 取证归纳：四篇论文的实验环境（操作系统与数据库软件）

## 一、对本步骤材料的核对结论

本步骤提供的原文片段全部来自 **RocketQA 论文的 Introduction 章节**（[KB-1] 至 [KB-5]），内容涉及研究动机、三项技术贡献（cross-batch negatives、denoised hard negatives、data augmentation）以及实验效果概述。经逐条核对：

- [KB-1] 仅说明"开展了大量实验以检验三种策略的有效性"，未提及任何实验环境信息 [KB-1]。
- [KB-2][KB-5] 描述三项技术贡献，与实验环境无关 [KB-2][KB-5]。
- [KB-3][KB-4] 说明端到端 QA 性能提升及在 MSMARCO、Natural Questions 数据集上的表现，仅涉及**数据集名称**，未涉及操作系统或数据库软件 [KB-3][KB-4]。

**结论：本步骤材料中没有任何一条原文提及操作系统（OS）、数据库软件、硬件环境或实验设置细节。因此，就 RocketQA 论文而言，"实验使用的操作系统和数据库软件"在本步骤材料范围内属于"论文中未报告"（更准确地说，是所提供的片段未覆盖）。**

## 二、对研究任务整体的说明

研究任务要求回答"四篇论文（ANCE、ColBERT、RocketQA、第四篇）实验使用的操作系统和数据库软件"。本步骤的材料仅覆盖 RocketQA 一篇，且只覆盖其 Introduction 章节，存在以下关键缺口：

1. **四篇论文的确定与各自实验章节定位**：本步骤材料未提供 ANCE、ColBERT 及"第四篇论文"的任何原文，无法据此确认四篇论文的完整清单，也无法定位各论文实验章节的具体位置。此部分应属上一研究步骤（"四篇论文分别是哪四篇，各自的实验章节在何处"）的产出，本步骤材料未包含该产出。
2. **实验环境取证（OS / 数据库软件 / 硬件）**：本步骤材料未包含任何实验设置（Experimental Setup）、硬件环境（hardware environment）或实现细节（Implementation Details）章节的原文，无法对任何一篇论文给出经原文支撑的操作系统或数据库软件结论。

## 三、可提供的受限归纳

- 就 RocketQA 而言，其 Introduction 明确实验覆盖 **MSMARCO 与 Natural Questions** 两个数据集 [KB-4]，但**操作系统与数据库软件：论文中未报告**（在所提供的 [KB-1]–[KB-5] 片段范围内）。
- ANCE、ColBERT 及第四篇论文：**本步骤材料未提供相关原文，无法归纳，论文中未报告（就本材料而言）**。

## 四、建议的后续取证方向

为完成研究任务，需补充检索以下论文的 **Experimental Setup / Implementation Details / Experimental Environment** 章节原文：

- **ANCE**（Xiong et al., 2020）：关注其训练硬件（GPU 型号/数量）、框架（如 PyTorch/TensorFlow）、是否使用 FAISS 等检索索引工具。
- **ColBERT**（Khattab & Zaharia, 2020）：关注其实现框架与索引/存储组件。
- **RocketQA**（Qu et al., 2021）：关注其基于 PaddlePaddle 的实现及训练环境描述。
- **第四篇论文**：需先由前述步骤确定篇目，再定位其实验章节。

> 说明：以上方向为取证建议，不构成已校验结论；在获得对应章节原文前，不应就操作系统或数据库软件作出任何断言。

## 参考文献

无（本步骤未使用外部检索来源，全部归纳基于用户专题论文库原文片段 [KB-1]–[KB-5]）。

### 2. 每篇论文实验所用操作系统是什么？（COMPLETED）

# 步骤取证归纳：每篇论文实验所用操作系统

## 问题陈述

本步骤要回答：用户专题论文库中的四篇论文，其**实验所用操作系统**分别是什么。

## 研究发现

### 关于所提供原文片段中是否报告操作系统

经逐条核查所提供的原文片段，**没有任何一条片段明确报告实验所用操作系统**。具体如下：

- [KB-1] 仅报告硬件配置：使用一块 Tesla V100 GPU（32 GiB 显存），服务器配有两颗 Intel Xeon Gold 6132 CPU（每颗 14 物理核 / 24 超线程）与 469 GiB 内存；CPU 检索与索引实验使用另一台同 CPU/内存规格但挂载四块 Titan V GPU（各 12 GiB）的服务器。该片段未提及操作系统。
- [KB-2] 仅说明"本节描述实验所用数据与基本设置"，未涉及操作系统。[KB-2]
- [KB-3] 仅报告数据集（MS MARCO 与 TREC-CAR）与评测指标。[KB-3]
- [KB-4] 仅说明基线设置精度差异源于更优超参数，并指向代码发布。[KB-4]
- [KB-5] 仅列出研究问题 RQ4（索引相关的离线计算与内存开销）。[KB-5]
- [KB-6] 仅说明在 NQ 数据集上检验三种训练策略的有效性。[KB-6]

## 结论

**基于所提供的 [KB-1] 至 [KB-6] 原文片段，四篇论文实验所用的操作系统均"论文中未报告"。** 现有片段最多只提供了硬件配置信息（[KB-1]），未涉及操作系统层面的描述。若需确认操作系统，需补充各论文中 Implementation / Experimental Setup 章节的完整原文（例如实现环境描述部分），当前材料不足以支撑该结论，不作猜测。

## 参考文献

- [KB-1] 章节 4.1 Methodology，页码 6（硬件与时间测量）
- [KB-2] 章节 4 Experimental Setup，页码 4
- [KB-3] 章节 4.1 Methodology，页码 6（数据集与指标）
- [KB-4] 章节 B Alternative Similarity Functions & Triplet Loss，页码 12
- [KB-5] 章节 4 Experimental Evaluation，页码 6
- [KB-6] 章节 A.2 The Effectiveness of The Three Training Strategies on NQ，页码 13

## 问题陈述

本步骤要求：从给定论文原文片段中，补充取证每篇论文实验所使用的**操作系统（Operating System）**，并连同数据库软件（Database Software）一并说明具体名称（如 Linux Ubuntu、MySQL 等）。

## 研究取证归纳

### 1. 关于"数据集"的原文证据（非操作系统信息）

所给材料中，[KB-1]、[KB-2]、[KB-3]、[KB-4] 全部描述的是**问答数据集与消融实验设置**，并未涉及运行环境：

- [KB-1] 说明沿用前人工作（Lee et al., 2019）的五个 QA 数据集及训练/开发/测试划分方法："We use the same five QA datasets and training/dev/testing splitting method as in previous work (Lee et al., 2019)."
- [KB-2] 是表 1 的说明，统计各 QA 数据集的问题数量及过滤后实际用于训练 DPR 的问题数："Table 1: Number of questions in each QA dataset..."
- [KB-3] 是图 5 的说明，报告 MS MARCO（Dev）上的消融结果及所用 BERT 层数："Figure 5: Ablation results on MS MARCO (Dev). Between brackets is the number of BERT layers used in each model."
- [KB-4] 说明实验在两个 QA 基准（MSMARCO Passage Ranking 与 Natural Questions）上进行："We conduct the experiments on two popular QA benchmarks: MSMARCO Passage Ranking (Nguyen et al., 2016) and Natural Questions (NQ) (Kwiatkowski et al., 2019)."

### 2. 关于操作系统的结论

上述四段原文 [KB-1]～[KB-4] 均**未提及任何操作系统名称**（如 Linux、Ubuntu、Windows、macOS 等），也未提及硬件、CUDA 版本、容器环境等运行配置。

因此，就本步骤所依据的材料而言：**每篇论文实验所用操作系统——论文中未报告。**

### 3. 关于数据库软件的结论

同样，[KB-1]～[KB-4] 仅涉及 QA 数据集（NQ、TriviaQA、WebQuestions、CuratedTREC、SQuAD、MSMARCO 等）与检索/重排实验设置，**未提及任何数据库软件**（如 MySQL、PostgreSQL、MongoDB 等），也未提及向量检索库或存储引擎。

因此：**数据库软件——论文中未报告。**

### 4. 材料局限说明

- 所给片段集中于"4.1.1 Datasets""4.2 Question Answering Datasets""4.4 Ablation Studies"等章节，属于数据与实验设计部分；操作系统与数据库软件通常出现在"实验设置/Implementation Details"或"实验环境"章节，本批材料未覆盖。
- 章节背景中的 MSMARCO、NQ 等属于**数据集名称**，不能等同于数据库软件；BM25、BERT、ColBERT 属于检索/模型方法，也不能等同于运行环境，均不得据此推断操作系统或数据库。

## 结论

基于所提供的 [KB-1]～[KB-4] 原文，**无法确定**这四篇论文实验所使用的操作系统与数据库软件的具体名称——**论文中未报告**。若要完成该取证，需要补充各论文"实验设置/实现细节"章节的原文片段（通常包含 OS、GPU、框架版本、依赖库等信息），方可据实归纳，当前材料不足以支撑任何具体命名（如 Linux Ubuntu、MySQL 等），故不作猜测。

## 参考文献

[KB-1] 用户专题论文库片段：章节 4.2 Question Answering Datasets，页码 4

[KB-2] 用户专题论文库片段：章节 4.2 Question Answering Datasets，页码 4（表 1 说明）

[KB-3] 用户专题论文库片段：章节 4.4 Ablation Studies，页码 9（图 5 说明）

[KB-4] 用户专题论文库片段：章节 4.1.1 Datasets，页码 6

### 3. 每篇论文实验所用数据库软件是什么？（COMPLETED）

## 问题陈述

本步骤要回答：这四篇论文的实验所使用的**数据库软件**是什么？

## 研究发现的归纳

### 关于"数据库软件"的取证结论

在所提供的原文片段中，**没有任何一篇论文明确报告所使用的"数据库软件"（如 MySQL、PostgreSQL、MongoDB 等）**。可检索到的相关内容仅为**数据集（datasets）**和**检索索引/相似度计算库（faiss）**，这些均不属于通常意义上的"数据库软件"，需加以区分说明：

1. **数据集（非数据库软件）**：论文实验使用的是 MS MARCO 与 TREC-CAR 两个检索数据集，而非数据库管理系统。原文："we conduct our experiments on the MS MARCO Ranking [24] (henceforth, MS MARCO) and TREC Complex Answer Retrieval (TREC-CAR) [6] datasets" [KB-3]。RocketQA 相关实验则在 MSMARCO 与 NQ 数据集上进行 [KB-5][KB-6]。

2. **向量索引库 faiss（索引/检索工具，非数据库软件）**：章节背景提到"For our faiss index, we set the number of partitions to P =2,000"，说明实验使用了 faiss 作为向量相似度检索索引。但请注意，该信息来自**章节背景**，并非上方已校验的 [KB-n] 原文片段，因此不能作为已核验结论引用；就所提供的 [KB-n] 材料而言，faiss 未被直接列为实验软件项。

3. **编程与深度学习框架（非数据库软件）**：章节背景提到"implemented using Python 3 and PyTorch 1 … transformers 5 library"，同样属于背景信息，且并非数据库软件。

### 材料不足之处

- 四篇论文中**实验使用的数据库软件**：**论文中未报告**。
- 若将"数据存储/检索基础设施"宽泛理解，材料仅能支持"使用 MS MARCO、TREC-CAR、NQ 等数据集"以及（背景中）"faiss 向量索引"，但这些都不是数据库软件，且 faiss 信息未绑定到已校验的 [KB-n] 原文。

## 结论

基于所提供的原文片段，**无法确定这四篇论文实验所使用的数据库软件**——论文中未报告任何数据库软件（如关系型或 NoSQL 数据库）。可确认的仅是实验所用**数据集**（MS MARCO、TREC-CAR [KB-3]；MSMARCO、NQ [KB-5][KB-6]）。若要回答"数据库软件"，需补充论文中实验环境/实现细节部分的原文，当前材料不足以支撑该结论。

## 参考文献

[KB-1] 论文库原文片段，章节 4.1 Methodology，页码 6

[KB-2] 论文库原文片段，章节 4 Experimental Setup，页码 4

[KB-3] 论文库原文片段，章节 4.1.1 Datasets & Metrics，页码 6

[KB-4] 论文库原文片段，章节 B Alternative Similarity Functions & Triplet Loss，页码 12

[KB-5] 论文库原文片段，章节 A.2 The Effectiveness of The Three Training Strategies on NQ，页码 13

[KB-6] 论文库原文片段，章节 4.2.2 The Effectiveness of The Three Training Strategies in RocketQA，页码 8

## 问题陈述

本步骤要回答：这四篇论文在实验中使用的**数据库软件**（如 MySQL、PostgreSQL、MongoDB 等）是什么，或论文是否明确声明未使用数据库软件。

## 取证归纳

### 一、材料中实际报告的实验环境要素

从用户专题论文库的原文片段看，实验环境与实现细节部分（[KB-1]、[KB-3]）主要报告的是模型、库、硬件与检索基础设施，**并未出现任何数据库管理系统（DBMS）的名称**。具体可提取的环境信息如下：

- 实验设置概述见 [KB-1]："This section describes our experimental setups. More details can be found in Appendix A.1 and A.2."（仅指向附录，未列举软件栈）
- 开源实现依赖：[KB-3] 指出 "All the DPR related experimental settings, baseline systems, and DPR Reader are based on their open source libarary"，RAG-Token 读者使用 "their open-source release in huggingface"，RAG-Seq 因不稳定而改用 RAG-Token，且 "RAG only releases the NQ models thus we use DPR reader on TriviaQA"。
- 检索基础设施：[KB-3] 说明向 RAG-Token 输入 "top 20 passages from ANCE"，向 DPR 的 BERT Reader 输入 "top 100 passages"。

以上均属于**检索库、模型库与检索流程**层面的描述，不涉及关系型或文档型数据库软件。

### 二、数据集层面的说明（非数据库）

[KB-2] 与 [KB-4] 描述的是问答数据集（NQ、TriviaQA、WebQuestions、CuratedTREC、SQuAD）及其划分方式："We use the same five QA datasets and training/dev/testing splitting method as in previous work (Lee et al., 2019)"，并说明训练样本的过滤与正例段落选择方法。这些是**数据集与数据准备**内容，并非数据库管理系统的使用说明。

### 三、结论

基于所提供的 [KB-1] 至 [KB-4] 原文片段：

1. **四篇论文均未报告所使用的数据库软件**（如 MySQL、PostgreSQL、MongoDB 等）。材料中没有任何一处提及具体 DBMS 名称。
2. **也未找到"明确声明未使用数据库软件"的表述**。论文仅描述了基于开源库（DPR 开源库、HuggingFace 的 RAG-Token）与检索流程（ANCE 召回 top-20/top-100 段落）的实现方式，未就数据库使用与否作出正面声明。

因此，就本步骤而言，可归纳为：**论文中未报告实验所用的数据库软件，亦未明确声明是否使用数据库软件**。如需进一步确认，应查阅各论文完整的"Implementation Details"段落及附录 A.2（[KB-1] 所指），但当前片段中不含该信息，故不作推测。

## 参考文献

[KB-1] 用户专题论文库片段，章节 5 EXPERIMENTAL METHODOLOGIES，页码 5。

[KB-2] 用户专题论文库片段，章节 4.2 Question Answering Datasets，页码 4（Table 1 说明）。

[KB-3] 用户专题论文库片段，章节 A.1 MORE EXPERIMENTAL DETAILS，页码 12。

[KB-4] 用户专题论文库片段，章节 4.2 Question Answering Datasets，页码 4。

### 4. 四篇论文在操作系统与数据库信息上的披露完整度如何？（COMPLETED）

## 问题重述

本步骤要回答的问题是：这四篇论文在“操作系统”与“数据库软件”这两类实验环境信息上的披露完整度如何。判断依据仅限用户专题论文库中提供的原文片段 [KB-1] 至 [KB-6]。

## 研究发现

### 一、关于操作系统（OS）的披露

在所提供的全部原文片段中，**没有任何一处提及实验所用的操作系统**。

- [KB-1] 在描述硬件与计时环境时，只列出了 GPU（Tesla V100、Titan V）、CPU（Intel Xeon Gold 6132）、内存（469 GiBs RAM）等硬件规格，未说明服务器运行的操作系统 [KB-1]。
- [KB-3] 仅笼统说明“describe the data we used for experiments and the basic setup”，未展开系统层面信息 [KB-3]。
- 其余片段（[KB-2]、[KB-4]、[KB-5]、[KB-6]）分别涉及相似度函数与超参数、端到端 QA 结果、数据集与评测指标、TREC DL 评测流程，均不涉及操作系统 [KB-2][KB-4][KB-5][KB-6]。

**结论：操作系统信息——论文中未报告。**

### 二、关于数据库软件的披露

在所提供的原文片段中，**同样没有任何一处提及数据库软件**（如 MySQL、PostgreSQL、MongoDB 等）。

- [KB-5] 提到实验使用 MS MARCO 与 TREC-CAR 两个“数据集”，但这是数据集而非数据库管理系统 [KB-5]。
- 章节背景中虽出现“faiss index”等检索索引相关表述，但该内容属于章节背景，未在 [KB-n] 原文片段中被校验引用，不能作为“数据库软件”已披露的依据；且 faiss 属于向量检索库而非数据库软件。
- 其余片段均不涉及数据库软件 [KB-1][KB-2][KB-3][KB-4][KB-6]。

**结论：数据库软件信息——论文中未报告。**

### 三、披露完整度评估

| 信息类别 | 是否披露 | 依据 |
|---|---|---|
| 操作系统 | 未披露 | 全部 [KB-n] 片段均无相关表述 |
| 数据库软件 | 未披露 | 全部 [KB-n] 片段均无相关表述 |
| 硬件规格（对照） | 部分披露 | [KB-1] 详列 GPU/CPU/内存 |

从可比对的维度看，论文对**硬件规格**的披露相对具体（[KB-1] 明确给出 GPU 型号、CPU 型号与核数、内存容量），但对**操作系统**与**数据库软件**这两类软件环境信息则完全缺失 [KB-1]。

## 结论

基于所提供的原文片段，四篇论文在“操作系统”与“数据库软件”两项上的披露完整度均为**零**：两类信息在 [KB-1]–[KB-6] 中均未被报告。相对而言，论文仅在硬件层面（GPU、CPU、内存）有较明确的披露 [KB-1]，但软件运行环境（操作系统、数据库软件）在现有材料中无法获知，需明确标注为“论文中未报告”，不得据硬件信息推测其操作系统或数据库选型。

## 参考文献

[KB-1] 4.1 Methodology, p.6（硬件与计时测量）

[KB-2] B Alternative Similarity Functions & Triplet Loss, p.12

[KB-3] 4 Experimental Setup, p.4

[KB-4] 6.2 Results, p.8

[KB-5] 4.1 Methodology, p.6（数据集与指标）

[KB-6] A.1 MORE EXPERIMENTAL DETAILS, p.12

## 问题陈述

本步骤需补充取证：四篇论文实验环境中所用的**操作系统**与**数据库软件**的具体名称、版本与配置，并评估其在操作系统与数据库信息上的披露完整度。

## 研究取证结果

### 一、操作系统（OS）

**论文中未报告。** 所提供的四段原文（[KB-1]–[KB-4]）均未提及任何操作系统名称或版本。

- [KB-1] 仅笼统说明"本节描述实验设置，更多细节见附录 A.1 与 A.2"（"This section describes our experimental setups. More details can be found in Appendix A.1 and A.2."），并未给出 OS 信息。
- 章节背景中虽出现硬件描述（如"a cloud VM with Intel(R) Xeon(R) Platinum 8168 CPU and 650GB of RAM"、"single 32GB V100 GPU"、"eight 32GB GPUs"），但**未标注 [KB-n]**，且其内容本身也只涉及 CPU/GPU/内存，不含操作系统。故不能据此推断 OS。

**结论：操作系统具体名称与版本——论文中未报告。**

### 二、数据库软件

需区分"数据库管理系统（DBMS）"与"向量检索/索引库"两类：

1. **传统数据库软件（如 MySQL、PostgreSQL 等）：论文中未报告。** 四段原文均无任何 DBMS 名称或版本。

2. **检索索引库（FAISS）**：这是原文中唯一明确出现的"库/软件"类组件，但其属于向量相似度检索库，并非数据库软件：
   - [KB-2] 明确给出 FAISS 配置："FAISS configuration: we used HNSW index type on CPU, neighbors to store per node = 512, construction time search depth = 200, search depth = 128."（即索引类型 HNSW、运行于 CPU、每节点邻居数 512、构建时搜索深度 200、搜索深度 128）。
   - 章节背景中还提到"Faiss IndexFlatIP Index"与"ANN search"，但该句未标注 [KB-n]，不能作为已校验结论引用。

**结论：数据库软件具体名称与版本——论文中未报告；仅 [KB-2] 报告了 FAISS 的索引配置参数（HNSW、CPU、邻居数 512、构建深度 200、搜索深度 128），但未给出 FAISS 的版本号。**

### 三、其他环境线索（非 OS/数据库，仅作背景，且须绑定原文）

- [KB-3] 说明 OpenQA 实验依赖开源库与 HuggingFace 发布版本（DPR 开源库、RAG-Token 的 HuggingFace 版本），但未提 OS 或数据库。
- [KB-4] 涉及索引吞吐与空间占用（ColBERT 约三小时索引 MS MARCO），属性能指标，非 OS/数据库信息。

## 披露完整度评估

| 维度 | 是否披露 | 依据 |
|---|---|---|
| 操作系统名称 | 否 | [KB-1]–[KB-4] 均未提及 → 论文中未报告 |
| 操作系统版本 | 否 | 同上 → 论文中未报告 |
| 数据库软件名称 | 否（仅 FAISS 检索库） | [KB-2] 仅给 FAISS 配置，无 DBMS → 数据库软件论文中未报告 |
| 数据库/索引版本号 | 否 | [KB-2] 无版本号 → 论文中未报告 |
| 索引配置参数 | 部分披露 | [KB-2] HNSW、CPU、邻居 512、构建深度 200、搜索深度 128 |

**总体判断：** 就本步骤所给材料而言，四篇论文在**操作系统**与**数据库软件**两项上的披露完整度**极低**——操作系统完全未报告，数据库软件亦未报告；仅 [KB-2] 披露了 FAISS 这一向量索引库的部分配置参数，且无版本信息。若需完整回答"操作系统与数据库软件的名称、版本、配置"，现有 [KB-n] 原文**不足以支撑**，相关项应标注为"论文中未报告"，不得推测。

## 结论

基于所提供的 [KB-1]–[KB-4] 原文：四篇论文均**未报告操作系统**（名称与版本皆缺），亦**未报告数据库软件**（名称与版本皆缺）；唯一可确认的软件类信息是 [KB-2] 中 FAISS 的索引配置（HNSW 索引、CPU 运行、每节点邻居 512、构建搜索深度 200、搜索深度 128），但未给出 FAISS 版本号。因此，就"操作系统与数据库软件"的披露完整度而言，材料显示为**基本未披露**，其余细节需回到论文附录 A.1/A.2 全文进一步核查，当前材料无法补足。

## 参考文献

[KB-1] 5 EXPERIMENTAL METHODOLOGIES, p.5

[KB-2] 6.1 End-to-end QA System, p.7

[KB-3] A.1 MORE EXPERIMENTAL DETAILS, p.12

[KB-4] 4.5 IndexingThroughput & Footprint, p.9

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
- 2010.08191（ACCEPTED）
- 检出 2 条存在反向支持关系的来源，需要单独核对条件和适用范围。
- 有 4 条来源仍待引用复核，不能作为无条件结论。

## 覆盖度与缺口

- 已检查来源：14 条；已完成步骤：4 / 4。

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
- 2010.08191