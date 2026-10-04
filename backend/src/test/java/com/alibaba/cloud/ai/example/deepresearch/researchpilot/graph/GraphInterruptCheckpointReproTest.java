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

package com.alibaba.cloud.ai.example.deepresearch.researchpilot.graph;

import com.alibaba.cloud.ai.example.deepresearch.serializer.DeepResearchStateSerializer;
import com.alibaba.cloud.ai.example.deepresearch.util.convert.FluxConverter;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.constant.SaverEnum;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H1 交付物 2：HITL 缺失 current_plan 的最小图复现测试（不依赖 Spring / DB / LLM）。
 *
 * <p>
 * 目标：用 2 节点最小图确定 P-01 根因到底在哪一次状态合并 / Checkpoint 时序——
 * <ol>
 * <li>场景 A（同步节点）：produce 同步返回 current_plan → interrupt checkpoint 是否含 current_plan；</li>
 * <li>场景 B（流式节点，模拟 planner 的 FluxConverter 内嵌流）：流结束 mapResult 合并 → interrupt checkpoint
 * 是否缺 current_plan（fork 内 6 轮实验的实测现象）；</li>
 * <li>场景 C：resume 是否从起始节点重跑（produce 执行次数计数）+ 恢复执行中 approve 读到的 current_plan 是否为
 * null。</li>
 * </ol>
 * 测试以"记录事实 + 断言当前行为"编写：一旦底座修复，对应断言需要同步翻转。
 */
class GraphInterruptCheckpointReproTest {

	private static final String PLAN_KEY = "current_plan";

	private static final String PLAN_VALUE = "PLAN-1";

	// 执行计数探针：验证 resume 是否重跑 produce
	private final AtomicInteger produceRuns = new AtomicInteger();

	// approve 节点在恢复执行中读到的 plan（null 即为复现成功）
	private final AtomicReference<Object> approveSawPlan = new AtomicReference<>("NOT_INVOKED");

	private CompiledGraph buildGraph(boolean streamingProduce, boolean planPojo) throws Exception {
		KeyStrategyFactory keyStrategyFactory = () -> {
			Map<String, KeyStrategy> keys = new HashMap<>();
			keys.put(PLAN_KEY, new ReplaceStrategy());
			keys.put("probe", new ReplaceStrategy());
			return keys;
		};
		StateGraph graph = new StateGraph("minimal-hitl-repro", keyStrategyFactory,
				new DeepResearchStateSerializer(OverAllState::new))
			.addNode("produce", node_async(state -> {
				produceRuns.incrementAndGet();
				// 场景 D：生产环境的 current_plan 是复杂 POJO（Plan，含嵌套 Step 列表）
				Object planValue = planPojo ? buildPlanPojo() : PLAN_VALUE;
				if (streamingProduce) {
					// 场景 B：模拟 PlannerNode 的内嵌流式输出（Map value 是 Flux，流结束才合并最终值）
					Flux<ChatResponse> fakeLlmStream = Flux.range(0, 3)
						.map(i -> ChatResponse.builder()
							.generations(List.of(new Generation(new AssistantMessage("chunk-" + i))))
							.build());
					return Map.of(PLAN_KEY,
							FluxConverter.builder()
								.startingNode("produce")
								.startingState(state)
								.mapResult(response -> Map.of(PLAN_KEY, planValue))
								.buildWithChatResponse(fakeLlmStream));
				}
				// 场景 A：同步返回
				return Map.of(PLAN_KEY, planValue);
			}))
			.addNode("approve", node_async(state -> {
				approveSawPlan.set(state.value(PLAN_KEY).orElse(null));
				return Map.of("probe", "approved");
			}))
			.addEdge(START, "produce")
			.addEdge("produce", "approve")
			.addEdge("approve", END);

		SaverConfig saverConfig = SaverConfig.builder()
			.register(SaverEnum.MEMORY.getValue(), new MemorySaver())
			.build();
		CompileConfig compileConfig = CompileConfig.builder()
			.saverConfig(saverConfig)
			.interruptBefore("approve")
			.build();
		return graph.compile(compileConfig);
	}

	private void runToInterrupt(CompiledGraph compiledGraph, RunnableConfig config) {
		compiledGraph.fluxStream(Map.of("probe", "start"), config).blockLast();
	}

	private StateSnapshot lastSnapshot(CompiledGraph compiledGraph, RunnableConfig config) {
		return compiledGraph.getState(config);
	}

	@Test
	void scenarioA_syncNode_checkpointShouldContainPlan() throws Exception {
		CompiledGraph compiledGraph = buildGraph(false, false);
		RunnableConfig config = RunnableConfig.builder().threadId("sync-run").build();

		runToInterrupt(compiledGraph, config);
		StateSnapshot snapshot = lastSnapshot(compiledGraph, config);

		Object planInCheckpoint = snapshot.state().data().get(PLAN_KEY);
		System.out.println("[场景A 同步节点] interrupt 快照 node=" + snapshot.node() + " current_plan=" + planInCheckpoint
				+ " produce 执行次数=" + produceRuns.get());
		// 基线事实断言：同步节点输出先合并后打点，快照应当含 current_plan
		assertEquals(PLAN_VALUE, planInCheckpoint, "同步节点场景下 interrupt 快照应含 current_plan");
	}

	@Test
	void scenarioB_streamingNode_checkpointShouldContainPlanAfterMapResult() throws Exception {
		CompiledGraph compiledGraph = buildGraph(true, false);
		RunnableConfig config = RunnableConfig.builder().threadId("stream-run").build();

		runToInterrupt(compiledGraph, config);
		StateSnapshot snapshot = lastSnapshot(compiledGraph, config);

		Object planInCheckpoint = snapshot.state().data().get(PLAN_KEY);
		System.out.println("[场景B 流式节点] interrupt 快照 node=" + snapshot.node() + " current_plan=" + planInCheckpoint
				+ " produce 执行次数=" + produceRuns.get());
		// 业务期望：mapResult 的最终值应先于 interrupt 合并进 checkpoint。
		// 若此断言失败 → 精确复现 P-01：内嵌流式节点的最终合并晚于 checkpoint 打点。
		assertEquals(PLAN_VALUE, planInCheckpoint, "流式节点场景下 interrupt 快照应含 mapResult 合并后的 current_plan");
	}

	@Test
	void scenarioC_resume_rebuildsFromCheckpoint_andRerunsFromStart() throws Exception {
		CompiledGraph compiledGraph = buildGraph(false, false);
		RunnableConfig config = RunnableConfig.builder().threadId("resume-run").build();

		runToInterrupt(compiledGraph, config);
		int runsBeforeResume = produceRuns.get();

		// 恢复执行
		OverAllState resumedState = compiledGraph.resume(new OverAllState.HumanFeedback(Map.of(), "approve"), config)
			.orElseThrow(() -> new IllegalStateException("无法构建恢复态"));
		compiledGraph.fluxStreamFromInitialNode(resumedState, config).blockLast();

		System.out.println("[场景C resume] produce 执行次数：中断前=" + runsBeforeResume + " 恢复后总计=" + produceRuns.get()
				+ " approve 读到 current_plan=" + approveSawPlan.get());
		// 字节码结论：resume = 从最后 checkpoint 重建 state + fluxStreamFromInitialNode 从头重跑。
		// 最小图实测（2026-10-02）：produce 在恢复段被再次执行（1→2），重跑语义成立 ——
		// 该断言记录此事实，业务级分段恢复（H1 方案 B）落地后翻转。
		assertEquals(runsBeforeResume + 1, produceRuns.get(), "当前底座 resume 语义 = 从起始节点重跑（已复现）");
		assertNotNull(approveSawPlan.get(), "恢复执行中 approve 应能读到 current_plan（String 场景）");
	}

	@Test
	void scenarioD_planPojo_checkpointShouldSurviveStateClone() throws Exception {
		// 生产差异最高嫌疑：current_plan 是复杂 POJO（Plan，嵌套 Step 列表），
		// checkpoint 克隆（cloneState → 序列化）可能静默丢失。
		CompiledGraph compiledGraph = buildGraph(false, true);
		RunnableConfig config = RunnableConfig.builder().threadId("pojo-run").build();

		runToInterrupt(compiledGraph, config);
		StateSnapshot snapshot = lastSnapshot(compiledGraph, config);

		Object planInCheckpoint = snapshot.state().data().get(PLAN_KEY);
		System.out
			.println("[场景D Plan POJO] interrupt 快照 node=" + snapshot.node() + " current_plan=" + planInCheckpoint);
		assertNotNull(planInCheckpoint, "Plan POJO 经 checkpoint 克隆后不应丢失");
		assertTrue(planInCheckpoint instanceof com.alibaba.cloud.ai.example.deepresearch.model.dto.Plan,
				"克隆后类型应保持为 Plan（而非降级为 LinkedHashMap）");
		var restored = (com.alibaba.cloud.ai.example.deepresearch.model.dto.Plan) planInCheckpoint;
		assertEquals("测试计划", restored.getTitle(), "Plan 字段应在克隆后完整保留");
		assertEquals(1, restored.getSteps().size(), "嵌套 Step 列表应在克隆后完整保留");
	}

	private com.alibaba.cloud.ai.example.deepresearch.model.dto.Plan buildPlanPojo() {
		var step = new com.alibaba.cloud.ai.example.deepresearch.model.dto.Plan.Step();
		step.setTitle("step-1");
		step.setDescription("取证");
		var plan = new com.alibaba.cloud.ai.example.deepresearch.model.dto.Plan();
		plan.setTitle("测试计划");
		plan.setHasEnoughContext(false);
		plan.setSteps(List.of(step));
		return plan;
	}

}
