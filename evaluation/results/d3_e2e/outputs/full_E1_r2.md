# E1 · full · round 2

RocketQA 的去噪（denoising）策略在论文的哪一部分？具体做了什么？

---

# 研究报告

## 直接回答

本报告汇总了 3 个已完成研究步骤的发现；逐项依据见下方证据与局限。

**研究问题**：RocketQA 的去噪（denoising）策略在论文的哪一部分？具体做了什么？

## 资料范围

- 专题内全部就绪资料（未限定单篇）

## 逐项发现

### 1. RocketQA 论文中哪一章节/部分明确讨论或提出去噪（denoising）策略？（COMPLETED）

## 问题陈述

本步骤要回答的问题是：RocketQA 论文中哪一章节/部分明确讨论或提出去噪（denoising）策略？该策略具体做了什么？

## 研究取证

### 一、去噪策略在论文中的定位

根据用户专题论文库中的原文，去噪（denoised hard negatives）策略是 RocketQA 的三大技术贡献之一，在论文的多个部分被提及：

1. **摘要（Abstract）**：明确将 "denoised hard negatives" 列为 RocketQA 的三大主要技术贡献之一，原文为 "We make three major technical contributions in RocketQA, namely crossbatch negatives, denoised hard negatives and data augmentation" [KB-2]。

2. **引言（Introduction）**：同样将其列为三大贡献之一，原文为 "The proposed RocketQA introduces three novel training strategies to improve dense passage retrieval for open-domain QA, namely cross-batch negatives, denoised hard negatives, and data augmentation" [KB-3]；并在贡献总结中再次强调 "RocketQA introduces denoised hard negatives" [KB-1]。

3. **方法（Approach, 第3节）**：原文说明该节将 "describe the three novel training strategies in RocketQA" [KB-6]，即三大训练策略（含去噪策略）的具体方法描述位于第 3 节 Approach 中。

4. **结论（Conclusions, 第5节）**：再次将其列为三大贡献之一，原文为 "namely cross-batch negatives, denoised hard negatives and data augmentation" [KB-5]。

**小结**：去噪策略作为核心贡献被提出/概述于 Abstract 与 Introduction [KB-1][KB-2][KB-3]，其具体方法描述对应第 3 节 Approach [KB-6]，并在 Conclusions 中被总结 [KB-5]。

### 二、去噪策略具体做了什么

论文库中对该策略"具体做法"的直接描述，最明确的是引言中的贡献说明：

> "Second, RocketQA introduces denoised hard negatives. It aims to remove false negatives from the top-ranked results retrieved by a retriever, and derive more reliable hard negatives." [KB-1]

即：去噪硬负例策略的目标是**从检索器返回的 top-ranked 结果中移除假负例（false negatives），从而得到更可靠的硬负例（hard negatives）** [KB-1]。

其动机在引言中也有交代：由于存在大量未标注的正例（unlabeled positives），从 top-k 检索结果中采样硬负例时容易引入假负例；论文以 MSMARCO 为例指出人工检查发现 top-retrieved 但原数据集未标注为正例的段落中有 70% 实际上是正例（此段属于章节背景，未在 [KB-n] 中校验，故仅作背景说明，不作结论引用）。

### 三、材料不足之处

- 去噪策略的**具体实现细节**（例如使用何种交叉编码器过滤、阈值设定、过滤流程的公式或算法步骤）在所提供的 [KB-n] 原文片段中**未报告**，这些内容应位于第 3 节 Approach 的具体小节中，但本步骤材料未包含该小节的正文，故不做推测。
- 去噪策略的**实验效果数据**（如消融实验的具体数值）在 [KB-n] 中**未报告**，仅笼统说明"实验验证了三种策略的有效性" [KB-4][KB-5]。

## 结论

在 RocketQA 论文中，去噪策略（denoised hard negatives）作为三大核心技术贡献之一，在 **Abstract [KB-2]、Introduction [KB-1][KB-3]、Approach（第3节）[KB-6] 和 Conclusions [KB-5]** 中被明确提出与讨论；其中第 3 节 Approach 是对该策略进行具体方法描述的章节 [KB-6]。就"具体做了什么"而言，[KB-1] 明确指出其目的是**从检索器 top-ranked 结果中移除假负例，以获得更可靠的硬负例**。至于该策略的详细实现步骤与实验数值，所给材料中未报告，需查阅第 3 节相应小节的完整正文。

## 参考文献

[KB-1] 用户专题论文库：RocketQA 论文，1 Introduction，页码 2

[KB-2] 用户专题论文库：RocketQA 论文，Abstract，页码 1

[KB-3] 用户专题论文库：RocketQA 论文，1 Introduction，页码 2

[KB-4] 用户专题论文库：RocketQA 论文，1 Introduction，页码 2

[KB-5] 用户专题论文库：RocketQA 论文，5 Conclusions，页码 9

[KB-6] 用户专题论文库：RocketQA 论文，3 Approach，页码 3

# 研究步骤取证：RocketQA 去噪策略所在的章节与具体做法

## 问题陈述

本步骤需要确认：RocketQA 论文中哪一章节/部分明确讨论或提出去噪（denoising）策略，并补充取证其具体实现细节（交叉编码器过滤流程、阈值设定、去噪硬负例的算法步骤）。

## 研究发现

### 1. 去噪策略出现的位置：第 3.4 节 "The Training Procedure"

用户论文库中的原文片段明确将该节指向去噪硬负例的讨论，并进一步把实现细节指向第 4 节。原文写道：

> "The implementation details of denoising hard negatives and data augmentation can be found in Section 4." [KB-1]

> "The cross-encoder is used both STEP 3 and STEP 4 with different purposes to promote the performance of the dual encoder." [KB-1]

由此可确认：**去噪策略在论文第 3.4 节 "The Training Procedure" 中被提出（作为 STEP 3 的核心机制），而具体实现细节位于第 4 节** [KB-1]。

### 2. 去噪策略的定位：训练流程中的 STEP 3

根据 [KB-1] 所在章节，去噪硬负例采样是训练流程中 STEP 3 引入的机制，用于进一步训练双编码器（dual-encoder）。原文同时强调跨批次负例（cross-batch negative）策略贯穿所有步骤，而交叉编码器（cross-encoder）在 STEP 3 与 STEP 4 中被用于不同目的 [KB-1]。

### 3. 具体实现细节（交叉编码器过滤流程、阈值设定、算法步骤）

关于本步骤要求补充取证的三项内容——交叉编码器过滤流程、阈值设定的具体数值、去噪硬负例的算法步骤——用户论文库提供的 [KB-1] 原文**仅给出了指向性说明**，即这些细节"can be found in Section 4" [KB-1]，但**未在 [KB-1] 中给出任何具体实现内容**。

因此，就现有材料而言：

- **交叉编码器过滤流程的具体步骤**：论文中未报告（[KB-1] 仅指向 Section 4，未展开）[KB-1]。
- **阈值设定的具体数值**：论文中未报告（[KB-1] 未提及任何阈值）[KB-1]。
- **去噪硬负例的算法步骤**：论文中未报告（[KB-1] 仅说明细节在 Section 4）[KB-1]。

## 结论

基于用户专题论文库的原文，可以确认：**RocketQA 的去噪（denoising）策略在第 3.4 节 "The Training Procedure" 中被提出，作为训练流程 STEP 3 的核心机制；其具体实现细节（去噪硬负例与数据增强）则被明确指向第 4 节** [KB-1]。交叉编码器在 STEP 3 与 STEP 4 中被用于不同目的以提升双编码器性能 [KB-1]。

但关于**交叉编码器过滤的具体流程、阈值设定数值、去噪硬负例的算法步骤**，现有 [KB-1] 原文片段并未展开，属于"论文中未报告"（在已提供的材料范围内），需查阅第 4 节原文方可进一步取证，本步骤不作猜测。

## 参考文献

[KB-1] 用户专题论文库原文片段：章节 3.4 The Training Procedure，页码 5（RocketQA 论文）

### 2. RocketQA 的去噪策略具体包含哪些操作步骤或机制？（COMPLETED）

## 问题陈述

本步骤要回答的是：RocketQA 的去噪（denoising）策略具体包含哪些操作步骤或机制？以及它属于论文的哪一部分。

## 研究结论

### 一、去噪策略在论文中的位置

去噪策略是 RocketQA 三大技术贡献之一，名为“denoised hard negatives”（去噪难负例）。它作为三大贡献之一在 **Abstract** 中被明确提出 [KB-1]，并在 **1 Introduction** 中被展开介绍 [KB-2][KB-4]，在 **5 Conclusions** 中再次被总结 [KB-6]。因此，去噪策略在论文的摘要、引言和结论部分都有出现；引言部分给出了它的定义与动机。

### 二、去噪策略的具体操作步骤或机制

根据论文引言中的描述，去噪策略的机制可以归纳为：

1. **目标：去除假负例（false negatives）**。该策略“aims to remove false negatives from the top-ranked results retrieved by a retriever”（旨在从检索器返回的 top-ranked 结果中去除假负例）[KB-2]。

2. **结果：获得更可靠的难负例**。通过上述去噪处理，“derive more reliable hard negatives”（从而得到更可靠的难负例）[KB-2]。

3. **动机：解决未标注正例（unlabeled positives）问题**。论文指出，由于存在大量未标注正例，从 top-k 检索结果中采样难负例时“it is likely to bring false negatives when sampling hard negatives from the top-k retrieved passages”（很可能会引入假负例）[KB-2]，去噪策略正是针对这一问题提出的。

### 三、材料未覆盖的部分

需要说明的是：上述 [KB-n] 原文片段只给出了去噪策略的**定义、目标与动机**，即“从 top-ranked 检索结果中去除假负例、得到更可靠难负例”。至于去噪的**具体实现细节**，例如用什么模型判定假负例、设定什么阈值、分几个阶段迭代、如何重新标注等逐步操作，本步骤提供的原文片段中均**未报告**，因此不做推测。

## 参考文献

[KB-1] RocketQA, Abstract, 页码 1

[KB-2] RocketQA, 1 Introduction, 页码 2

[KB-3] RocketQA, 1 Introduction, 页码 2

[KB-4] RocketQA, 1 Introduction, 页码 2

[KB-5] RocketQA, 1 Introduction, 页码 2

[KB-6] RocketQA, 5 Conclusions, 页码 9

# RocketQA 去噪（Denoising）策略取证归纳

## 问题陈述

本步骤要回答：RocketQA 的去噪策略具体包含哪些操作步骤或机制？即 "denoised hard negatives" 的具体实现方法——用交叉编码器过滤假负例的流程、阈值与步骤。

## 研究结果

### 1. 去噪策略在论文中的位置

去噪策略的**流程性描述**出现在第 3.4 节 "The Training Procedure" 的 STEP 3 中，而**具体实现细节**（阈值等）出现在第 4.1.3 节 "Implementation Details" 的 "Denoised hard negatives and data augmentation" 段落。论文明确说明："The implementation details of denoising hard negatives and data augmentation can be found in Section 4" [KB-2]。

### 2. 去噪的机制：用交叉编码器过滤假负例

去噪的核心是借助交叉编码器（cross-encoder）MC 对双编码器检索出的"硬负例"进行二次判定，从而剔除那些实际上与问题相关、却被误当作负例的"假负例"（false negatives）。

在 STEP 3 中，对每个带标注的问题 q ∈ QL，硬负例从 M(0)D 检索出的 top passages 中采样，**只有被交叉编码器 MC 以高置信度判定为负例的段落才会被选中** [KB-2]。原文为："the hard negatives are sampled from the top passages retrieved by M(0)D from C, and only the passages that are predicted as negatives by the cross-encoder MC with high confidence scores will be selected" [KB-2]。

### 3. 具体阈值与判定规则

第 4.1.3 节给出了可操作的量化阈值 [KB-1]：

- **负例筛选**：选取 top retrieved passages 中交叉编码器得分**小于 0.1** 的段落作为负例；
- **正例（数据增强）筛选**：得分**大于 0.9** 的段落作为正例；
- 去噪与数据增强**共用同一个交叉编码器**（"We use the cross-encoder for both denoising hard negatives and data augmentation"）[KB-1]。

原文："we select the top retrieved passages with scores less than 0.1 as negatives and those with scores higher than 0.9 as positives" [KB-1]。

### 4. 质量验证

论文对筛选出的数据做了人工评估，报告准确率高于 90%："We manually evaluated the selected data, and the accuracy was higher than 90%" [KB-1]。这为 0.1 / 0.9 阈值的有效性提供了经验支撑。

### 5. 去噪与数据增强的关系（STEP 3 vs STEP 4）

交叉编码器在 STEP 3 和 STEP 4 中被用于不同目的："The cross-encoder is used both STEP 3 and STEP 4 with different purposes" [KB-2]。STEP 3 用于去噪硬负例（提升 M(1)D），STEP 4 则用 MC 对 M(1)D 检索的 top-k 段落打标签以构造伪训练数据 DU（数据增强）[KB-2]。两者共享同一套阈值机制 [KB-1]。

## 结论

RocketQA 的去噪策略位于论文 **3.4 节 STEP 3（机制描述）** 与 **4.1.3 节（实现细节）**。其操作可归纳为：(1) 由 M(0)D 检索 top passages 采样硬负例；(2) 用交叉编码器 MC 对候选段落打分；(3) 以 **< 0.1** 判定为负例、**> 0.9** 判定为正例的阈值过滤假负例；(4) 人工抽检确认准确率 > 90% [KB-1][KB-2]。同一交叉编码器与阈值体系在 STEP 4 中复用于数据增强 [KB-2]。

**论文中未报告**的部分：材料未给出交叉编码器得分的具体归一化方式、阈值 0.1/0.9 的选取依据，以及去噪前后假负例比例的量化统计，这些均无法从现有 [KB-1][KB-2] 中得出。

## 参考文献

[KB-1] RocketQA, Section 4.1.3 "Implementation Details", p.6 — "Denoised hard negatives and data augmentation"

[KB-2] RocketQA, Section 3.4 "The Training Procedure", p.5 — STEP 3 / STEP 4 描述

### 3. 论文是否给出该去噪策略的设计动机或与其他方法的对比说明？（COMPLETED）

# 研究步骤归纳：去噪策略的设计动机与对比说明

## 问题陈述

本步骤要回答的是：RocketQA 论文是否为"去噪硬负例（denoised hard negatives）"策略给出了设计动机，或与其他方法（尤其是常规硬负例采样方法）做了对比说明。

## 研究结果

### 1. 论文明确给出了去噪策略的设计动机

论文在引言中把"去噪"作为三项主要技术贡献之一提出，并直接说明其目的：从检索器返回的高排名结果中剔除假负例（false negatives），从而获得更可靠的硬负例。原文为："Second, RocketQA introduces denoised hard negatives. It aims to remove false negatives from the top-ranked results retrieved by a retriever, and derive more reliable hard negatives." [KB-1]

摘要层面也把"denoised hard negatives"列为三大贡献之一，并指出其针对的挑战包括"the existence of unlabeled positives" [KB-2]，即大量未标注正例的存在。

### 2. 动机的"问题来源"：假负例风险

虽然用户库中未直接收录引言中关于假负例统计的完整段落，但从已收录的 [KB-1] 可确认：去噪的动机是"top-ranked results"中存在假负例，需要将其移除。引言背景（非已校验结论）进一步指出，MSMARCO 中每个问题平均只有 1.1 个标注正例，而人工检查发现 top 检索结果中约 70% 未标注正例实际是正例，因此从 top-k 结果中采样硬负例容易引入假负例。**该 70% 等具体数字未出现在用户提供的 [KB-n] 原文片段中，属于章节背景材料，不能作为已校验结论引用。**

### 3. 与其他方法的对比说明

论文在引言中确实做了方法层面的对比，但对比对象是"in-batch negatives"与"cross-batch negatives"，而非直接针对去噪策略与常规硬负例采样的定量对比。原文："Comparing to inbatch negatives, it increases the number of available negatives for each question during training, and alleviates the discrepancy between training and inference." [KB-1] 这一对比针对的是 cross-batch negatives，不是去噪策略。

对于去噪策略本身，论文的"对比"体现为**机制性说明**而非独立的方法对比段落：在训练流程 STEP 3 中，硬负例从 M(0)_D 检索的 top passages 中采样，且"only the passages that are predicted as negatives by the cross-encoder MC with high confidence scores will be selected" [P-409d5b5a8137 对应 KB-5 同段内容；用户库中 [KB-5] 仅收录了该节末尾关于 cross-batch 与 cross-encoder 用途的说明]。需要说明：**STEP 3 的完整描述出现在章节背景 P-409d5b5a8137 中，而用户库 [KB-5] 只收录了该节最后一段**，因此严格按 [KB-n] 引用，只能确认"cross-encoder 在 STEP 3 和 STEP 4 中用于不同目的以提升 dual-encoder 性能"，以及"去噪硬负例的实现细节见 Section 4" [KB-5]。

### 4. 实验层面的有效性验证（非方法对比）

论文在摘要与结论中说明对三种策略做了大量实验以检验有效性："We also conduct extensive experiments to examine the effectiveness of the three strategies in RocketQA." [KB-2]；"Extensive experiments have shown the effectiveness of the proposed approach by incorporating the three optimization strategies." [KB-3] 这属于对策略整体有效性的验证，**不是去噪策略与其他采样方法的对照实验说明**。

### 5. 材料不足之处

- 论文是否给出"去噪硬负例 vs 普通硬负例采样"的**专门消融对比实验**：用户提供的 [KB-n] 片段中**未报告**具体消融设置与数字，仅笼统提及对三种策略做了实验 [KB-2][KB-3]。
- 去噪策略的具体实现细节（如置信度阈值、采样数量）：[KB-5] 仅指向"Section 4"，**该节原文未收录，论文中未在所提供的片段中报告**。
- 引言中关于假负例比例（如 70%）的定量动机：**未出现在所提供的 [KB-n] 原文中**，仅有章节背景材料提及，不能作为已校验结论。

## 结论

就本步骤的问题而言：**论文确实给出了去噪策略的设计动机**——即移除 top 排名结果中的假负例、获得更可靠的硬负例，并将其列为 RocketQA 三大贡献之一 [KB-1][KB-2]。在**对比说明**方面，论文在引言中明确对比了 cross-batch negatives 与 in-batch negatives 的差异 [KB-1]，并通过训练流程说明 cross-encoder 在 STEP 3 中用于筛选高置信度负例、在 STEP 4 中用于数据增广的不同用途 [KB-5]；但**针对去噪策略与常规硬负例采样方法的专门对比实验，在所提供的 [KB-n] 片段中未报告**，仅笼统说明对三种策略做了有效性实验 [KB-2][KB-3]。去噪的具体实现细节位于 Section 4，该部分原文未收录，故**论文中（就现有材料而言）未报告**其细节。

## 参考文献

[KB-1] RocketQA 论文，1 Introduction，第 2 页

[KB-2] RocketQA 论文，Abstract，第 1 页

[KB-3] RocketQA 论文，5 Conclusions，第 9 页

[KB-4] RocketQA 论文，4.2.3 Passage Reading with RocketQA，第 9 页

[KB-5] RocketQA 论文，3.4 The Training Procedure，第 5 页

[KB-6] RocketQA 论文，4.2.3 Passage Reading with RocketQA，第 9 页

# 取证归纳：RocketQA 去噪硬负例策略的出处与实现细节

## 一、问题陈述

本步骤需确认两件事：(1) RocketQA 的"去噪硬负例（denoised hard negatives）"策略在论文中出现在哪一部分、具体做了什么；(2) 该策略的设计动机、以及与其他方法的对比说明是否在论文中给出。重点补充 Section 4 中关于置信度阈值、采样数量与过滤流程的实现细节。

## 二、研究结果

### 1. 去噪策略在论文中的位置

去噪硬负例是 RocketQA 提出的三项核心训练策略之一，与 cross-batch negatives、data augmentation 并列，在引言中被明确列为贡献点 [KB-1]。

在训练流程中，该策略对应 **STEP 3**，即"在 DL 上进一步引入去噪硬负例采样来训练双编码器 M(1)D" [KB-2]。因此，其**策略层面的位置是 Section 3.4（The Training Procedure）的 STEP 3**。

需要特别指出的是，Section 3.4 原文明确写道："The implementation details of denoising hard negatives and data augmentation can be found in **Section 4**"（去噪硬负例与数据增强的实现细节见 Section 4）。也就是说，**Section 3.4 只给出策略框架，具体实现细节位于 Section 4**。

### 2. 具体做了什么（基于原文可确认的部分）

根据 [KB-2]，STEP 3 的具体做法为：

- **负例来源**：对每个问题 q ∈ QL，硬负例从 M(0)D 从语料库 C 中检索出的 top passages 中采样 [KB-2]。
- **过滤条件**：只有被 cross-encoder MC **以高置信度分数（high confidence scores）预测为负例**的段落才会被选中 [KB-2]。

即整体流程为"双编码器检索 top 段落 → 交叉编码器打分 → 仅保留被高置信度判为负例的段落 → 用作硬负例训练双编码器"。这一"检索—交叉编码器复核—过滤"的流程即为去噪（denoising）的核心机制。

### 3. 设计动机与对比说明

论文在引言中给出了该策略的问题背景，可视为其设计动机：

- 为缩小训练与推理之间的候选集差异，已有工作尝试从 top-k 检索候选中选取少量硬负例，但"由于下面的挑战，会遭受假负例（false negative）问题" [KB-1 所在章节背景，见 P-140621b255be]。
- 论文以 MSMARCO 为例指出，每个问题平均仅有 1.1 个标注正例，而语料库有 8.8M 段落；作者人工检查 top 检索结果中未被标注为正例的段落，发现其中 **70% 实际上是正例**，因此从 top-k 检索段落中采样硬负例很可能引入假负例 [同上背景]。

上述内容说明了"为何需要对硬负例去噪"的动机。但需注意：**这些动机论述来自 Introduction 的章节背景文本，而非本步骤所绑定的 [KB-1]、[KB-2] 原文片段**；[KB-1] 仅列出三项策略名称，[KB-2] 仅描述 STEP 3 的操作。因此，就本步骤已校验的 [KB-n] 原文而言，**论文是否给出与其他去噪方法的直接对比说明，论文中未报告（在所提供的 [KB-1]、[KB-2] 片段范围内无法确认）**。

### 4. Section 4 的实现细节（置信度阈值、采样数量、过滤流程）

- **置信度阈值**：论文中未报告（[KB-1]、[KB-2] 仅提及"high confidence scores"，未给出具体阈值数值）。
- **采样数量**：论文中未报告（原文只说从 top passages 中采样，未给出具体采样个数）。
- **过滤流程的量化细节**：论文中未报告（[KB-2] 只描述了"检索→交叉编码器高置信度判负→选中"的定性流程，未给出分步参数）。

以上三项的具体数值与参数均指向 Section 4，但本步骤提供的原文片段未包含 Section 4 的内容，故无法据实归纳，需补充 Section 4 原文后再行取证。

## 三、结论

RocketQA 的去噪硬负例策略在**策略层面出现于 Section 3.4 的 STEP 3**，并被引言列为三大训练策略之一 [KB-1][KB-2]。其具体做法是：从 M(0)D 检索的 top 段落中采样硬负例，且仅保留被交叉编码器 MC 以高置信度判为负例的段落用于训练双编码器 [KB-2]。论文明确将**实现细节指向 Section 4**，但本步骤所绑定的原文片段未包含 Section 4，因此置信度阈值的具体数值、采样数量、以及过滤流程的量化参数均**论文中未报告**（在现有 [KB-n] 范围内），设计动机的完整论述与其他方法的对比说明亦需以 Introduction 与 Section 4 原文进一步核实，不能据现有片段下结论。

## 四、参考文献

[KB-1] RocketQA 论文，Section 1 Introduction，第 2 页（用户专题论文库原文片段）

[KB-2] RocketQA 论文，Section 3.4 The Training Procedure，第 5 页（用户专题论文库原文片段）

## 证据与分歧

- 2010.08191（ACCEPTED）
- 2010.08191（ACCEPTED）
- 2010.08191（ACCEPTED）
- 有 1 条来源仍待引用复核，不能作为无条件结论。

## 覆盖度与缺口

- 已检查来源：3 条；已完成步骤：3 / 3。
- 当前已接纳来源没有标记为反向证据；这不等于已经证明不存在冲突。

## 来源清单

- 2010.08191
- 2010.08191
- 2010.08191