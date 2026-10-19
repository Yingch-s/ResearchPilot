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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0-B：准入服务集成测试（真实 PG）。
 *
 * <p>
 * 测试库与运行库共用（现有 TestDatabase 约定），库中存在真实业务数据—— 所有限额按「当前 DB active 基线」动态构造，断言只针对本测试插入的行。 直接
 * INSERT 构造运行行，不走 LLM/调度器——只验证准入决策本身。
 *
 * <p>
 * 注意：直接 new 的服务没有 Spring 事务代理，{@code pg_advisory_xact_lock} 依赖事务边界——因此每次准入调用都用
 * TransactionTemplate 显式包事务， 与生产 {@code @Transactional} 语义一致。
 */
class RpAdmissionServiceIT {

	private static JdbcTemplate jdbc;

	private static TransactionTemplate tx;

	@BeforeAll
	static void setup() {
		DriverManagerDataSource ds;
		try {
			ds = new DriverManagerDataSource(TestDatabase.url(), "researchpilot", "researchpilot");
			jdbc = new JdbcTemplate(ds);
			jdbc.execute("SELECT 1");
		}
		catch (Exception e) {
			Assumptions.assumeTrue(false, "测试库不可用: " + e.getMessage());
			return;
		}
		tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
	}

	@AfterEach
	void clean() {
		jdbc.update("DELETE FROM rp_runs WHERE thread_id LIKE 'admit-it-%'");
	}

	private RpAdmissionService service(int activeLimit, int queueLimit) {
		return new RpAdmissionService(jdbc, null, activeLimit, queueLimit);
	}

	private UUID insertRun(String status) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO rp_runs (id, thread_id, query, status, created_at)
				VALUES (?, ?, 'q', ?, now())
				""", id, "admit-it-" + id, status);
		return id;
	}

	private static int activeCount() {
		Integer n = jdbc.queryForObject("""
				SELECT count(*) FROM rp_runs WHERE status IN ('RESEARCHING','VERIFYING','FINALIZING')
				""", Integer.class);
		return n == null ? 0 : n;
	}

	private RpAdmissionService.AdmitDecision admit(RpAdmissionService service, UUID runId) {
		return tx.execute(s -> service.tryAdmit(runId));
	}

	private List<UUID> promote(RpAdmissionService service, int max) {
		return tx.execute(s -> service.promoteNext(max));
	}

	@Test
	void slotFreeAdmitsDirectly() {
		int baseline = activeCount(); // 活库：现场取基线，避免跨用例漂移
		RpAdmissionService admission = service(baseline + 1, 10);
		UUID run = insertRun("PLANNING");
		assertEquals(RpAdmissionService.AdmitDecision.STARTED, admit(admission, run));
		assertEquals("RESEARCHING", jdbc.queryForObject("SELECT status FROM rp_runs WHERE id = ?", String.class, run));
	}

	@Test
	void fullEnqueues() {
		RpAdmissionService admission = service(activeCount(), 10); // 已满载
		UUID run = insertRun("PLANNING");
		assertEquals(RpAdmissionService.AdmitDecision.QUEUED, admit(admission, run));
		assertEquals("QUEUED", jdbc.queryForObject("SELECT status FROM rp_runs WHERE id = ?", String.class, run));
	}

	@Test
	void queueFullThrows() {
		int baseline = activeCount();
		RpAdmissionService admission = service(baseline, 0); // 满载且队列容量 0
		UUID run = insertRun("PLANNING");
		RpAdmissionQueueFullException e = assertThrows(RpAdmissionQueueFullException.class,
				() -> admit(admission, run));
		assertEquals(baseline, e.stats().active());
		assertEquals("PLANNING", jdbc.queryForObject("SELECT status FROM rp_runs WHERE id = ?", String.class, run));
	}

	@Test
	void concurrentAdmitNeverExceedsLimit() throws Exception {
		int limit = activeCount() + 2;
		RpAdmissionService admission = service(limit, 20);
		List<UUID> runs = new java.util.ArrayList<>();
		for (int i = 0; i < 10; i++) {
			runs.add(insertRun("PLANNING"));
		}
		ExecutorService pool = Executors.newFixedThreadPool(10);
		CountDownLatch latch = new CountDownLatch(10);
		for (UUID r : runs) {
			pool.submit(() -> {
				try {
					tx.execute(s -> admission.tryAdmit(r));
				}
				catch (Exception ignored) {
					// 队列满被拒绝也算合法结果，断言在总数上
				}
				finally {
					latch.countDown();
				}
			});
		}
		latch.await();
		pool.shutdownNow();
		assertTrue(activeCount() <= limit, "活跃 run 数不得超过上限 " + limit + "，实际 " + activeCount());
	}

	@Test
	void promoteFifo() {
		// 用自己插入的 RESEARCHING 行占住唯一的槽（limit = 当前 active），不触碰库里的真实数据
		UUID occupier = insertRun("RESEARCHING");
		RpAdmissionService admission = service(activeCount(), 10); // 已满载
		UUID q1 = insertRun("PLANNING");
		UUID q2 = insertRun("PLANNING");
		// created_at 显式错开，保证 FIFO 判定的确定性（now() 同事务同秒不稳）
		jdbc.update("UPDATE rp_runs SET created_at = now() - interval '2 seconds' WHERE id = ?", q1);
		jdbc.update("UPDATE rp_runs SET created_at = now() - interval '1 seconds' WHERE id = ?", q2);
		assertEquals(RpAdmissionService.AdmitDecision.QUEUED, admit(admission, q1));
		assertEquals(RpAdmissionService.AdmitDecision.QUEUED, admit(admission, q2));
		// 释放槽位：最老的 QUEUED（q1）先被提升
		jdbc.update("DELETE FROM rp_runs WHERE id = ?", occupier);
		List<UUID> promoted = promote(admission, 1);
		assertEquals(1, promoted.size());
		assertEquals(q1, promoted.get(0));
		assertEquals("RESEARCHING", jdbc.queryForObject("SELECT status FROM rp_runs WHERE id = ?", String.class, q1));
		// 已提升后队列只剩 q2，且槽位已被 q1 占用
		assertTrue(promote(admission, 5).isEmpty());
	}

	@Test
	void terminalRunCannotBeRevivedByAdmission() {
		// C1：终态 run 绝不能被准入“复活”为 RESEARCHING（条件更新必须先过 canTransitionTo）
		RpAdmissionService admission = service(activeCount() + 1, 10);
		UUID run = insertRun("CANCELLED");
		assertThrows(IllegalStateException.class, () -> admit(admission, run));
		assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM rp_runs WHERE id = ?", String.class, run));
	}

	@Test
	void admissionOffStillTransitionsToResearching() {
		// C2：activeLimit<=0 关闭准入时，仍必须完成源状态→RESEARCHING 的合法转移（否则 run 卡死 PLANNING）
		RpAdmissionService admission = service(0, 0);
		UUID run = insertRun("PLANNING");
		assertEquals(RpAdmissionService.AdmitDecision.STARTED, admit(admission, run));
		assertEquals("RESEARCHING", jdbc.queryForObject("SELECT status FROM rp_runs WHERE id = ?", String.class, run));
	}

	@Test
	void queueFullDoesNotPoisonOuterTransaction() {
		// C3：tryAdmit 用 REQUIRES_NEW —— 外层事务里捕获 QueueFull 后，外层必须能正常提交
		RpAdmissionService admission = service(activeCount(), 0); // 满载且队列容量 0
		UUID run = insertRun("PLANNING");
		// 模拟 continue/branch 的外层事务：事务内先写标记行，捕获 QueueFull 后正常返回
		UUID marker = UUID.randomUUID();
		tx.execute(s -> {
			jdbc.update(
					"INSERT INTO rp_runs (id, thread_id, query, status, created_at) VALUES (?, ?, 'marker', 'CANCELLED', now())",
					marker, "admit-it-marker-" + marker);
			try {
				admission.tryAdmit(run);
			}
			catch (RpAdmissionQueueFullException ignored) {
				// 调用方捕获并映射 429 —— 外层事务不应被标记 rollback-only
			}
			return null;
		});
		// 外层提交成功 → 标记行仍在
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM rp_runs WHERE id = ?", Integer.class, marker));
	}

	@Test
	void retryFailedStepQueuesInsteadOfDirectStart() {
		// I2：手动重试复活 FAILED run 落 QUEUED（经准入），不得直插 RESEARCHING 绕过限额
		UUID run = UUID.randomUUID();
		jdbc.update(
				"INSERT INTO rp_runs (id, thread_id, query, status, created_at) VALUES (?, ?, 'q', 'FAILED', now())",
				run, "admit-it-retry-" + run);
		jdbc.update("INSERT INTO rp_run_steps (run_id, step_index, step_id, status) VALUES (?, 0, 'step-0', 'FAILED')",
				run);
		RpRunService runService = new RpRunService(jdbc, new RpEventLogger("./target/rp-events-admission-it"));
		assertTrue(runService.retryFailedStep(run, 0));
		assertEquals("QUEUED", jdbc.queryForObject("SELECT status FROM rp_runs WHERE id = ?", String.class, run));
	}

	@Test
	void statsReflectsCurrentState() {
		int baseline = activeCount();
		RpAdmissionService admission = service(baseline + 3, 7);
		RpAdmissionService.AdmissionStats stats = admission.stats();
		assertEquals(baseline, stats.active());
		assertEquals(baseline + 3, stats.activeLimit());
		assertEquals(7, stats.queueLimit());
	}

}
