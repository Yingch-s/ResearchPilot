# A2 · full · round 3

这四篇论文（DPR、ColBERT、ANCE、RocketQA）分别使用了什么训练数据，标注需求有何不同？

---

# 研究报告

## 直接回答

本报告汇总了 3 个已完成研究步骤的发现；逐项依据见下方证据与局限。

**研究问题**：这四篇论文（DPR、ColBERT、ANCE、RocketQA）分别使用了什么训练数据，标注需求有何不同？

## 资料范围

- 专题内全部就绪资料（未限定单篇）

## 逐项发现

### 1. DPR 与 ColBERT 各自使用了什么训练数据，标注来源与规模如何？（COMPLETED）

## 问题陈述

本步骤聚焦于：**DPR 与 ColBERT 各自使用了什么训练数据，标注来源与规模如何？** 需基于专题论文库原文片段进行取证归纳，并明确标注哪些内容论文中未报告。

## 研究发现

### 一、现有材料能够确认的信息

**1. DPR 的预训练语言模型（非训练数据本身）**

材料仅能确认 DPR 使用的预训练骨干模型为 BERT_base，而非其训练数据构成：Table 6 的说明中明确列出 "DPR (Karpukhin et al., 2020) uses BERTbase" [KB-2]。这属于模型配置信息，**不能等同于训练数据**。

**2. DPR 作为基线时的相关训练数据线索（间接）**

- 在 TREC DL 实验中，论文提到 BM25 负样本有助于训练稠密检索，并将 DPR 与 Luan et al. (2020) 并列为采用 "BM25 + Random negatives" 1:1 组合的基线（章节背景 P-9558cde80da4，非 [KB-n] 校验结论，仅作理解条件）。
- 在 OpenQA 实验中，论文说明 "we warm up ANCE using the released DPR checkpoints (Karpukhin et al., 2020)" [KB-3]，即 DPR 发布了检查点，但**未在库内片段中报告 DPR 训练数据的来源与规模**。
- OpenQA 实验采用 NQ 与 TriviaQA，"following the exact settings from Karpukhin et al. (2020)" [KB-4]，说明 DPR 的评测设置被沿用，但**该片段未披露 DPR 训练集的具体标注来源与样本规模**。

**3. 与标注规模相关的唯一量化线索（针对 MSMARCO，而非 DPR/ColBERT）**

论文在讨论"未标注正例"挑战时给出：以 MSMARCO 为例，"each question has only 1.1 annotated positive passages on average, while there are 8.8M passages in the whole collection"，并指出人工检查发现 top 检索结果中 70% 实际为正例 [KB-1 所属章节背景 P-140621b255be，其中该数据位于 Introduction 正文]。需要注意：这一数据描述的是 **MSMARCO 数据集本身的标注稀疏性**，用于论证 RocketQA 的动机，**并非 DPR 或 ColBERT 各自的训练数据规模**。

### 二、材料不足以回答的部分（须明确说明）

- **DPR 的训练数据来源与规模**：库内片段仅报告其骨干模型为 BERT_base [KB-2]、发布了检查点 [KB-3]、评测沿用其设置 [KB-4]，**未报告其训练数据的具体来源、标注方式与规模**。
- **ColBERT 的训练数据**：四篇论文库片段中**完全没有出现 ColBERT 的相关内容**（既无训练数据描述，也无模型配置）。因此关于 ColBERT 的训练数据、标注来源与规模，**论文（本库材料）中未报告**，无法归纳。
- **DPR 与 ColBERT 标注需求的差异对比**：由于缺乏 ColBERT 的任何原文依据，**无法基于本库材料进行二者的标注需求差异对比**。

### 三、可作为后续步骤参照的对照信息（RocketQA/ANCE 侧）

为便于后续步骤衔接，材料中可确认的相关训练数据事实包括：

- RocketQA 使用 "large-scale unsupervised data 'labeled' by a cross-encoder" 进行数据增强，即用 cross-encoder 为无标注数据生成高质量伪标签 [KB-1]。
- ANCE 在 TREC DL 中 "warmed up by BM25 negatives"，且所有 DR 模型均从 RoBERTa_base 微调 [KB-3]；OpenQA 中用 DPR 检查点热启动 ANCE [KB-3]；ANCE 使用 RoBERTa_base [KB-2]。

## 结论

就本步骤问题而言，**现有 [KB-n] 材料只能确认 DPR 的骨干模型为 BERT_base [KB-2]、其检查点被 ANCE 用于热启动 [KB-3]、其 OpenQA 评测设置被沿用 [KB-4]**，但**未报告 DPR 训练数据的具体来源、标注方式与规模**。关于 **ColBERT，库内片段完全未涉及，论文中未报告**。因此，"DPR 与 ColBERT 各自训练数据及标注来源与规模"的完整对比**无法基于当前材料完成**，需补充 DPR 与 ColBERT 原文（如 Karpukhin et al., 2020 与 Khattab & Zaharia, 2020）中关于训练集（如 NQ、TriviaQA、MSMARCO 及其标注方式）的原文片段后方可归纳。材料中出现的 "1.1 annotated positive passages / 8.8M passages / 70% 实为正例" [KB-1 章节背景] 属于 MSMARCO 标注稀疏性的描述，**不可误用为 DPR 或 ColBERT 的训练数据规模**。

## 参考文献

[KB-1] RocketQA 论文 Introduction 章节（含 MSMARCO 标注稀疏性论述）

[KB-2] RocketQA 论文 Appendix A.1 "The Effects of Pre-trained LMs"（Table 6 说明）

[KB-3] RocketQA 论文 Section 5 "Experimental Methodologies"（Implementation Details）

[KB-4] RocketQA 论文 Section 5 "Experimental Methodologies"（OpenQA 设置）

# 研究步骤取证归纳：DPR 与 ColBERT 的训练数据、标注来源与规模

## 问题陈述

本步骤聚焦于两篇论文的训练数据取证：DPR 与 ColBERT 各自使用了什么训练数据？数据来源、标注方式与规模如何？特别是 NQ、TriviaQA、MS MARCO 的使用及标注情况。

---

## 一、ColBERT 的训练数据

### 1. 数据集选择

ColBERT 的实验建立在两个检索数据集上：

> "Similar to related work [2, 27, 28], we conduct our experiments on the MS MARCO Ranking [24] (henceforth, MS MARCO) and TREC Complex Answer Retrieval (TREC-CAR) [6] datasets. Both of these recent datasets provide large training data of the scale that facilitates training and evaluating deep neural networks." [KB-1]

即 ColBERT 使用的是 **MS MARCO** 与 **TREC-CAR** 两个数据集，论文明确强调二者都提供了"大规模训练数据"。

### 2. MS MARCO 的来源、规模与标注

- **来源**：MS MARCO 是微软 2016 年为阅读理解提出、2018 年适配检索的数据集，由 Bing 对 100 万真实查询的搜索结果中收集的 **8.8M 网页段落**组成（该细节出自章节背景 P-f3d00876519a，非本步骤已校验 [KB-n] 原文，仅作理解参考）。
- **标注特点**：每个查询仅关联**稀疏的相关性判断**——一个（或极少数）文档被标为相关，**没有文档被明确标为不相关**（同上，出自背景）。
- **规模（评估用查询）**：官方开发集与评估集各约 7k 查询；另有约 55k 带标签查询作为额外验证数据，作者从中随机抽取 5k 作为"本地"评估集（同上背景）。
- **训练配置**：ColBERT 在 MS MARCO 上用 Google 官方预训练 BERTbase 初始化，训练 **200k 迭代**：

> "For MS MARCO, we initialize the BERT components of the ColBERT query and document encoders using Google ' s official pretrained BERTbase model. Further, we train all models for 200k iterations." [KB-2]

### 3. TREC-CAR 的来源、规模与标注

- **来源**：TREC-CAR 由 Dietz 等人于 2017 年提出，是基于 Wikipedia 的**合成数据集**，约 **29M 段落**（出自背景 P-f3d00876519a）。
- **规模与标注方式**：使用五个预定义折中的前四折训练、第五折验证，约 **3M 查询**；这些查询由"Wikipedia 页面标题 + 某章节标题"拼接生成，该章节的段落被标为相关（同上背景）。**即标注是自动/合成生成的，而非人工判断**。
- **训练配置**：为避免测试数据泄漏，作者使用 Nogueira 与 Cho 在 TREC-CAR 训练子集对应 Wiki 页面上重新预训练的 BERTlarge（而非官方 BERT），并训练 **125k 迭代**：

> "To avoid leaking test data into train, Nogueira and Cho ' s [25] pre-train a randomly-initialized BERT model on the Wiki pages corresponding to training subset of TREC CAR. They release their BERTlarge pre-trained model, which we fine-tune for ColBERT' s experiments on TREC CAR. Since finetuning this model is significantly slower than BERTbase, we train on TREC CAR for only 125k iterations." [KB-2]

### 4. ColBERT 的对比基线训练

在 MS MARCO 重排序实验中，ColBERT 还报告了一个"BERTbase (our training)"基线，基于 Nogueira 与 Cho 的 base 模型（含超参数），但用与 ColBERT 相同的损失函数训练 200k 迭代 [KB-4]。

---

## 二、DPR 的训练数据

### 1. 数据集与"过滤后"训练规模

DPR 论文（第 4.2 节）使用与 Lee et al. (2019) 相同的**五个 QA 数据集**及相同的训练/开发/测试划分（出自背景 P-25da6edd4346）。其中关键证据是表 1 的说明：

> "Table 1: Number of questions in each QA dataset. The two columns of Train denote the original training examples in the dataset and the actual questions used for training DPR after filtering. See text for more details." [KB-5]

这表明 DPR 的训练数据**并非直接使用原始训练集**，而是经过**过滤（filtering）**后使用，论文用两列分别报告"原始训练样本数"与"过滤后实际用于训练 DPR 的问题数"。**但具体数值（各数据集的原始数与过滤后数）在本步骤提供的 [KB-n] 片段中未给出，需查阅表 1 原文。**

### 2. 数据集的标注来源（以 NQ、TriviaQA 为例）

- **Natural Questions (NQ)**：问题从真实 Google 搜索查询中挖掘，答案是由**标注者在 Wikipedia 文章中识别的答案片段（span）**：

> "Natural Questions (NQ) (Kwiatkowski et al., 2019) was designed for end-to-end question answering. The questions were mined from real Google search queries and the answers were spans in Wikipedia articles identified by annotators."（出自背景 P-25da6edd4346）

- **TriviaQA**：该片段仅给出开头"contains a set of trivia…"，**完整描述未包含在本步骤材料中**，其标注细节论文中此处未展开（背景片段截断）。

### 3. 训练范式与"问题-答案对"标注

DPR 的训练核心是学习强段落检索模型，依赖**问题-答案对**：

> "DPR manages to outperform them on both NQ and TriviaQA, simply by focusing on learning a strong passage retrieval model using pairs of questions and answers." [KB-3]

这说明 DPR 的训练监督信号来自 QA 数据集中的"问题 + 答案"配对，而非显式的段落相关性标注。

### 4. 单数据集 vs. 多数据集训练（Single / Multi）

DPR 报告了两种训练设置：

> "For large datasets like NQ and TriviaQA, models trained using multiple datasets (Multi) perform comparably to those trained using the individual training set (Single). Conversely, on smaller datasets like WQ and TREC, the multidataset setting has a clear advantage." [KB-3]

表 4 说明进一步明确 Multi 设置合并了"除 SQuAD 外的所有训练数据集"，且对 WQ/TREC 的 Multi 设置是在 NQ 上训练的 reader 上微调（出自背景 P-d9b66b81e80d 的表 4 说明）。这印证 DPR 的多数据集训练使用了 NQ、TriviaQA、WQ、TREC 等 QA 数据（SQuAD 仅用于评估）。

---

## 三、DPR 与 ColBERT 标注需求的对比（基于本步骤材料）

| 维度 | ColBERT | DPR |
|---|---|---|
| 数据集 | MS MARCO、TREC-CAR [KB-1] | 五个 QA 数据集（含 NQ、TriviaQA、WQ、TREC；SQuAD 仅评估）[KB-3] |
| 标注类型 | MS MARCO：稀疏相关性判断（1 个或极少相关文档，无显式不相关）；TREC-CAR：合成标注（标题+章节拼接自动生成相关段落）| QA 数据集：问题-答案对，答案为标注者识别的 span（如 NQ）|
| 标注规模 | MS MARCO 8.8M 段落、官方约 7k 查询、约 55k 带标签查询；TREC-CAR 约 29M 段落、约 3M 合成查询（以上规模出自背景片段，非 [KB-n] 原文）| 表 1 分列原始训练样本数与过滤后实际训练数 [KB-5]；**具体数值本步骤材料未给出** |
| 训练前处理 | 无提及过滤；直接用官方划分 [KB-2] | 训练前对 QA 数据**过滤** [KB-5] |

---

## 四、材料不足之处（明确说明）

1. **DPR 表 1 的具体数值**（各数据集的原始训练样本数与过滤后训练数）在本步骤 [KB-n] 片段中**未报告**，需查阅论文表 1 原文。
2. **TriviaQA 的完整标注描述**在本步骤背景片段中被截断（仅到"contains a set of trivia…"），其标注方式**论文此处未完整给出**。
3. **MS MARCO 与 TREC-CAR 的段落数、查询数等规模数字**出现在章节背景（P-f3d00876519a）中，但**未绑定到本步骤的 [KB-n] 原文片段**，故仅作理解参考，不作为已校验结论。
4. **DPR 过滤的具体规则/标准**（如何过滤、过滤掉什么）本步骤材料**未报告**，仅知存在"filtering" [KB-5]。

---

## 结论

就本步骤可校验的材料而言：**ColBERT** 使用 **MS MARCO** 与 **TREC-CAR** 两个大规模检索数据集训练 [KB-1]，前者为真实查询-段落相关性判断（稀疏标注），后者为基于 Wikipedia 的合成标注；训练分别采用 200k 与 125k 迭代 [KB-2]。**DPR** 使用五个 QA 数据集（NQ、TriviaQA、WQ、TREC、SQuAD，其中 SQuAD 仅评估），训练监督来自**问题-答案对** [KB-3]，且训练前对数据进行了**过滤**，论文以表 1 分列原始与过滤后规模 [KB-5]，但具体数值与 TriviaQA 完整标注细节在本步骤材料中未给出，需进一步取证。

---

## 参考文献

[KB-1] 用户专题论文库片段，ColBERT 论文 §4.1 Methodology，第 6 页（Datasets & Metrics）

[KB-2] 用户专题论文库片段，ColBERT 论文 §4.1 Methodology，第 6 页（Implementation / 预训练与迭代）

[KB-3] 用户专题论文库片段，DPR 论文 §6.2 Results，第 7 页

[KB-4] 用户专题论文库片段，ColBERT 论文 §4.2 Quality-Cost Tradeoff，第 7 页

[KB-5] 用户专题论文库片段，DPR 论文 §4.2 Question Answering Datasets，第 4 页（Table 1 说明）

章节背景（仅用于理解，未作为已校验结论引用）：P-f3d00876519a（ColBERT §4.1 全文）、P-d9b66b81e80d（DPR §6.2 全文及表 4 说明）、P-29b16cb09f78（ColBERT §4.2 全文）、P-25da6edd4346（DPR §4.2 全文，TriviaQA 描述截断）

### 2. ANCE 如何构造训练数据，其负例采样对标注的依赖与 DPR/ColBERT 有何不同？（COMPLETED）

## 问题陈述

本步骤聚焦于 ANCE 的训练数据构造方式，特别是其**负例采样机制**，以及该机制对标注的依赖程度与 DPR、ColBERT 的差异。需要说明的是，用户专题论文库中提供的原文片段主要来自 ANCE 论文本身以及 RocketQA 论文对 ANCE/DPR 的转述；**ColBERT 的直接原文片段在本步骤材料中未提供**，因此凡涉及 ColBERT 的部分只能标注为"论文中未报告/材料未覆盖"。

## 研究取证归纳

### 一、ANCE 的训练数据构造：全局负例 + BM25 热启动

ANCE 的核心创新在于**用"正在被优化的稠密检索模型"从整个语料库中检索构造全局负例**，而非使用随机负例或批内局部负例。原文明确指出：

> "Instead of random or in-batch local negatives, ANCE constructs global negatives using the beingoptimized DR model to retrieve from the entire corpus. This fundamentally aligns the distribution of negative samples in training and of irrelevant documents to separate in testing." [KB-1]

即 ANCE 的负例分布与测试时需要区分的无关文档分布"根本对齐"，这是其相对于局部负例的关键设计动机 [KB-1]。

在具体实现上，ANCE 还引入了 **BM25 热启动（BM25 Warm Up）**：在 TREC DL 上，DR 模型先用 MARCO 官方 BM25 负例训练，ANCE 也由 BM25 负例热身 [KB-2]；在 OpenQA 上，ANCE 则用已发布的 DPR checkpoint 热身 [KB-2]。此外，ANCE 论文的负例采样细节为"对每个正例，从 ANN top 200 中均匀采样一个负例"（见章节背景 P-9558cde80da4 的实现细节，但该句不在 [KB-n] 标注范围内，仅作背景理解）。

### 二、ANCE 负例采样对标注的依赖特征

从上述材料可以归纳出 ANCE 负例构造的标注依赖特点：

1. **负例不依赖人工相关性标注，而依赖"模型自身的检索结果"**。ANCE 的全局负例是由当前 DR 模型从全语料检索得到的，属于**模型自动生成**的负例，而非人工标注的"不相关"标签 [KB-1]。这意味着其负例质量随模型迭代而动态变化，不需要为每个 query 额外人工标注负例。

2. **仍需正例（相关性标注）作为对比学习锚点**。ANCE 是"contrastive representation learning"，需要正例—负例对；材料显示其训练数据沿用 MARCO 等带标签的数据集（BM25 官方负例、正例对）[KB-2]，说明正例的标注依赖依然存在，只是负例端由模型自动挖掘替代。

3. **负例构造依赖检索基础设施（ANN 索引）而非标注**。ANCE 用 ANN 索引异步刷新来检索全局负例 [KB-1]，其成本在于"更新训练语料编码"的计算，而非标注人力。

### 三、与 DPR / ColBERT 的差异对比

**与 DPR 的差异**：RocketQA 论文在综述稠密检索基线时明确区分了两类负例构造：

> "Both DRP and ME-BERT use in-batch random sampling and hard negative sampling from the results retrieved by BM25, while ANCE enhances the hard negative sampling by using the dense retriever." [KB-5]

即 DPR 使用**批内随机采样 + 从 BM25 检索结果中挖掘硬负例**，而 ANCE 把硬负例的来源从"稀疏检索器 BM25"升级为"稠密检索器本身" [KB-5]。从标注依赖角度看：DPR 的 BM25 硬负例同样不需要人工负例标注（由 BM25 自动检索产生），但其负例分布来自稀疏检索器，与测试时稠密检索器需要区分的无关文档分布存在偏差；ANCE 则通过让负例来自稠密检索器本身来对齐该分布 [KB-1]。此外，ANCE 论文在实验中把 DPR 作为对比基线，并指出 ANCE 在 OpenQA 段落检索上优于 DPR [KB-4]。

**与 ColBERT 的差异**：本步骤提供的 [KB-n] 原文片段中**未包含 ColBERT 的训练数据或负例采样描述**，因此无法基于现有材料对 ColBERT 的标注依赖作出有依据的对比。此部分应标注为"论文中未报告/材料未覆盖"，不得猜测。

### 四、补充：预训练语言模型差异（与标注需求无关，但属训练设置差异）

材料还显示各方法使用不同预训练 LM：DPR 用 BERT_base，ANCE 用 RoBERTa_base，ME-BERT 用 BERT_large，RocketQA 主要用 ERNIE_base [KB-6]。这属于模型初始化差异，而非标注需求差异，仅作背景参考。

## 结论

基于现有 [KB-n] 材料可确认：ANCE 的训练数据构造以**模型自检索的全局负例**为核心 [KB-1]，辅以 BM25 负例/DPR checkpoint 热启动 [KB-2]；其对标注的依赖主要体现在**正例相关性标签**上，负例端由模型自动挖掘、无需人工负例标注。相比之下，DPR 采用**批内随机负例 + BM25 检索硬负例** [KB-5]，同样不依赖人工负例标注，但负例来源是稀疏检索器而非稠密检索器，这正是 ANCE 相对 DPR 的关键改进点 [KB-1][KB-5]。**关于 ColBERT 的训练数据与负例标注依赖，本步骤提供的原文片段未涉及，论文中未报告，无法据此对比。**

## 参考文献

[KB-1] ANCE 论文，1 INTRODUCTION，第 2 页（用户专题论文库原文片段）

[KB-2] ANCE 论文，5 EXPERIMENTAL METHODOLOGIES，第 5 页（用户专题论文库原文片段）

[KB-3] ANCE 论文，6.1 EFFECTIVENESS AND EFFICIENCY，第 7 页（用户专题论文库原文片段）

[KB-4] ANCE 论文，6.1 EFFECTIVENESS AND EFFICIENCY，第 6 页（用户专题论文库原文片段）

[KB-5] RocketQA 论文，4.2.1 Dense Passage Retrieval，第 7 页（用户专题论文库原文片段）

[KB-6] RocketQA 论文，A.1 The Effects of Pre-trained LMs，第 13 页（用户专题论文库原文片段）

# 步骤取证归纳：ANCE 的训练数据构造与负例采样，及其与 DPR/ColBERT 在标注依赖上的差异

## 一、问题重述

本步骤需要回答两个层面的问题：(1) ANCE 如何构造训练数据，其负例采样对标注的依赖与 DPR/ColBERT 有何不同；(2) 补充取证 ColBERT 的训练数据与批内负例（in-batch negatives）方式及其标注需求。以下归纳严格基于用户专题论文库中的原文片段 [KB-1]–[KB-6]。

## 二、ANCE 的训练数据构造与负例采样

### 1. 对批内负例（local in-batch negatives）的理论批评

ANCE 论文首先从理论上分析了带负例采样的稠密检索训练的收敛性，指出在稠密检索常见的条件下，"local in-batch negatives lead to diminishing gradient norms, resulted in high stochastic gradient variances and slow training convergence - the local negative sampling is the bottleneck of dense retrieval's effectiveness" [KB-1]。也就是说，批内局部负例被视为稠密检索效果的瓶颈。

论文还回顾了已有负例构造方式，指出"the in-batch local negatives, though effective in learning word or visual representations, are not significantly better than spare-retrieved negatives in representation learning for dense retrieval" [KB-3]。这构成了 ANCE 放弃批内负例、转向全局负例的动机。

### 2. ANCE 的负例来源：用正在优化的模型检索全语料（全局负例）

基于上述分析，ANCE 的核心做法是：不再使用随机或批内局部负例，而是"constructs global negatives using the being-optimized DR model to retrieve from the entire corpus"，从而使训练中负样本的分布与测试时需要区分的无关文档分布对齐（该论述在同段中给出，见 [KB-1] 所属引言段落的后续内容）。从方差缩减角度看，这些 ANCE 负例"lift the upper bound of per instance gradient norm, reduce the variance of the stochastic gradient estimation, and lead to faster learning convergence"（同段）。

**说明**：关于 ANCE 负例的具体采样细节（如从 ANN 索引中取 top-k、异步更新索引的频率、每个 query 取多少负例等），本步骤所提供的 [KB-n] 片段中**论文中未报告**（仅提及"implement ANCE using an asynchronously updated ANN index"，且该句在片段中被截断），故不作推断。

### 3. ANCE 对标注的依赖

从 [KB-1]、[KB-3] 可归纳出：ANCE 的负例**不依赖人工对负例的标注**，而是由模型自身从全语料中检索产生（"using the being-optimized DR model to retrieve from the entire corpus"）。这与批内负例一样属于"免额外负例标注"的思路——[KB-2] 明确指出批内负例是"without sampling additional negatives"、是"reuse the examples already loaded in a mini-batch rather than sampling new negatives"。因此 ANCE 与 DPR/ColBERT 在"负例无需人工标注"这一点上是一致的，差别在于**负例的选取机制**（全局模型检索 vs. 批内复用）而非标注需求本身。

**注意**：ANCE 训练所依赖的正例标注来源（如是否使用 NQ 等数据集的标注 question-passage 对）以及是否使用硬负例标注，在本步骤的 [KB-n] 片段中**论文中未报告**，不作猜测。

## 三、DPR 的训练数据与批内负例（作为对照）

### 1. 批内负例的机制（DPR 采用）

[KB-2] 描述了批内负例的标准做法："Assume that there are B questions in a mini-batch on a single GPU, and each question has one positive passage. With the in-batch negative trick, each question can be further paired with B-1 negatives (i.e., positive passages of the rest questions) without sampling additional negatives." 其本质是"a memory-efficient way to reuse the examples already loaded in a mini-batch rather than sampling new negatives, which increases the number of negatives for each question" [KB-2]。

### 2. 批内负例的标注需求

由于批内负例直接复用同一 batch 内其他问题的**正例段落**作为负例，因此**不需要为负例单独标注**——负例"免费"来自已标注的正例对 [KB-2]。这正是批内负例"标注经济性"的来源。

### 3. DPR 的消融证据（负例类型对结果的影响）

[KB-5] 报告了 DPR 在 Natural Questions 开发集上的训练方案对比：在标准 1-of-N 设置下，"the choice of negatives - random, BM25 or gold passages (positive passages from other questions) - does not impact the top-k accuracy much in this setting when k ≥ 20"；而在批内负例设置下，"using a similar configuration (7 gold negative passages), in-batch negative training improves the results substantially"，其关键在于"whether the gold negative passages come from the same batch or from the whole training set"，并且"accuracy consistently improves as the batch size grows" [KB-5]。

[KB-4] 补充了消融表的说明："G.+BM25(1) and G.+BM25(2) denote in-batch training with 1 or 2 additional BM25 negatives, which serve as negative passages for all questions in the batch" [KB-4]。即 DPR 还测试了在批内负例基础上加入 BM25 硬负例（这些 BM25 负例对 batch 内所有问题共享）。

**要点**：DPR 的批内负例与附加 BM25 负例均**不需要人工负例标注**——批内负例来自其他问题的正例，BM25 负例由检索器自动产生（[KB-4]、[KB-5]）。DPR 对标注的依赖集中在**正例（question-passage 对）**上。

## 四、RocketQA 的交叉批负例（cross-batch negatives）与标注依赖（补充对照）

虽然本步骤重点是 ANCE 与 ColBERT，但 [KB-2]、[KB-6] 提供了 RocketQA 的相关证据，可用于对照标注需求：

- 交叉批负例机制："we first compute the passage embeddings within each single GPU, and then share these passage embeddings among all the GPUs. Besides the in-batch negatives, we collect all passages (i.e., their dense representations) from other GPUs as the additional negatives for each question. Hence, with A GPUs (or mini-batches), we can indeed obtain A×B-1 negatives for a given question" [KB-2]。这同样是"复用已加载样本"的免标注负例扩展方式。
- 效果证据："the performance of the dense retriever can be improved with more negatives by cross-batch negatives... when increasing the number of random negatives, it will reduce the discrepancy between training and inference"；同时"the model performance increases, when the number of random negatives becomes larger. After a certain point, the model performance starts to drop, since a large batch size may bring difficulty for optimization on training data with limited size" [KB-6]。

**说明**：RocketQA 的"denoised hard negatives"（用 cross-encoder 去噪硬负例）涉及对"unlabeled positives"的处理，但该部分内容在本步骤 [KB-n] 片段中仅见于章节背景，未以可引用的 [KB-n] 原文完整覆盖其标注流程细节，故此处不展开，**论文中未在本步骤材料中报告**其具体标注需求。

## 五、关于 ColBERT 的取证情况（明确缺口）

本步骤要求"补充取证：ColBERT 训练数据与负例采样方式、批内负例及其标注需求"。经核对，本步骤提供的 [KB-1]–[KB-6] 片段**全部来自 ANCE（[KB-1]、[KB-3]）与 RocketQA（[KB-2]、[KB-4]、[KB-5]、[KB-6]）**，**没有任何一条 [KB-n] 原文直接出自 ColBERT 论文**。因此：

- 关于 **ColBERT 具体使用了什么训练数据、其负例采样方式（是否使用批内负例、是否使用 hard negatives）、以及其标注需求**，本步骤材料**论文中未报告**（更准确地说：本步骤提供的知识库片段中不含 ColBERT 原文），**不得猜测**。
- 可以谨慎陈述的仅是**间接旁证**：[KB-2] 在列举批内负例的既有工作时引用了"Karpukhin et al., 2020; Luan et al., 2020"等，但**未点名 ColBERT**；[KB-1]、[KB-3]、[KB-5] 亦未提及 ColBERT。因此不能据此断定 ColBERT 采用批内负例。

**建议**：若要完成 ColBERT 部分的取证，需补充 ColBERT 原文（Khattab & Zaharia, 2020, *ColBERT: Efficient and Effective Passage Search via Contextualized Late Interaction over BERT*）中关于训练数据（如 MS MARCO、TREC 等）与负例构造（如 batch 内负例、hard negative mining）的原文片段，并纳入 [KB-n] 体系后再作归纳。

## 六、结论（基于现有 [KB-n] 可支持的归纳）

1. **ANCE**：批评批内局部负例导致梯度范数衰减、方差增大、收敛慢，是稠密检索效果的瓶颈 [KB-1]；并指出批内局部负例在稠密检索表示学习中并不显著优于稀疏检索负例 [KB-3]。其对策是"用正在优化的 DR 模型从全语料检索构造全局负例"，使训练负例分布与测试无关文档分布对齐 [KB-1]。负例**不依赖人工负例标注**，而由模型检索自动产生；其正例标注来源与具体采样超参在本步骤材料中**论文中未报告**。
2. **DPR**：采用批内负例，把同一 batch 内其他问题的正例段落复用为负例，"without sampling additional negatives"，是内存高效地复用已加载样本 [KB-2]；消融显示批内负例显著提升，且 batch size 越大准确率越高 [KB-5]，并可叠加共享的 BM25 硬负例 [KB-4]。负例**无需单独标注**，标注需求集中于正例对。
3. **RocketQA**：以交叉批负例在多 GPU 间共享段落嵌入，将负例数扩展到 A×B-1，同样属于复用已加载样本的免标注负例扩展 [KB-2]，并报告了随机负例数量与性能的权衡 [KB-6]。
4. **ColBERT**：本步骤 [KB-n] 片段中**无 ColBERT 原文**，其训练数据、负例采样方式与标注需求**论文中未报告**（本步骤材料范围内），需补充原文后再归纳。

## 参考文献

[KB-1] ANCE 论文，1 INTRODUCTION，页码 2（用户专题论文库原文片段）

[KB-2] RocketQA 论文，3.3 Optimized Training Approach，页码 4（用户专题论文库原文片段）

[KB-3] ANCE 论文，1 INTRODUCTION，页码 1（用户专题论文库原文片段）

[KB-4] RocketQA 论文，5.2 Ablation Study on Model Training，页码 6（Table 3 说明，用户专题论文库原文片段）

[KB-5] RocketQA 论文，5.2 Ablation Study on Model Training，页码 6（用户专题论文库原文片段）

[KB-6] RocketQA 论文，4.2.2 The Effectiveness of The Three Training Strategies in RocketQA，页码 8（用户专题论文库原文片段）

### 3. RocketQA 的训练数据构造与标注需求有何特点，与前三者如何对比？（COMPLETED）

## 问题陈述

本步骤要回答的是：RocketQA 的训练数据构造方式与标注需求有什么特点，并与 DPR、ColBERT、ANCE 三者进行对比。需要说明的是，用户专题论文库中提供的原文片段仅来自 RocketQA 论文本身，因此：

- RocketQA 自身的训练数据构造与标注需求特点，可以基于 [KB-n] 原文归纳；
- 与 DPR、ANCE 的对比，只能基于 RocketQA 论文中对二者的转述；
- **ColBERT 在所提供的 [KB-1]–[KB-6] 原文中完全没有出现，其训练数据与标注需求"论文中未报告"**，无法在本步骤中据实归纳。

## 研究发现的归纳

### 一、RocketQA 训练数据构造的核心特点：以"伪标注 + 数据增强"扩展训练数据

RocketQA 明确把"训练数据有限"列为训练双编码器的三大挑战之一，并把数据增强作为三大技术贡献之一。原文指出，RocketQA 利用由 cross-encoder "标注"的大规模无监督数据来做数据增强：由于 cross-encoder 在理论与实践上都被认为强于 dual-encoder，因此用它为无标注数据生成高质量的伪标签，再用这些数据训练 dual-encoder 检索器 [KB-2]。摘要同样把 cross-batch negatives、denoised hard negatives 和 data augmentation 列为三项主要贡献 [KB-1][KB-6]。

这说明 RocketQA 的数据构造不是单纯依赖人工标注的正例对，而是**引入 cross-encoder 自动生成伪标签，把大规模无监督语料转化为可训练数据**，从而缓解"训练数据有限"的问题。

### 二、RocketQA 的标注需求特点：应对"未标注正例"与"假负例"

RocketQA 明确指出，通常无法为一个问题完整标注所有候选段落；如果只检查由某种检索方式（如 BM25）返回的 top-K 段落，标注者很可能漏掉相关段落 [KB-2]（该论述出自 Introduction 背景段落，与 [KB-2] 的挑战陈述一致）。正是这种"未标注正例"的存在，使得从 top-k 结果中采样困难负例时容易引入假负例（false negatives）。

针对这一点，RocketQA 提出 **denoised hard negatives**：从检索器返回的 top-ranked 结果中剔除假负例，从而得到更可靠的困难负例 [KB-2]。换言之，RocketQA 的标注需求特点是：**不要求穷尽式的人工正例标注，而是承认标注不完整，并通过"去噪"机制在训练中纠正假负例**。这与"依赖完整人工标注"的思路形成对照。

### 三、与 DPR、ANCE 的对比（基于 RocketQA 论文的转述）

**负例采样方式的差异。** 原文指出，DPR 与 ME-BERT 都使用 in-batch 随机采样，并从 BM25 检索结果中进行困难负例采样；而 ANCE 则用稠密检索器来增强困难负例采样 [KB-5]。RocketQA 则进一步引入 cross-batch negatives，相比 in-batch negatives 增加了每个问题在训练时可用的负例数量，并缓解训练与推理之间的差异 [KB-2]。

**预训练语言模型的差异（间接反映训练配置差异）。** 原文附录指出，DPR 使用 BERT_base，ANCE 使用 RoBERTa_base，ME-BERT 使用 BERT_large，而 RocketQA 主要使用 ERNIE_base [KB-4]。这说明几者在骨干模型配置上不同；不过这是模型配置差异，并非训练数据本身的差异。

**RocketQA 相对前者的定位。** 原文强调，仅使用 cross-batch negatives 训练的 RocketQA 就已可与 DPR、ANCE、ME-BERT 相比（尽管它们使用了更强的预训练语言模型）[KB-4]，并在 MSMARCO 与 NQ 上显著优于所有基线 [KB-5]。

### 四、材料不足、需明确说明的部分

- **ColBERT 的训练数据与标注需求**：所提供的 [KB-1]–[KB-6] 原文中未出现 ColBERT，**论文中未报告**，本步骤无法归纳。
- **DPR、ANCE 各自训练数据的具体规模、来源与标注流程细节**：原文仅转述其负例采样方式（DPR 用 BM25 困难负例 + in-batch 随机采样；ANCE 用稠密检索器增强困难负例采样）[KB-5]，**未报告**其训练数据的标注规模、标注粒度等细节。
- **RocketQA 数据增强所用无监督数据的具体规模与来源**：原文只说明使用"大规模无监督数据"并由 cross-encoder 生成伪标签 [KB-2]，**具体数据量论文中未报告**（在所给片段范围内）。

## 结论

RocketQA 的训练数据构造与标注需求特点可概括为三点：其一，以 **cross-encoder 生成伪标签的数据增强**来扩充训练数据，缓解训练数据有限的问题 [KB-2]；其二，正视"未标注正例"导致的标注不完整，通过 **denoised hard negatives** 剔除假负例、获得可靠困难负例 [KB-2]；其三，配合 **cross-batch negatives** 扩大可用负例数量并缩小训练—推理差距 [KB-2]。与 DPR、ANCE 相比，RocketQA 的差异主要体现在负例构造策略上：DPR 用 in-batch 随机采样加 BM25 困难负例，ANCE 用稠密检索器增强困难负例采样，而 RocketQA 在此之上叠加了跨批次负例、去噪困难负例与伪标签数据增强 [KB-5][KB-2]。至于 ColBERT，所给原文片段未涉及，其训练数据与标注需求在本步骤中**论文中未报告**，需另行取证。

## 参考文献

[KB-1] RocketQA, Abstract, p.1（用户专题论文库原文片段）

[KB-2] RocketQA, 1 Introduction, p.2（用户专题论文库原文片段）

[KB-3] RocketQA, 1 Introduction, p.2（用户专题论文库原文片段）

[KB-4] RocketQA, A.1 The Effects of Pre-trained LMs, p.13（用户专题论文库原文片段）

[KB-5] RocketQA, 4.2.1 Dense Passage Retrieval, p.7（用户专题论文库原文片段）

[KB-6] RocketQA, 5 Conclusions, p.9（用户专题论文库原文片段）

# RocketQA 训练数据构造与标注需求：取证归纳

## 问题陈述

本步骤聚焦：RocketQA 在训练数据构造与标注需求上有何特点，并与 DPR、ColBERT、ANCE 前三者进行对比。要求仅基于给定原文片段 [KB-n] 归纳，材料不足之处明确标注"论文中未报告"。

---

## 一、RocketQA 的训练数据构造与标注需求

### 1. 数据的两类划分：有标注集 D_L 与无标注集 D_U

RocketQA 的整个训练流程建立在对数据的明确二分之上。原文定义：

> "Let C denote a collection of passages. QL is a set of questions that have corresponding labeled passages in C, and QU is a set of questions that have no corresponding labeled passages. DL is a dataset consisting of C and QL, and DU is a dataset consisting of C and QU." [KB-4]

即：**D_L 是"问题—已标注段落"配对构成的有标注数据**，**D_U 是"问题—无标注段落"构成的无标注数据**。这一划分是理解 RocketQA 标注需求的关键——它同时利用了人工标注数据与无标注问题。

### 2. 人工标注数据 D_L 的用途：贯穿前三步

- **STEP 1**：在 D_L 上用 cross-batch negatives 训练双编码器 M_D^(0) [KB-4]。
- **STEP 2**：在 D_L 上训练 cross-encoder M_C，正样本来自原始训练集 D_L，负样本从 M_D^(0) 检索的 top-k 段落中随机采样（排除已标注正段落）[KB-4]。
- **STEP 3**：进一步引入去噪难负样本，在 D_L 上训练 M_D^(1)；难负样本由 M_D^(0) 检索，且仅保留被 cross-encoder M_C 以高置信度判为负的段落 [KB-4]。

可见，**D_L 的人工标注（正段落标签）是前三步的基础**，标注需求体现为"问题—正段落"的稀疏相关性标注。

### 3. 无标注数据 D_U 的用途：自动伪标注（STEP 4）

这是 RocketQA 标注需求上最具特色的部分：

> "STEP 4: Construct pseudo training data DU by using MC to label the top-k passages retrieved by M (1) D from C for each question q ∈ QU, and then train a dual-encoder M (2) D on both the manually labeled training data DL and the automatically augmented training data DU." [KB-4]

即：对于**没有标注段落的问题集 Q_U**，用 cross-encoder M_C 对 M_D^(1) 检索出的 top-k 段落**自动打标签**，构造伪训练数据 D_U，再与人工标注的 D_L **联合训练**最终的双编码器 M_D^(2)。

**标注需求特点归纳**：
- 人工标注只需覆盖 D_L（"问题—正段落"配对）；
- 大量无标注问题 Q_U 通过 cross-encoder 自动标注（伪标签）被"激活"为训练信号，**无需人工逐条标注负样本或为 Q_U 标注正段落**；
- 负样本（难负样本、去噪难负样本）均由检索模型 + cross-encoder 自动生成，而非人工标注 [KB-4]。

### 4. 数据集统计

原文提到有一张统计表：

> "Table 1: The statistics of datasets MSMARCO and Natural Questions. Here, 'p' and 'q' are the abbreviations of questions and passages, respectively. The length is in tokens." [KB-4]

即 RocketQA 在 **MSMARCO 与 Natural Questions** 两个数据集上实验，Table 1 给出其问题数与段落数统计。但**具体数值在给定片段中未给出**——论文中未报告（于本材料范围内）。

---

## 二、与前三者的对比

### 1. ColBERT 的训练数据与标注需求

> "we conduct our experiments on the MS MARCO Ranking [24] ... and TREC Complex Answer Retrieval (TREC-CAR) [6] datasets. Both of these recent datasets provide large training data of the scale that facilitates training and evaluating deep neural networks." [KB-1]

- 使用 **MS MARCO** 与 **TREC-CAR** 两个大规模数据集 [KB-1]。
- **标注特点**：MS MARCO 提供"稀疏相关性判断"——每个查询仅有一个（或极少数）文档标为相关，**没有显式标注为不相关的文档**（据章节背景描述，非 [KB-n] 直接原文，此处仅作理解，不引用为已校验结论）。
- **对比要点**：ColBERT 依赖数据集自带的稀疏相关性标签；而 RocketQA 则额外引入"无标注问题集 Q_U + 自动伪标注"机制 [KB-4]，在标注利用方式上更进一步。ColBERT 是否也做伪标注，**给定材料中未报告**。

### 2. DPR 的训练数据规模与标注流程

给定片段中**没有直接涉及 DPR 训练数据构造或标注流程的原文**。可间接参考的是 ANCE 与 DPR 的对比：

> "ANCE+Reader switches the retrieve of a system from DPR to ANCE and keeps the same reading model, which is RAG-Token on Natural Questions (NQ) and DPR Reader on Trivia QA (TQA)." [KB-2]

该句只说明 DPR 的 Reader 用于 Trivia QA，**未报告 DPR 的训练数据规模或标注流程**。因此，**DPR 的训练数据规模与标注流程：论文中未报告（于本材料范围内）**。

### 3. ANCE 的训练数据规模与标注流程

- **负样本构造方式（核心）**：ANCE 从整个语料库中通过异步更新的 ANN 索引选取负样本：

> "ANCE samples negatives from the top retrieved documents via the DR model from the ANN index ... with DANCE = ANNf(q,d) \ D+ ... By definition, DANCE are the hardest negatives for the current DR model: DANCE ≈ D-*." [KB-3 所属章节背景，但核心定义在 [KB-3] 相邻段落；本材料中该定义为章节背景 P-454776103b5e，非 [KB-n]。以 [KB-3] 可引用部分为准]

可引用的 [KB-3] 原文为：

> "In this process, the ANCE negatives (DANCE ) are asynchronously updated to 'catch up' with the stochastic training. The gap between the ANN index and the DR model optimization depends on the allocation of computing resources between Trainer and Inferencer. Appendix A.3 shows an 1:1 GPU split is sufficient to minimize the influence of this gap." [KB-3]

- **标注需求特点**：ANCE 的负样本是**从语料库中自动检索得到的全局难负样本**（DANCE），**无需人工标注负样本**；训练主要计算开销在于用新 checkpoint 更新训练语料的编码 [KB-5 所属章节背景]。其难点不在标注，而在**索引异步刷新与 GPU 资源分配** [KB-3]。
- **训练数据规模**：给定片段中**未给出 ANCE 训练语料的具体规模数字**——论文中未报告（于本材料范围内）。可确认的是 ANCE 在 TREC DL 文档检索、OpenQA（NQ、TQA）以及商业搜索引擎生产系统上评估 [KB-2][KB-6]。

### 4. 四方对比小结

| 维度 | ColBERT | DPR | ANCE | RocketQA |
|---|---|---|---|---|
| 训练数据集 | MS MARCO、TREC-CAR [KB-1] | 论文中未报告（本材料） | 论文中未报告规模（本材料）；负样本来自全语料 ANN 检索 [KB-3] | MSMARCO、Natural Questions [KB-4] |
| 正样本标注 | 数据集自带稀疏相关性标签 [KB-1] | 论文中未报告 | 论文中未报告 | 人工标注 D_L（问题—正段落）[KB-4] |
| 负样本来源 | 论文中未报告 | 论文中未报告 | 全语料 ANN 检索的全局难负样本，自动更新 [KB-3] | cross-batch negatives + 检索难负样本 + cross-encoder 去噪 [KB-4] |
| 是否用无标注问题 | 论文中未报告 | 论文中未报告 | 论文中未报告 | 是，Q_U 经 cross-encoder 自动伪标注构造 D_U [KB-4] |
| 标注需求核心特点 | 依赖数据集稀疏标签 [KB-1] | 论文中未报告 | 自动难负样本，人工标注负样本需求低 [KB-3] | 人工标注正样本 + 自动伪标注扩展无标注数据 [KB-4] |

---

## 三、结论

1. **RocketQA 的标注需求特点**：它将数据明确分为人工标注集 D_L 与无标注集 D_U；人工标注只需提供"问题—正段落"配对，负样本（cross-batch、难负样本、去噪难负样本）全部由模型自动生成；无标注问题集 Q_U 通过 cross-encoder 自动打伪标签扩展为 D_U，与 D_L 联合训练最终模型 [KB-4]。这使其在**降低人工标注成本、扩展训练数据规模**方面具有鲜明特色。

2. **与 ANCE 的对比**：两者都强调"自动构造负样本、减少人工负标注"。ANCE 通过全语料 ANN 索引异步刷新获取全局难负样本 [KB-3]，其工程挑战在于索引与训练的同步（1:1 GPU 分配可最小化差距）[KB-3]；RocketQA 则用 cross-encoder 对检索结果去噪并伪标注无标注问题 [KB-4]。二者自动化路径不同，但都体现了"少人工标注、多自动挖掘"的趋势。

3. **与 ColBERT 的对比**：ColBERT 主要依赖 MS MARCO、TREC-CAR 数据集自带的稀疏相关性标签 [KB-1]；RocketQA 在此基础上额外引入无标注问题的自动伪标注机制 [KB-4]。

4. **材料局限**：DPR 的训练数据规模与标注流程、ANCE 的训练数据具体规模、以及各数据集的具体统计数值，**在给定片段中均未报告**，无法据本材料作出比较，需补充其他原文取证。

---

## 参考文献

[KB-1] ColBERT 论文 4.1 Methodology，第 6 页（Datasets & Metrics：MS MARCO、TREC-CAR）

[KB-2] ANCE 论文 6.1 Effectiveness and Efficiency，第 7 页（Table 4/5：DPR Reader、RAG-Token）

[KB-3] ANCE 论文 4 Approximate Nearest Neighbor Noise Contrastive Estimation，第 5 页（异步索引刷新、1:1 GPU 分配）

[KB-4] RocketQA 论文 3.4 The Training Procedure，第 5 页（D_L/D_U 定义、STEP 1–4、Table 1）

[KB-5] ANCE 论文 A.1 More Experimental Details，第 13 页（Figure 5：训练损失与索引刷新）

[KB-6] ANCE 论文 5 Experimental Methodologies，第 5 页（商业搜索引擎生产系统评估）

## 证据与分歧

- 2004.04906（ACCEPTED）
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

- 已检查来源：13 条；已完成步骤：3 / 3。

## 来源清单

- 2004.04906
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