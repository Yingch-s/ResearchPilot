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
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.ResearchPilotConfig.ResearchBudgetProperties;
import com.alibaba.cloud.ai.example.deepresearch.util.StateUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * @author sixiyida
 * @since 2025/6/12 09:14
 */

public class ResearchTeamNode implements NodeAction {

	private static final Logger logger = LoggerFactory.getLogger(ResearchTeamNode.class);

	// ResearchPilot Phase E：统一 Research Budget（决策记录 2026-10-02）。
	// 本节点是研究循环（research_team ↔ parallel_executor）的必经咽喉点，
	// 全局超时 / 循环轮次上限 / 无进展检测在此强制执行。
	private final ResearchBudgetProperties budget;

	public ResearchTeamNode(ResearchBudgetProperties budget) {
		this.budget = budget;
	}

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		logger.info("research_team node is running.");
		String nextStep = "professional_kb_decision";
		Map<String, Object> updated = new HashMap<>();

		Plan curPlan = StateUtil.getPlan(state);
		// ResearchPilot: 诊断日志 —— 打印 plan 步骤与状态，定位研究循环不收敛问题
		if (curPlan.getSteps() == null) {
			logger.info("research_team plan: steps=null");
		}
		else {
			logger.info("research_team plan: steps={} {}", curPlan.getSteps().size(),
					curPlan.getSteps()
						.stream()
						.map(s -> s.getStepType() + "|" + s.getExecutionStatus() + "|res="
								+ (s.getExecutionRes() == null ? "null" : s.getExecutionRes().length()))
						.collect(java.util.stream.Collectors.joining(" ; ")));
		}
		// 判断steps里的每个step都有执行结果
		// ResearchPilot fix (2026-10-01): planner 可能返回空步骤计划（has_enough_context=true，
		// 背景调查已足够回答问题）—— 原实现对空计划返回 false 导致 research_team ↔
		// parallel_executor 无限循环。空计划视为"研究已完成"，直通聚合/报告阶段
		boolean allDone = CollectionUtils.isEmpty(curPlan.getSteps()) || areAllExecutionResultsPresent(curPlan);

		// ResearchPilot Phase E：预算记账（时间与轮次在 state 中跨轮持久，HITL resume 亦连续）
		long now = System.currentTimeMillis();
		long startedAt = state.value("research_started_at", 0L);
		if (startedAt == 0L) {
			startedAt = now;
			updated.put("research_started_at", startedAt);
		}
		int visits = state.value("research_loop_count", 0) + 1;
		updated.put("research_loop_count", visits);

		if (allDone) {
			nextStep = "professional_kb_decision";
		}
		else if (enforceBudget(state, updated, curPlan, startedAt, visits, now)) {
			// 诚实停止：预算耗尽 ≠ 证据充分 —— 未完成步骤显式标记，随报告披露
			nextStep = "professional_kb_decision";
		}
		else {
			nextStep = "parallel_executor";
		}
		updated.put("research_team_next_node", nextStep);
		logger.info("research_team node -> {} node", nextStep);
		return updated;
	}

	/**
	 * Research Budget 强制：全局超时 / 循环轮次上限 / 无进展检测。 触发任一预算即停止研究，返回部分成果（返回 true）。
	 */
	private boolean enforceBudget(OverAllState state, Map<String, Object> updated, Plan curPlan, long startedAt,
			int visits, long now) {
		long elapsedMs = now - startedAt;
		boolean globalTimeout = elapsedMs > budget.globalTimeoutSeconds() * 1000L;
		boolean loopCap = visits > budget.maxLoopVisits();

		// No Progress Detector：相邻两轮之间"已有结果的步骤数"不增长视为无进展
		int progress = (int) curPlan.getSteps()
			.stream()
			.filter(s -> s.getExecutionStatus() != null
					&& (s.getExecutionStatus().startsWith(StateUtil.EXECUTION_STATUS_COMPLETED_PREFIX)
							|| s.getExecutionStatus().startsWith(StateUtil.EXECUTION_STATUS_ERROR_PREFIX)))
			.count();
		int prevProgress = state.value("rp_progress_fingerprint", -1);
		int noProgressRounds = progress == prevProgress ? state.value("rp_no_progress_rounds", 0) + 1 : 0;
		updated.put("rp_progress_fingerprint", progress);
		updated.put("rp_no_progress_rounds", noProgressRounds);
		boolean stuck = noProgressRounds >= budget.noProgressRounds();

		String reason = null;
		if (globalTimeout) {
			reason = "global_timeout(" + (elapsedMs / 1000) + "s>" + budget.globalTimeoutSeconds() + "s)";
		}
		else if (loopCap) {
			reason = "loop_cap(" + visits + ">" + budget.maxLoopVisits() + ")";
		}
		else if (stuck) {
			reason = "no_progress(" + noProgressRounds + " rounds)";
		}
		if (reason == null) {
			return false;
		}

		logger.warn("ResearchPilot Research Budget 触发（诚实停止）：{} —— 未完成步骤显式标记，产出部分成果", reason);
		for (Plan.Step s : curPlan.getSteps()) {
			if (s.getExecutionStatus() == null
					|| !(s.getExecutionStatus().startsWith(StateUtil.EXECUTION_STATUS_COMPLETED_PREFIX)
							|| s.getExecutionStatus().startsWith(StateUtil.EXECUTION_STATUS_ERROR_PREFIX))) {
				s.setExecutionStatus(StateUtil.EXECUTION_STATUS_ERROR_PREFIX + "budget_exhausted");
				s.setExecutionRes((s.getExecutionRes() == null ? "" : s.getExecutionRes()) + " [研究预算耗尽：" + reason
						+ "，该步骤未完成，相关字段应视为证据不足]");
			}
		}
		updated.put("current_plan", curPlan);
		updated.put("research_budget_exhausted", reason);
		return true;
	}

	public boolean areAllExecutionResultsPresent(Plan plan) {
		if (CollectionUtils.isEmpty(plan.getSteps())) {
			return false;
		}

		return plan.getSteps()
			.stream()
			.allMatch(step -> step.getExecutionStatus() != null
					&& (step.getExecutionStatus().startsWith(StateUtil.EXECUTION_STATUS_COMPLETED_PREFIX)
							|| step.getExecutionStatus().startsWith(StateUtil.EXECUTION_STATUS_ERROR_PREFIX)));
	}

}
