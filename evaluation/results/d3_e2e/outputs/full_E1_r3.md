# E1 · full · round 3

RocketQA 的去噪（denoising）策略在论文的哪一部分？具体做了什么？

---

# 研究报告

## 直接回答

本报告汇总了 3 个已完成研究步骤的发现；逐项依据见下方证据与局限。

**研究问题**：RocketQA 的去噪（denoising）策略在论文的哪一部分？具体做了什么？

## 资料范围

- 专题内全部就绪资料（未限定单篇）

## 逐项发现

### 1. RocketQA 论文在哪个章节或小节中提出并描述去噪（denoising）策略？（COMPLETED）

## 问题陈述

本步骤要回答的问题是：RocketQA 论文在哪个章节或小节中提出并描述去噪（denoising）策略？具体做了什么？

## 取证归纳

### 1. 去噪策略在论文中的提出位置

根据现有原文片段，去噪策略（denoised hard negatives）在论文中作为 RocketQA 的三大技术贡献之一被提出，主要出现在以下位置：

- **摘要（Abstract）**：将 "denoised hard negatives" 列为 RocketQA 的三大技术贡献之一 [KB-4]。
- **引言（1 Introduction）**：同样将 "denoised hard negatives" 列为三项新训练策略之一 [KB-2]，并对该策略的目的做了说明 [KB-3]。
- **方法（3 Approach）**：该节明确声明会"describe the three novel training strategies in RocketQA"（描述 RocketQA 的三个新训练策略），因此去噪策略的具体描述应位于第 3 节 Approach 之中 [KB-6]。
- **结论（5 Conclusions）**：再次将 "denoised hard negatives" 列为三大技术贡献之一 [KB-1]。

需要说明的是：现有片段只能确认第 3 节 Approach 是"描述三个训练策略"的章节 [KB-6]，但**具体是第 3 节下的哪一个子小节（subsection）提出并详细描述去噪策略，论文中未报告**（提供的片段未包含该子小节标题或编号）。

### 2. 去噪策略具体做了什么

根据 [KB-3] 的原文表述：

> "Second, RocketQA introduces denoised hard negatives. It aims to remove false negatives from the top-ranked results retrieved by a retriever, and derive more reliable hard negatives." [KB-3]

即去噪策略的核心作用是：**从检索器返回的 top-ranked（排名靠前）结果中移除假负例（false negatives），从而得到更可靠的难负例（hard negatives）** [KB-3]。

其动机在引言背景中也有交代：由于存在大量未标注的正例（unlabeled positives），仅从 top-k 检索结果中采样难负例容易引入假负例（false negatives）[章节背景 P-140621b255be，仅作理解背景，非 [KB-n] 校验结论]。这与 [KB-3] 所述"移除假负例、获得更可靠难负例"的目标一致。

### 3. 材料不足之处

- 去噪策略的**具体实现细节**（如用什么模型判定假负例、具体流程、参数设置等）：**论文中未报告**（所提供的 [KB-n] 片段未包含这些内容）。
- 去噪策略在**第 3 节内的具体子小节编号/标题**：**论文中未报告**（片段仅显示第 3 节整体会描述三个策略 [KB-6]）。

## 结论

RocketQA 的去噪策略（denoised hard negatives）作为三大技术贡献之一，在**摘要 [KB-4]、引言 [KB-2][KB-3]、方法（第 3 节 Approach）[KB-6]、结论 [KB-1]** 中被提出和提及，其中第 3 节 Approach 是集中"描述三个新训练策略"的章节 [KB-6]。其具体做法是：**从检索器 top-ranked 结果中移除假负例（false negatives），以获得更可靠的难负例（hard negatives）** [KB-3]。至于第 3 节内的具体子小节位置及实现细节，现有材料未报告，无法确认。

## 参考文献

[KB-1] 用户专题论文库片段：RocketQA，章节 5 Conclusions，页码 9

[KB-2] 用户专题论文库片段：RocketQA，章节 1 Introduction，页码 2

[KB-3] 用户专题论文库片段：RocketQA，章节 1 Introduction，页码 2

[KB-4] 用户专题论文库片段：RocketQA，章节 Abstract，页码 1

[KB-5] 用户专题论文库片段：RocketQA，章节 1 Introduction，页码 2

[KB-6] 用户专题论文库片段：RocketQA，章节 3 Approach，页码 3

# 研究取证归纳：RocketQA 去噪（Denoising）策略的章节位置与具体做法

## 问题陈述

本步骤需回答两个问题：(1) RocketQA 论文在哪个章节/小节中提出并描述去噪（denoising）策略？(2) 该策略具体做了什么，尤其是"denoised hard negatives"的实现细节与假负例（false negatives）判定方法。

## 研究结论

### 一、去噪策略所在的章节位置

去噪策略以 **"Denoised Hard Negatives"** 为小标题，出现在 **3.3 Optimized Training Approach** 小节中，是作者针对双编码器训练三大挑战所提出的三项改进训练策略之一（另两项为 Cross-batch Negatives 和数据增强）。原文明确写道："Denoised Hard Negatives Although the above strategy can increase the number of negatives, most of negatives are easy ones..." [KB-1]。

此外，**3.4 The Training Procedure** 小节将去噪策略组织进完整训练流程（STEP 3），并明确指出其实现细节的位置："The implementation details of denoising hard negatives and data augmentation can be found in Section 4." [KB-2]。因此，**方法论描述在 3.3，流程位置在 3.4 的 STEP 3，实现细节被指向 Section 4**。

### 二、去噪策略要解决的问题（动机）

- **难负例的重要性**：增加负例数量后，大多数负例是"容易区分"的简单负例，而难负例对训练双编码器很重要 [KB-1]。
- **直接取 top-ranked passages 会引入假负例**：直观方法是把排名靠前的段落（排除已标注正例）当作负例，但由于标注者只能标注少量 top-retrieved 段落，这会带来假负例（即未标注的正例）[KB-1]。
- **既有去噪方法不适用于非事实型问题**：以往工作主要针对答案简短的事实型（factoid）问题，可用短答案过滤假负例；但该方法无法用于非事实型（non-factoid）问题。本文要同时面向两类问题，因此"needs a more effective way for denoising hard negatives" [KB-1]。

### 三、具体做法：用交叉编码器去噪

核心思路是**利用训练良好的交叉编码器（cross-encoder）剔除被检索出的、很可能是假负例的 top-retrieved 段落** [KB-1]。理由在于交叉编码器通过深层交互捕捉语义相似度，能力更强、更稳健，但在大规模候选上推理效率低 [KB-1]。

具体流程为：先训练一个交叉编码器；然后在从稠密检索器召回的 top-ranked 段落中采样难负例时，**只选择被交叉编码器以高置信度判定为负例的段落** [KB-1]。

这一做法在 3.4 的流程中被落实为 **STEP 3**：对每个问题，从 M(0)_D 召回的 top passages 中采样难负例，"only the passages that are predicted as negatives by the cross-encoder MC with high confidence scores will be selected" [KB-2]。

### 四、假负例判定方法小结

- **判定器**：一个先行训练的 cross-encoder（架构见 Figure 1b）[KB-1]。
- **判定原则**：交叉编码器以**高置信度**预测为负例的段落才被保留为难负例；疑似假负例（未标注正例）被剔除 [KB-1][KB-2]。
- **采样来源**：从稠密检索器 M(0)_D 召回的 top-ranked passages 中采样（排除已标注正例）[KB-1][KB-2]。

## 材料不足之处

- **"高置信度"的具体阈值、置信度打分形式、采样数量等定量实现细节**：论文中未报告（原文仅指向 Section 4 "The implementation details of denoising hard negatives and data augmentation can be found in Section 4." [KB-2]，而 Section 4 的内容未包含在本步骤提供的材料中）。
- 交叉编码器的具体训练损失、超参数等：论文中未报告（本材料范围内）。

## 参考文献

[KB-1] RocketQA 论文，3.3 Optimized Training Approach，"Denoised Hard Negatives" 段落，第 4 页。

[KB-2] RocketQA 论文，3.4 The Training Procedure，第 5 页（含 STEP 3 描述及指向 Section 4 的实现细节说明）。

### 2. RocketQA 去噪策略的具体做法是什么（如对负样本/训练数据如何处理）？（COMPLETED）

## 问题陈述

本步骤要回答的问题是：RocketQA 的去噪（denoising）策略具体是什么？即它对负样本／训练数据做了哪些处理？依据用户专题论文库中的原文片段进行归纳。

## 研究结论

### 一、去噪策略所在的论文部分

去噪策略在论文中主要出现在以下三处：

1. **第 3.3 节 "Optimized Training Approach"**：作为三大训练策略之一，以 "Denoised Hard Negatives" 为小标题正式提出，阐述动机与方法思路 [KB-4]。
2. **第 3.4 节 "The Training Procedure"**：在整体训练流程中落到具体步骤（STEP 3），并说明实现细节放在第 4 节 [KB-2]。
3. **第 4.2.2 节 "The Effectiveness of The Three Training Strategies in RocketQA"**：通过实验验证去噪硬负样本的效果，并给出采样前后的对比分析 [KB-3]。

此外，第 1 节 Introduction 将其列为 RocketQA 的三大技术贡献之一 [KB-1]。

### 二、去噪策略的具体做法

**1. 问题动机：硬负样本中混有"假负样本"**

论文指出，虽然增加负样本数量有效，但多数负样本是"容易区分"的简单负样本，而训练双编码器需要硬负样本（hard negatives）[KB-4]。最直接的硬负样本获取方式是选取排名靠前的段落（排除已标注正样本）作为负样本，但这容易引入假负样本（即未标注的正样本），因为标注者只能标注少量排名靠前的段落 [KB-4]。论文还强调，以往工作主要针对答案简短的事实型问题，可以用短答案过滤假负样本，但该方法不适用于非事实型问题，因此需要"更有效的去噪方式" [KB-4]。

**2. 核心做法：用 cross-encoder 对硬负样本做去噪筛选**

论文提出的做法是"denoised hard negatives"，即利用一个强大的 cross-encoder 来过滤硬负样本，得到更可靠的硬负样本 [KB-1][KB-3]。在训练流程 STEP 3 中具体描述为：对每个问题 q，硬负样本从双编码器 M(0)D 检索出的排名靠前段落中采样，**只有被 cross-encoder MC 以高置信度分数预测为负样本的段落才会被选中** [KB-2]。

**3. 在整体流程中的位置与配套设计**

- 去噪硬负样本用于 STEP 3 训练双编码器 M(1)D；cross-encoder 在 STEP 3 和 STEP 4 中承担不同用途以提升双编码器性能 [KB-2]。
- 跨批次负样本（cross-batch negatives）策略贯穿训练双编码器的所有步骤 [KB-2]。
- 去噪硬负样本与数据增强的实现细节，论文说明可参见第 4 节 [KB-2]。

**4. 实验证据与效果**

- **不去噪的后果**：直接引入未去噪的硬负样本会使检索器性能显著下降，论文推测原因是存在大量未标注正样本 [KB-3]。
- **假负样本比例**：人工检查 100 个问题的排名靠前但未标注为正样本的段落，发现约 70% 实际上是正样本或高度相关 [KB-3]。
- **去噪后的效果**：采用 cross-encoder 去噪后的硬负样本可提升双编码器检索性能 [KB-3]。
- **过滤分布规律**：排名越低的位置被过滤（去噪）的段落越多，因为低排名处更可能存在假负样本 [KB-3]。

## 说明

- 关于去噪的具体实现细节（如 cross-encoder 的置信度阈值、采样数量等超参数），材料中仅说明"实现细节见 Section 4" [KB-2]，但所提供的 [KB-4]、[KB-3] 片段未给出这些具体数值，故**论文中未报告**（在本次提供的材料范围内）。
- 关于 Table 3、Table 4、Figure 5 的具体数值，材料只给出定性描述，未提供具体数字，故不作推测。

## 参考文献

[KB-1] 章节 1 Introduction，页码 2

[KB-2] 章节 3.4 The Training Procedure，页码 5

[KB-3] 章节 4.2.2 The Effectiveness of The Three Training Strategies in RocketQA，页码 8

[KB-4] 章节 3.3 Optimized Training Approach，页码 4

[KB-5] 章节 3.4 The Training Procedure，页码 5

[KB-6] 章节 4.2 Question Answering Datasets，页码 4

### 3. 论文是否给出支持去噪策略有效性的实验或消融证据？（COMPLETED）

## 问题陈述

本步骤要回答的问题是：**RocketQA 论文是否给出实验或消融证据，支持其去噪（denoising）策略的有效性？**

## 研究发现

### 1. 论文明确设置了针对三种训练策略（含去噪）的消融实验

论文在第 4.2.2 节专门开展实验检验三种策略的有效性，并说明该实验在 MSMARCO 数据集上进行，NQ 数据集结果类似（见附录 A.2）：

> "In this part, we conduct the extensive experiments on MSMARCO dataset to examine the effectiveness of the three strategies in RocketQA. Results on NQ dataset has shown the similar findings (see in Section A.2 in Appendix)." [KB-6]

### 2. 去噪策略的具体消融对比证据（Table 3）

论文给出了"不去噪的 hard negatives"与"去噪后的 hard negatives"的直接对比：引入未去噪的 hard negatives 会使检索器性能显著下降（Table 3 第三行），而去噪后的 negatives 能提升密集检索器性能（Table 3 第四行）：

> "As shown in the third row in Table 3, the performance of the retriever significantly decreases by introducing hard negatives without denoising... From the fourth row in Table 3, we can see that denoised negatives improve the performance of the dense retriever." [KB-3]

### 3. 对噪声来源的量化分析与佐证证据

论文解释了性能下降的原因，即大量未标注正例。作者人工检查了 100 个问题的 top-retrieved passages，发现约 70% 实际为正例或高度相关，因此直接采样 hard negatives 会引入噪声：

> "We manually examine the topretrieved passages of 100 questions, that were not labeled as true positives. We find that about 70% of them are actually positives or highly relevant." [KB-3]

此外，论文还通过 Table 4（去噪前后 hard negatives 示例）和 Figure 5（不同排名位置被过滤 passages 的比例）提供进一步洞察：

> "To obtain more insights about denoised hard negatives, Table 4 gives the sampled hard negatives for two questions before and after denoising. Figure 5 further illustrates the ratio of filtered passages at different ranks. We can see that there are more passages filtered (i.e. denoised) at lower ranks..." [KB-3]

### 4. 整体实验与结论层面的支持

引言与结论均声明三种策略（含去噪 hard negatives）经广泛实验验证有效：

> "We conduct extensive experiments to examine the effectiveness of the above three strategies in RocketQA. Experimental results show that the three strategies are effective to improve the performance of dense passage retrieval." [KB-1]

> "Extensive experiments have shown the effectiveness of the proposed approach by incorporating the three optimization strategies." [KB-2]

## 结论

**论文给出了明确的实验/消融证据支持去噪策略的有效性。** 证据主要来自第 4.2.2 节的消融实验（Table 3）：不去噪的 hard negatives 导致性能显著下降，而去噪后的 hard negatives 带来性能提升 [KB-3]；并辅以对 70% 未标注正例的量化分析、Table 4 的去噪前后示例与 Figure 5 的过滤比例分析 [KB-3]，以及引言和结论中对三种策略整体有效性的声明 [KB-1][KB-2]。

需说明的限定：材料中仅给出上述定性描述与表/图编号引用，**具体的数值指标（如 Table 3 中的具体分数、提升幅度）论文中未在所提供的片段内报告**，故此处不作数值层面的断言。

## 参考文献

[KB-1] RocketQA 论文 1 Introduction, 页码 2

[KB-2] RocketQA 论文 5 Conclusions, 页码 9

[KB-3] RocketQA 论文 4.2.2 The Effectiveness of The Three Training Strategies in RocketQA, 页码 8

[KB-4] RocketQA 论文 3.4 The Training Procedure, 页码 5

[KB-5] RocketQA 论文 4.2.1 Dense Passage Retrieval, 页码 7

[KB-6] RocketQA 论文 4.2.2 The Effectiveness of The Three Training Strategies in RocketQA, 页码 8

## 证据与分歧

- 2004.04906（ACCEPTED）
- 2004.04906（ACCEPTED）
- 2010.08191（ACCEPTED）
- 2010.08191（ACCEPTED）
- 2010.08191（ACCEPTED）
- 有 2 条来源仍待引用复核，不能作为无条件结论。

## 覆盖度与缺口

- 已检查来源：5 条；已完成步骤：3 / 3。
- 当前已接纳来源没有标记为反向证据；这不等于已经证明不存在冲突。

## 来源清单

- 2004.04906
- 2004.04906
- 2010.08191
- 2010.08191
- 2010.08191