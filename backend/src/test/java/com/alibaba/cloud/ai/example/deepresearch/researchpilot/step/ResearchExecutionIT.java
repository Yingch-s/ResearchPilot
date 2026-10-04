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

import com.alibaba.cloud.ai.example.deepresearch.TestDatabase;

import com.alibaba.cloud.ai.example.deepresearch.researchpilot.evidence.RpClaimVerifier;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.evidence.RpEvidenceService;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpEventLogger;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpRunService;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RunStatus;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V0.4 H1 终验收集成测试（项目所有者指定的关键场景，stub 驱动不依赖 LLM）。
 *
 * <pre>
 * 三步 Plan → Step1 成功 → Step2 执行中触发 HITL（执行段中止）
 *   → 进程重启（全新服务实例，同数据库）→ 恢复 → Step2 完成 → Step3 执行 → 报告 → COMPLETED
 * </pre> 验证：Step1 不重跑；Step2 跨两个 Attempt 各一次且身份独立；Step3 只认领一次； 无 Graph
 * 参与且临时状态丢失不影响已持久化成果；报告凭 run_id 可查。
 */
class ResearchExecutionIT {

	/** 步骤执行记录（Attempt 身份跟随每次调用）。 */
	private record Call(int stepIndex, UUID attemptId) {
	}

	private static JdbcTemplate jdbc;

	@BeforeAll
	static void setUp() {
		DriverManagerDataSource ds = new DriverManagerDataSource(TestDatabase.url(), "researchpilot", "researchpilot");
		try (Connection ignored = ds.getConnection()) {
			// 连通性探测
		}
		catch (Exception e) {
			Assumptions.assumeTrue(false, "PostgreSQL 不可达，跳过: " + e.getMessage());
		}
		jdbc = new JdbcTemplate(ds);
	}

	private static RpEvidenceService stubEvidence() {
		return new RpEvidenceService(jdbc, null, new RpClaimVerifier(null, null));
	}

	@Test
	void threeStepPlan_hitlMidStep_processRestart_recovery_completes() {
		List<Call> calls = new ArrayList<>();

		// ===== 第一段执行（进程 A）：Step1 成功，Step2 执行中 HITL 暂停 =====
		RpRunService runServiceA = new RpRunService(jdbc, new RpEventLogger("./target/rp-events-it-a"));
		String threadId = "it3-" + UUID.randomUUID();
		UUID runId = runServiceA.createRun(threadId, "it-session", null, "三步计划 + HITL + 重启恢复");
		runServiceA.transition(runId, RunStatus.CREATED, RunStatus.PLANNING, null);
		runServiceA.transition(runId, RunStatus.PLANNING, RunStatus.RESEARCHING, null);
		runServiceA.initSteps(runId, List.of("步骤一", "步骤二", "步骤三"));

		List<UUID> attemptsSeen = new ArrayList<>();
		StepExecutor firstLeg = (ctx, stepIndex, query, stepTitle, stepDescription) -> {
			calls.add(new Call(stepIndex, ctx.attemptId()));
			attemptsSeen.add(ctx.attemptId());
			if (stepIndex == 1) {
				throw new StepExecutor.StepAbortedException("HITL 暂停");
			}
			return StepResult.completed(stepIndex, "步骤 " + stepIndex + " 的研究归纳（stub）", 3);
		};
		ResearchScheduler schedulerA = new ResearchScheduler(runServiceA, stubEvidence(),
				new RpEventLogger("./target/rp-events-it-a"), firstLeg, jdbc, 1200, 120);
		schedulerA.startOrResumeRun(runId);
		UUID attempt1Id = attemptsSeen.get(0);

		// ---- 第一段验证：暂停现场 ----
		assertEquals(RunStatus.RESEARCHING, runServiceA.currentStatus(runId), "HITL 暂停后 Run 停留在 RESEARCHING");
		assertEquals(1, ((Number) runServiceA.loadRun(runId).get("execution_cursor")).intValue(), "只有 Step1 完成入游标");
		assertEquals("COMPLETED", String.valueOf(runServiceA.loadSteps(runId).get(0).get("status")));
		assertEquals("RUNNING", String.valueOf(runServiceA.loadSteps(runId).get(1).get("status")),
				"Step2 中止时保留 RUNNING 现场");
		assertEquals(1, calls.stream().filter(c -> c.stepIndex() == 0).count(), "Step1 恰好执行一次");

		// ===== 进程重启：全新服务实例（同数据库），恢复扫描发现 RESEARCHING 的 Run =====
		RpRunService runServiceB = new RpRunService(jdbc, new RpEventLogger("./target/rp-events-it-b"));
		assertTrue(
				runServiceB.recoveryScan().stream().anyMatch(r -> runId.toString().equals(String.valueOf(r.get("id")))),
				"重启后恢复扫描必须发现 RESEARCHING 的 Run");

		StepExecutor secondLeg = (ctx, stepIndex, query, stepTitle, stepDescription) -> {
			calls.add(new Call(stepIndex, ctx.attemptId()));
			return StepResult.completed(stepIndex, "步骤 " + stepIndex + " 的研究归纳（stub 恢复段）", 3);
		};
		ResearchScheduler schedulerB = new ResearchScheduler(runServiceB, stubEvidence(),
				new RpEventLogger("./target/rp-events-it-b"), secondLeg, jdbc, 1200, 120);
		schedulerB.startOrResumeRun(runId);

		// ===== 终态验证 =====
		assertEquals(RunStatus.COMPLETED, runServiceB.currentStatus(runId));
		assertEquals(3, ((Number) runServiceB.loadRun(runId).get("execution_cursor")).intValue());

		// Step1 不重跑：两次执行段中只出现一次
		assertEquals(1, calls.stream().filter(c -> c.stepIndex() == 0).count(), "Step1 不得重新执行");
		// Step2 跨两个 Attempt 各一次，Attempt 身份独立
		List<UUID> step2Attempts = calls.stream().filter(c -> c.stepIndex() == 1).map(Call::attemptId).toList();
		assertEquals(2, step2Attempts.size(), "Step2 跨两个执行段各执行一次");
		assertNotEquals(step2Attempts.get(0), step2Attempts.get(1), "每次 Attempt 有独立身份");
		assertEquals(attempt1Id, step2Attempts.get(0));
		// Step3 只执行一次
		assertEquals(1, calls.stream().filter(c -> c.stepIndex() == 2).count(), "Step3 只执行一次");

		// 步骤结果均为最终提交（COMPLETED），无重复写入痕迹
		var steps = runServiceB.loadSteps(runId);
		steps.forEach(s -> assertEquals("COMPLETED", String.valueOf(s.get("status"))));

		// 报告持久化，凭 run_id 可查
		var report = jdbc.queryForList("SELECT * FROM rp_reports WHERE run_id = ?::uuid", runId.toString());
		assertEquals(1, report.size());
		assertEquals(3, ((Number) report.get(0).get("total_steps")).intValue());
		assertEquals(3, ((Number) report.get(0).get("completed_steps")).intValue());
	}

	@Test
	void parallelStepsUseBoundedWorkerBoundaryAndCommitNormally() {
		RpRunService runService = new RpRunService(jdbc, new RpEventLogger("./target/rp-events-parallel-it"));
		UUID runId = runService.createRun("parallel-" + UUID.randomUUID(), "parallel-session", null, "并行步骤边界");
		runService.transition(runId, RunStatus.CREATED, RunStatus.PLANNING, null);
		runService.transition(runId, RunStatus.PLANNING, RunStatus.RESEARCHING, null);
		runService.initSteps(runId, List.of("并行一", "并行二"));
		java.util.concurrent.atomic.AtomicInteger active = new java.util.concurrent.atomic.AtomicInteger();
		java.util.concurrent.atomic.AtomicInteger maxActive = new java.util.concurrent.atomic.AtomicInteger();
		StepExecutor executor = (ctx, stepIndex, query, stepTitle, stepDescription) -> {
			int now = active.incrementAndGet();
			maxActive.accumulateAndGet(now, Math::max);
			try {
				try {
					Thread.sleep(100);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(e);
				}
				return StepResult.completed(stepIndex, "worker result " + stepIndex, 0);
			}
			finally {
				active.decrementAndGet();
			}
		};
		ResearchScheduler scheduler = new ResearchScheduler(runService, stubEvidence(),
				new RpEventLogger("./target/rp-events-parallel-it"), executor, jdbc, 1200, 120, true, 2);
		assertTrue(scheduler.startOrResumeRun(runId));
		assertEquals(RunStatus.COMPLETED, runService.currentStatus(runId));
		assertTrue(maxActive.get() >= 2, "独立步骤应在有界 worker 中并行执行");
		assertTrue(runService.loadSteps(runId).stream().allMatch(s -> "COMPLETED".equals(s.get("status"))));
	}

}
