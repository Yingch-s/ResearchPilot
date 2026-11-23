/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.example.deepresearch.researchpilot.ask;

import com.alibaba.cloud.ai.example.deepresearch.researchpilot.retrieve.RpRetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.usage.LlmUsageRecorder;

/**
 * V1 UX-1：快速提问（Quick Ask）—— 基于当前勾选且已就绪的资料回答，附带编号引用。
 *
 * <p>
 * 与深度研究的边界：快速提问不走研究计划/多步骤/矩阵，只做一次检索 + 一次回答； 资料不足时明确说"在当前所选资料中未找到足够依据"，不转为未经告知的全网搜索。
 * 回答不冒充经过矩阵审阅的深度研究成果。
 */
@Service
public class RpAskService {

	private static final Logger logger = LoggerFactory.getLogger(RpAskService.class);

	private static final int TOP_K = 6;

	private final RpRetrievalService retrieval;

	private final ChatClient researchAgent;

	// V1 UX：网络补充来源（论文库不足时系统判断启用，引用标 WEB + URL）
	private final RpWebSearchService webSearch;

	private final Duration llmRequestTimeout;

	@org.springframework.beans.factory.annotation.Autowired
	private LlmUsageRecorder usageRecorder;

	private final java.util.concurrent.ExecutorService callPool = java.util.concurrent.Executors
		.newCachedThreadPool(r -> {
			Thread thread = new Thread(r, "rp-quick-ask-call");
			thread.setDaemon(true);
			return thread;
		});

	@org.springframework.beans.factory.annotation.Autowired
	private org.springframework.jdbc.core.JdbcTemplate jdbc;

	@org.springframework.beans.factory.annotation.Autowired
	public RpAskService(RpRetrievalService retrieval, ChatClient researchAgent, RpWebSearchService webSearch) {
		this(retrieval, researchAgent, webSearch, 120);
	}

	public RpAskService(RpRetrievalService retrieval, ChatClient researchAgent, RpWebSearchService webSearch,
			long timeoutSeconds) {
		this.retrieval = retrieval;
		this.researchAgent = researchAgent;
		this.webSearch = webSearch;
		this.llmRequestTimeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
	}

	private <T> T callWithTimeout(Callable<T> task) {
		LlmUsageRecorder.Timer usage = usageRecorder == null ? null : usageRecorder.timer("quick_answer");
		var future = callPool.submit(task);
		try {
			T result = future.get(llmRequestTimeout.toMillis(), TimeUnit.MILLISECONDS);
			if (usage != null && result instanceof ChatResponse response && response.getMetadata() != null
					&& response.getMetadata().getUsage() != null) {
				var metadata = response.getMetadata().getUsage();
				usage.usage(metadata.getPromptTokens(), metadata.getCompletionTokens());
			}
			if (usage != null)
				usage.success();
			return result;
		}
		catch (java.util.concurrent.TimeoutException e) {
			future.cancel(true);
			if (usage != null)
				usage.failure("timeout");
			throw new IllegalStateException("快速回答请求超时（" + llmRequestTimeout.toSeconds() + "s）", e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			future.cancel(true);
			if (usage != null)
				usage.failure("interrupted");
			throw new IllegalStateException("快速回答请求被中断", e);
		}
		catch (Exception e) {
			if (usage != null)
				usage.failure("failed");
			throw new IllegalStateException("快速回答请求失败", e.getCause() == null ? e : e.getCause());
		}
		finally {
			if (usage != null)
				usage.close();
		}
	}

	public record Citation(int n, String paperId, String chunkId, String sectionPath, Integer page, String text,
			String sourceType, String url) {
	}

	public static final String SOURCE_PAPER = "PAPER";

	public static final String SOURCE_WEB = "WEB";

	public record AskResult(String answer, List<Citation> citations, int usedPapers) {
	}

	public AskResult ask(String topicId, List<String> paperIds, String question) {
		return ask(topicId, paperIds, question, false, false);
	}

	/**
	 * allowGeneral：无资料或资料未命中时，经用户知情选择（前端明示"通用回答"）后允许模型 用通用知识回答——回答带明确前缀，不冒充资料依据（设计原则
	 * 5"不制造确定性"）。
	 */
	/**
	 * V1 交互重构：意图路由 + 弹性回答（项目所有者定案：不套模板，提示词只给行为准则， 引用标注在真正引用资料的位置）。
	 */
	public AskResult ask(String topicId, List<String> paperIds, String question, boolean allowGeneral) {
		return ask(topicId, paperIds, question, allowGeneral, false);
	}

	/**
	 * 快速问答的来源策略由用户明确选择。allowWeb=false 时严格限制为当前资料， 即使检索命中不足也不得隐式联网。
	 */
	public AskResult ask(String topicId, List<String> paperIds, String question, boolean allowGeneral,
			boolean allowWeb) {
		return ask(topicId, paperIds, question, allowGeneral, allowWeb, question);
	}

	public AskResult ask(String topicId, List<String> paperIds, String question, boolean allowGeneral, boolean allowWeb,
			String conversationContext) {
		// 1. 先用确定性路由决定回答策略。控制决策不再为每个问题额外调用模型。
		String intent = classifyIntent(question);
		if ("chitchat".equals(intent)) {
			// 问候/闲聊：正常说话，不检索、无引用、无模板
			return new AskResult(directAnswer(conversationContext), List.of(), 0);
		}

		// 2. 需要资料的意图：检索
		boolean hasScope = paperIds != null && !paperIds.isEmpty();
		List<RpRetrievalService.Candidate> candidates;
		try {
			candidates = retrieval.hybridRetrieve(topicId, hasScope ? paperIds : null, question, TOP_K, true);
		}
		catch (Exception e) {
			logger.warn("快速提问检索失败: {}", e.getMessage());
			return new AskResult("在当前所选资料中未找到足够依据（检索失败，请稍后重试）。", List.of(), 0);
		}
		// 联网搜索是用户显式选择的来源。开启后一定执行一次搜索，但本地论文和网页
		// 会在 Source Pack 中分层，避免网页片段悄悄替代专题原文。
		List<RpWebSearchService.WebResult> webResults = List.of();
		if (allowWeb && !webSearch.available()) {
			return new AskResult("联网搜索暂不可用：搜索服务尚未配置。此次没有执行网页搜索，请稍后重试或取消联网搜索后提问。", List.of(), 0);
		}
		boolean needWeb = allowWeb && webSearch.available();
		if (needWeb) {
			webResults = webSearch.search(question, 3);
			if (webResults.isEmpty()) {
				return new AskResult("本次联网搜索没有取得可引用的网页结果。可能是搜索服务失败或没有匹配来源，请稍后重试或调整问题。", List.of(), 0);
			}
		}
		if (candidates.isEmpty() && webResults.isEmpty()) {
			// 论文库与网络都无命中：通用知识意图或用户已知情选择（前端徽标明示）→ 通用回答；否则诚实说未找到
			if (allowGeneral || "general".equals(intent)) {
				return new AskResult("（未依据专题资料——以下为通用知识回答，未经资料验证）\n\n" + generalAnswer(conversationContext), List.of(),
						0);
			}
			return new AskResult("在当前所选资料中未找到足够依据。可调整左侧资料范围后重试。", List.of(), 0);
		}

		List<Citation> citations = new ArrayList<>();
		StringBuilder sources = new StringBuilder();
		Map<String, String> paperSourceUrls = sourceUrls(candidates);
		for (int i = 0; i < candidates.size(); i++) {
			var c = candidates.get(i);
			String savedUrl = paperSourceUrls.get(c.paperId());
			citations.add(new Citation(i + 1, c.paperId(), c.chunkId(), c.sectionPath(), c.page(), c.text(),
					savedUrl == null ? SOURCE_PAPER : SOURCE_WEB, savedUrl));
			sources.append("[")
				.append(i + 1)
				.append("] ")
				.append(savedUrl == null ? "" : "（网络来源快照）" + savedUrl + " ")
				.append(c.sectionPath() == null ? "" : "章节:" + c.sectionPath() + " ")
				.append("页码:")
				.append(c.page() == null ? "?" : c.page())
				.append('\n')
				.append(c.text())
				.append("\n\n");
		}
		for (int w = 0; w < webResults.size(); w++) {
			var wr = webResults.get(w);
			int n = candidates.size() + w + 1;
			citations
				.add(new Citation(n, null, "web|" + wr.url(), wr.title(), null, wr.content(), SOURCE_WEB, wr.url()));
			sources.append("[")
				.append(n)
				.append("] （网络来源）")
				.append(wr.title())
				.append('\n')
				.append(wr.content())
				.append("\n\n");
		}

		// 3. 按意图选择回答准则（行为准则，不是结构模板）
		String guideline;
		if ("locate".equals(intent)) {
			guideline = "用户在查找资料中出现某内容的位置。回答以来源清单为主：直接列出相关来源及各自页码/章节，不要展开长篇叙述。";
		}
		else if ("general".equals(intent)) {
			guideline = "问题偏向通用知识，但资料中有相关内容。回答以资料能佐证的部分为主，资料未覆盖的通用补充放在最后并明确说明这是通用知识。";
		}
		else {
			guideline = "回答用户的资料内容问题。";
		}
		String background = "";
		try {
			background = com.alibaba.cloud.ai.example.deepresearch.researchpilot.retrieve.ParentContextBudget.assemble(
					candidates,
					retrieval.expandParents(candidates.stream().map(RpRetrievalService.Candidate::parentId).toList(),
							topicId, paperIds),
					8000);
		}
		catch (Exception e) {
			logger.warn("快速提问章节背景不可用，保留原文片段");
		}
		String contextSources = "【可引用来源】\n" + sources
				+ (background.isBlank() ? "" : "\n【章节背景，仅用于理解，不得新增引用编号】\n" + background);
		String prompt = guidelines(conversationContext, guideline, contextSources);
		String answer;
		try {
			ChatResponse response = callWithTimeout(() -> researchAgent.prompt().user(prompt).call().chatResponse());
			answer = response == null || response.getResult() == null || response.getResult().getOutput() == null ? ""
					: response.getResult().getOutput().getText();
		}
		catch (Exception e) {
			logger.warn("快速提问回答失败: {}", e.getMessage());
			return new AskResult("回答生成失败，请稍后重试。", List.of(), 0);
		}

		// V1 两段式联网判断：模型自认"资料依据不足"时，自动补充网络来源重新生成
		// （计数不能作为依据——检索常返回沾边但不回答问题的片段）
		if (allowWeb && answer != null && answer.contains("[[NEED_MORE_SOURCES]]") && webResults.isEmpty()
				&& webSearch.available()) {
			webResults = webSearch.search(question, 3);
			if (!webResults.isEmpty()) {
				for (int w = 0; w < webResults.size(); w++) {
					var wr = webResults.get(w);
					int n = candidates.size() + w + 1;
					citations.add(new Citation(n, null, "web|" + wr.url(), wr.title(), null, wr.content(), SOURCE_WEB,
							wr.url()));
					sources.append("[")
						.append(n)
						.append("] （网络来源）")
						.append(wr.title())
						.append('\n')
						.append(wr.content())
						.append("\n\n");
				}
				String recall = guidelines(
						conversationContext, "第一次仅依据论文资料回答时依据不足。现在补充了编号较大的"
								+ "（网络来源）片段，请综合论文资料与网络来源重新回答：论文资料能佐证的部分照常标注编号，" + "仅来自网络来源的表述标注对应编号。",
						sources.toString());
				try {
					ChatResponse secondResponse = callWithTimeout(
							() -> researchAgent.prompt().user(recall).call().chatResponse());
					String second = secondResponse == null || secondResponse.getResult() == null
							|| secondResponse.getResult().getOutput() == null ? ""
									: secondResponse.getResult().getOutput().getText();
					if (second != null && !second.isBlank()) {
						answer = second.trim();
					}
				}
				catch (Exception e) {
					logger.warn("联网补充重答失败（保留首次回答）: {}", e.getMessage());
				}
			}
		}
		if (answer != null) {
			answer = answer.replace("[[NEED_MORE_SOURCES]]", "").trim();
		}
		// The first source pack may have been extended by the explicit web fallback.
		// Rebuild it before the quality repair pass so new web citations are valid
		// repair targets as well.
		contextSources = "【可引用来源】\n" + sources + (background.isBlank() ? "" : "\n【章节背景，仅用于理解，不得新增引用编号】\n" + background);
		AnswerQualityGate.Result quality = AnswerQualityGate.validate(answer, citations.size());
		if (!allowGeneral && !citations.isEmpty() && (!quality.hasReferences() || !quality.invalid().isEmpty())) {
			String repaired = repairCitations(question, answer, contextSources, citations.size());
			AnswerQualityGate.Result repairedQuality = AnswerQualityGate.validate(repaired, citations.size());
			if (repairedQuality.hasReferences() && repairedQuality.invalid().isEmpty()) {
				answer = repairedQuality.answer();
				quality = repairedQuality;
				logger.info("快速回答通过引用修复闸门: referenced={}", quality.referenced());
			}
		}
		if (!quality.invalid().isEmpty()) {
			logger.warn("快速回答包含越界引用编号: {}", quality.invalid());
		}
		int usedPapers = (int) candidates.stream()
			.map(RpRetrievalService.Candidate::paperId)
			.filter(java.util.Objects::nonNull)
			.distinct()
			.count();
		return new AskResult(quality.answer(), citations, usedPapers);
	}

	/**
	 * One bounded repair pass keeps a fluent answer from becoming an unsupported answer
	 * when the model forgot the source namespace. This is deliberately a rewrite pass,
	 * not an open-ended second research loop.
	 */
	private String repairCitations(String question, String answer, String sourcePack, int sourceCount) {
		String prompt = """
				请修复下面这段科研问答，使每个可核验的事实句都在句末使用真实来源编号，例如 [1] 或 [2]。
				只能使用 1 到 %d 的编号，不要创造编号，不要添加来源中没有的事实。
				保留原问题的语言，删掉无法由来源支持的细节，并保持回答简洁。
				只输出修复后的回答正文，不要解释修复过程。

				问题：%s
				原回答：%s
				来源：
				%s
				""".formatted(sourceCount, question, answer == null ? "" : answer, sourcePack);
		try {
			String repaired = callWithTimeout(() -> researchAgent.prompt().user(prompt).call().content());
			return repaired == null ? "" : repaired.trim();
		}
		catch (Exception e) {
			logger.warn("回答引用修复失败，保留原回答: {}", e.getMessage());
			return answer == null ? "" : answer;
		}
	}

	/**
	 * 回答行为准则（弹性结构）：语气与长度匹配问题复杂度；引用只标在真正来自资料处； 不规定小节模板。闲聊类不走此路径。
	 */
	private String guidelines(String question, String guideline, String sources) {
		return """
								你在回答用户基于一组研究资料片段提出的问题。

								行为准则：
								1. 用与问题相称的方式回答——简单问题两三句即可，比较类或复杂问题才分段展开；不要使用固定的小节标题模板。
								2. 诚实优先：资料不足以回答的部分，直接说明"在当前所选资料中未找到足够依据"，不得编造，也不得把资料之外的内容冒充为资料内容。若资料不足以支撑答案的主体，在回答的最后一行单独输出标记 [[NEED_MORE_SOURCES]]。
								3. 事实性句子必须在句末标注一个或多个真实来源编号（如 [2]）。只能使用【可引用来源】中出现的编号，禁止猜测或创造编号。
								4. 用中文回答。
								检索片段不代表全文。没有检索到某章节时，只能说“本次检索未找到”，不能断言原资料不存在该内容。用户要求原文和翻译时，先给检索到的原文，再给中文翻译。
				5. 编号中标注"（网络来源）"的内容来自网页而非本专题论文。涉及这些来源的表述保持编号，并明确说“网页来源”。

								"""
				+ guideline + "\n\n问题：" + question + "\n\n资料片段：\n" + sources;
	}

	/**
	 * 意图分类（轻量调用）：chitchat=问候/闲聊；general=通用知识；material=资料内容问题； locate=查找/定位。解析失败回退
	 * material（保守，等价旧行为）。
	 */
	private String classifyIntent(String question) {
		return IntentRouter.classify(question);
	}

	/**
	 * Resolve URL-backed papers with one database round trip instead of one per citation.
	 */
	private Map<String, String> sourceUrls(List<RpRetrievalService.Candidate> candidates) {
		if (jdbc == null) {
			return Map.of();
		}
		List<String> paperIds = candidates.stream()
			.map(RpRetrievalService.Candidate::paperId)
			.filter(java.util.Objects::nonNull)
			.distinct()
			.toList();
		if (paperIds.isEmpty()) {
			return Map.of();
		}
		String placeholders = String.join(",", java.util.Collections.nCopies(paperIds.size(), "?::uuid"));
		String sql = "SELECT id::text, metadata->>'source_url' AS url FROM rp_papers " + "WHERE id IN (" + placeholders
				+ ") AND metadata->>'source_type' = 'WEB'";
		try {
			List<Map<String, Object>> rows = jdbc.queryForList(sql, paperIds.toArray());
			Map<String, String> urls = new HashMap<>();
			for (Map<String, Object> row : rows) {
				if (row.get("id") != null && row.get("url") != null) {
					urls.put(String.valueOf(row.get("id")), String.valueOf(row.get("url")));
				}
			}
			return urls;
		}
		catch (Exception e) {
			logger.warn("来源 URL 批量读取失败，保留论文来源: {}", e.getMessage());
			return Map.of();
		}
	}

	private String directAnswer(String question) {
		try {
			return callWithTimeout(() -> researchAgent.prompt()
				.user("用户向你打了个招呼或闲聊了一句。自然、简短地回应（中文，两三句以内），" + "并顺带说明你可以基于他们上传的研究资料回答问题。不要提及联网搜索或其他能力。用户输入：" + question)
				.call()
				.content());
		}
		catch (Exception e) {
			return "你好！把论文资料添加到左侧，就可以基于它们提问了。";
		}
	}

	private String generalAnswer(String question) {
		try {
			return callWithTimeout(
					() -> researchAgent.prompt().user("用中文简洁回答以下问题（通用知识，不超过 300 字）：\n" + question).call().content());
		}
		catch (Exception e) {
			logger.warn("通用回答生成失败: {}", e.getMessage());
			return "回答生成失败，请稍后重试。";
		}
	}

}
