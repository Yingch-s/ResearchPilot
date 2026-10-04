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

package com.alibaba.cloud.ai.example.deepresearch.researchpilot.run;

import com.alibaba.cloud.ai.example.deepresearch.model.dto.Plan;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.usage.LlmUsageRecorder;

/**
 * V0.4 H1.4：业务层规划服务 —— 一次性 ChatClient 调用生成 Plan（不再依赖流式 Graph 节点 + checkpoint
 * 传递）。规划结果直接落库（rp_run_steps），审批走 Run 状态机。
 */
@Service
public class RpPlanningService {

	private static final Logger logger = LoggerFactory.getLogger(RpPlanningService.class);

	/** 决策 3：Max Plan Steps 硬上限（与 planner 提示的 2-4 步约定一致）。 */
	static final int MAX_PLAN_STEPS = 4;

	private final ChatClient plannerAgent;

	private final BeanOutputConverter<Plan> converter = new BeanOutputConverter<>(
			new ParameterizedTypeReference<Plan>() {
			});

	private final Duration requestTimeout;

	@org.springframework.beans.factory.annotation.Autowired
	private LlmUsageRecorder usageRecorder;

	private final java.util.concurrent.ExecutorService callPool = java.util.concurrent.Executors
		.newCachedThreadPool(r -> {
			Thread thread = new Thread(r, "rp-planner-call");
			thread.setDaemon(true);
			return thread;
		});

	public RpPlanningService(ChatClient plannerAgent,
			@org.springframework.beans.factory.annotation.Value("${researchpilot.research-budget.llm-request-timeout-seconds:120}") long timeoutSeconds) {
		this.plannerAgent = plannerAgent;
		this.requestTimeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
	}

	private <T> T callWithTimeout(Callable<T> task) {
		LlmUsageRecorder.Timer usage = usageRecorder == null ? null : usageRecorder.timer("planning");
		var future = callPool.submit(task);
		try {
			T result = future.get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
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
			throw new IllegalStateException("规划请求超时（" + requestTimeout.toSeconds() + "s）", e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			future.cancel(true);
			if (usage != null)
				usage.failure("interrupted");
			throw new IllegalStateException("规划请求被中断", e);
		}
		catch (Exception e) {
			if (usage != null)
				usage.failure("failed");
			throw new IllegalStateException("规划请求失败", e.getCause() == null ? e : e.getCause());
		}
		finally {
			if (usage != null)
				usage.close();
		}
	}

	/** 计划草稿：步骤 + 拟分析维度（用户未指定时由 planner 提出，计划页可改）。 */
	public record PlanDraft(List<String> steps, List<String> dimensions, List<String> clarifyingQuestions,
			List<String> assumptions) {
	}

	/**
	 * 生成研究计划草稿。steps 为空 = 规划失败（调用方提示重试）。
	 */
	public PlanDraft planDraft(String query, String topicId, List<String> dims, List<String> clarifications) {
		return planDraft(query, topicId, dims, clarifications, false);
	}

	/**
	 * Generate a plan with an explicit user choice to accept safe defaults. This keeps
	 * clarification useful when needed, while making the optional path deterministic.
	 */
	public PlanDraft planDraft(String query, String topicId, List<String> dims, List<String> clarifications,
			boolean skipClarification) {
		StringBuilder prompt = new StringBuilder();
		prompt.append("为以下跨文献研究任务制定 2-4 个具体的取证步骤（research steps）。每个步骤聚焦一个可从论文原文取证的问题。\n\n");
		prompt.append("研究任务：").append(query).append('\n');
		if (clarifications != null && !clarifications.isEmpty()) {
			prompt.append("用户对澄清问题的回答：\n");
			for (int i = 0; i < clarifications.size(); i++) {
				prompt.append(i + 1).append(". ").append(clarifications.get(i)).append('\n');
			}
		}
		if (dims != null && !dims.isEmpty()) {
			prompt.append("需要分析的维度（每个维度至少对应一个步骤）：").append(String.join("、", dims)).append('\n');
		}
		if (topicId != null) {
			prompt.append("(任务面向用户上传的专题论文库，步骤必须可以从论文原文取证)\n");
		}
		prompt.append(skipClarification ? """
				用户已选择使用默认研究设置。不要提出澄清问题；直接按问题相关维度制定可执行计划，默认覆盖可用资料，允许联网补充，并在计划中体现这些假设。

				"""
				: """
						若研究问题信息不足以规划有意义的取证步骤（过于宽泛、缺少比较对象或关键条件），
						改为返回（steps 留空，clarifying_questions 最多 3 个）：
						{"needs_clarification":true,"clarifying_questions":["..."],"steps":[]}

						否则返回，不要 markdown：
						{"title":"研究计划","has_enough_context":false,"steps":[{"title":"步骤一的问题","description":"取证说明","step_type":"RESEARCH"}]}

						注意：has_enough_context 必须为 false（需要实际取证）。
						""");
		ChatResponse response = callWithTimeout(
				() -> plannerAgent.prompt().user(prompt.toString()).call().chatResponse());
		String raw = response == null || response.getResult() == null || response.getResult().getOutput() == null ? ""
				: response.getResult().getOutput().getText();
		if (raw == null) {
			return new PlanDraft(skipClarification ? defaultSteps() : List.of(), dims == null ? List.of() : dims,
					List.of(), defaultAssumptions(topicId));
		}
		try {
			String json = raw.trim().replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("```\\s*$", "").trim();
			var tree = new ObjectMapper().readTree(json);
			if (tree.path("needs_clarification").asBoolean(false) && tree.path("clarifying_questions").isArray()
					&& !tree.path("clarifying_questions").isEmpty()) {
				if (skipClarification) {
					logger.info("用户跳过澄清，使用默认研究步骤");
					return new PlanDraft(defaultSteps(), dims == null ? List.of() : dims, List.of(),
							defaultAssumptions(topicId));
				}
				List<String> qs = new ArrayList<>();
				tree.path("clarifying_questions").forEach(q -> qs.add(q.asText()));
				logger.info("planner 请求澄清: {} 个问题", qs.size());
				return new PlanDraft(List.of(), dims == null ? List.of() : dims, qs, defaultAssumptions(topicId));
			}
			Plan plan = converter.convert(json);
			List<String> stepIds = new ArrayList<>();
			if (plan.getSteps() != null) {
				plan.getSteps().stream().limit(MAX_PLAN_STEPS).forEach(s -> {
					String title = s.getTitle() == null || s.getTitle().isBlank() ? "研究步骤 " + (stepIds.size() + 1)
							: s.getTitle();
					stepIds.add(title);
				});
			}
			// V1 交互定案：维度不由前端死板指定——用户未给时由 planner 依据研究问题自行提出，
			// 在计划预览页展示并允许用户增删后再确认
			List<String> proposed = (dims == null || dims.isEmpty()) ? proposeDimensions(query) : dims;
			logger.info("业务层规划完成: {} 个步骤, 维度 {}", stepIds.size(), proposed);
			return new PlanDraft(stepIds, proposed, List.of(), defaultAssumptions(topicId));
		}
		catch (Exception e) {
			logger.warn("计划解析失败: {}", e.getMessage());
			return new PlanDraft(skipClarification ? defaultSteps() : List.of(), dims == null ? List.of() : dims,
					List.of(), defaultAssumptions(topicId));
		}
	}

	private List<String> defaultSteps() {
		return List.of("界定研究问题和资料范围", "提取与问题直接相关的方法、数据和结果证据", "比较关键发现并标记资料未报告或需要复核的部分");
	}

	/**
	 * Defaults are deliberately short and visible; they are part of the reviewed plan,
	 * not hidden prompt state.
	 */
	private List<String> defaultAssumptions(String topicId) {
		var assumptions = new ArrayList<String>();
		assumptions.add(topicId == null ? "以可访问的公开来源和用户问题为范围" : "仅使用本专题已选资料作为主要证据");
		assumptions.add("没有明确时间范围时，覆盖所选资料中可用的全部版本");
		assumptions.add("每个结论都需要可定位的原文证据；资料未报告的内容明确标记为未知");
		return assumptions;
	}

	/**
	 * planner 依据研究问题提出拟分析维度（用户未指定时的默认行为）。
	 */
	private List<String> proposeDimensions(String query) {
		try {
			String raw = callWithTimeout(() -> plannerAgent.prompt().user("""
					针对以下跨文献研究任务，提出 3-5 个适合做证据矩阵列的分析维度（每个维度是简短的名词短语）。
					只输出 JSON 数组，不要 markdown：
					["核心方法","训练需求",...]

					研究任务：%s
					""".formatted(query)).call().content());
			if (raw == null) {
				return List.of();
			}
			String json = raw.trim().replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("```\\s*$", "").trim();
			return new ObjectMapper().readValue(json, new TypeReference<List<String>>() {
			});
		}
		catch (Exception e) {
			logger.warn("维度提议失败（回退默认维度）: {}", e.getMessage());
			return List.of("核心方法", "实验设置", "主要结论");
		}
	}

}
