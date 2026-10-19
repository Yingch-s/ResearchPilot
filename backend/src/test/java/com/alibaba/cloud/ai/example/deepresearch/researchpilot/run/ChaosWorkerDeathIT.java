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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 5 演练 1：worker 死亡恢复。 模拟：执行中的 run 因 JVM kill -9 租约过期——recoveryScan 必须把它识别为可接管， 且孤儿
 * RUNNING 步骤被失效（attempt 幂等键防旧 worker 迟到提交）。
 */
class ChaosWorkerDeathIT {

	private static JdbcTemplate jdbc;

	private static RpRunService runService;

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
		runService = new RpRunService(jdbc, new RpEventLogger("./target/rp-events-chaos"));
	}

	@AfterEach
	void clean() {
		jdbc.update("DELETE FROM rp_run_steps WHERE run_id IN (SELECT id FROM rp_runs WHERE thread_id LIKE 'chaos-%')");
		jdbc.update("DELETE FROM rp_runs WHERE thread_id LIKE 'chaos-%'");
	}

	@Test
	void deadWorkerRunIsRecoverable() {
		UUID runId = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO rp_runs (id, thread_id, query, status, created_at, lease_until, owner_user_id)
				VALUES (?, 'chaos-1', 'q', 'RESEARCHING', now(), now() - interval '5 minutes', ?)
				""", runId, adminOwner());
		// 孤儿 RUNNING 步骤（旧 worker 死前未提交）
		jdbc.update("""
				INSERT INTO rp_run_steps (run_id, step_index, step_id, status, active_attempt_id)
				VALUES (?, 0, 'chaos-step-0', 'RUNNING', ?)
				""", runId, UUID.randomUUID());

		// 1) recoveryScan 识别可接管
		List<Map<String, Object>> recovered = runService.recoveryScan();
		assertTrue(recovered.stream().anyMatch(r -> String.valueOf(r.get("id")).equalsIgnoreCase(runId.toString())),
				"租约过期的 RESEARCHING run 应被识别为可恢复");

		// 2) 模拟新 worker 接管：先拿租约，再开 attempt+1 执行段（幂等键隔离旧 attempt）
		String newWorker = "chaos-new-worker:" + UUID.randomUUID();
		assertTrue(runService.acquireLease(runId, newWorker, java.time.Duration.ofSeconds(30)), "接管者应能拿到过期租约");
		UUID attemptId = runService.startResearchSegment(runId, java.time.Duration.ofSeconds(1200),
				java.time.Duration.ofSeconds(120), newWorker);
		assertTrue(attemptId != null);

		// 3) 旧 attempt 的迟到提交走真实生产守卫 commitStep —— 必须被拒
		UUID oldAttempt = jdbc.queryForObject(
				"SELECT active_attempt_id FROM rp_run_steps WHERE run_id = ? AND step_index = 0", UUID.class, runId);
		assertTrue(oldAttempt != null);
		boolean lateCommitted = runService.commitStep(runId, 0, oldAttempt, StepStatus.COMPLETED, "旧 worker 迟到结果",
				"chaos-old-worker:" + oldAttempt);
		assertFalse(lateCommitted, "旧 attempt 的迟到提交必须被 commitStep 条件更新拒绝");
		// 新 attempt 的合法提交成功
		assertTrue(runService.claimStep(runId, 0, attemptId, newWorker), "新 worker 应能认领孤儿步骤");
		assertTrue(runService.commitStep(runId, 0, attemptId, StepStatus.COMPLETED, "新 worker 结果", newWorker),
				"当前 attempt 的提交应被接受");
	}

	private Object adminOwner() {
		return jdbc.queryForObject("SELECT id FROM rp_users WHERE username = 'admin'", UUID.class);
	}

}
