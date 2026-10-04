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

package com.alibaba.cloud.ai.example.deepresearch.controller;

import com.alibaba.cloud.ai.example.deepresearch.model.dto.Plan;
import com.alibaba.cloud.ai.example.deepresearch.config.DeepResearchProperties;
import com.alibaba.cloud.ai.example.deepresearch.controller.graph.GraphProcess;
import com.alibaba.cloud.ai.example.deepresearch.controller.request.ChatRequestProcess;
import com.alibaba.cloud.ai.example.deepresearch.model.ApiResponse;
import com.alibaba.cloud.ai.example.deepresearch.model.req.ChatRequest;
import com.alibaba.cloud.ai.example.deepresearch.model.req.FeedbackRequest;
import com.alibaba.cloud.ai.example.deepresearch.model.req.GraphId;
import com.alibaba.cloud.ai.example.deepresearch.util.SearchBeanUtil;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.constant.SaverEnum;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.observation.GraphObservationLifecycleListener;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.Map;

/**
 * @author yingzi
 * @since 2025/5/17 19:27
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/chat")
public class ChatController {

	private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

	private final CompiledGraph compiledGraph;

	private final GraphProcess graphProcess;

	private final SearchBeanUtil searchBeanUtil;

	private final org.springframework.jdbc.core.JdbcTemplate jdbc;

	private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

	@Autowired
	public ChatController(@Qualifier("deepResearch") StateGraph stateGraph, SearchBeanUtil searchBeanUtil,
			ObjectProvider<ObservationRegistry> observationRegistry, DeepResearchProperties deepResearchProperties,
			org.springframework.jdbc.core.JdbcTemplate jdbc) throws GraphStateException {
		this.jdbc = jdbc;
		this.objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
		SaverConfig saverConfig = SaverConfig.builder()
			.register(SaverEnum.MEMORY.getValue(), new MemorySaver())
			.build();
		this.compiledGraph = stateGraph.compile(CompileConfig.builder()
			.saverConfig(saverConfig)
			// ResearchPilot fix (2026-10-01): 原中断点 human_feedback 之前的状态尚未包含
			// current_plan（InformationNode 的输出在打点后才合并），导致 resume 后 research_team
			// 读 plan 抛 NoSuchElementException。改在 research_team 之前中断：此刻计划已生成
			// 并入 state，语义也正好是"等用户批准后开始研究"
			.interruptBefore("human_feedback")
			.withLifecycleListener(new GraphObservationLifecycleListener(
					observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP)))
			.build());
		this.compiledGraph.setMaxIterations(deepResearchProperties.getMaxIterations());
		this.searchBeanUtil = searchBeanUtil;
		this.graphProcess = new GraphProcess(this.compiledGraph);
		logger.info("ChatController initialized with graph maxIterations: {}",
				deepResearchProperties.getMaxIterations());
	}

	/**
	 * SSE (Server-Sent Events) endpoint for chat streaming.
	 *
	 * Accepts a ChatRequest and returns a Flux that streams chat responses as
	 * ServerSentEvent<String>. Supports both initial questions and human feedback
	 * handling.
	 */
	@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<String>> chatStream(@RequestBody(required = false) ChatRequest chatRequest)
			throws GraphRunnerException, IllegalArgumentException {
		chatRequest = ChatRequestProcess.getDefaultChatRequest(chatRequest, searchBeanUtil);
		if (searchBeanUtil.getSearchService(chatRequest.searchEngine()).isEmpty()) {
			throw new IllegalArgumentException("Search Engine not available.");
		}
		logger.info("Received chat request: {}", chatRequest);
		// 创建线程ID
		GraphId graphId = graphProcess.createNewGraphId(chatRequest.sessionId());
		chatRequest = ChatRequestProcess.updateThreadId(chatRequest, graphId.threadId());

		RunnableConfig runnableConfig = RunnableConfig.builder().threadId(chatRequest.threadId()).build();

		Map<String, Object> objectMap = new HashMap<>();
		// Create a unicast sink to emit ServerSentEvents
		Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();

		// Handle human feedback if auto-accept is disabled and feedback is provided
		if (!chatRequest.autoAcceptPlan() && StringUtils.hasText(chatRequest.interruptFeedback())) {
			graphProcess.handleHumanFeedback(graphId, chatRequest, objectMap, runnableConfig, sink);
		}
		// First question
		else {
			ChatRequestProcess.initializeObjectMap(chatRequest, objectMap);
			logger.info("init inputs: {}", objectMap);
			Flux<NodeOutput> resultFuture = compiledGraph.fluxStream(objectMap, runnableConfig);
			graphProcess.processStream(graphId, resultFuture, sink);
		}

		return sink.asFlux()
			.doOnCancel(() -> logger.info("Client disconnected from stream"))
			.onErrorResume(throwable -> {
				logger.error("Error occurred during streaming", throwable);
				return Mono.just(ServerSentEvent.<String>builder()
					.event("error")
					.data("Error occurred during streaming: " + throwable.getMessage())
					.build());
			});
	}

	@PostMapping("/stop")
	public ApiResponse<String> stopGraph(@RequestBody GraphId graphId) {
		return graphProcess.stopGraph(graphId) ? ApiResponse.success(graphId.threadId())
				: ApiResponse.error("Failure", graphId.threadId());
	}

	@PostMapping(value = "/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<String>> resume(@RequestBody(required = false) FeedbackRequest humanFeedback)
			throws GraphRunnerException {
		RunnableConfig runnableConfig = RunnableConfig.builder().threadId(humanFeedback.threadId()).build();
		Map<String, Object> objectMap = new HashMap<>();
		objectMap.put("feedback", humanFeedback.feedback());
		objectMap.put("feedback_content", humanFeedback.feedbackContent());

		// Create a unicast sink to emit ServerSentEvents
		Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();
		GraphProcess graphProcess = new GraphProcess(this.compiledGraph);

		StateSnapshot stateSnapshot = compiledGraph.getState(runnableConfig);
		OverAllState state = stateSnapshot.state();
		// ResearchPilot Phase D 修复：interrupt checkpoint 缺 current_plan（时序问题），
		// 从 InformationNode 落库的快照取回并注入恢复 state
		try {
			var planRow = jdbc.queryForList("SELECT plan::text FROM rp_plan_snapshots WHERE thread_id = ?",
					humanFeedback.threadId());
			if (!planRow.isEmpty()) {
				Plan restoredPlan = objectMapper.readValue((String) planRow.get(0).get("plan"), Plan.class);
				objectMap.put("current_plan", restoredPlan);
				logger.info("RP-DIAG resume: 已从快照注入 current_plan (steps={})",
						restoredPlan.getSteps() == null ? -1 : restoredPlan.getSteps().size());
			}
			else {
				logger.warn("RP-DIAG resume: 无 plan 快照 thread={}", humanFeedback.threadId());
			}
		}
		catch (Exception e) {
			logger.warn("RP-DIAG resume: plan 快照恢复失败: {}", e.getMessage());
		}
		// ResearchPilot 诊断：检查恢复快照中的 current_plan
		logger.info("RP-DIAG resume: current_plan present={} node={}",
				state.value("current_plan", Plan.class).isPresent(), stateSnapshot.node());
		// ResearchPilot Phase D 实验2：使用 graph-core 正式 resume API 构建恢复态
		// （内部加载 checkpoint + 合并 feedback + 置 resume 标记，checkpoint.nextNodeId 指向断点后继）
		var feedback = new OverAllState.HumanFeedback(objectMap, "research_team");
		OverAllState resumedState = compiledGraph.resume(feedback, runnableConfig)
			.orElseThrow(() -> new IllegalStateException("无法构建恢复态：无 checkpoint thread=" + humanFeedback.threadId()));
		// ResearchPilot Phase D：feedback map 的键不进入顶层 state —— 将快照 plan 直写恢复态
		if (objectMap.containsKey("current_plan")) {
			resumedState.input(Map.of("current_plan", objectMap.get("current_plan")));
			logger.info("RP-DIAG resume: current_plan 直写恢复态完成");
		}
		Flux<NodeOutput> resultFuture = compiledGraph.fluxStreamFromInitialNode(resumedState, runnableConfig);
		graphProcess.processStream(new GraphId(humanFeedback.sessionId(), humanFeedback.threadId()), resultFuture,
				sink);

		return sink.asFlux()
			.doOnCancel(() -> logger.info("Client disconnected from stream"))
			.doOnError(e -> logger.error("Error occurred during streaming", e));
	}

}
