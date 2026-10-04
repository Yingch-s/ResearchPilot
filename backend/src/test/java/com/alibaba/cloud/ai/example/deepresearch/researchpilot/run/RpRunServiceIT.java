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

import com.alibaba.cloud.ai.example.deepresearch.TestDatabase;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V0.4 H1.1：RpRunService 持久化状态机集成测试（决策 1/2/3 验收）。
 *
 * <p>
 * 环境约定：依赖本地 Docker PostgreSQL（localhost:5433）。PG 不可达时 **SKIP（非 FAIL）**——
 * 环境缺失与代码回归明确区分；H4/H5 阶段改用 Testcontainers 使其无条件运行。
 */
class RpRunServiceIT {

	private static RpRunService service;

	private static JdbcTemplate jdbc;

	@BeforeAll
	static void setUp() {
		DriverManagerDataSource ds = new DriverManagerDataSource(TestDatabase.url(), "researchpilot", "researchpilot");
		try (Connection ignored = ds.getConnection()) {
			// 连通性探测
		}
		catch (Exception e) {
			Assumptions.assumeTrue(false, "PostgreSQL 不可达，跳过集成测试（环境缺失 ≠ 代码回归）: " + e.getMessage());
		}
		jdbc = new JdbcTemplate(ds);
		ProxyFactory proxy = new ProxyFactory(new RpRunService(jdbc, new RpEventLogger("./target/rp-events-test")));
		proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),
				new AnnotationTransactionAttributeSource()));
		service = (RpRunService) proxy.getProxy();
	}

	@Test
	void createRunIsIdempotentPerThread() {
		String threadId = "it-" + UUID.randomUUID();
		UUID first = service.createRun(threadId, "s1", null, "测试查询");
		UUID second = service.createRun(threadId, "s1", null, "测试查询");
		// 重复创建（重复点击"开始研究"）返回既有 Run，不产生第二个执行
		assertEquals(first, second);
	}

	@Test
	void createRunIsIdempotentPerClientRequest() {
		String requestId = "client-" + UUID.randomUUID();
		UUID first = service.createRun("thread-" + UUID.randomUUID(), "s1", null, "请求幂等测试", null, requestId);
		UUID second = service.createRun("thread-" + UUID.randomUUID(), "s1", null, "请求幂等测试", null, requestId);
		assertEquals(first, second);
		assertEquals(first, service.findRunByClientRequest(requestId).orElseThrow());
		assertTrue(service.transition(first, RunStatus.CREATED, RunStatus.PLANNING, null));
		assertFalse(service.transition(second, RunStatus.CREATED, RunStatus.PLANNING, null),
				"相同 client_request_id 的并发请求只能有一个请求初始化计划");
	}

	@Test
	void pauseRequestIsDurableAndCanBeCleared() {
		UUID runId = service.createRun("thread-" + UUID.randomUUID(), "s1", null, "暂停测试");
		service.transition(runId, RunStatus.CREATED, RunStatus.PLANNING, null);
		service.transition(runId, RunStatus.PLANNING, RunStatus.RESEARCHING, null);
		assertTrue(service.requestPause(runId));
		assertEquals(Boolean.TRUE, service.loadRun(runId).get("pause_requested"));
		assertTrue(service.clearPause(runId));
		assertEquals(Boolean.FALSE, service.loadRun(runId).get("pause_requested"));
	}

	@Test
	void manualStepRetryReopensOnlyFailedStepWithoutResettingRunBudgetOrCompletedWork() {
		UUID runId = service.createRun("thread-" + UUID.randomUUID(), "s1", null, "步骤重试测试");
		service.transition(runId, RunStatus.CREATED, RunStatus.PLANNING, null);
		service.transition(runId, RunStatus.PLANNING, RunStatus.RESEARCHING, null);
		service.initSteps(runId, List.of("done", "failed"));
		UUID attempt = service.startResearchSegment(runId, Duration.ofMinutes(20), Duration.ofMinutes(2));
		assertTrue(service.claimStep(runId, 0, attempt));
		assertTrue(service.commitStep(runId, 0, attempt, StepStatus.COMPLETED, "kept result"));
		assertTrue(service.claimStep(runId, 1, attempt));
		assertTrue(service.commitStep(runId, 1, attempt, StepStatus.FAILED, "transient failure"));
		String deadline = String.valueOf(service.loadRun(runId).get("global_deadline"));
		service.transition(runId, RunStatus.RESEARCHING, RunStatus.VERIFYING, null);
		service.transition(runId, RunStatus.VERIFYING, RunStatus.FINALIZING, null);
		service.transition(runId, RunStatus.FINALIZING, RunStatus.PARTIAL_COMPLETED, null);
		assertTrue(service.retryFailedStep(runId, 1));
		var steps = service.loadSteps(runId);
		assertEquals("COMPLETED", steps.get(0).get("status"));
		assertEquals("kept result", steps.get(0).get("result"));
		assertEquals("PENDING", steps.get(1).get("status"));
		assertEquals(1, steps.get(1).get("attempt_count"));
		assertEquals(RunStatus.RESEARCHING.name(), service.currentStatus(runId).name());
		assertEquals(deadline, String.valueOf(service.loadRun(runId).get("global_deadline")));
		assertFalse(service.retryFailedStep(runId, 0), "completed steps cannot be manually retried");
	}

	@Test
	void approvedPlanAdjustmentOnlyChangesPendingStepsAndCreatesVersion() {
		UUID runId = service.createRun("thread-" + UUID.randomUUID(), "s1", null, "计划调整测试");
		service.transition(runId, RunStatus.CREATED, RunStatus.PLANNING, null);
		service.transition(runId, RunStatus.PLANNING, RunStatus.RESEARCHING, null);
		service.initSteps(runId, List.of("已完成步骤", "待执行步骤"));
		service.savePlanSnapshot(runId, Map.of("steps", List.of("已完成步骤", "待执行步骤")));
		UUID attempt = service.startResearchSegment(runId, Duration.ofMinutes(20), Duration.ofMinutes(2));
		assertTrue(service.claimStep(runId, 0, attempt));
		assertTrue(service.commitStep(runId, 0, attempt, StepStatus.COMPLETED, "保留结果"));

		var applied = service.applyPlanAdjustment(runId, List.of("已完成步骤", "调整后的步骤", "新增核验步骤"));
		assertEquals(List.of("已完成步骤", "调整后的步骤", "新增核验步骤"), applied.get("steps"));
		var steps = service.loadSteps(runId);
		assertEquals("已完成步骤", steps.get(0).get("step_id"));
		assertEquals("COMPLETED", steps.get(0).get("status"));
		assertEquals("调整后的步骤", steps.get(1).get("step_id"));
		assertEquals("新增核验步骤", steps.get(2).get("step_id"));
		assertTrue(service.planVersions(runId).size() >= 2);
	}

	@Test
	void doubleApprovalIsRejected() {
		String threadId = "it-" + UUID.randomUUID();
		UUID runId = service.createRun(threadId, "s1", null, "审批测试");
		service.transition(runId, RunStatus.CREATED, RunStatus.PLANNING, null);
		service.transition(runId, RunStatus.PLANNING, RunStatus.WAITING_APPROVAL, null);
		// 第一次审批成功
		assertTrue(service.transition(runId, RunStatus.WAITING_APPROVAL, RunStatus.RESEARCHING, null));
		// 同一审批请求重复提交：状态已非 WAITING_APPROVAL，被拒绝
		assertFalse(service.transition(runId, RunStatus.WAITING_APPROVAL, RunStatus.RESEARCHING, null),
				"重复审批必须被拒绝（验收表：同一审批提交两次不生成两个研究执行）");
	}

	@Test
	void illegalTransitionIsRejected() {
		String threadId = "it-" + UUID.randomUUID();
		UUID runId = service.createRun(threadId, "s1", null, "非法转移测试");
		// CREATED → RESEARCHING 不在合法转移表
		assertFalse(service.transition(runId, RunStatus.CREATED, RunStatus.RESEARCHING, null));
		assertEquals(RunStatus.CREATED, service.currentStatus(runId));
	}

	@Test
	void staleAttemptWriteBackIsRejectedAndCursorAdvancesOnlyOnce() {
		String threadId = "it-" + UUID.randomUUID();
		UUID runId = service.createRun(threadId, "s1", null, "Attempt 隔离测试");
		service.transition(runId, RunStatus.CREATED, RunStatus.PLANNING, null);
		service.transition(runId, RunStatus.PLANNING, RunStatus.RESEARCHING, null);
		service.initSteps(runId, java.util.List.of("step-A", "step-B", "step-C"));

		UUID attempt1 = service.startResearchSegment(runId, Duration.ofSeconds(1200), Duration.ofSeconds(120));
		// Attempt1 认领 step-0 → 模拟进程中断 → Attempt2 恢复段启动
		assertTrue(service.claimStep(runId, 0, attempt1));
		UUID attempt2 = service.startResearchSegment(runId, Duration.ofSeconds(1200), Duration.ofSeconds(120));
		assertNotEquals(attempt1, attempt2);
		// 新段启动即孤儿化上一段的 RUNNING 步骤（标 FAILED，可被新段重新认领）
		assertEquals("FAILED", String.valueOf(service.loadSteps(runId).get(0).get("status")));

		// Attempt1 迟到写回：status 已非 RUNNING → 拒绝（决策 2 核心场景）
		assertFalse(service.commitStep(runId, 0, attempt1, StepStatus.COMPLETED, "旧段结果"), "过期 Attempt 的迟到写回必须被拒绝");
		// 迟到写回不得推进游标
		assertEquals(0, ((Number) service.loadRun(runId).get("execution_cursor")).intValue());

		// Attempt2 重新认领并提交成功
		assertTrue(service.claimStep(runId, 0, attempt2));
		assertTrue(service.commitStep(runId, 0, attempt2, StepStatus.COMPLETED, "新段结果"));
		// 重复提交（状态已非 RUNNING）被拒 —— 幂等
		assertFalse(service.commitStep(runId, 0, attempt2, StepStatus.COMPLETED, "重复结果"));
		assertEquals(1, ((Number) service.loadRun(runId).get("execution_cursor")).intValue());
	}

	@Test
	void recoveryScanFindsResearchingRuns() {
		String threadId = "it-" + UUID.randomUUID();
		UUID runId = service.createRun(threadId, "s1", null, "恢复扫描测试");
		service.transition(runId, RunStatus.CREATED, RunStatus.PLANNING, null);
		service.transition(runId, RunStatus.PLANNING, RunStatus.RESEARCHING, null);

		boolean found = service.recoveryScan()
			.stream()
			.anyMatch(r -> runId.toString().equals(String.valueOf(r.get("id"))));
		assertTrue(found, "RESEARCHING 状态的 Run 必须出现在恢复扫描中");

		// 终态后不再出现
		service.initSteps(runId, java.util.List.of("s"));
		service.claimStep(runId, 0,
				service.startResearchSegment(runId, Duration.ofSeconds(60), Duration.ofSeconds(10)));
		service.commitStep(runId, 0,
				service.loadSteps(runId).stream().findFirst().map(s -> (UUID) s.get("active_attempt_id")).orElseThrow(),
				StepStatus.COMPLETED, "done");
		// Verify → Finalize → Completed
		service.transition(runId, RunStatus.RESEARCHING, RunStatus.VERIFYING, null);
		service.transition(runId, RunStatus.VERIFYING, RunStatus.FINALIZING, null);
		service.transition(runId, RunStatus.FINALIZING, RunStatus.COMPLETED, null);
		assertFalse(service.recoveryScan().stream().anyMatch(r -> runId.toString().equals(String.valueOf(r.get("id")))),
				"终态 Run 不应出现在恢复扫描中");
	}

	@Test
	void runLeaseExcludesOtherWorkersAndHeartbeatRenewsIt() {
		UUID runId = service.createRun("lease-" + UUID.randomUUID(), "s1", null, "租约测试");
		service.transition(runId, RunStatus.CREATED, RunStatus.PLANNING, null);
		service.transition(runId, RunStatus.PLANNING, RunStatus.RESEARCHING, null);
		assertTrue(service.acquireLease(runId, "worker-a", Duration.ofSeconds(30)));
		assertFalse(service.acquireLease(runId, "worker-a", Duration.ofSeconds(30)));
		assertFalse(service.acquireLease(runId, "worker-b", Duration.ofSeconds(30)));
		assertTrue(service.heartbeat(runId, "worker-a", Duration.ofSeconds(30)));
		assertFalse(service.heartbeat(runId, "worker-b", Duration.ofSeconds(30)));
		service.releaseLease(runId, "worker-a");
		assertTrue(service.acquireLease(runId, "worker-b", Duration.ofSeconds(30)));
		service.releaseLease(runId, "worker-b");
	}

	@Test
	void expiredOwnerCannotReviveLeaseOrMutateStepsAfterTakeover() {
		UUID runId = researchingRun();
		service.initSteps(runId, java.util.List.of("step-A"));
		assertTrue(service.acquireLease(runId, "old", Duration.ofSeconds(30)));
		UUID attempt = service.startResearchSegment(runId, Duration.ofSeconds(60), Duration.ofSeconds(10), "old");
		assertTrue(service.claimStep(runId, 0, attempt, "old"));
		jdbc.update("UPDATE rp_runs SET lease_until = now() - interval '1 second' WHERE id = ?", runId);
		assertFalse(service.heartbeat(runId, "old", Duration.ofSeconds(30)));
		assertFalse(service.ownsLease(runId, "old"));
		assertFalse(service.commitStep(runId, 0, attempt, StepStatus.COMPLETED, "expired", "old"));
		assertTrue(service.acquireLease(runId, "new", Duration.ofSeconds(30)));
		service.releaseLease(runId, "old");
		assertTrue(service.ownsLease(runId, "new"));
		assertFalse(service.skipRemainingSteps(runId, "old"));
		assertFalse(service.transition(runId, RunStatus.RESEARCHING, RunStatus.VERIFYING, null, "old"));
		UUID resumed = service.startResearchSegment(runId, Duration.ofSeconds(60), Duration.ofSeconds(10), "new");
		assertFalse(service.claimStep(runId, 0, attempt, "old"));
		assertTrue(service.claimStep(runId, 0, resumed, "new"));
		assertFalse(service.commitStep(runId, 0, attempt, StepStatus.COMPLETED, "stale", "old"));
		assertTrue(service.commitStep(runId, 0, resumed, StepStatus.COMPLETED, "current", "new"));
		assertEquals("current", service.loadSteps(runId).get(0).get("result"));
		assertEquals(1, ((Number) service.loadRun(runId).get("execution_cursor")).intValue());
		service.releaseLease(runId, "new");
	}

	@Test
	void terminalRunRejectsStepCommit() {
		UUID runId = researchingRun();
		service.initSteps(runId, java.util.List.of("step-A"));
		UUID attempt = service.startResearchSegment(runId, Duration.ofSeconds(60), Duration.ofSeconds(10));
		assertTrue(service.claimStep(runId, 0, attempt));
		assertTrue(service.transition(runId, RunStatus.RESEARCHING, RunStatus.CANCELLED, null));
		assertFalse(service.commitStep(runId, 0, attempt, StepStatus.COMPLETED, "late"));
		assertEquals(0, ((Number) service.loadRun(runId).get("execution_cursor")).intValue());
	}

	@Test
	void finalizationFailureRollsBackStateAndReport() {
		UUID runId = researchingRun();
		assertTrue(service.acquireLease(runId, "finalizer", Duration.ofSeconds(30)));
		assertThrows(IllegalStateException.class, () -> service.finalizeWithLease(runId, "finalizer", () -> {
			assertTrue(service.transition(runId, RunStatus.RESEARCHING, RunStatus.VERIFYING, null));
			assertTrue(service.transition(runId, RunStatus.VERIFYING, RunStatus.FINALIZING, null));
			jdbc.update("INSERT INTO rp_reports (run_id, report, completed_steps, total_steps) VALUES (?, ?, 0, 1)",
					runId, "incomplete");
			throw new IllegalStateException("simulated report write failure");
		}));
		assertEquals(RunStatus.RESEARCHING, service.currentStatus(runId));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM rp_reports WHERE run_id = ?", Integer.class, runId));
		assertFalse(service.finalizeWithLease(runId, "stale", () -> {
			throw new AssertionError("stale finalizer must never execute");
		}));
		service.releaseLease(runId, "finalizer");
	}

	@Test
	void evidenceAcceptanceIsBoundToSuccessfulStepCommit() {
		UUID runId = researchingRun();
		service.initSteps(runId, java.util.List.of("step-A", "step-B"));
		UUID oldAttempt = service.startResearchSegment(runId, Duration.ofSeconds(60), Duration.ofSeconds(10));
		assertTrue(service.claimStep(runId, 0, oldAttempt));
		UUID oldEvidence = candidateEvidence(runId, oldAttempt, "step-A");
		UUID current = service.startResearchSegment(runId, Duration.ofSeconds(60), Duration.ofSeconds(10));
		assertTrue(service.claimStep(runId, 0, current));
		UUID evidence = candidateEvidence(runId, current, "step-A");
		UUID otherStep = candidateEvidence(runId, current, "step-B");
		assertFalse(service.commitStep(runId, 0, oldAttempt, StepStatus.COMPLETED, "stale"));
		assertTrue(service.commitStep(runId, 0, current, StepStatus.COMPLETED, "accepted"));
		assertEquals("ACCEPTED", evidenceStatus(evidence));
		assertEquals("CANDIDATE", evidenceStatus(oldEvidence));
		assertEquals("CANDIDATE", evidenceStatus(otherStep));
	}

	@Test
	void takeoverWaitsForAtomicFinalizationAndCannotReopenCompletedRun() throws Exception {
		UUID runId = researchingRun();
		assertTrue(service.acquireLease(runId, "finalizer", Duration.ofSeconds(30)));
		var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
		var started = new java.util.concurrent.CountDownLatch(1);
		var takeover = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<Boolean>>();
		try {
			assertTrue(service.finalizeWithLease(runId, "finalizer", () -> {
				takeover.set(executor.submit(() -> {
					started.countDown();
					// Force contention without clock timing or a live recovery scanner.
					return jdbc.update(
							"UPDATE rp_runs SET worker_id = 'new-worker' WHERE id = ? AND status = 'RESEARCHING'",
							runId) == 1;
				}));
				try {
					assertTrue(started.await(2, java.util.concurrent.TimeUnit.SECONDS));
					assertThrows(java.util.concurrent.TimeoutException.class,
							() -> takeover.get().get(200, java.util.concurrent.TimeUnit.MILLISECONDS));
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(e);
				}
				assertTrue(service.transition(runId, RunStatus.RESEARCHING, RunStatus.VERIFYING, null));
				assertTrue(service.transition(runId, RunStatus.VERIFYING, RunStatus.FINALIZING, null));
				assertTrue(service.transition(runId, RunStatus.FINALIZING, RunStatus.COMPLETED, null));
			}));
			assertFalse(takeover.get().get(3, java.util.concurrent.TimeUnit.SECONDS));
			assertEquals(RunStatus.COMPLETED, service.currentStatus(runId));
		}
		finally {
			executor.shutdownNow();
		}
	}

	private UUID candidateEvidence(UUID runId, UUID attemptId, String stepId) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO rp_evidence (id, task_id, topic_id, schema_field_id, value, status,
				  run_id, attempt_id, step_id, evidence_status)
				VALUES (?, ?, ?, 'test-field', 'finding', 'SUPPORTED', ?, ?, ?, 'CANDIDATE')
				""", id, runId.toString(), UUID.randomUUID(), runId, attemptId, stepId);
		return id;
	}

	private String evidenceStatus(UUID id) {
		return jdbc.queryForObject("SELECT evidence_status FROM rp_evidence WHERE id = ?", String.class, id);
	}

	@Test
	void recoveryExcludesPausedRunsAndActiveLeases() {
		UUID runId = researchingRun();
		assertTrue(service.requestPause(runId));
		assertFalse(service.recoveryScan().stream().anyMatch(r -> runId.equals(r.get("id"))));
		assertFalse(service.acquireLease(runId, "paused", Duration.ofSeconds(30)));
		assertTrue(service.clearPause(runId));
		assertTrue(service.acquireLease(runId, "active", Duration.ofSeconds(30)));
		assertFalse(service.recoveryScan().stream().anyMatch(r -> runId.equals(r.get("id"))));
		service.releaseLease(runId, "active");
		assertTrue(service.recoveryScan().stream().anyMatch(r -> runId.equals(r.get("id"))));
	}

	private UUID researchingRun() {
		UUID id = service.createRun("fencing-" + UUID.randomUUID(), "s1", null, "执行权测试");
		assertTrue(service.transition(id, RunStatus.CREATED, RunStatus.PLANNING, null));
		assertTrue(service.transition(id, RunStatus.PLANNING, RunStatus.RESEARCHING, null));
		return id;
	}

}
