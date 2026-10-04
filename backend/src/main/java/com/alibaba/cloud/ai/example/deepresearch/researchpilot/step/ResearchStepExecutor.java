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

package com.alibaba.cloud.ai.example.deepresearch.researchpilot.step;

import com.alibaba.cloud.ai.example.deepresearch.researchpilot.evidence.RpEvidenceService;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.retrieve.RpRetrievalService;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.ExecutionContext;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpEventLogger;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpEventType;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpStepArtifactService;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.store.WebSourceFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * V0.4 H1.2：单步骤执行器（决策 1 的 Step Graph）。
 *
 * <p>
 * 职责边界：本执行器只负责"怎样完成一个研究步骤"——步内检索 → 取证研究 → 有界反思补充取证； 它不决定全局步骤认领，不维护跨步骤游标。执行结果以
 * {@link StepResult} 返回， 证据以 CANDIDATE 登记，接纳由调度器在步骤提交事务后决定（决策 2）。
 *
 * <p>
 * 反思边界：最多 {@link #MAX_REFLECTION_ROUNDS} 轮补充取证（H2 的进展指纹上线前， 这是"有限轮次中间探索"的兜底实现，防止步内无限反思）。
 */
@Service
public class ResearchStepExecutor implements StepExecutor {

	private static final Logger logger = LoggerFactory.getLogger(ResearchStepExecutor.class);

	/** 步内反思上限（含首轮后的补充取证轮次）。 */
	static final int MAX_REFLECTION_ROUNDS = 1;

	/** 步内单次检索 Top-K。 */
	static final int RETRIEVE_TOP_K = 6;

	private final RpRetrievalService retrieval;

	private final RpEvidenceService evidenceService;

	private final ChatClient researchAgent;

	private final RpEventLogger events;

	private final RpStepArtifactService artifacts;

	// V1 UX：网络补充来源（论文库弱命中时）
	private final com.alibaba.cloud.ai.example.deepresearch.researchpilot.ask.RpWebSearchService webSearch;

	/** Discovery results are never formal evidence until the linked page is fetched. */
	private final WebSourceFetcher webFetcher;

	// H2.2：请求级超时（LLM 请求自身可执行的超时，不再只靠外层 Deadline 判断）
	private final Duration llmRequestTimeout;

	/** 请求级超时用的守护线程池（不阻断进程退出）。 */
	private final java.util.concurrent.ExecutorService callPool = java.util.concurrent.Executors
		.newCachedThreadPool(r -> {
			Thread t = new Thread(r, "rp-llm-call");
			t.setDaemon(true);
			return t;
		});

	@org.springframework.beans.factory.annotation.Autowired
	public ResearchStepExecutor(RpRetrievalService retrieval, RpEvidenceService evidenceService,
			ChatClient researchAgent, RpEventLogger events,
			com.alibaba.cloud.ai.example.deepresearch.researchpilot.ask.RpWebSearchService webSearch,
			RpStepArtifactService artifacts, WebSourceFetcher webFetcher,
			@org.springframework.beans.factory.annotation.Value("${researchpilot.research-budget.llm-request-timeout-seconds:120}") long llmTimeoutSeconds) {
		this.retrieval = retrieval;
		this.evidenceService = evidenceService;
		this.researchAgent = researchAgent;
		this.events = events;
		this.artifacts = artifacts;
		this.webSearch = webSearch;
		this.webFetcher = webFetcher;
		this.llmRequestTimeout = Duration.ofSeconds(llmTimeoutSeconds);
	}

	/** Compatibility constructor used by focused unit tests without a database. */
	public ResearchStepExecutor(RpRetrievalService retrieval, RpEvidenceService evidenceService,
			ChatClient researchAgent, RpEventLogger events,
			com.alibaba.cloud.ai.example.deepresearch.researchpilot.ask.RpWebSearchService webSearch,
			long llmTimeoutSeconds) {
		this(retrieval, evidenceService, researchAgent, events, webSearch, null, llmTimeoutSeconds);
	}

	private ResearchStepExecutor(RpRetrievalService retrieval, RpEvidenceService evidenceService,
			ChatClient researchAgent, RpEventLogger events,
			com.alibaba.cloud.ai.example.deepresearch.researchpilot.ask.RpWebSearchService webSearch,
			RpStepArtifactService artifacts, long llmTimeoutSeconds) {
		this(retrieval, evidenceService, researchAgent, events, webSearch, artifacts, null, llmTimeoutSeconds);
	}

	/**
	 * H2.2：带请求级超时的阻塞调用。超时/中断抛 RuntimeException，由调用方按降级路径处理。
	 */
	private <T> T callWithTimeout(Callable<T> task) {
		var future = callPool.submit(task);
		try {
			return future.get(llmRequestTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
		}
		catch (java.util.concurrent.TimeoutException e) {
			future.cancel(true);
			throw new IllegalStateException("LLM 请求超时（" + llmRequestTimeout.toSeconds() + "s）", e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			future.cancel(true);
			throw new IllegalStateException("LLM 请求被中断", e);
		}
		catch (java.util.concurrent.ExecutionException e) {
			throw new IllegalStateException("LLM 请求失败: " + e.getCause().getMessage(), e.getCause());
		}
	}

	@Override
	public StepResult execute(ExecutionContext ctx, int stepIndex, String query, String stepTitle,
			String stepDescription) {
		String stepId = ctx.stepId();
		logger.info("StepExecutor 开始: run={} step={} attempt={}", ctx.runId(), stepId, ctx.attemptId());
		Instant segStart = Instant.now();

		try {
			// 1. 步内检索（Topic Scope 由服务端强制过滤，Agent 无法扩大范围）
			// Keep the user's constraints in every retrieval query. The planner's step
			// title is only a route label (and is often generic), so using it alone
			// silently drops year, venue, language, and other requirements.
			String stepQuery = buildStepQuery(query, stepTitle, stepDescription);
			RpProgressTracker progress = new RpProgressTracker();
			progress.recordQuery(stepQuery);
			List<RpRetrievalService.Candidate> candidates = artifacts == null ? List.of()
					: artifacts.loadRetrieval(ctx.runId(), stepIndex, ctx.topicId(), ctx.paperIds(), stepQuery);
			if (candidates.isEmpty()) {
				candidates = retrieveQuietly(ctx, ctx.topicId() == null ? null : ctx.topicId().toString(), stepQuery);
				if (artifacts != null && !candidates.isEmpty())
					artifacts.saveRetrieval(ctx.runId(), stepIndex, ctx.attemptId(), ctx.topicId(), ctx.paperIds(),
							stepQuery, candidates);
			}
			candidates = progress.filterNovel(candidates, RpRetrievalService.Candidate::chunkId);

			// An explicit web toggle is a source policy, not a fallback hint. A local
			// hit must never short-circuit the required web search.
			if (ctx.allowWeb()) {
				if (!webSearch.available()) {
					events.log(ctx.runId(), stepId, ctx.attemptId(), RpEventType.RESEARCH_BLOCKED,
							"ResearchStepExecutor", segStart,
							Map.of("reason", "web_search_unavailable", "query", stepQuery));
					throw new IllegalStateException("已开启联网搜索，但联网搜索服务不可用；研究未使用本地结果冒充联网结果");
				}
				events.log(ctx.runId(), stepId, ctx.attemptId(), RpEventType.WEB_SEARCH_STARTED, "ResearchStepExecutor",
						segStart, Map.of("query", stepQuery, "max_results", 3));
				var web = webSearch.searchRequired(stepQuery, 3);
				int fetched = 0;
				for (var wr : web) {
					try {
						var snapshot = webFetcher == null ? null : webFetcher.fetch(wr.url());
						String content = snapshot == null ? wr.content() : snapshot.text();
						String title = snapshot == null ? wr.title() : snapshot.title();
						if (content == null || content.isBlank())
							continue;
						candidates.add(new RpRetrievalService.Candidate("web|" + wr.url(), null, null, content,
								"🌐 " + title, null, "web", 0, List.of("web")));
						fetched++;
					}
					catch (Exception fetchFailure) {
						logger.warn("网页来源抓取失败，跳过正式引用: url={} reason={}", wr.url(), fetchFailure.getMessage());
					}
				}
				if (!web.isEmpty() && fetched == 0)
					throw new IllegalStateException("联网搜索找到了网页，但没有网页正文可供引用；研究未使用搜索摘要冒充原文");
				events.log(ctx.runId(), stepId, ctx.attemptId(), RpEventType.WEB_SEARCH_COMPLETED,
						"ResearchStepExecutor", segStart,
						Map.of("query", stepQuery, "results", web.size(), "fetched", fetched));
				if (!web.isEmpty()) {
					logger.info("步骤已完成联网搜索，获得 {} 条网页来源: run={} step={}", web.size(), ctx.runId(), stepId);
				}
			}

			// 2. 取证研究（注入带锚点原文片段）
			String researchText = research(ctx, query, stepTitle, stepDescription, candidates);
			events.log(ctx.runId(), stepId, ctx.attemptId(), RpEventType.LLM_CALL_COMPLETED, "ResearchStepExecutor",
					segStart, Map.of("round", 0, "candidates", candidates.size()));

			// 3. 反思循环：进展检测器驱动（H2.1）——补充取证仅当①缺失方面是"新查询签名"
			// ②检索命中未见 chunk（语义换说法命中所见 chunk 同样判无进展）③Deadline 未到
			int evidenceCount = 0;
			for (int round = 1; round <= MAX_REFLECTION_ROUNDS; round++) {
				if (ctx.stepDeadline() != null && Instant.now().isAfter(ctx.stepDeadline())) {
					logger.warn("步骤 Deadline 已过，停止步内反思: run={} step={}", ctx.runId(), stepId);
					break;
				}
				String missing = checkSufficiency(query, stepTitle, researchText);
				if (missing == null) {
					break;
				}
				if (!progress.isNewQuery(missing)) {
					logger.info("反思轮查询与已尝试重复（签名命中），停止: run={} step={}", ctx.runId(), stepId);
					break;
				}
				progress.recordQuery(missing);
				events.log(ctx.runId(), stepId, ctx.attemptId(), RpEventType.LLM_CALL_STARTED, "ResearchStepExecutor",
						segStart, Map.of("reflection_round", round, "missing", missing));
				List<RpRetrievalService.Candidate> more = progress.filterNovel(
						retrieveQuietly(ctx, ctx.topicId() == null ? null : ctx.topicId().toString(), missing),
						RpRetrievalService.Candidate::chunkId);
				if (more.isEmpty()) {
					logger.info("补充检索无未见 chunk（无进展信号），停止: run={} step={}", ctx.runId(), stepId);
					break;
				}
				List<RpRetrievalService.Candidate> merged = new ArrayList<>(candidates);
				merged.addAll(more);
				candidates = merged;
				researchText = researchText + "\n\n" + research(ctx, query, stepTitle, "补充取证：" + missing, more);
				if (artifacts != null)
					artifacts.saveRetrieval(ctx.runId(), stepIndex, ctx.attemptId(), ctx.topicId(), ctx.paperIds(),
							missing, more);
				logger.info("步骤反思补充取证 round={} step={}", round, stepId);
			}

			// 4. 证据登记（CANDIDATE，接纳由调度器决定）
			if (ctx.topicId() != null && !candidates.isEmpty()) {
				evidenceService.registerRunStepEvidence(ctx.runId().toString(), ctx.topicId().toString(),
						ctx.attemptId().toString(), stepId, stepTitle, researchText,
						candidates.stream()
							.map(c -> new RpEvidenceService.KbCandidate(c.chunkId(), c.paperId(), c.text(),
									c.sectionPath(), c.page(),
									c.chunkId() != null && c.chunkId().startsWith("web|") ? "WEB" : "PAPER",
									c.chunkId() != null && c.chunkId().startsWith("web|") ? c.chunkId().substring(4)
											: null))
							.toList());
				evidenceCount = candidates.size();
			}

			if (artifacts != null)
				artifacts.saveSummary(ctx.runId(), stepIndex, ctx.attemptId(), stepQuery, researchText);
			return StepResult.completed(stepIndex, researchText, evidenceCount);
		}
		catch (StepExecutor.StepAbortedException e) {
			throw e;
		}
		catch (Exception e) {
			logger.error("步骤执行失败: run={} step={}: {}", ctx.runId(), stepId, e.getMessage(), e);
			return StepResult.failed(stepIndex, e.getMessage());
		}
	}

	private String buildStepQuery(String query, String stepTitle, String stepDescription) {
		StringBuilder out = new StringBuilder(query == null ? "" : query.trim());
		if (stepTitle != null && !stepTitle.isBlank())
			out.append("\n研究步骤：").append(stepTitle.trim());
		if (stepDescription != null && !stepDescription.isBlank() && !stepDescription.equals(stepTitle))
			out.append("\n步骤说明：").append(stepDescription.trim());
		return out.toString().trim();
	}

	private List<RpRetrievalService.Candidate> retrieveQuietly(ExecutionContext ctx, String topicId, String query) {
		try {
			// V1 UX-2：范围快照 —— 检索限定在 Run 创建时冻结的资料范围内
			List<RpRetrievalService.Candidate> candidates = retrieval.hybridRetrieve(topicId, ctx.paperIds(), query,
					RETRIEVE_TOP_K, true);
			events.log(ctx.runId(), ctx.stepId(), ctx.attemptId(), RpEventType.RETRIEVAL_COMPLETED,
					"ResearchStepExecutor", null, Map.of("candidates", candidates.size()));
			return candidates;
		}
		catch (Exception e) {
			logger.warn("步内检索失败（降级为无片段研究）: {}", e.getMessage());
			return List.of();
		}
	}

	private String research(ExecutionContext ctx, String query, String stepTitle, String stepDescription,
			List<RpRetrievalService.Candidate> candidates) {
		StringBuilder prompt = new StringBuilder();
		prompt.append("研究任务：").append(query).append("\n");
		prompt.append("当前研究步骤：").append(stepTitle).append('\n');
		if (stepDescription != null && !stepDescription.isBlank()) {
			prompt.append("步骤说明：").append(stepDescription).append('\n');
		}
		if (!candidates.isEmpty()) {
			prompt.append("\n以下是用户专题论文库中的原文片段，你的归纳必须引用这些原文（标注 [KB-n]），不得编造：\n");
			int idx = 1;
			for (RpRetrievalService.Candidate c : candidates) {
				prompt.append("[KB-")
					.append(idx++)
					.append("] 章节: ")
					.append(c.sectionPath() == null ? "" : c.sectionPath())
					.append(" | 页码: ")
					.append(c.page() == null ? "未知" : c.page())
					.append('\n')
					.append(c.text())
					.append("\n\n");
			}
		}
		prompt.append("\n请基于上述材料完成本步骤的取证归纳。用中文回答，引用原文时保留 [KB-n] 标注；");
		prompt.append("材料不足以回答的部分明确说明\"论文中未报告\"，不得猜测。");
		try {
			var parents = retrieval.expandParents(
					candidates.stream().map(RpRetrievalService.Candidate::parentId).toList(),
					ctx.topicId() == null ? null : ctx.topicId().toString(), ctx.paperIds());
			String background = com.alibaba.cloud.ai.example.deepresearch.researchpilot.retrieve.ParentContextBudget
				.assemble(candidates, parents, 12000);
			if (!background.isBlank())
				prompt.append("\n以下章节背景仅用于理解条件与限定；引用仍必须绑定上面的 [KB-n] 原文，不得将背景当作已校验结论：\n").append(background);
		}
		catch (Exception e) {
			logger.warn("章节背景读取失败，保留已检索原文: {}", e.getMessage());
		}
		long start = System.currentTimeMillis();
		var response = callWithTimeout(() -> researchAgent.prompt().user(prompt.toString()).call().chatResponse());
		logLlmUsage(ctx, "research", start, response);
		return response == null || response.getResult() == null || response.getResult().getOutput() == null ? ""
				: response.getResult().getOutput().getText();
	}

	/**
	 * H2.1：LLM 调用事件（耗时 + Token；模型不返回 usage 时记 null，不虚构）。
	 */
	private void logLlmUsage(ExecutionContext ctx, String kind, long start,
			org.springframework.ai.chat.model.ChatResponse response) {
		Map<String, Object> detail = new java.util.LinkedHashMap<>();
		detail.put("kind", kind);
		detail.put("duration_ms", System.currentTimeMillis() - start);
		Integer promptTokens = null;
		Integer completionTokens = null;
		try {
			if (response != null && response.getMetadata() != null && response.getMetadata().getUsage() != null) {
				var usage = response.getMetadata().getUsage();
				promptTokens = usage.getPromptTokens();
				completionTokens = usage.getCompletionTokens();
			}
		}
		catch (Exception ignored) {
			// usage 元数据异常不影响执行
		}
		detail.put("prompt_tokens", promptTokens);
		detail.put("completion_tokens", completionTokens);
		events.log(ctx.runId(), ctx.stepId(), ctx.attemptId(), RpEventType.LLM_CALL_COMPLETED, "ResearchStepExecutor",
				null, detail);
	}

	/**
	 * 有界反思的充分性检查：返回 null = 证据充分；否则返回缺失方面的描述（作为补充检索查询）。
	 */
	private String checkSufficiency(String query, String stepTitle, String researchText) {
		try {
			String prompt = """
					判断以下研究归纳是否足以回答任务目标。只输出 JSON，不要 markdown：
					{"sufficient": true}
					或
					{"sufficient": false, "missing": "缺失的具体方面（作为检索查询，中文）"}

					任务目标：%s
					研究步骤：%s
					现有归纳：
					%s
					""".formatted(query, stepTitle, researchText);
			String raw = callWithTimeout(() -> researchAgent.prompt().user(prompt).call().content());
			if (raw == null) {
				return null;
			}
			String json = raw.trim().replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("```\\s*$", "");
			boolean sufficient = json.contains("\"sufficient\"") && json.contains("true");
			if (sufficient) {
				return null;
			}
			int m = json.indexOf("\"missing\"");
			if (m < 0) {
				return stepTitle;
			}
			String missing = json.substring(m + 10).replaceAll("[^\\p{L}\\p{N}\\s]", " ").trim();
			return missing.isBlank() ? stepTitle : missing.substring(0, Math.min(80, missing.length()));
		}
		catch (Exception e) {
			logger.warn("充分性检查失败（视为充分，停止反思）: {}", e.getMessage());
			return null;
		}
	}

}
