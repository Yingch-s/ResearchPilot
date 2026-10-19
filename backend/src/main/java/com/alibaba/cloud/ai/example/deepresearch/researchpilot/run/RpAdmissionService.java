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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * P0-B 任务准入：全局并发上限 + DB 排队 + FIFO 补位。
 *
 * <p>
 * 准入决策用 PG 会话级行锁（pg_advisory_xact_lock，事务内生效）串行化： 「数槽位 → 条件转移」两步不存在并发窗口，多实例部署也安全。 队列即
 * rp_runs 的 QUEUED 行，重启不丢顺序（FIFO 按 created_at）。
 *
 * <p>
 * {@code activeLimit <= 0} 时不启用准入（直接 STARTED），保持旧「直接执行」语义，可一键关闭。
 */
@Service
public class RpAdmissionService {

	public enum AdmitDecision {

		STARTED, QUEUED

	}

	public record AdmissionStats(int active, int queued, int activeLimit, int queueLimit) {
	}

	/** 活跃 = 正在占用执行资源的终态前状态。 */
	private static final String ACTIVE_SQL = """
			SELECT count(*) FROM rp_runs
			WHERE status IN ('RESEARCHING', 'VERIFYING', 'FINALIZING')
			""";

	private static final String ADMISSION_LOCK = "SELECT pg_advisory_xact_lock(hashtext('rp_admission'))";

	private final JdbcTemplate jdbc;

	private final RpEventLogger events;

	private final int activeLimit;

	private final int queueLimit;

	public RpAdmissionService(JdbcTemplate jdbc, RpEventLogger events,
			@Value("${researchpilot.admission.active-runs:${RP_ADMISSION_ACTIVE:4}}") int activeLimit,
			@Value("${researchpilot.admission.queue-runs:${RP_ADMISSION_QUEUE:50}}") int queueLimit) {
		this.jdbc = jdbc;
		this.events = events;
		this.activeLimit = activeLimit;
		this.queueLimit = queueLimit;
	}

	public AdmissionStats stats() {
		return new AdmissionStats(count(ACTIVE_SQL), countQueued(), activeLimit, queueLimit);
	}

	/**
	 * 准入一个 run：有空槽 → RESEARCHING；满载且队列未满 → QUEUED；队列也满 → 抛
	 * {@link RpAdmissionQueueFullException}（调用方映射 429）。 源状态允许 PLANNING / WAITING_APPROVAL
	 * / QUEUED（QUEUED 重入 = 维持排队）。
	 */
	public AdmitDecision tryAdmit(UUID runId) {
		return tx().execute(s -> doTryAdmit(runId));
	}

	private AdmitDecision doTryAdmit(UUID runId) {
		if (activeLimit <= 0) {
			// 准入关闭：跳过计数/排队，但仍必须完成源状态→RESEARCHING 的合法转移（否则 run 卡死 PLANNING）
			RunStatus from = requireStatus(runId);
			if (from == RunStatus.RESEARCHING) {
				return AdmitDecision.STARTED; // resume 幂等
			}
			transitionOrThrow(runId, from, RunStatus.RESEARCHING, "admission off");
			return AdmitDecision.STARTED;
		}
		jdbc.execute(ADMISSION_LOCK);
		RunStatus from = requireStatus(runId);
		int active = count(ACTIVE_SQL);
		if (active < activeLimit && from != RunStatus.QUEUED) {
			transitionOrThrow(runId, from, RunStatus.RESEARCHING, "admitted " + active + "/" + activeLimit);
			return AdmitDecision.STARTED;
		}
		if (from == RunStatus.QUEUED) {
			return AdmitDecision.QUEUED; // 已在队列，维持原状
		}
		if (from == RunStatus.RESEARCHING) {
			return AdmitDecision.STARTED; // 幂等：已在执行
		}
		int queued = countQueued();
		if (queued >= queueLimit) {
			throw new RpAdmissionQueueFullException(new AdmissionStats(active, queued, activeLimit, queueLimit));
		}
		transitionOrThrow(runId, from, RunStatus.QUEUED, "queued: active " + active + "/" + activeLimit);
		logEvent(runId, RpEventType.RUN_QUEUED, Map.of("queued", queued + 1, "active", active));
		return AdmitDecision.QUEUED;
	}

	/** FIFO 补位：提升至多 max 个 QUEUED run；返回本次实际启动的 runId（已转为 RESEARCHING）。 */
	public List<UUID> promoteNext(int max) {
		return tx().execute(s -> doPromoteNext(max));
	}

	private List<UUID> doPromoteNext(int max) {
		jdbc.execute(ADMISSION_LOCK);
		if (max <= 0) {
			return List.of();
		}
		// 准入关闭：无槽位概念，直接按 FIFO 全量补位（避免关闭准入时 QUEUED 永久搁浅）
		int slots = activeLimit <= 0 ? max : activeLimit - count(ACTIVE_SQL);
		if (slots <= 0) {
			return List.of();
		}
		List<UUID> candidates = jdbc.queryForList(
				"SELECT id FROM rp_runs WHERE status = 'QUEUED' ORDER BY created_at LIMIT ?", UUID.class,
				Math.min(max, slots));
		List<UUID> started = new java.util.ArrayList<>();
		for (UUID id : candidates) {
			try {
				transitionOrThrow(id, RunStatus.QUEUED, RunStatus.RESEARCHING, "promoted");
				logEvent(id, RpEventType.RUN_PROMOTED, Map.of());
				started.add(id);
			}
			catch (IllegalStateException e) {
				// 该 run 已被取消/转移（补位与取消竞争）——跳过，不视为错误
			}
		}
		return started;
	}

	private void transitionOrThrow(UUID runId, RunStatus from, RunStatus to, String reason) {
		// C1：准入路径与全局状态机同一权威 —— 终态/非法转移在此拒绝（如 CANCELLED 复活）
		if (!from.canTransitionTo(to)) {
			throw new IllegalStateException("非法状态转移拒绝: " + runId + " " + from + " -> " + to);
		}
		// 与 RpRunService.transition 同语义的单点条件更新（写 budget_reason；advisory lock 已保证无并发窗口）。
		// 不复用 RpRunService.transition：它会经 toStatusEvent 记 RUN_STARTED 事件，
		// 与准入路径的事件语义（RUN_QUEUED/RUN_PROMOTED）重叠，保持事件单一来源。
		int updated = jdbc.update("""
				UPDATE rp_runs SET status = ?, budget_reason = ?, updated_at = now()
				WHERE id = ? AND status = ?
				""", to.name(), reason, runId, from.name());
		if (updated != 1) {
			throw new IllegalStateException("状态转移被拒: " + runId + " " + from + " -> " + to);
		}
	}

	private RunStatus requireStatus(UUID runId) {
		String current = currentStatus(runId);
		if (current == null) {
			throw new IllegalArgumentException("run 不存在: " + runId);
		}
		return RunStatus.valueOf(current);
	}

	/**
	 * 准入事务：显式 REQUIRES_NEW —— continue/branch 控制器自带外层事务， REQUIRED 会把「队列满」异常标记为外层
	 * rollback-only，429 契约变成 500（审查 C3）。
	 */
	private org.springframework.transaction.support.TransactionTemplate tx() {
		org.springframework.transaction.support.TransactionTemplate t = new org.springframework.transaction.support.TransactionTemplate(
				new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
		t.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		return t;
	}

	private String currentStatus(UUID runId) {
		var rows = jdbc.queryForList("SELECT status FROM rp_runs WHERE id = ?", runId);
		return rows.isEmpty() ? null : String.valueOf(rows.get(0).get("status"));
	}

	private int count(String sql) {
		Integer n = jdbc.queryForObject(sql, Integer.class);
		return n == null ? 0 : n;
	}

	private int countQueued() {
		return count("SELECT count(*) FROM rp_runs WHERE status = 'QUEUED'");
	}

	private void logEvent(UUID runId, RpEventType type, Map<String, Object> detail) {
		if (events != null) {
			events.log(runId, null, null, type, "RpAdmissionService", null, detail);
		}
	}

}
