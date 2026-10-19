# C1 · full · round 3

梳理从 DPR 到 RocketQA 负样本策略的演进主线，覆盖全部四篇论文。

---

# 研究报告

## 直接回答

本报告汇总了 4 个已完成研究步骤的发现；逐项依据见下方证据与局限。

**研究问题**：梳理从 DPR 到 RocketQA 负样本策略的演进主线，覆盖全部四篇论文。

## 资料范围

- 专题内全部就绪资料（未限定单篇）

## 逐项发现

### 1. DPR原文中使用了哪些负样本类型，其采样来源与数量配置如何？（COMPLETED）

## 问题陈述

本步骤聚焦 DPR 原文的负样本配置：DPR 使用了哪些负样本类型，采样来源是什么，数量如何配置。

## 研究取证与归纳

### 一、DPR 使用的负样本类型与来源

根据论文库中的相关原文，DPR 的负样本策略可归纳为两类：

1. **批内随机负样本（in-batch negatives）**：DPR 主实验模型采用 "in-batch negative setting"，即在一个训练批次内，将其他问题的正样本文档作为当前问题的负样本 [KB-3]。这一做法也被后续工作概括为 "in-batch random sampling" [KB-1]。

2. **BM25 检索负样本（hard negatives）**：DPR 在每个问题原有批内负样本的基础上，额外加入 **1 个来自 BM25 检索结果的负样本文档** [KB-3]。相关综述性原文也指出，DPR 与 ME-BERT 均使用 "in-batch random sampling and hard negative sampling from the results retrieved by BM25" [KB-1]；ANCE 论文在基线设置中同样将 DPR 归为 "BM25 与 Random 负样本 1:1 组合"（BM25 + Rand Neg）的做法 [KB-2]。

### 二、数量配置

- **批内负样本数量**：由批大小决定，DPR 主实验使用 **batch size = 128** [KB-3]。按批内负样本机制，每个问题的批内负样本数量与同批次其他问题数相关（即约 127 个，此为本步骤基于机制的直接推论，原文未显式给出该数字）。
- **BM25 硬负样本数量**：每个问题 **额外 1 个** BM25 负样本文档 [KB-3]。
- **BM25 负样本的候选池规模**：DPR 原文片段中**未报告**具体从 BM25 top-k 的哪一档中采样（RocketQA 原文在描述基线时仅笼统写作 "random sampling from BM25 top 100"，但该表述指向的是被比较的 DR 基线整体，且其引用对象包含 Lee et al. 与 Gao et al.，不能直接等同于 DPR 原文的配置）[KB-2]。因此，DPR 原文中 BM25 负样本的具体候选池深度在本材料中**论文中未报告**。

### 三、相关背景性限定

DPR 负样本策略的动因在于：DR 首阶段检索需要从整个语料库的全部无关文档中区分相关文档，这些"全局负样本"与稀疏模型检索出的负样本差异很大 [KB-6]；同时，仅从 top-k 检索结果中采样硬负样本会带来假阴性问题 [KB-6]。这构成了后续 RocketQA 改进负样本策略（如跨批次去噪、无标注正样本等）的出发点 [KB-1][KB-6]。

## 结论

就本步骤问题而言：DPR 原文中的负样本由**批内随机负样本（batch size 128）**与**每问题 1 个 BM25 检索负样本**两部分构成，采样来源分别为"同批次其他问题的正样本文档"和"BM25 检索结果" [KB-3]，这一组合也被后续文献概括为 BM25 与随机负样本的 1:1 组合 [KB-1][KB-2]。至于 BM25 负样本候选池的具体深度、以及批内负样本的精确计数，**论文中未报告**，需在后续核对 DPR 原文第 3.2 节时补充确认。

## 参考文献

[KB-1] RocketQA 论文，4.2.1 Dense Passage Retrieval，第 7 页（用户专题论文库）

[KB-2] ANCE 论文，5 Experimental Methodologies，第 5 页（用户专题论文库）

[KB-3] DPR 论文，5 Experiments: Passage Retrieval，第 5 页（用户专题论文库）

[KB-6] RocketQA 论文，1 Introduction，第 1 页（用户专题论文库）

### 2. RocketQA原文针对DPR的负样本问题提出了哪些具体改进，跨batch负样本与去噪策略的原文依据是什么？（COMPLETED）

# RocketQA 针对 DPR 负样本问题的改进：取证归纳

## 一、问题陈述

本步骤需要回答两个问题：（1）RocketQA 原文针对 DPR（Dense Passage Retrieval，即双编码器稠密检索）的负样本问题提出了哪些具体改进；（2）"跨 batch 负样本"与"去噪策略"的原文依据是什么。以下归纳严格绑定用户专题论文库中的 [KB-n] 原文片段。

## 二、研究结论

### 1. RocketQA 识别出的 DPR 负样本相关挑战

RocketQA 将 DPR 难以有效训练归结为三类挑战，其中与负样本直接相关的是前两类：训练与推理之间的差异（discrepancy between training and inference），以及大量未标注正样本（unlabeled positives）的存在 [KB-3][KB-2]。原文指出，为缓解训练/推理差异，以往工作尝试从 top-k 检索候选中选取少量难负样本，但受"假阴性"（false negative）问题困扰 [KB-2]。

### 2. 针对负样本问题的三项具体改进

RocketQA 原文明确提出三项训练策略：**cross-batch negatives（跨 batch 负样本）、denoised hard negatives（去噪难负样本）与 data augmentation（数据增强）** [KB-1][KB-3][KB-4]。其中前两项直接对应负样本问题：

- **跨 batch 负样本**：原文说明，相较于 in-batch negatives，跨 batch 负样本在训练中为每个问题**增加了可用负样本的数量**，并**缓解了训练与推理之间的差异**（"it increases the number of available negatives for each question during training, and alleviates the discrepancy between training and inference"）[KB-2]。
- **去噪难负样本**：原文说明，其目的是**从检索器返回的 top-ranked 结果中移除假阴性**，从而**获得更可靠的难负样本**（"remove false negatives from the top-ranked results retrieved by a retriever, and derive more reliable hard negatives"）[KB-2]。

第三项 data augmentation 则是利用 cross-encoder 为大规模无监督数据生成高质量伪标签，用于训练双编码器 [KB-2]，它针对的是"训练数据有限"的挑战，而非直接针对负样本构造。

### 3. 三项策略的有效性

RocketQA 原文声明通过大量实验检验了上述三项策略的有效性，结果表明三项策略均能有效提升稠密段落检索的性能 [KB-3][KB-5][KB-4]。

## 三、材料覆盖度说明

- 关于跨 batch 负样本与去噪难负样本，原文库片段仅提供了**动机层面的表述**（增加负样本数量、缓解训练/推理差异；移除假阴性、获得更可靠难负样本）[KB-2]，以及它们在引言/摘要/结论中的定位 [KB-1][KB-3][KB-4]。**具体的实现机制、跨 batch 的构造方式、去噪所用的判定模型与阈值等细节，所给片段中未报告。**
- [KB-6] 来自另一篇论文（ANCE）的引言，其内容是对 in-batch 局部负样本导致梯度范数衰减、训练收敛缓慢的理论分析，属于对 DPR 负采样瓶颈的**外部诊断**，并非 RocketQA 原文自身的改进表述；本步骤引用时仅作为背景理解，不作为 RocketQA 改进的原文依据。
- 关于 RocketQA 与 DPR 在具体负样本数量、采样流程上的定量对比，**论文中未报告（就所给片段而言）**。

## 四、参考文献

[KB-1] RocketQA, 1 Introduction, p.2

[KB-2] RocketQA, 1 Introduction, p.2

[KB-3] RocketQA, Abstract, p.1

[KB-4] RocketQA, 5 Conclusions, p.9

[KB-5] RocketQA, 1 Introduction, p.2

[KB-6] ANCE, 1 INTRODUCTION, p.2

### 3. 四篇论文中负样本策略的关键参数（负样本数量、来源、是否跨batch、是否去噪）如何逐篇取值？（COMPLETED）

## 问题陈述

本步骤要求梳理从 DPR 到 RocketQA 的负样本策略演进主线，并聚焦于四篇论文中负样本策略的四个关键参数：**负样本数量、来源、是否跨 batch、是否去噪**，逐篇给出取值。

## 研究结论（基于论文库原文）

### 重要说明：材料覆盖范围

用户专题论文库中提供的原文片段 [KB-1] 至 [KB-6] **仅覆盖 RocketQA 一篇论文**（以及其中引用的 ANCE 相关工作背景）。关于 DPR 原文、ANCE 原文、以及第四篇论文（如 RocketQA v2 / 或其它）的负样本参数原文**未出现在所提供的 [KB-n] 片段中**。因此，本步骤只能就材料中可核实的内容进行归纳，其余部分必须标注"论文中未报告（材料未提供）"。

---

### 一、RocketQA（材料可核实部分）

**是否跨 batch：是。** 论文明确将 cross-batch negatives 列为首要技术创新："RocketQA introduces cross-batch negatives. Comparing to in-batch negatives, it increases the number of available negatives for each question during training, and alleviates the discrepancy between training and inference." [KB-2]；摘要与结论亦反复将 "cross-batch negatives" 列为三大贡献之一 [KB-5][KB-3]。

**是否去噪：是。** "Second, RocketQA introduces denoised hard negatives. It aims to remove false negatives from the top-ranked results retrieved by a retriever, and derive more reliable hard negatives." [KB-2]；结论同样确认 "denoised hard negatives" [KB-3]。

**负样本来源：** 去噪硬负样本来自"检索器返回的 top-ranked 结果"（top-ranked results retrieved by a retriever），并剔除其中的假负例 [KB-2]。此外，RocketQA 还使用 cross-encoder 为大规模无监督数据生成伪标签用于数据增强 [KB-2]，但材料未说明该部分是否直接作为负样本使用。

**负样本数量：** 论文中未报告（提供的 [KB-n] 片段未给出具体负样本数量数值，如每问题取多少个负样本）。

**去噪的具体机制/阈值：** 论文中未报告（材料仅说明目的是移除假负例、获得更可靠硬负例，未给出具体过滤规则或阈值）。

---

### 二、DPR

**论文中未报告。** 提供的 [KB-n] 片段中未包含 DPR 论文关于负样本数量、来源、是否跨 batch、是否去噪的原文描述。材料仅在背景与引用层面提及 in-batch random negatives 的一般做法（如"Typically, the dual-encoder is trained by using in-batch random negatives"属于 RocketQA 引言对通用做法的描述，见章节背景，非 DPR 原文 [KB-n]），不能据此作为 DPR 的参数结论。

---

### 三、ANCE

**论文中未报告（就四参数而言）。** 材料 [KB-4] 与 [KB-6] 提供了 ANCE 的核心论点：局部 in-batch negatives 会导致梯度范数衰减、随机梯度方差高、收敛慢，是稠密检索效果的瓶颈 [KB-4]；并指出 DR 的关键挑战是构造合适的负样本，全局负样本与稀疏模型检索的负样本差异很大 [KB-6]。这些说明了 ANCE 采用"全局负样本"的方向，但**具体的负样本数量、来源细节、是否跨 batch、是否去噪的取值，在提供的 [KB-n] 片段中未报告**。

---

### 四、第四篇论文

**论文中未报告。** 提供的 [KB-n] 片段未涉及第四篇论文的任何负样本策略原文，无法归纳其四参数取值。

---

## 结论

在所提供的论文库片段范围内，唯一可完整对应"是否跨 batch / 是否去噪"两项参数的论文是 **RocketQA**：其明确采用 **cross-batch negatives（跨 batch）** [KB-2][KB-5][KB-3] 与 **denoised hard negatives（去噪硬负例，来源为检索器 top-ranked 结果）** [KB-2][KB-3]。而 RocketQA 的**负样本具体数量**、**去噪的具体规则/阈值**，以及 **DPR、ANCE、第四篇论文**在四个参数上的取值，**论文中未报告（材料未提供）**，不应猜测。若要完成"逐篇取值"的完整对照表，需补充 DPR、ANCE 及第四篇论文的原文片段。

## 参考文献

[KB-1] RocketQA, 1 Introduction, p.2（用户专题论文库片段）

[KB-2] RocketQA, 1 Introduction, p.2（用户专题论文库片段）

[KB-3] RocketQA, 5 Conclusions, p.9（用户专题论文库片段）

[KB-4] ANCE, 1 INTRODUCTION, p.2（用户专题论文库片段）

[KB-5] RocketQA, Abstract, p.1（用户专题论文库片段）

[KB-6] ANCE, 1 INTRODUCTION, p.1（用户专题论文库片段）

## 问题陈述

本步骤要求：针对"DPR → ANCE → RocketQA"主线中的四篇论文，逐篇取证其**负样本策略的关键参数**——负样本数量、来源、是否跨 batch、是否去噪。材料为用户专题论文库中的原文片段（[KB-1] 至 [KB-6]）。

## 研究取证

### 1. DPR 论文

**论文中未报告。** 提供的 [KB-1]–[KB-6] 原文片段中，没有 DPR 论文本身的原文内容。DPR 仅作为被比较对象出现在 ANCE 的实验方法部分：[KB-6] 提到 "In OpenQA, we compare with DPR, BM25, and their combinations (Karpukhin et al., 2020)"，但这只是比较对象，并未描述 DPR 的负样本数量、来源、是否跨 batch、是否去噪等参数。因此本步骤无法基于现有材料对 DPR 的负样本策略进行取证，需补充 DPR 原文片段。

### 2. ANCE 论文

**来源（全局语料，非局部/in-batch）**：[KB-1] 明确指出 "Instead of random or in-batch local negatives, ANCE constructs global negatives using the beingoptimized DR model to retrieve from the entire corpus"；[KB-2] 亦载 "ANCE, a learning mechanism that selects hard training negatives globally from the entire corpus, using an asynchronously updated ANN index"。

**是否跨 batch**：ANCE 的核心即否定 in-batch 局部负样本，转而使用全局负样本，[KB-2] 指出学习瓶颈源于 "uninformative negatives sampled locally in batch"。因此其负样本取自整个语料而非当前 batch，可视为"跨 batch/全局"策略（原文表述为 global negatives / from the entire corpus）。

**是否去噪**：**论文中未报告。** 现有片段未提及 ANCE 对负样本做去噪处理；[KB-6] 仅列出 ANCE 对比的负样本构造基线（Rand Neg、BM25 Neg、BM25+Rand Neg、NCE Neg），未涉及去噪机制。

**负样本数量**：**论文中未报告。** 现有片段未给出 ANCE 每个查询采样的负样本具体数量。

**实现机制（补充）**：[KB-5] 说明 ANCE 使用 "an asynchronously updated ANN index of the corpus representation"，维护一个 Inferencer 并行计算文档编码，并在完成后刷新用于负样本采样的 ANN 索引。

### 3. RocketQA 论文

**是否跨 batch——是**：[KB-4] 明确 "the cross-batch negative strategy is applied through all the steps for training the dual-encoder"；[KB-3] 所在章节背景亦述 "we compare cross-batch negatives with inbatch negatives... the performance of the dense retriever can be improved with more negatives by cross-batch negatives"。

**是否去噪——是，且为关键创新**：[KB-3] 指出 "the performance of the retriever significantly decreases by introducing hard negatives without denoising"，并 "propose denoised hard negatives by a powerful cross-encoder"；[KB-4] 亦载 "The implementation details of denoising hard negatives and data augmentation can be found in Section 4"。

**来源（硬负样本来自 dense retriever 的 top-k 检索，再经 cross-encoder 去噪）**：[KB-3] 载 "denoised hard negatives from the top-k passages retrieved by the dense retriever"；并说明未去噪时约 "70% of them are actually positives or highly relevant"，故直接采样会引入噪声。

**负样本数量**：**论文中未报告具体数值。** [KB-3] 所在章节背景仅定性说明 "the model performance increases, when the number of random negatives becomes larger. After a certain point, the model performance starts to drop"，强调 "there should be a balance between the batch size and the number of negatives"，但未给出确切数量。

### 4. 第四篇论文（RocketQA 之后的论文）

**论文中未报告。** 用户专题论文库提供的片段 [KB-1]–[KB-6] 全部来自 ANCE 与 RocketQA 两篇论文，未包含第四篇论文的任何原文。因此第四篇论文的负样本数量、来源、是否跨 batch、是否去噪均无法基于现有材料取证，需补充该论文原文片段。

## 结论

基于现有 [KB-1]–[KB-6] 材料，仅能对 **ANCE** 与 **RocketQA** 两篇论文完成部分取证：

- **ANCE**：负样本来源为"全局语料、由正在优化的 DR 模型检索得到"（非 in-batch 局部负样本），可视为跨 batch/全局策略 [KB-1][KB-2]；负样本数量与是否去噪**论文中未报告**。
- **RocketQA**：采用**跨 batch** 负样本 [KB-4]，并引入**去噪**硬负样本（由 cross-encoder 过滤 dense retriever top-k 检索结果）[KB-3][KB-4]；负样本数量**论文中未报告具体数值**。

**DPR 论文**与**第四篇论文**因论文库未提供相应原文片段，其四项参数（负样本数量、来源、是否跨 batch、是否去噪）均**无法取证**，需补充原文后再行归纳。为避免编造，此处不对上述两篇作任何推测性取值。

## 参考文献

[KB-1] ANCE, 1 INTRODUCTION, 页码 2

[KB-2] ANCE, ABSTRACT, 页码 1

[KB-3] RocketQA, 4.2.2 The Effectiveness of The Three Training Strategies in RocketQA, 页码 8

[KB-4] RocketQA, 3.4 The Training Procedure, 页码 5

[KB-5] ANCE, 1 INTRODUCTION, 页码 2

[KB-6] ANCE, 5 EXPERIMENTAL METHODOLOGIES, 页码 5

### 4. 各论文是否报告了负样本策略的消融或对比实验，其原文结论如何支撑演进关系？（COMPLETED）

## 问题陈述

本步骤要回答的问题是：DPR、ANCE、RocketQA 等论文是否报告了针对**负样本策略**的消融实验或对比实验，其原文结论如何支撑"从 DPR 到 RocketQA 负样本策略演进"这一主线。

需要说明的是：用户提供的论文库片段 [KB-1]～[KB-6] 全部来自 **RocketQA** 一篇论文（引言、摘要、结论、4.2.1 节）。DPR、ANCE 等论文的原文片段**未出现在本步骤的材料中**，因此凡涉及这些论文的具体消融数字或结论，本归纳只能依据 [KB] 中"转述他人工作"的句子，并明确标注材料不足。

---

## 研究发现

### 1. RocketQA 明确报告了负样本策略的消融/对比实验

RocketQA 在摘要与引言中都明确声称对三项训练策略做了"广泛实验"以检验其有效性：

- 摘要："We also conduct extensive experiments to examine the effectiveness of the three strategies in RocketQA." [KB-6]
- 引言："We conduct extensive experiments to examine the effectiveness of the above three strategies in RocketQA. Experimental results show that the three strategies are effective to improve the performance of dense passage retrieval." [KB-1]

其中与负样本直接相关的两项策略是 **cross-batch negatives**（跨批次负样本）与 **denoised hard negatives**（去噪难负样本）：

- "The proposed RocketQA introduces three novel training strategies ... namely cross-batch negatives, denoised hard negatives, and data augmentation." [KB-2]
- 结论重申："We have made three major technical contributions in RocketQA, namely cross-batch negatives, denoised hard negatives and data augmentation. Extensive experiments have shown the effectiveness of the proposed approach by incorporating the three optimization strategies." [KB-4]

此外，章节背景（4.2.1 节）提到存在一张"Table 3: The experiments to examine the effectiveness of the three proposed training strategies in RocketQA on MSMARCO Passage Ranking"，以及"Figure 4: The effect of the number of random negatives paired for a question on MSMARCO dataset"。**但需注意：该 Table 3 / Figure 4 的具体数值与逐项结论并未出现在所提供的 [KB-n] 原文片段中，因此本步骤无法引用其具体数据，应视为"论文中未在给定片段内报告具体数值"。**

### 2. 演进关系的原文支撑：DPR 的负样本做法（作为起点）

RocketQA 在 4.2.1 节转述了 DPR 等基线所用的负样本策略：

- "The dense passage retrievers include DPR (Karpukhin et al., 2020), ME-BERT (Luan et al., 2020) and ANCE (Xiong et al., 2020). Both DRP and ME-BERT use in-batch random sampling and hard negative sampling from the results retrieved by BM25, while ANCE enhances the hard negative sampling by using the dense retriever." [KB-5]

这句话明确刻画了演进的两级：**DPR/ME-BERT = in-batch 随机负样本 + 从 BM25 结果中采样的难负样本**；**ANCE = 用稠密检索器本身来增强难负样本采样**。这为"DPR → ANCE → RocketQA"的负样本演进提供了直接的原文依据（以 RocketQA 的转述形式）。

### 3. 演进动因的原文支撑：in-batch 局部负样本是瓶颈、假负样本问题

RocketQA 引言（章节背景 P-140621b255be）指出双塔模型"typically ... trained by using in-batch random negatives"，并列出三大挑战：训练/推理差异、大量未标注正例（假负样本问题）、训练数据有限。其中假负样本问题直接指向"从 top-k 检索结果中采难负样本"的缺陷，这正是 RocketQA 提出 denoised hard negatives 的动机。

需要说明：上述"三大挑战"的详细表述来自**章节背景**，本步骤未在 [KB-n] 中找到对应原文句子，故不作为已校验结论引用；[KB-6] 仅概括为"the discrepancy between training and inference, the existence of unlabeled positives and limited training data"。

### 4. ANCE 的理论动因（材料有限）

章节背景 P-ed0e1d2d326b 中出现了 ANCE 论文的引言文字（如"local in-batch negatives lead to diminishing gradient norms ... the local negative sampling is the bottleneck of dense retrieval's effectiveness"），但这段文字**未以 [KB-n] 形式提供**，因此按本步骤规则不能作为已校验引用。就 [KB-n] 而言，ANCE 的负样本策略只通过 [KB-5] 的转述得到体现（"ANCE enhances the hard negative sampling by using the dense retriever"）。

### 5. 材料不足之处（明确说明）

- **DPR 论文本身是否报告负样本策略消融**：论文中未报告（给定 [KB-n] 中无 DPR 原文的消融结论，仅有 [KB-5] 对其方法的转述）。
- **ANCE 论文本身是否报告负样本策略消融**：论文中未报告（给定 [KB-n] 中无 ANCE 原文的消融结论，仅有 [KB-5] 的转述）。
- **RocketQA 各策略的逐项消融数值与结论**：论文中未在给定片段内报告（仅知存在 Table 3 与 Figure 4，见章节背景，但具体数值与结论未提供）。
- **"四篇论文"中的第四篇**：给定 [KB-n] 与章节背景均只涉及 RocketQA、ANCE（背景）、DPR（转述），无法识别第四篇，论文中未报告。

---

## 结论

就可校验的 [KB-n] 材料而言：

1. **RocketQA 确实报告了负样本相关策略的消融/对比实验**，其原文明确声称对 cross-batch negatives、denoised hard negatives、data augmentation 三项策略做了 extensive experiments 并证明有效 [KB-1][KB-2][KB-4][KB-6]。
2. **演进主线在原文中的直接支撑**来自 [KB-5]：DPR/ME-BERT 使用 in-batch 随机负样本 + BM25 难负样本，ANCE 用稠密检索器增强难负样本，而 RocketQA 进一步提出 cross-batch negatives 与 denoised hard negatives [KB-2][KB-4]，构成"局部 in-batch → 稠密检索器难负样本 → 跨批次负样本 + 去噪难负样本"的递进。
3. **但 DPR 与 ANCE 论文自身的消融实验结论、以及 RocketQA Table 3/Figure 4 的具体数值，均未在给定材料中报告**，因此"各论文是否报告消融"这一问题只能对 RocketQA 给出肯定回答，对其余论文应标注"论文中未报告（材料不足）"，不得据背景推测。

---

## 参考文献

[KB-1] RocketQA, 1 Introduction, p.2（用户专题论文库片段）

[KB-2] RocketQA, 1 Introduction, p.2（用户专题论文库片段）

[KB-3] 用户专题论文库片段（章节标注为 1 INTRODUCTION, p.2，内容为 ANCE 引言文字）

[KB-4] RocketQA, 5 Conclusions, p.9（用户专题论文库片段）

[KB-5] RocketQA, 4.2.1 Dense Passage Retrieval, p.7（用户专题论文库片段）

[KB-6] RocketQA, Abstract, p.1（用户专题论文库片段）

# 步骤取证归纳：DPR 论文负样本策略消融实验的原文结论

## 问题陈述

本步骤要求补充取证：**DPR 论文是否报告了负样本策略的消融或对比实验，其原文结论如何支撑 DPR→RocketQA 负样本策略的演进关系？**

## 研究发现的整理

### 1. 关于 DPR 负样本策略的"消融实验"：论文片段中未报告

在所提供的 DPR 原文片段中，**没有出现针对负样本采样策略（如随机负样本、BM25 负样本、in-batch 负样本）本身的独立消融实验表格或结论**。片段中唯一明确称为"ablation"的实验是**流水线训练 vs. 联合训练**的对比，而非负样本策略：

> "To compare our pipeline training approach with joint learning, we run an ablation on Natural Questions where the retriever and reader are jointly trained, following Lee et al. (2019). This approach obtains a score of 39.8 EM..." [KB-2]

另一个可视为"对比实验"的是**远程监督（distant supervision）正样本设置**的对比，同样属于正样本构造而非负样本策略：

> "When training our final DPR model using Natural Questions, we use the passages in our collection that best match the gold context as the positive passages... whether there is a significant performance degradation... Table 5 shows the performance of DPR when trained using the original setting and the distant supervision setting." [KB-4]

**结论：就本步骤所给材料而言，"DPR 论文负样本策略消融实验"的具体实验设置与数值结果——论文中未报告（至少未在所提供的 [KB-1]~[KB-5] 片段中出现）。**

### 2. DPR 负样本策略的定性结论（可作为演进起点的取证）

虽然缺少专门的消融表，但片段中有若干关于 DPR 负样本做法的**定性论断**，它们构成演进主线的起点：

- **任务本质要求区分全语料负样本**：DR 需从整个语料中区分相关文档，"irrelevant documents to separate are from the entire corpus (D- = C \ D+)"，从而产生"millions of negative instances"必须被采样 [KB-3]。
- **BM25 负样本的局限**：从 BM25 检索结果中采样负样本"may bias the DR model to merely learn sparse retrieval and do not elevate DR models much beyond BM25" [KB-3]。
- **in-batch 局部负样本的局限**：局部小批量负样本"do not significantly outperform BM25 negatives" [KB-3]；引言亦重申"the in-batch local negatives... are not significantly better than spare-retrieved negatives in representation learning for dense retrieval" [KB-5]。

这些论断表明：DPR 时代已认识到**负样本池的规模与来源**是关键瓶颈，但 DPR 本身（据片段）并未给出系统化的负样本策略消融。

### 3. 演进关系的原文支撑：从 DPR 到更大/全局负样本池

[KB-1] 明确把 ANCE 定位为沿"扩大负样本池"轨迹的推进，并直接以 DPR 为改进对象，这是支撑演进主线最直接的原文：

> "A parallel work (Xiong et al., 2020) improves DPR by sampling negatives from a memory bank... Instead of a bigger local pool, ANCE goes all the way along this trajectory and constructs negatives globally from the entire corpus, using an asynchronously updated ANN index." [KB-1]

[KB-5] 进一步点明 ANCE 的动机正是针对 DPR 式"随机或 in-batch 局部负样本"：

> "Instead of random or in-batch local negatives, ANCE constructs global negatives using the being-optimized DR model to retrieve from the entire corpus." [KB-5]

由此可见演进逻辑：**DPR（BM25/in-batch 负样本）→ 扩大负样本池（memory bank 等并行工作）→ ANCE（全语料全局负样本 + ANN 索引）**，这一链条由 [KB-1]、[KB-5] 的原文直接支撑。

### 4. 材料不足的部分（需明确标注）

- **DPR 负样本策略消融的具体数值/表格**：论文中未报告（所给片段内无）。
- **RocketQA 相关论文的负样本消融结论**：本步骤所给 [KB-1]~[KB-5] 全部来自 DPR 与 ANCE 论文，**未包含 RocketQA 论文的任何原文片段**，因此 RocketQA 一侧的负样本策略消融结论在本步骤材料中无法取证，论文中未报告（就本材料而言）。

## 结论

就本步骤所给材料而言，**DPR 论文并未报告针对负样本策略的专门消融实验**；片段中被称为 ablation 的是流水线 vs. 联合训练对比 [KB-2]，另有远程监督正样本对比 [KB-4]，二者均非负样本策略消融。DPR 对负样本的贡献主要是**定性诊断**：全语料负样本规模巨大 [KB-3]、BM25 负样本与 in-batch 局部负样本均难以显著超越 BM25 [KB-3][KB-5]。正是这些诊断，被 ANCE 原文明确接续为"扩大负样本池直至全语料全局负样本"的演进轨迹 [KB-1][KB-5]，从而支撑 DPR→（更大负样本池）→ANCE 的演进关系。至于 RocketQA 一侧的负样本消融证据，本步骤材料未提供，论文中未报告（就本材料而言），需在后续步骤补充其原文片段后再行归纳。

## 参考文献

[KB-1] ANCE 论文，7 RELATED WORK，第 8 页（用户专题论文库片段）

[KB-2] DPR 论文，6.2 Results，第 8 页（用户专题论文库片段）

[KB-3] ANCE 论文，2 PRELIMINARIES，第 2 页（用户专题论文库片段）

[KB-4] DPR 论文，Appendix A Distant Supervision，第 12 页（用户专题论文库片段）

[KB-5] ANCE 论文，1 INTRODUCTION，第 2 页（用户专题论文库片段）

## 证据与分歧

- 2004.04906（ACCEPTED）
- 2004.04906（ACCEPTED）
- 2004.04906（ACCEPTED）
- 2007.00808（ACCEPTED）
- 2007.00808（ACCEPTED）
- 2007.00808（ACCEPTED）
- 2010.08191（ACCEPTED）
- 2010.08191（ACCEPTED）
- 2010.08191（ACCEPTED）
- 2010.08191（ACCEPTED）
- 检出 1 条存在反向支持关系的来源，需要单独核对条件和适用范围。
- 有 3 条来源仍待引用复核，不能作为无条件结论。

## 覆盖度与缺口

- 已检查来源：10 条；已完成步骤：4 / 4。

## 来源清单

- 2004.04906
- 2004.04906
- 2004.04906
- 2007.00808
- 2007.00808
- 2007.00808
- 2010.08191
- 2010.08191
- 2010.08191
- 2010.08191