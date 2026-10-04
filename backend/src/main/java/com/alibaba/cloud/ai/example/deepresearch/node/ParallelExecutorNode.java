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

import com.alibaba.cloud.ai.example.deepresearch.model.enums.ParallelEnum;
import com.alibaba.cloud.ai.example.deepresearch.model.dto.Plan;
import com.alibaba.cloud.ai.example.deepresearch.util.StateUtil;
import com.alibaba.cloud.ai.example.deepresearch.config.DeepResearchProperties;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * @author sixiyida
 * @since 2025/6/12
 */

public class ParallelExecutorNode implements NodeAction {

	private static final Logger logger = LoggerFactory.getLogger(ParallelExecutorNode.class);

	private final Map<String, Integer> parallelNodeCount;

	public ParallelExecutorNode(DeepResearchProperties properties) {
		this.parallelNodeCount = properties.getParallelNodeCount();
	}

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		long currResearcher = 0;
		long currCoder = 0;

		Plan curPlan = StateUtil.getPlan(state);
		for (Plan.Step step : curPlan.getSteps()) {
			// 跳过不需要处理的步骤
			// ResearchPilot fix (2026-10-01): LLM 生成的计划可能带占位状态（如 "PENDING"），
			// 原条件把非空占位状态当作已分配，导致步骤永远无法被 Researcher/Coder 认领。
			// 只有"已有执行结果"或"状态已进入执行生命周期（assigned_/processing_/completed_/
			// waiting_*/error_）"才跳过
			if (StringUtils.hasText(step.getExecutionRes()) || isAssignedOrBeyond(step.getExecutionStatus())) {
				continue;
			}

			Plan.StepType stepType = step.getStepType();

			switch (stepType) {
				case PROCESSING:
					if (areAllResearchStepsCompleted(curPlan)) {
						step.setExecutionStatus(assignRole(stepType, currCoder));
						currCoder = (currCoder + 1) % parallelNodeCount.get(ParallelEnum.CODER.getValue());
					}
					logger.info("Waiting for remaining research steps executed");
					break;

				case RESEARCH:
					step.setExecutionStatus(assignRole(stepType, currResearcher));
					currResearcher = (currResearcher + 1) % parallelNodeCount.get(ParallelEnum.RESEARCHER.getValue());
					break;

				// 处理其他可能的StepType
				default:
					logger.debug("Unhandled step type: {}", stepType);
			}
		}
		// ResearchPilot fix (2026-10-01): state 值在节点间经序列化往返，本节点对 plan 的
		// 状态分配必须显式写回，否则 Researcher/Coder 读到的永远是未分配的副本（无限空转循环）
		return Map.of("current_plan", curPlan);
	}

	/**
	 * ResearchPilot: 判定步骤是否已进入执行生命周期（而非占位状态如 PENDING/null/空串）。
	 */
	private boolean isAssignedOrBeyond(String status) {
		if (!StringUtils.hasText(status)) {
			return false;
		}
		return status.startsWith(StateUtil.EXECUTION_STATUS_ASSIGNED_PREFIX)
				|| status.startsWith(StateUtil.EXECUTION_STATUS_PROCESSING_PREFIX)
				|| status.startsWith(StateUtil.EXECUTION_STATUS_COMPLETED_PREFIX)
				|| status.startsWith(StateUtil.EXECUTION_STATUS_ERROR_PREFIX)
				|| status.startsWith(StateUtil.EXECUTION_STATUS_WAITING_REFLECTING)
				|| status.startsWith(StateUtil.EXECUTION_STATUS_WAITING_PROCESSING);
	}

	private String assignRole(Plan.StepType type, long executorId) {
		String role = type == Plan.StepType.PROCESSING ? ParallelEnum.CODER.getValue()
				: ParallelEnum.RESEARCHER.getValue();
		return StateUtil.EXECUTION_STATUS_ASSIGNED_PREFIX + role + "_" + executorId;
	}

	private boolean areAllResearchStepsCompleted(Plan plan) {
		if (CollectionUtils.isEmpty(plan.getSteps())) {
			return true;
		}

		return plan.getSteps()
			.stream()
			.filter(step -> step.getStepType() == Plan.StepType.RESEARCH)
			.allMatch(step -> step.getExecutionStatus().startsWith(StateUtil.EXECUTION_STATUS_COMPLETED_PREFIX)
					|| step.getExecutionStatus().startsWith(StateUtil.EXECUTION_STATUS_ERROR_PREFIX));
	}

}
