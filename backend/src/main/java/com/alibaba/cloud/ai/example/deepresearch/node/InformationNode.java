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

import com.alibaba.cloud.ai.example.deepresearch.model.dto.Plan;
import com.alibaba.cloud.ai.example.deepresearch.util.StateUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;

/**
 * @author yingzi
 * @since 2025/5/18 15:52
 */

public class InformationNode implements NodeAction {

	private static final Logger logger = LoggerFactory.getLogger(InformationNode.class);

	private final BeanOutputConverter<Plan> converter;

	// ResearchPilot Phase D：plan 快照持久化（graph-core interrupt checkpoint 不含 current_plan
	// 的时序问题，恢复时从此表取回注入）
	private final org.springframework.jdbc.core.JdbcTemplate jdbc;

	private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

	public InformationNode(org.springframework.jdbc.core.JdbcTemplate jdbc) {
		this.jdbc = jdbc;
		this.converter = new BeanOutputConverter<>(new ParameterizedTypeReference<Plan>() {
		});
	}

	@Override
	public Map<String, Object> apply(OverAllState state) {
		String result = state.value("planner_content", "");
		logger.info("planner_content: {}", result);
		assert Strings.isNotBlank(result);

		Plan curPlan = null;
		String nextStep = "research_team";
		Map<String, Object> updated = new HashMap<>();
		try {
			curPlan = converter.convert(result);
			// ResearchPilot Phase E：Max Plan Steps 硬上限（Research Budget 维度之一，
			// 上限取 max(4, max_step_num)，与 planner 提示的 2-4 步约定一致）
			int cap = Math.max(4, StateUtil.getMaxStepNum(state));
			if (curPlan.getSteps() != null && curPlan.getSteps().size() > cap) {
				logger.warn("plan 步骤数 {} 超预算上限 {}，截断（Research Budget: max plan steps）", curPlan.getSteps().size(), cap);
				curPlan.setSteps(new java.util.ArrayList<>(curPlan.getSteps().subList(0, cap)));
			}
			logger.info("反序列成功，convert: {}", curPlan);
		}
		catch (Exception e) {
			// 2.2 反序列化失败，尝试重新生成计划
			logger.error("反序列化失败");
			if (StateUtil.getPlanIterations(state) < StateUtil.getPlanMaxIterations(state)) {
				// 尝试重新生成计划
				updated.put("plan_iterations", StateUtil.getPlanIterations(state) + 1);
				nextStep = "planner";
				updated.put("information_next_node", nextStep);
				logger.info("information node -> {} node", nextStep);
				return updated;
			}
			else {
				nextStep = END;
				updated.put("information_next_node", nextStep);
				logger.warn("information node -> {} node", nextStep);
				return updated;
			}
		}
		// 2.3 上下文不足，跳转到human_feedback节点
		if (!StateUtil.getAutoAcceptedPlan(state)) {
			nextStep = "human_feedback";
		}
		else {
			nextStep = "research_team";
			updated.put("plan_iterations", StateUtil.getPlanIterations(state) + 1);
		}
		updated.put("current_plan", curPlan);
		// ResearchPilot Phase D：同步落库 plan 快照，供 /chat/resume 恢复时取回注入
		try {
			String threadId = StateUtil.getThreadId(state);
			String planJson = objectMapper.writeValueAsString(curPlan);
			jdbc.update(
					"INSERT INTO rp_plan_snapshots (thread_id, plan) VALUES (?, ?::jsonb) "
							+ "ON CONFLICT (thread_id) DO UPDATE SET plan = EXCLUDED.plan, saved_at = now()",
					threadId, planJson);
		}
		catch (Exception e) {
			logger.warn("plan 快照保存失败: {}", e.getMessage());
		}
		updated.put("information_next_node", nextStep);
		logger.info("information node -> {} node", nextStep);
		return updated;
	}

}
