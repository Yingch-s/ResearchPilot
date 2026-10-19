# C1 · full · round 1

梳理从 DPR 到 RocketQA 负样本策略的演进主线，覆盖全部四篇论文。

---

# 研究报告

## 直接回答

本报告汇总了 4 个已完成研究步骤的发现；逐项依据见下方证据与局限。

**研究问题**：梳理从 DPR 到 RocketQA 负样本策略的演进主线，覆盖全部四篇论文。

## 资料范围

- 专题内全部就绪资料（未限定单篇）

## 逐项发现

### 1. DPR原文如何构建与使用负样本（in-batch negatives 与 BM25 hard negatives 的具体设定）？（COMPLETED）

## 问题陈述

本步骤聚焦于 DPR（Dense Passage Retrieval）原文中负样本的构建与使用方式，具体包括 **in-batch negatives** 与 **BM25 hard negatives** 两类负样本的具体设定。需要说明的是：用户专题论文库中的 [KB-1] 至 [KB-6] 均为 RocketQA 论文片段，其中对 DPR 负样本策略的描述属于**二手转述**，DPR 原文本身并不在所提供的材料范围内。因此，以下归纳严格依据 [KB-n] 中可核实的表述，凡材料未覆盖之处均标注"论文中未报告"。

---

## 研究结论

### 一、DPR 负样本策略在材料中的定位

材料显示，DPR 被作为 RocketQA 的对比基线之一，其负样本构成为"in-batch 随机采样 + 从 BM25 检索结果中采样 hard negative"的组合方式：

- 在段落检索对比中，材料明确指出："The dense passage retrievers include DPR (Karpukhin et al., 2020), ME-BERT (Luan et al., 2020) and ANCE (Xiong et al., 2020). Both DRP and ME-BERT use in-batch random sampling and hard negative sampling from the results retrieved by BM25, while ANCE enhances the hard negative sampling by using the dense retriever." [KB-5]

这确认了 DPR 负样本的两大来源：**in-batch 随机采样（in-batch random sampling）** 与 **BM25 检索结果中的 hard negative 采样（hard negative sampling from the results retrieved by BM25）**。

### 二、in-batch negatives 的具体设定

- 材料在实验基线说明中，将 DPR 归入"BM25 与随机负样本 1:1 组合"这一类："We implement recent DR baselines that use the same BERT-Siamese, but vary in negative construction: random sampling in batch (Rand Neg), random sampling from BM25 top 100 (BM25 Neg) (Lee et al., 2019; Gao et al., 2020b) and the 1:1 combination of BM25 and Random negatives (BM25 + Rand Neg) (Karpukhin et al., 2020; Luan et al., 2020)." [KB-2]

据此可推断 DPR 的负样本构成为 **BM25 负样本与随机（in-batch）负样本按 1:1 比例组合** [KB-2]。其中 in-batch 随机负样本的机制在章节背景（非已校验结论）中被描述为"for each question-positive passage pair in a training batch, the positive passages for the other questions in the batch would be used as negatives"，但该描述来自 Introduction 背景段落，**并非 [KB-n] 原文直接针对 DPR 的表述**，故不作为已校验结论引用。

- 关于 in-batch negatives 的具体数量、batch 内采样比例、温度/损失函数等细节：**论文中未报告**。

### 三、BM25 hard negatives 的具体设定

- 材料确认 DPR 使用"从 BM25 检索结果中采样的 hard negative" [KB-5]，并将其与"从 BM25 top 100 随机采样（BM25 Neg）"这一基线区分开来 [KB-2]。
- 关于 BM25 hard negative 的**具体 top-k 取值、采样数量、是否去重、是否与正样本 1:1 配比**等操作细节：**论文中未报告**（材料仅给出"1:1 combination of BM25 and Random negatives"这一组合比例 [KB-2]，但未细化 BM25 侧的候选池规模与采样规则）。

### 四、材料对 DPR 负样本策略局限性的评价（为后续 RocketQA 演进铺垫）

材料从 RocketQA 视角指出了 DPR 式 in-batch / 稀疏检索负样本的不足：

- "the in-batch local negatives, though effective in learning word or visual representations, are not significantly better than spare-retrieved negatives in representation learning for dense retrieval. In addition, the accuracy of dense retrieval models often underperform BM25, especially on documents (Lee et al., 2019; Gao et al., 2020b; Luan et al., 2020)." [KB-3]

这为 RocketQA 提出 cross-batch negatives、denoised hard negatives 等改进提供了动因 [KB-1][KB-4]。

---

## 局限性说明

1. 所提供材料全部为 RocketQA 论文片段，**不包含 DPR 原文**，因此 DPR 负样本策略的"具体设定"（如 BM25 top-k 数值、每问题负样本数、in-batch 与 hard negative 的精确混合方式、损失函数形式）在材料中**均未报告**，本步骤无法给出原文级细节。
2. 材料中可核实的 DPR 负样本信息仅限于：使用"in-batch 随机采样 + BM25 检索结果 hard negative 采样"，且被归为"BM25 与随机负样本 1:1 组合"这一类 [KB-2][KB-5]。
3. 章节背景中关于 in-batch negatives 机制的一般性描述（来自 Introduction）仅用于理解上下文，**不作为已校验结论**引用。

---

## 参考文献

[KB-1] RocketQA 论文片段，章节 1 Introduction，页码 2。

[KB-2] RocketQA 论文片段，章节 5 EXPERIMENTAL METHODOLOGIES，页码 5。

[KB-3] RocketQA 论文片段，章节 1 INTRODUCTION，页码 1。

[KB-4] RocketQA 论文片段，章节 1 Introduction，页码 2。

[KB-5] RocketQA 论文片段，章节 4.2.1 Dense Passage Retrieval，页码 7。

[KB-6] RocketQA 论文片段，章节 5 Conclusions，页码 9。

# 研究步骤取证归纳：DPR 原文中负样本的构建与使用

## 一、问题重述

本步骤要查清 DPR（Dense Passage Retrieval）论文原文中两类负样本的具体设定：(1) in-batch negatives 的构建与作用；(2) BM25 hard negatives 的具体配置，包括每问题负样本数量、BM25 top-k 取值、采样方式等。

## 二、研究结论

### 1. In-batch negatives 的设定

- **构建方式**：DPR 采用标准的 1-of-N 训练设置，批次中每个问题与一个正段落及其自身的一组 n 个负段落配对；in-batch 训练则是把同一批次内其他问题的正段落复用为负样本。论文明确指出这是"an easy and memory-efficient way to reuse the negative examples already in the batch rather than creating new ones"，并因此"produces more pairs and thus increases the number of training examples" [KB-4]。
- **效果**：在使用相近配置（7 个 gold 负段落）时，"in-batch negative training improves the results substantially"；且"accuracy consistently improves as the batch size grows" [KB-4]。
- **对照结论**：在标准 1-of-N 设置下，负样本类型（随机、BM25、gold 段落）在 k ≥ 20 时对 top-k 准确率影响不大 [KB-4]。

### 2. BM25 hard negatives 的设定

- **定义**：附加的"hard"负段落是"have high BM25 scores given the question, but do not contain the answer string"的段落 [KB-4]。
- **使用范围**：这些附加段落"are used as negative passages for all questions in the same batch" [KB-4]。
- **数量设定**：论文测试了 G.+BM25(1) 与 G.+BM25(2) 两种配置，即"in-batch training with 1 or 2 additional BM25 negatives" [KB-3]。实验结论为"adding a single BM25 negative passage improves the result substantially while adding two does not help further" [KB-4]。

### 3. 关于 false negative 的动机说明

论文指出，由于每个问题只有少量标注正样本（如 MSMARCO 平均每问题仅 1.1 个标注正段落，而全库有 8.8M 段落），人工检查发现 top 检索结果中约 70% 未被标注为负的段落实际是正样本，因此"it is likely to bring false negatives when sampling hard negatives from the top-k retrieved passages" [KB-1]。这正是从 top-k 检索结果中采样 hard negatives 的风险所在。

## 三、材料不足部分（论文中未报告）

基于所提供的 [KB-1] 至 [KB-4] 片段，以下细节**论文中未报告**，不作推测：

- 每个问题具体使用多少个负样本（n 的确切数值，除对照中提到的"7 gold negative passages"外）；
- BM25 hard negatives 采样时的具体 top-k 取值（如 top-100 等）；
- BM25 检索的具体工具、参数或采样比例/策略细节；
- in-batch negatives 与 BM25 hard negatives 组合时的批次大小等超参数。

上述内容需查阅 DPR 论文第 3 节（Training）与第 4 节（Experimental Setup）原文方可补充取证。

## 四、参考文献

[KB-1] DPR 论文 Introduction 章节原文片段（页码 2）。

[KB-2] DPR 论文 Figure 1 说明原文片段（页码 2）。

[KB-3] DPR 论文 5.2 Ablation Study on Model Training，Table 3 说明原文片段（页码 6）。

[KB-4] DPR 论文 5.2 Ablation Study on Model Training 正文原文片段（页码 6）。

### 2. DPR之后、RocketQA之前的过渡论文（如ANCE或该专题库中对应文献）如何批评并改进DPR的负样本策略？（COMPLETED）

## 问题陈述

本步骤要回答的是：在 DPR 之后、RocketQA 之前的过渡性工作（以 ANCE 为代表）是如何批评并改进 DPR 负样本策略的。下面严格依据用户专题论文库中的原文片段 [KB-1]～[KB-6] 进行归纳，凡材料未覆盖之处均明确标注"论文中未报告"。

## 研究发现的归纳

### 一、对 DPR 负样本策略的定位与批评

**1. DPR 的负样本做法被明确界定为"批内随机采样 + BM25 检索结果中的难负样本"。**

RocketQA 论文在对比基线时写道：DPR（Karpukhin et al., 2020）与 ME-BERT 均"use in-batch random sampling and hard negative sampling from the results retrieved by BM25"，而 ANCE 则是"enhances the hard negative sampling by using the dense retriever" [KB-5]。这段原文同时给出了 DPR 与 ANCE 在负样本来源上的关键分野：DPR 的难负样本来自稀疏检索器（BM25）的检索结果，ANCE 的难负样本则来自稠密检索器本身。

**2. 对 DPR 式"难负样本"的核心批评：假阴性（false negative）问题。**

RocketQA 指出，以往工作试图"design specific mechanisms for selecting a few hard negatives from the top-k retrieved candidates (Gillick et al., 2019; Wu et al., 2020; Karpukhin et al., 2020; Luan et al., 2020; Xiong et al., 2020)"，但"it suffers from the false negative issue" [KB-3 所属背景，见 [KB-1][KB-2] 同段语境]。其根源在于大量"unlabeled positives"：以 MSMARCO 为例，每个问题平均只有 1.1 条标注正样本，而整个语料库有 8.8M 条段落；作者人工检查 top 检索中未被标为正样本的段落，发现其中 70% 实际上是正样本，"Hence, it is likely to bring false negatives when sampling hard negatives from the top-k retrieved passages"（章节背景 P-140621b255be）。这一批评直接指向 DPR 从 top-k 检索候选中采样难负样本的做法。

> 说明：上述"false negative / unlabeled positives"论述出现在 [KB-1][KB-2] 所在论文的 Introduction 背景段落中，[KB-1][KB-2] 本身只点明 RocketQA 的三项策略名称与有效性，未展开批评细节；细节表述来自同章节背景原文，已如实标注。

### 二、ANCE 的改进路径

**1. 用"正在优化的稠密检索器"构造全局负样本，取代局部/稀疏负样本。**

ANCE 明确以 DPR 式批内局部负样本为批评对象：批内局部负样本"though effective in learning word or visual representations, are not significantly better than spare-retrieved negatives in representation learning for dense retrieval"（章节背景 P-ed0e1d2d326b，引 Karpukhin et al., 2020 的观察）。ANCE 因此提出"Instead of random or in-batch local negatives, ANCE constructs global negatives using the being-optimized DR model to retrieve from the entire corpus"，使训练中负样本分布与测试中需区分的无关文档分布对齐（章节背景 P-ed0e1d2d326b）。

**2. 实现方式：异步更新的 ANN 索引。**

ANCE 原文写道："We implement ANCE using an asynchronously updated ANN index of the corpus representation. Similar to Guu et al. (2020), we maintain an Inferencer that parallelly computes the document encodings with a recent checkpoint from the being optimized DR model, and refresh the ANN index used for negative sampling once it finishes, to keep up with the model training." [KB-4] 这正是其"用稠密检索器增强难负样本采样"的具体机制，与 [KB-5] 中"ANCE enhances the hard negative sampling by using the dense retriever"的概括相互印证。

**3. 理论依据：梯度范数与收敛性。**

ANCE 从方差缩减视角论证：局部批内负样本"lead to diminishing gradient norms, resulted in high stochastic gradient variances and slow training convergence - the local negative sampling is the bottleneck of dense retrieval's effectiveness"（章节背景 P-ed0e1d2d326b）。作者称"we also empirically validate our theory that the gradient norms on ANCE sampled negatives are much bigger than local negatives and thus improve the convergence of dense retrieval models" [KB-4]。

### 三、RocketQA 对 DPR 与 ANCE 的继承与再推进（对照参考）

RocketQA 将 DPR/ANCE 时代的负样本困境归纳为三大挑战：训练与推理的差异（discrepancy between training and inference）、未标注正样本的存在（unlabeled positives）、训练数据有限（limited training data）[KB-3]。针对性地提出三项策略——cross-batch negatives、denoised hard negatives、data augmentation [KB-1][KB-3]，并称实验证明三者均有效 [KB-2]。在端到端 QA 上，RocketQA 复用 DPR 发布的抽取式阅读器并采用相同推理设置（取 100 段），同时用 RocketQA 检索结果训练新阅读器（训练取 top 50 段），理由是"the reader should be adapted to the retrieval distribution of RocketQA" [KB-6]。

## 结论

就本步骤所问的"过渡论文如何批评并改进 DPR 负样本策略"，专题库材料给出的主线是：

- **批评点**：DPR 采用"批内随机采样 + BM25 难负样本"的组合 [KB-5]，其难负样本来自 top-k 检索候选，因而受假阴性问题困扰——大量未标注正样本（MSMARCO 中 70% 的 top 检索未标注段落实为正样本）使难负样本采样易引入噪声（章节背景 P-140621b255be）。
- **ANCE 的改进**：不再依赖随机/批内局部负样本或稀疏检索器，而是"using the being-optimized DR model to retrieve from the entire corpus"构造全局负样本（章节背景 P-ed0e1d2d326b），并通过异步更新的 ANN 索引与并行 Inferencer 落地实现 [KB-4]；理论上以梯度范数更大、随机梯度方差更小来解释其收敛优势 [KB-4]（章节背景 P-ed0e1d2d326b）。RocketQA 亦确认 ANCE 的本质是"用稠密检索器增强难负样本采样" [KB-5]。
- **RocketQA 的接续**：在 ANCE 基础上进一步以 cross-batch negatives、denoised hard negatives、data augmentation 三项策略应对训练-推理差异、未标注正样本与数据稀缺 [KB-1][KB-3]。

**材料不足之处（论文中未报告）**：上述片段未给出 ANCE 对 DPR 负样本策略的定量对比数据（如具体 Recall/MRR 差值）、未报告 ANCE 与 DPR 在同一设置下的消融数字，也未报告 RocketQA 对 ANCE 负样本策略缺陷的逐条批评原文；这些内容在本步骤材料中均未报告，故不作推断。

## 参考文献

[KB-1] RocketQA, 1 Introduction, p.2（用户专题论文库原文片段）

[KB-2] RocketQA, 1 Introduction, p.2（用户专题论文库原文片段）

[KB-3] RocketQA, Abstract, p.1（用户专题论文库原文片段）

[KB-4] ANCE, 1 INTRODUCTION, p.2（用户专题论文库原文片段）

[KB-5] RocketQA, 4.2.1 Dense Passage Retrieval, p.7（用户专题论文库原文片段）

[KB-6] RocketQA, 4.2.3 Passage Reading with RocketQA, p.9（用户专题论文库原文片段）

章节背景 P-140621b255be（RocketQA 1 Introduction，用于理解 false negative / unlabeled positives 语境）

章节背景 P-ed0e1d2d326b（ANCE 1 INTRODUCTION，用于理解 ANCE 对局部负样本的批评与全局负样本主张）

章节背景 P-aea12f4a1929（RocketQA 4.2.1 完整段落，与 [KB-5] 对应）

### 3. RocketQA原文提出哪些负样本相关技术（cross-batch negatives、denoised negatives、hard negative mining）及其各自动机与效果？（COMPLETED）

## 问题陈述

本步骤要回答：RocketQA 原文提出了哪些与负样本相关的技术（cross-batch negatives、denoised hard negatives、hard negative mining），各自的动机是什么，效果如何？

## 研究发现

### 一、RocketQA 的负样本相关技术总览

RocketQA 提出三项核心训练策略，其中两项直接与负样本构造相关（cross-batch negatives、denoised hard negatives），第三项为数据增强：

> "The proposed RocketQA introduces three novel training strategies to improve dense passage retrieval for open-domain QA, namely cross-batch negatives, denoised hard negatives, and data augmentation." [KB-1]

> "We make three major technical contributions in RocketQA, namely crossbatch negatives, denoised hard negatives and data augmentation." [KB-3]

结论部分同样重申这三项贡献 [KB-4]。需要注意：**原文片段中并未出现"hard negative mining"这一独立技术名称**，与硬负样本相关的表述是"denoised hard negatives"，即"去噪的硬负样本"，其内涵是从检索器 top 排序结果中挖掘硬负样本并去除假负例（见下）。因此"hard negative mining"应理解为 denoised hard negatives 策略中的挖掘环节，而非论文单列的第三项负样本技术。

### 二、动机：三项挑战

RocketQA 的负样本策略源于对双编码器训练中三项挑战的分析：

1. **训练与推理的差异（discrepancy between training and inference）**：推理时需从数百万候选中识别正例，而训练时受单卡内存限制只能在小候选集上估计概率。为缓解该差异，已有工作尝试从 top-k 检索候选中选取少量硬负样本，但会遭遇假负例问题 [KB-2 背景亦见 KB-3]。

> "First, RocketQA introduces cross-batch negatives. Comparing to inbatch negatives, it increases the number of available negatives for each question during training, and alleviates the discrepancy between training and inference." [KB-2]

2. **大量未标注正例（unlabeled positives）**：MSMARCO 中每题平均仅 1.1 个标注正例，而全库有 8.8M 段落；人工检查发现 top 检索结果中未标注为正例的段落有 70% 实际是正例，因此从 top-k 中采样硬负样本极易引入假负例。

> "Second, RocketQA introduces denoised hard negatives. It aims to remove false negatives from the top-ranked results retrieved by a retriever, and derive more reliable hard negatives." [KB-2]

3. **大规模训练数据获取成本高（limited training data）**：RocketQA 借助 cross-encoder 为无监督数据生成高质量伪标签用于数据增强，以训练双编码器 [KB-2][KB-3]。此项属于数据增强，而非负样本构造技术。

### 三、各技术的动机与作用机制

| 技术 | 动机 | 机制/作用 |
|---|---|---|
| cross-batch negatives | 缓解训练-推理差异；in-batch 负例数量有限 | 相比 in-batch negatives，增加每题在训练时可用负例的数量，并缓解训练与推理的差异 [KB-2] |
| denoised hard negatives | 解决假负例问题（未标注正例多） | 从检索器 top 排序结果中去除假负例，得到更可靠的硬负样本 [KB-2] |
| data augmentation（非负样本技术，但相关） | 训练数据有限 | 用 cross-encoder 为无监督数据生成高质量伪标签训练双编码器 [KB-2] |

### 四、效果

- 总体效果：RocketQA 在 MSMARCO 与 Natural Questions 上显著优于此前 SOTA 模型；论文还通过大量实验检验了三种策略的有效性，并证明基于 RocketQA 检索器可提升端到端 QA 性能 [KB-3]。
- 结论部分亦指出，大量实验表明结合三项优化策略后方法有效 [KB-4]。

**材料不足说明**：本步骤提供的原文片段中，**未报告** cross-batch negatives、denoised hard negatives 各自的定量消融数值（如各策略单独带来的 Recall/MRR 提升幅度），也未给出"hard negative mining"作为独立技术的定义或单独效果数据。上述定量细节在论文中可能存在于实验章节，但不在当前 [KB-n] 片段范围内，故不作推测。

## 结论

RocketQA 原文明确提出的负样本相关技术为两项：**cross-batch negatives**（扩大每题可用负例数量、缓解训练-推理差异）与 **denoised hard negatives**（从检索器 top 结果中剔除假负例以获得可靠硬负样本），二者与 data augmentation 共同构成三大贡献 [KB-1][KB-2][KB-3][KB-4]。其中"hard negative mining"在原文表述中并非独立技术名，而是 denoised hard negatives 的挖掘环节；论文对三项策略做了广泛实验验证整体有效性并在 MSMARCO、NQ 上取得 SOTA [KB-3][KB-4]，但当前片段未提供各负样本技术的单独定量效果，需在后续步骤补充实验章节证据。

## 参考文献

[KB-1] RocketQA, 1 Introduction, p.2（用户专题论文库原文片段）

[KB-2] RocketQA, 1 Introduction, p.2（用户专题论文库原文片段）

[KB-3] RocketQA, Abstract, p.1（用户专题论文库原文片段）

[KB-4] RocketQA, 5 Conclusions, p.9（用户专题论文库原文片段）

[KB-5] ANCE, 1 INTRODUCTION, p.2（用户专题论文库原文片段，本步骤未直接引用其结论）

[KB-6] ANCE, 5 EXPERIMENTAL METHODOLOGIES, p.5（用户专题论文库原文片段，本步骤未直接引用其结论）

### 4. 四篇论文在负样本策略上的关键差异与继承关系能否用原文表述与实验对比直接对齐？（COMPLETED）

# 步骤取证归纳：四篇论文负样本策略的原文表述与实验对比能否直接对齐？

## 问题陈述

本步骤要回答的是：从 DPR 到 RocketQA 的负样本策略演进主线，能否用四篇论文的**原文表述**与**实验对比**直接对齐？即：各代策略的差异与继承关系，是否有原文措辞可作依据，又有实验数据可作佐证。

## 研究发现的边界说明（重要）

必须先说明材料覆盖范围。用户专题论文库中本步骤可用的原文片段 [KB-1]～[KB-6] **全部来自 RocketQA 一篇论文**（摘要、引言、4.2.1 实验节、结论）。四篇论文中的另外三篇（DPR、ANCE，以及第四篇）**在本步骤提供的 [KB-n] 材料中没有原文片段**。

因此，本步骤只能做到：

- **RocketQA 自身的负样本策略表述与其对前作的转述**：可用 [KB-n] 直接对齐；
- **DPR、ANCE 等前作的第一手原文表述**：论文中未报告（本步骤材料未提供），只能依赖 RocketQA 对它们的**二手转述**；
- **四篇论文之间的实验数值横向对比**：论文中未报告（[KB-4] 仅给出定性结论"RocketQA significantly outperforms all the baselines"，未在片段中给出具体数值）。

## 一、可用原文直接对齐的部分

### 1. 演进起点：in-batch 随机负样本（DPR 一系的基线做法）

RocketQA 在 4.2.1 节转述前作做法时明确写道：DPR 与 ME-BERT "use in-batch random sampling and hard negative sampling from the results retrieved by BM25"，而 ANCE "enhances the hard negative sampling by using the dense retriever" [KB-4]。这是本步骤材料中**唯一一处**把 DPR 与 ANCE 的负样本策略并置对照的原文，可作为"DPR→ANCE"继承关系的二手证据：两者都用难负样本，差别在于难负样本的来源（BM25 检索结果 vs. 稠密检索器本身）。

> 限定：此为 RocketQA 作者对 DPR/ANCE 的**转述**，并非 DPR/ANCE 原文；DPR 原文如何自述其负样本构造，论文中未报告（材料未提供）。

### 2. 理论批判：局部 in-batch 负样本是瓶颈

[KB-2] 给出对 in-batch 局部负样本的理论否定："local in-batch negatives lead to diminishing gradient norms, resulted in high stochastic gradient variances and slow training convergence - the local negative sampling is the bottleneck of dense retrieval's effectiveness." 这段原文（从措辞与"variance reduction framework"看，属 ANCE 一系的分析话语）构成对"DPR 式 in-batch 随机负样本"的直接批评，是演进主线中"为何要改"的原文依据。

> 限定：[KB-2] 所在章节标注为"1 INTRODUCTION"，与 RocketQA 引言章节同名；其内容（ANCE、global negatives）指向 ANCE 论文。但材料未标明该片段的论文归属，**引用时只能绑定 [KB-2] 原文措辞本身**，不宜断言其出自哪一篇。

### 3. RocketQA 的三项策略：对前作的两条改进线

RocketQA 摘要与引言两处一致地列出三项贡献："cross-batch negatives, denoised hard negatives and data augmentation" [KB-5][KB-6]，引言另表述为"cross-batch negatives, denoised hard negatives, and data augmentation" [KB-3]。其中与负样本策略直接相关的两项，可与前述基线形成对齐：

- **cross-batch negatives** ← 针对 in-batch 局部负样本的候选集过小问题（[KB-2] 所指"local"瓶颈）；
- **denoised hard negatives** ← 针对难负样本中的假阴性问题（[KB-4] 所述 DPR/ANCE 从检索结果取难负样本的做法）。

> 限定：RocketQA 原文未逐条明说"cross-batch negatives 是对 DPR in-batch 的继承式扩展"或"denoised hard negatives 是对 ANCE 难负样本的去噪修正"；上述对应关系是**基于 [KB-3][KB-4][KB-5] 措辞的合理归纳**，原文未以"继承/改进自 X"的句式明写。这一点须在最终报告中标注为推断而非原文直述。

### 4. 实验对比：只有定性结论，无逐策略数值

- 总体对比：[KB-4] 称"RocketQA significantly outperforms all the baselines on both MSMARCO and NQ datasets"，并指出稠密检索器整体优于稀疏检索器。
- 策略有效性：[KB-1] 称"the three strategies are effective to improve the performance of dense passage retrieval"；[KB-6] 称"Extensive experiments have shown the effectiveness of the proposed approach by incorporating the three optimization strategies."
- 消融实验的载体：材料提到"Table 3: The experiments to examine the effectiveness of the three proposed training strategies in RocketQA on MSMARCO Passage Ranking"及图 4（随机负样本数量对效果的影响），但**表中具体数值未在 [KB-n] 片段中给出**。

> 因此，"用实验对比直接对齐"在本步骤**只能对齐到定性结论层面**；四篇论文间的定量横向对比（如 DPR vs. ANCE vs. RocketQA 的 MRR/Top-k 数值）论文中未报告（本步骤材料未提供）。

## 二、材料不足以回答的部分（明确列出）

1. **DPR 原文对负样本策略的自述**：论文中未报告（[KB-n] 无 DPR 原文片段）。
2. **ANCE 原文对 global negatives 的完整表述**：[KB-2] 提供了一句理论批判，但 ANCE 如何构造全局负样本的完整原文，论文中未报告（材料仅有章节背景，不可作已校验结论引用）。
3. **第四篇论文的任何负样本策略原文**：论文中未报告（材料完全未涉及）。
4. **四篇论文实验数值的逐项对比**：论文中未报告（[KB-4] 仅定性；Table 3 数值缺失）。

## 结论

就本步骤的提问而言，答案是**部分可以对齐，但不能完整对齐**：

- **原文表述层面**：可用 [KB-4] 对齐 DPR/ANCE 的负样本来源差异（BM25 难负样本 vs. 稠密检索器难负样本），用 [KB-2] 对齐对 in-batch 局部负样本的理论否定，用 [KB-3][KB-5][KB-6] 对齐 RocketQA 的 cross-batch negatives 与 denoised hard negatives 两项改进。但"继承关系"的明述句式在原文中缺失，属归纳推断。
- **实验对比层面**：仅能对齐到"RocketQA 显著优于所有基线"及"三项策略均有效"的定性结论 [KB-1][KB-4][KB-6]；具体消融数值与四篇论文的横向数值对比，论文中未报告（本步骤材料未提供）。
- **四篇论文全覆盖**：本步骤材料只覆盖 RocketQA 一篇，其余三篇的第一手原文缺失，构成对齐主线的主要证据缺口。

## 参考文献

[KB-1] RocketQA, 1 Introduction, p.2（用户专题论文库原文片段）

[KB-2] 1 INTRODUCTION, p.2（用户专题论文库原文片段）

[KB-3] RocketQA, 1 Introduction, p.2（用户专题论文库原文片段）

[KB-4] RocketQA, 4.2.1 Dense Passage Retrieval, p.7（用户专题论文库原文片段）

[KB-5] RocketQA, Abstract, p.1（用户专题论文库原文片段）

[KB-6] RocketQA, 5 Conclusions, p.9（用户专题论文库原文片段）

## 问题陈述

本步骤要核验的是：能否用论文原文表述与实验对比，直接对齐四篇论文（DPR → ANCE → RocketQA 线索上的四篇）在负样本策略上的关键差异与继承关系。用户当前给出的原文片段全部来自 ANCE 一篇论文（含其引言、摘要、实验方法章节），其中对 DPR 的负样本做法只有转述性引用，没有 DPR 原文表述；RocketQA 相关原文片段完全缺失。因此下面只归纳材料能够支撑的部分，并明确标注缺口。

## 取证归纳

### 一、ANCE 对"局部批内负样本"的定位与批评（可用原文直接支撑）

ANCE 把稠密检索的学习瓶颈归因于"批内局部采样"的无信息负样本，其摘要原文为：学习瓶颈"is due to the domination of uninformative negatives sampled locally in batch, which yield diminishing gradient norms, large stochastic gradient variances, and slow learning convergence" [KB-4]。引言进一步指出，已有研究用对比学习"to select hard negatives in current or recent mini-batches"，但"the in-batch local negatives, though effective in learning word or visual representations, are not significantly better than spare-retrieved negatives in representation learning for dense retrieval" [KB-2]。同一段还指出稠密检索精度"often underperform BM25, especially on documents" [KB-2]。

### 二、ANCE 自身策略：全局负样本 + 异步更新的 ANN 索引（可用原文直接支撑）

ANCE 的自我定位是"Instead of random or in-batch local negatives, ANCE constructs global negatives using the beingoptimized DR model to retrieve from the entire corpus"，并声称这"fundamentally aligns the distribution of negative samples in training and of irrelevant documents to separate in testing" [KB-1]；摘要版本为"selects hard training negatives globally from the entire corpus, using an asynchronously updated ANN index" [KB-4]。

### 三、ANCE 实验中对各类负样本策略的显式枚举（可用于对齐"差异"）

ANCE 的实验基线按负样本构造方式划分，原文列出："random sampling in batch (Rand Neg), random sampling from BM25 top 100 (BM25 Neg) (Lee et al., 2019; Gao et al., 2020b) and the 1:1 combination of BM25 and Random negatives (BM25 + Rand Neg) (Karpukhin et al., 2020; Luan et al., 2020)"，并另设"contrastive learning/Noise Contrastive Estimation, which uses hardest negatives in batch (NCE Neg)" [KB-3]。实现细节中，ANCE"is also warmed up by BM25 negatives"，且"BM25 Warm Up"设定的动机是"BM25 negatives can help train dense retrieval (Karpukhin et al., 2020; Luan et al., 2020)" [KB-3 所在章节背景，其中该句属实验方法章节原文]。此外，ANN 负样本的取用方式为"For each positive, we uniformly sample one negative from ANN top 200" [同上章节背景]。

据此可确认的"差异"是：ANCE 把 BM25 top-100 随机负样本、批内随机负样本、BM25+随机 1:1 混合、批内最难负样本（NCE）并列为其对照策略，而自身采用从全语料 ANN 检索的全局负样本 [KB-3][KB-1][KB-4]。其中"BM25 + Rand Neg 的 1:1 组合"被明确标注为引自 Karpukhin et al., 2020 与 Luan et al., 2020 [KB-3]，这是材料中唯一能把"DPR 系负样本做法"与具体构造形式挂钩的线索。

### 四、可视化证据（可用原文直接支撑）

图 1 的说明原文为："T-SNE (Maaten & Hinton, 2008) representations of query, relevant documents, negative training instances from BM25 (BM25 Neg) or randomly sampled (Rand Neg), and testing negatives (DR Neg) in dense retrieval" [KB-5]，即该图对比了 BM25 负样本、随机负样本与测试期 DR 负样本的表示分布，可作为"训练负样本分布与测试负样本分布不一致"这一论点的图示证据。

## 材料不足以回答的部分（明确缺口）

1. **DPR 原文表述缺失**：材料中没有 DPR 论文的原文片段。ANCE 只在 [KB-2] 中以"as observed in recent research (Karpukhin et al., 2020)"转述"批内局部负样本并不显著优于稀疏检索负样本"，并在 [KB-3] 中把"BM25 + Rand Neg 的 1:1 组合"归引至 Karpukhin et al., 2020。DPR 原文对"in batch negatives"与"BM25 hard negatives"的原始措辞、以及其 top-k BM25 候选的具体设定，**论文中未报告**（就本材料而言无法核验）。
2. **ANCE 的"global negatives"与 DPR 的"BM25 hard negatives"的继承关系**：材料只能说明 ANCE 以 BM25 负样本做 warm up 且承认 BM25 负样本"can help train dense retrieval" [KB-3 章节背景]，但不能据此断言 ANCE 在策略上"继承"DPR；这一因果/谱系判断缺乏原文直接表述支撑。
3. **RocketQA 全部内容缺失**：材料中没有任何 RocketQA 原文片段，其负样本策略（如跨批次负样本、去噪硬负样本等具体机制）、与 ANCE/DPR 的差异及实验对比均**论文中未报告**，无法在本步骤给出任何经原文校验的结论。
4. **实验数值层面的对齐**：材料仅给出表 1、表 2 的标题（TREC DL 结果、NQ/TQA 的 Coverage@20/100）[KB-3 章节背景]，未给出任何具体数值，因此"用实验对比直接对齐差异"在数值层面**论文中未报告**。

## 结论

在现有材料范围内，可以**部分**对齐：ANCE 原文明确把自身策略（全语料 ANN 全局负样本、异步更新索引）与四类对照策略（批内随机、BM25 top-100 随机、BM25+随机 1:1 混合、批内最难/NCE）并列，并明确批评批内局部负样本导致梯度范数衰减、方差增大与收敛变慢 [KB-3][KB-1][KB-4][KB-2]；图 1 提供了 BM25 负样本、随机负样本与测试负样本分布差异的图示证据 [KB-5]。但"DPR 原文表述"与"RocketQA 全部内容"在本材料中均缺失，ANCE 与 DPR 之间的继承关系也只有间接线索（BM25 负样本 warm up、1:1 混合被归引至 DPR）[KB-3]，不足以支撑"用原文表述与实验对比直接对齐四篇论文"的完整结论。建议补充 DPR 与 RocketQA 的原文片段（尤其是各自负样本构造段落与主实验表数值）后，再完成四篇论文的完整对齐。

## 参考文献

[KB-1] ANCE, 1 INTRODUCTION, p.2（用户专题论文库原文片段）

[KB-2] ANCE, 1 INTRODUCTION, p.1（用户专题论文库原文片段）

[KB-3] ANCE, 5 EXPERIMENTAL METHODOLOGIES, p.5（用户专题论文库原文片段）

[KB-4] ANCE, ABSTRACT, p.1（用户专题论文库原文片段）

[KB-5] ANCE, 1 INTRODUCTION, p.2, Figure 1 说明（用户专题论文库原文片段）

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