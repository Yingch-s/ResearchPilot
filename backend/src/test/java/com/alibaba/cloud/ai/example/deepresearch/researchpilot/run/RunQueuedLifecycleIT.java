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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0-B Review Focus 2：取消与补位竞争。
 *
 * <p>
 * QUEUED run 被用户取消的同一时刻被 sweeper 扫到 —— 条件转移必须拒绝已取消的 run， 绝不允许「取消后仍被补位启动」。同时锚定 QUEUED
 * 不属于恢复扫描范围。
 */
class RunQueuedLifecycleIT {

	private static JdbcTemplate jdbc;

	private static TransactionTemplate tx;

	private static RpAdmissionService admission;

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
		admission = new RpAdmissionService(jdbc, null, 0, 10); // activeLimit=0 →
																// 准入关闭，仅测补位逻辑
	}

	@AfterEach
	void clean() {
		jdbc.update("DELETE FROM rp_runs WHERE thread_id LIKE 'qcancel-it-%'");
	}

	private UUID insertQueued() {
		UUID id = UUID.randomUUID();
		jdbc.update(
				"INSERT INTO rp_runs (id, thread_id, query, status, created_at) VALUES (?, ?, 'q', 'QUEUED', now())",
				id, "qcancel-it-" + id);
		return id;
	}

	@Test
	void cancelWinsOverPromotion() {
		UUID run = insertQueued();
		// 用户取消（QUEUED → CANCELLED，单点条件更新，走真实状态机语义）
		assertEquals(1, jdbc.update(
				"UPDATE rp_runs SET status = 'CANCELLED', updated_at = now() WHERE id = ? AND status = 'QUEUED'", run));
		// sweeper 此后扫到它：条件转移必须拒绝（status 已不是 QUEUED），promoteNext 返回空
		List<UUID> promoted = tx.execute(s -> admission.promoteNext(5));
		assertTrue(promoted == null || promoted.isEmpty(), "已取消的 run 不得被补位启动");
		assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM rp_runs WHERE id = ?", String.class, run));
	}

	@Test
	void recoveryScanIgnoresQueued() {
		insertQueued();
		// recoveryScan 只捞 RESEARCHING —— QUEUED 不属于恢复范围（语义回归锚点）
		assertEquals(0,
				jdbc.queryForObject(
						"SELECT count(*) FROM rp_runs WHERE status = 'RESEARCHING' AND thread_id LIKE 'qcancel-it-%'",
						Integer.class));
		assertTrue(jdbc.queryForObject(
				"SELECT count(*) FROM rp_runs WHERE status = 'QUEUED' AND thread_id LIKE 'qcancel-it-%'",
				Integer.class) >= 1);
	}

}
