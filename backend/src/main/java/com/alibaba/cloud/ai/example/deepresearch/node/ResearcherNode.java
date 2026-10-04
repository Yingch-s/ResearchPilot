/*
 * Copyright 2025 the original author or authors.
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

package com.alibaba.cloud.ai.example.deepresearch.node;

import com.alibaba.cloud.ai.example.deepresearch.config.SmartAgentProperties;
import com.alibaba.cloud.ai.example.deepresearch.model.enums.StreamNodePrefixEnum;
import com.alibaba.cloud.ai.example.deepresearch.model.dto.Plan;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.retrieve.RpRetrievalService;
import com.alibaba.cloud.ai.example.deepresearch.model.multiagent.AgentSelectionResult;
import com.alibaba.cloud.ai.example.deepresearch.service.McpProviderFactory;
import com.alibaba.cloud.ai.example.deepresearch.service.SearchFilterService;
import com.alibaba.cloud.ai.example.deepresearch.service.SearchInfoService;
import com.alibaba.cloud.ai.example.deepresearch.service.multiagent.SmartAgentDispatcherService;
import com.alibaba.cloud.ai.example.deepresearch.service.multiagent.SmartAgentSelectionHelperService;
import com.alibaba.cloud.ai.example.deepresearch.util.*;
import com.alibaba.cloud.ai.example.deepresearch.util.convert.FluxConverter;
import com.alibaba.cloud.ai.example.deepresearch.util.multiagent.AgentIntegrationUtil;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.toolcalling.jinacrawler.JinaCrawlerService;
import com.alibaba.cloud.ai.toolcalling.searches.SearchEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author sixiyida
 * @since 2025/6/14 11:17
 */

public class ResearcherNode implements NodeAction {

	private static final Logger logger = LoggerFactory.getLogger(ResearcherNode.class);

	private final ChatClient researchAgent;

	private final String executorNodeId;

	private final String nodeName;

	// ResearchPilot Phase B2.3：论文库检索服务（state 带 rp_topic_id 时启用）
	private final com.alibaba.cloud.ai.example.deepresearch.researchpilot.retrieve.RpRetrievalService rpRetrievalService;

	private final com.alibaba.cloud.ai.example.deepresearch.researchpilot.evidence.RpEvidenceService rpEvidenceService;

	private final ReflectionProcessor reflectionProcessor;

	// MCP工厂
	private final McpProviderFactory mcpFactory;

	private final SearchInfoService searchInfoService;

	private final SmartAgentSelectionHelperService smartAgentSelectionHelper;

	public ResearcherNode(ChatClient researchAgent, String executorNodeId, ReflectionProcessor reflectionProcessor,
			McpProviderFactory mcpFactory, SearchFilterService searchFilterService,
			SmartAgentDispatcherService smartAgentDispatcher, SmartAgentProperties smartAgentProperties,
			JinaCrawlerService jinaCrawlerService,
			com.alibaba.cloud.ai.example.deepresearch.researchpilot.retrieve.RpRetrievalService rpRetrievalService,
			com.alibaba.cloud.ai.example.deepresearch.researchpilot.evidence.RpEvidenceService rpEvidenceService) {
		this.researchAgent = researchAgent;
		this.executorNodeId = executorNodeId;
		this.nodeName = "researcher_" + executorNodeId;
		this.reflectionProcessor = reflectionProcessor;
		this.mcpFactory = mcpFactory;
		this.searchInfoService = new SearchInfoService(jinaCrawlerService, searchFilterService, null);
		this.smartAgentSelectionHelper = AgentIntegrationUtil.createSelectionHelper(smartAgentProperties,
				smartAgentDispatcher, null, null);
		this.rpRetrievalService = rpRetrievalService;
		this.rpEvidenceService = rpEvidenceService;
	}

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		Plan currentPlan = StateUtil.getPlan(state);
		Map<String, Object> updated = new HashMap<>();

		Plan.Step assignedStep = findAssignedStep(currentPlan);

		if (assignedStep == null) {
			logger.info("No remaining steps to be executed by {}", nodeName);
			return updated;
		}

		// Handle reflection logic
		if (reflectionProcessor != null) {
			ReflectionProcessor.ReflectionHandleResult reflectionResult = reflectionProcessor
				.handleReflection(assignedStep, nodeName, "researcher");

			if (!ReflectionUtil.shouldContinueAfterReflection(reflectionResult)) {
				logger.debug("Step {} reflection processing completed, skipping execution", assignedStep.getTitle());
				// ResearchPilot fix: reflection 落地后的状态变更也要写回 state
				updated.put("current_plan", currentPlan);
				return updated;
			}
		}

		// Mark step as processing
		assignedStep.setExecutionStatus(StateUtil.EXECUTION_STATUS_PROCESSING_PREFIX + nodeName);
		// ResearchPilot fix (2026-10-01): state 值在节点间经序列化往返，processing 状态必须写回，
		// 否则并行同僚/重入节点会重复认领同一步骤
		updated.put("current_plan", currentPlan);

		try {
			// Build task messages
			List<Message> messages = new ArrayList<>();
			TemplateUtil.addShortUserRoleMemory(messages, state);
			// Build task message with reflection history
			String originTaskContent = buildTaskMessage(assignedStep);
			String taskContent = buildTaskMessageWithReflectionHistory(assignedStep);
			Message taskMessage = new UserMessage(taskContent);
			messages.add(taskMessage);

			// ResearchPilot Phase C：本步骤命中的论文库候选（流结束后与产出一起登记证据）
			List<com.alibaba.cloud.ai.example.deepresearch.researchpilot.evidence.RpEvidenceService.KbCandidate> kbCandidates = new ArrayList<>();

			// ResearchPilot Phase B2.3：state 带 rp_topic_id 时，从用户论文库做混合检索，
			// 将带锚点的原文段落注入任务消息 —— 研究步骤优先使用专题文献而非全网搜索
			String rpTopicId = state.value("rp_topic_id", "");
			if (org.springframework.util.StringUtils.hasText(rpTopicId)) {
				String stepQuery = assignedStep.getTitle() + " " + assignedStep.getDescription();
				try {
					var candidates = rpRetrievalService.hybridRetrieve(rpTopicId, null, stepQuery, 6, true);
					if (!candidates.isEmpty()) {
						for (var c : candidates) {
							kbCandidates.add(
									new com.alibaba.cloud.ai.example.deepresearch.researchpilot.evidence.RpEvidenceService.KbCandidate(
											c.chunkId(), c.paperId(), c.text(), c.sectionPath(), c.page()));
						}
						String kb = buildKbExcerpts(candidates);
						messages.add(new UserMessage(kb));
						logger.info("researcher {} 注入论文库片段 {} 条 (topic={})", nodeName, candidates.size(), rpTopicId);
					}
					else {
						logger.info("researcher {} 论文库检索无命中 (topic={})", nodeName, rpTopicId);
					}
				}
				catch (Exception e) {
					logger.warn("researcher {} 论文库检索失败（降级为网络搜索）: {}", nodeName, e.getMessage());
				}
			}

			// Add researcher-specific citation reminder
			Message citationMessage = new UserMessage(
					"IMPORTANT: DO NOT include inline citations in the text. Instead, track all sources and include a References section at the end using link reference format. Include an empty line between each citation for better readability. Use this format for each reference:\\n- [Source Title](URL)\\n- [Source Title](URL)");
			messages.add(citationMessage);

			logger.debug("{} Node messages: {}", nodeName, messages);

			// Get search tool
			SearchEnum searchEnum = state.value("search_engine", SearchEnum.class).orElse(null);

			AgentSelectionResult agentSelection = selectSmartAgent(assignedStep, taskContent, state);
			ChatClient selectedAgent = agentSelection.getSelectedAgent();

			// 将智能Agent的状态更新合并到updated中
			updated.putAll(agentSelection.getStateUpdate());

			// Call agent
			var requestSpec = selectedAgent.prompt();

			// 使用MCP工厂创建MCP提供者
			AsyncMcpToolCallbackProvider mcpProvider = mcpFactory != null
					? mcpFactory.createProvider(state, "researchAgent") : null;
			if (mcpProvider != null) {
				requestSpec = requestSpec.toolCallbacks(mcpProvider.getToolCallbacks());
			}

			List<Map<String, String>> siteInformation = new ArrayList<>();
			Object obj = state.value("site_information", new ArrayList<Map<String, String>>());
			if (obj instanceof List<?>) {
				siteInformation = (List<Map<String, String>>) obj;
			}

			List<Map<String, String>> searchResults = searchInfoService
				.searchInfo(state.value("enable_search_filter", true), searchEnum, originTaskContent);
			siteInformation.addAll(searchResults);
			updated.put("site_information", siteInformation);

			messages.add(new UserMessage("以下是搜索结果：\n\n" + searchResults.stream().map(r -> {
				return String.format("标题: %s\n权重: %s\n内容: %s\nurl: %s\n", r.get("title"), r.get("weight"),
						r.get("content"), r.get("url"));
			}).collect(Collectors.joining("\n\n"))));

			Flux<ChatResponse> streamResult = requestSpec.messages(messages)
				.stream()
				.chatResponse()
				.doOnError(error -> StateUtil.handleStepError(assignedStep, nodeName, error, logger));

			// Add step title
			boolean isReflectionNode = assignedStep.getReflectionHistory() != null
					&& !assignedStep.getReflectionHistory().isEmpty();
			String prefix = isReflectionNode ? StreamNodePrefixEnum.RESEARCHER_REFLECT_LLM_STREAM.getPrefix()
					: StreamNodePrefixEnum.RESEARCHER_LLM_STREAM.getPrefix();
			String nodeNum = NodeStepTitleUtil.registerStepTitle(state, isReflectionNode, executorNodeId, "Researcher",
					assignedStep.getTitle(), prefix);

			logger.info("ResearcherNode {} starting streaming with key: {}", executorNodeId, nodeName);

			Flux<GraphResponse<StreamingOutput>> generator = FluxConverter.builder()
				.startingNode(nodeNum)
				.startingState(state)
				.mapResult(response -> {
					// Only handle successful responses - errors are handled in doOnError
					String researchContent = response.getResult().getOutput().getText();
					assignedStep
						.setExecutionStatus(ReflectionUtil.getCompletionStatus(reflectionProcessor != null, nodeName));
					assignedStep.setExecutionRes(Objects.requireNonNull(researchContent));
					logger.info("{} completed, content: {}", nodeName, researchContent);

					updated.put("researcher_content_" + executorNodeId, researchContent);
					// ResearchPilot Phase C：登记本步骤证据（归纳 + 原文，确定性引用校验）
					try {
						rpEvidenceService.registerStepEvidence(state.value("thread_id", ""),
								state.value("rp_topic_id", ""), nodeName + ":" + assignedStep.getTitle(),
								assignedStep.getTitle(), researchContent, kbCandidates);
					}
					catch (Exception evidenceError) {
						logger.warn("证据登记失败（不影响研究流）: {}", evidenceError.getMessage());
					}
					// ResearchPilot fix: completed 状态随 plan 写回 state（见 apply 内注释）
					updated.put("current_plan", currentPlan);
					return updated;
				})
				.buildWithChatResponse(streamResult);

			updated.put("researcher_content_" + executorNodeId, generator);
			return updated;
		}
		catch (Exception e) {
			// Handle any exception that occurs before or during stream setup
			StateUtil.handleStepError(assignedStep, nodeName, e, logger);
			// ResearchPilot fix: error 状态也要随 plan 写回 state
			updated.put("current_plan", currentPlan);
			return updated;
		}
	}

	/**
	 * Find steps assigned to current node
	 */
	/**
	 * ResearchPilot Phase B2.3：把论文库候选格式化为带锚点的引用素材。
	 */
	private String buildKbExcerpts(java.util.List<RpRetrievalService.Candidate> candidates) {
		StringBuilder kb = new StringBuilder();
		kb.append("以下是用户专题论文库中与该研究步骤相关的原文片段（来自已上传论文，优先引用）：").append('\n');
		int idx = 1;
		for (RpRetrievalService.Candidate c : candidates) {
			kb.append("[KB-")
				.append(idx++)
				.append("] 章节: ")
				.append(c.sectionPath())
				.append(" | 页码: ")
				.append(c.page() == null ? "未知" : c.page())
				.append('\n')
				.append(c.text())
				.append("\n\n");
		}
		return kb.toString();
	}

	private Plan.Step findAssignedStep(Plan currentPlan) {
		for (Plan.Step step : currentPlan.getSteps()) {
			if (Plan.StepType.RESEARCH.equals(step.getStepType()) && ReflectionUtil.shouldProcessStep(step, nodeName)) {
				return step;
			}
		}
		return null;
	}

	/**
	 * Build task message
	 */
	private String buildTaskMessage(Plan.Step step) {
		StringBuilder content = new StringBuilder();

		// Basic task information
		content.append("# Current Task\n\n")
			.append("## Title\n\n")
			.append(step.getTitle())
			.append("\n\n")
			.append("## Description\n\n")
			.append(step.getDescription())
			.append("\n\n");

		return content.toString();
	}

	/**
	 * Build task message with reflection history
	 */
	private String buildTaskMessageWithReflectionHistory(Plan.Step step) {
		StringBuilder content = new StringBuilder();

		// Basic task information
		content.append("# Current Task\n\n")
			.append("## Title\n\n")
			.append(step.getTitle())
			.append("\n\n")
			.append("## Description\n\n")
			.append(step.getDescription())
			.append("\n\n");

		// Add reflection history if available
		if (ReflectionUtil.hasReflectionHistory(step)) {
			content.append(ReflectionUtil.buildReflectionHistoryContent(step));
			content.append(
					"Please re-complete this research task based on the above previous attempt results and reflection feedback, ensuring to avoid the previously identified issues and improve upon the previous results.\n\n");
		}

		return content.toString();
	}

	/**
	 * 智能选择Agent 如果智能Agent功能开启，则根据问题类型选择专业化Agent 否则使用原有的researchAgent
	 */
	private AgentSelectionResult selectSmartAgent(Plan.Step step, String taskContent, OverAllState state) {
		String questionContent = step.getTitle();
		if (step.getDescription() != null) {
			questionContent += " " + step.getDescription();
		}

		AgentSelectionResult selectionResult = smartAgentSelectionHelper.selectSmartAgent(questionContent, state,
				researchAgent);

		if (selectionResult.isSmartAgent()) {
			logger.info("为研究任务选择智能Agent: {} -> {} (executorNodeId: {})", questionContent,
					selectionResult.getAgentType(), executorNodeId);
		}
		else {
			logger.debug("使用默认researchAgent: {} (executorNodeId: {})", selectionResult.getReason(), executorNodeId);
		}

		return selectionResult;
	}

}
