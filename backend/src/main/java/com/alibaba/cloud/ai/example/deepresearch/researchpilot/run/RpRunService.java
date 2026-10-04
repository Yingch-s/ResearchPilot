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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * V0.4 H1.1：ResearchRun 持久化状态机（决策 1 / 2 / 3）。
 *
 * <p>
 * PostgreSQL 是业务事实唯一来源；Graph State / checkpoint 只是执行段内部缓存。 核心不变量： 1. 状态转移单点 + 乐观并发（UPDATE
 * ... WHERE status=from），非法转移被拒绝； 2. 只有当前有效 Attempt 能提交步骤结果（active_attempt_id 条件更新，旧
 * Attempt 迟到写回行数为 0）； 3. 步骤认领/提交/游标推进全部条件更新 + 行数校验； 4. 终态 Run 拒绝一切写回。
 */
@Service
public class RpRunService {

	private static final Logger logger = LoggerFactory.getLogger(RpRunService.class);

	private final JdbcTemplate jdbc;

	private final RpEventLogger events;

	public RpRunService(JdbcTemplate jdbc, RpEventLogger events) {
		this.jdbc = jdbc;
		this.events = events;
	}

	// ---------- Run 创建与查询 ----------

	@Transactional
	public UUID addRequirement(UUID runId, String requirement) {
		var runs = jdbc.queryForList("SELECT status, budget_reason FROM rp_runs WHERE id = ? FOR UPDATE", runId);
		if (runs.isEmpty()) {
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.NOT_FOUND, "研究不存在");
		}
		if (!"RESEARCHING".equals(runs.get(0).get("status"))
				|| "stop_requested".equals(runs.get(0).get("budget_reason"))) {
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.CONFLICT, "只有运行中或已暂停的研究可以补充要求");
		}
		UUID id = UUID.randomUUID();
		jdbc.update("INSERT INTO rp_run_requirements (id, run_id, requirement) VALUES (?, ?, ?)", id, runId,
				requirement.trim());
		return id;
	}

	/**
	 * Claim and capture requirements under the same Run lock; replay applied requirements
	 * after recovery.
	 */
	@Transactional
	public Optional<String> claimStepWithRequirements(UUID runId, int stepIndex, UUID attemptId, String leaseOwner,
			String originalQuery) {
		if (!lockResearchRun(runId, leaseOwner)) {
			return Optional.empty();
		}
		var run = loadRun(runId);
		if (Boolean.TRUE.equals(run.get("pause_requested")) || "stop_requested".equals(run.get("budget_reason"))
				|| !claimStep(runId, stepIndex, attemptId, leaseOwner)) {
			return Optional.empty();
		}
		var requirements = jdbc.queryForList(
				"SELECT requirement FROM rp_run_requirements WHERE run_id = ? AND status IN ('PENDING', 'APPLIED') ORDER BY created_at, id",
				runId);
		if (requirements.isEmpty()) {
			return Optional.of(originalQuery);
		}
		StringBuilder context = new StringBuilder(originalQuery).append("\n\n运行中补充要求（仅作用于尚未执行的步骤）：");
		for (var row : requirements) {
			context.append("\n- ").append(row.get("requirement"));
		}
		jdbc.update("UPDATE rp_run_requirements SET status = 'APPLIED' WHERE run_id = ? AND status = 'PENDING'", runId);
		return Optional.of(context.toString());
	}

	/**
	 * 创建 Run（幂等）：同 thread_id 唯一，重复创建返回既有 Run —— 前端重复点击"开始研究"不产生第二个执行。
	 */
	public UUID createRun(String threadId, String sessionId, UUID topicId, String query) {
		return createRun(threadId, sessionId, topicId, query, null);
	}

	public UUID createRun(String threadId, String sessionId, UUID topicId, String query, List<String> paperScope) {
		return createRun(threadId, sessionId, topicId, query, paperScope, null);
	}

	public UUID createRun(String threadId, String sessionId, UUID topicId, String query, List<String> paperScope,
			String clientRequestId) {
		return createRun(threadId, sessionId, topicId, query, paperScope, clientRequestId, false);
	}

	@Transactional
	public UUID createRun(String threadId, String sessionId, UUID topicId, String query, List<String> paperScope,
			String clientRequestId, boolean allowWeb) {
		if (topicId != null
				&& jdbc.queryForList("SELECT id FROM rp_topics WHERE id = ? FOR UPDATE", topicId).isEmpty()) {
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.NOT_FOUND, "专题不存在");
		}
		String scopeJson = toScopeJson(paperScope);
		int inserted = jdbc.update("""
				INSERT INTO rp_runs (thread_id, session_id, topic_id, query, paper_scope, client_request_id, allow_web)
				SELECT ?, ?, ?::uuid, ?, ?::jsonb, ?, ?
				WHERE NOT EXISTS (SELECT 1 FROM rp_runs WHERE thread_id = ?)
				  AND (?::text IS NULL OR NOT EXISTS (SELECT 1 FROM rp_runs WHERE client_request_id = ?::text))
				ON CONFLICT DO NOTHING
				""", threadId, sessionId, topicId == null ? null : topicId.toString(), query, scopeJson,
				clientRequestId, allowWeb, threadId, clientRequestId, clientRequestId);
		List<UUID> threadIds = jdbc.queryForList("SELECT id FROM rp_runs WHERE thread_id = ?", UUID.class, threadId);
		UUID runId = threadIds.stream().findFirst().orElse(null);
		if (runId == null && clientRequestId != null) {
			runId = jdbc.queryForObject("SELECT id FROM rp_runs WHERE client_request_id = ?", UUID.class,
					clientRequestId);
		}
		if (runId == null) {
			throw new IllegalStateException("创建研究任务失败：无法找到幂等任务");
		}
		if (inserted > 0) {
			events.log(runId, null, null, RpEventType.RUN_STARTED, "RpRunService", null, Map.of("thread_id", threadId));
		}
		return runId;
	}

	public Optional<UUID> findRunByThread(String threadId) {
		List<UUID> ids = jdbc.queryForList("SELECT id FROM rp_runs WHERE thread_id = ?", UUID.class, threadId);
		return ids.stream().findFirst();
	}

	public Optional<UUID> findRunByClientRequest(String clientRequestId) {
		if (clientRequestId == null || clientRequestId.isBlank()) {
			return Optional.empty();
		}
		return jdbc
			.query("SELECT id FROM rp_runs WHERE client_request_id = ?", (rs, rowNum) -> rs.getObject(1, UUID.class),
					clientRequestId)
			.stream()
			.findFirst();
	}

	/** 跨进程 Run 租约：只有租约不存在或已过期的执行器可以获得本次执行权。 */
	public boolean acquireLease(UUID runId, String workerId, Duration lease) {
		return jdbc.update("""
				UPDATE rp_runs SET worker_id = ?, lease_until = now() + (? * interval '1 second'),
					heartbeat_at = now(), updated_at = now()
				WHERE id = ? AND status = 'RESEARCHING' AND NOT pause_requested
				  AND (lease_until IS NULL OR lease_until < now())
				""", workerId, lease.toSeconds(), runId) == 1;
	}

	public boolean heartbeat(UUID runId, String workerId, Duration lease) {
		return jdbc.update(
				"""
								UPDATE rp_runs SET lease_until = now() + (? * interval '1 second'), heartbeat_at = now(), updated_at = now()
						WHERE id = ? AND status = 'RESEARCHING' AND worker_id = ? AND lease_until > now()
								""",
				lease.toSeconds(), runId, workerId) == 1;
	}

	public boolean ownsLease(UUID runId, String workerId) {
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM rp_runs WHERE id = ? AND worker_id = ? AND lease_until > now())",
				Boolean.class, runId, workerId));
	}

	public void releaseLease(UUID runId, String workerId) {
		jdbc.update(
				"UPDATE rp_runs SET lease_until = NULL, worker_id = NULL, heartbeat_at = NULL, updated_at = now() WHERE id = ? AND worker_id = ?",
				runId, workerId);
	}

	public boolean requestPause(UUID runId) {
		return jdbc.update("UPDATE rp_runs SET pause_requested = TRUE, updated_at = now() "
				+ "WHERE id = ? AND status = 'RESEARCHING'", runId) > 0;
	}

	public boolean clearPause(UUID runId) {
		return jdbc.update("UPDATE rp_runs SET pause_requested = FALSE, updated_at = now() WHERE id = ?", runId) > 0;
	}

	public RunStatus currentStatus(UUID runId) {
		return RunStatus.valueOf(jdbc.queryForObject("SELECT status FROM rp_runs WHERE id = ?", String.class, runId));
	}

	public Map<String, Object> loadRun(UUID runId) {
		return jdbc.queryForMap("SELECT * FROM rp_runs WHERE id = ?", runId);
	}

	// ---------- 状态机（单点转移） ----------

	/**
	 * 状态转移：非法转移或并发竞争（状态已非 from）返回 false，调用方据此实现幂等（重复审批第二次被拒）。
	 */
	public boolean transition(UUID runId, RunStatus from, RunStatus to, String reason) {
		return transition(runId, from, to, reason, null);
	}

	public boolean transition(UUID runId, RunStatus from, RunStatus to, String reason, String leaseOwner) {
		if (!from.canTransitionTo(to)) {
			logger.warn("非法状态转移拒绝: run={} {} -> {}", runId, from, to);
			return false;
		}
		int rows = jdbc.update("""
				UPDATE rp_runs SET status = ?, budget_reason = ?, updated_at = now()
				WHERE id = ? AND status = ?
				  AND (?::text IS NULL OR (worker_id = ? AND lease_until > now()))
				""", to.name(), reason, runId, from.name(), leaseOwner, leaseOwner);
		if (rows == 0) {
			logger.warn("状态转移竞争失败（状态已变更）: run={} {} -> {}", runId, from, to);
			return false;
		}
		events.log(runId, null, null, toStatusEvent(to), "RpRunService", null,
				reason == null ? Map.of() : Map.of("reason", reason));
		return true;
	}

	private RpEventType toStatusEvent(RunStatus status) {
		return switch (status) {
			case COMPLETED -> RpEventType.RUN_COMPLETED;
			case PARTIAL_COMPLETED -> RpEventType.RUN_PARTIAL_COMPLETED;
			case FAILED -> RpEventType.RUN_FAILED;
			case CANCELLED -> RpEventType.RUN_CANCELLED;
			case RESEARCHING -> RpEventType.RUN_RESUMED;
			case FINALIZING -> RpEventType.RUN_FINALIZING;
			default -> RpEventType.RUN_STARTED;
		};
	}

	// ---------- 计划与步骤 ----------

	/**
	 * 挂接计划并初始化步骤表（PENDING）。重复挂接（计划修订）覆盖步骤表。
	 */
	public void initSteps(UUID runId, List<String> stepIds) {
		jdbc.update("DELETE FROM rp_run_steps WHERE run_id = ?", runId);
		for (int i = 0; i < stepIds.size(); i++) {
			jdbc.update("INSERT INTO rp_run_steps (run_id, step_index, step_id) VALUES (?, ?, ?)", runId, i,
					stepIds.get(i));
		}
		jdbc.update("UPDATE rp_runs SET execution_cursor = 0, updated_at = now() WHERE id = ?", runId);
		events.log(runId, null, null, RpEventType.PLAN_CREATED, "RpRunService", null, Map.of("steps", stepIds.size()));
	}

	public void savePlanSnapshot(UUID runId, Map<String, Object> snapshot) {
		try {
			String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(snapshot);
			Integer version = jdbc.queryForObject(
					"SELECT COALESCE(MAX(version), 0) + 1 FROM rp_run_plan_versions WHERE run_id = ?", Integer.class,
					runId);
			jdbc.update("INSERT INTO rp_run_plan_versions (run_id, version, snapshot) VALUES (?, ?, ?::jsonb)", runId,
					version, json);
			jdbc.update("UPDATE rp_runs SET plan_snapshot = ?::jsonb, updated_at = now() WHERE id = ?", json, runId);
		}
		catch (Exception e) {
			throw new IllegalStateException("研究计划保存失败", e);
		}
	}

	/**
	 * Load explicitly selected user notes into the step prompt, scoped to this run's
	 * topic.
	 */
	public String selectedNoteContext(UUID runId) {
		List<Map<String, Object>> rows = jdbc.queryForList("""
				SELECT n.title, n.body, n.source_quote
				FROM rp_notes n JOIN rp_runs r ON r.topic_id = n.topic_id
				WHERE r.id = ? AND n.id::text IN
				  (SELECT jsonb_array_elements_text(COALESCE(r.plan_snapshot->'note_ids', '[]'::jsonb)))
				ORDER BY n.updated_at DESC LIMIT 12
				""", runId);
		if (rows.isEmpty()) {
			return "";
		}
		StringBuilder context = new StringBuilder("\n用户明确选用的专题笔记（仅作研究线索，必须用资料原文验证）：\n");
		for (Map<String, Object> row : rows) {
			context.append("- ").append(row.get("title")).append(": ").append(row.get("body"));
			if (row.get("source_quote") != null) {
				context.append("\n  原文摘录：").append(row.get("source_quote"));
			}
			context.append('\n');
		}
		return context.toString();
	}

	public List<Map<String, Object>> planVersions(UUID runId) {
		return jdbc.queryForList(
				"SELECT version, snapshot::text AS snapshot, created_at FROM rp_run_plan_versions WHERE run_id = ? ORDER BY version",
				runId);
	}

	/**
	 * Apply a user-approved plan proposal without touching completed or running steps.
	 * Pending rows are reused first and extra rows are appended, so stale workers cannot
	 * submit against a silently replaced step identity.
	 */
	@Transactional
	public Map<String, Object> applyPlanAdjustment(UUID runId, List<String> proposedSteps) {
		if (proposedSteps == null || proposedSteps.isEmpty() || proposedSteps.size() > 6) {
			throw new IllegalArgumentException("调整后的计划必须包含 1 到 6 个步骤");
		}
		Map<String, Object> run = jdbc.queryForMap(
				"SELECT status, plan_snapshot::text AS plan_snapshot FROM rp_runs WHERE id = ? FOR UPDATE", runId);
		String status = String.valueOf(run.get("status"));
		if (!List.of("RESEARCHING", "WAITING_APPROVAL").contains(status)) {
			throw new IllegalStateException("当前状态不允许调整计划：" + status);
		}
		List<Map<String, Object>> rows = jdbc.queryForList(
				"SELECT step_index, step_id, status FROM rp_run_steps WHERE run_id = ? ORDER BY step_index FOR UPDATE",
				runId);
		Set<String> immutable = rows.stream()
			.filter(row -> !"PENDING".equals(String.valueOf(row.get("status"))))
			.map(row -> String.valueOf(row.get("step_id")))
			.collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
		if (!proposedSteps.containsAll(immutable)) {
			throw new IllegalArgumentException("已执行或正在执行的步骤不能从调整计划中删除");
		}
		List<String> pendingProposal = proposedSteps.stream().filter(step -> !immutable.contains(step)).toList();
		List<Map<String, Object>> pending = rows.stream()
			.filter(row -> "PENDING".equals(String.valueOf(row.get("status"))))
			.toList();
		int reused = Math.min(pending.size(), pendingProposal.size());
		for (int i = 0; i < reused; i++) {
			jdbc.update(
					"UPDATE rp_run_steps SET step_id = ?, result = NULL, updated_at = now() WHERE run_id = ? AND step_index = ? AND status = 'PENDING'",
					pendingProposal.get(i), runId, pending.get(i).get("step_index"));
		}
		for (int i = reused; i < pending.size(); i++) {
			jdbc.update(
					"UPDATE rp_run_steps SET status = 'SKIPPED', result = '用户确认的计划移除了该步骤', updated_at = now() WHERE run_id = ? AND step_index = ? AND status = 'PENDING'",
					runId, pending.get(i).get("step_index"));
		}
		int nextIndex = rows.stream().mapToInt(row -> ((Number) row.get("step_index")).intValue()).max().orElse(-1) + 1;
		for (int i = reused; i < pendingProposal.size(); i++) {
			jdbc.update("INSERT INTO rp_run_steps (run_id, step_index, step_id) VALUES (?, ?, ?)", runId, nextIndex++,
					pendingProposal.get(i));
		}
		List<String> finalSteps = jdbc.queryForList(
				"SELECT step_id FROM rp_run_steps WHERE run_id = ? AND status <> 'SKIPPED' ORDER BY step_index",
				String.class, runId);
		try {
			Map<String, Object> snapshot = run.get("plan_snapshot") == null ? new java.util.LinkedHashMap<>()
					: new com.fasterxml.jackson.databind.ObjectMapper()
						.readValue(String.valueOf(run.get("plan_snapshot")), Map.class);
			snapshot.put("steps", finalSteps);
			snapshot.put("adjusted_by_user", true);
			snapshot.put("adjusted_at", java.time.OffsetDateTime.now().toString());
			savePlanSnapshot(runId, snapshot);
		}
		catch (Exception e) {
			throw new IllegalStateException("计划调整版本保存失败", e);
		}
		events.log(runId, null, null, RpEventType.PLAN_CREATED, "RpRunService", null,
				Map.of("adjusted", true, "steps", finalSteps.size()));
		return Map.of("run_id", runId.toString(), "status", status, "steps", finalSteps);
	}

	/**
	 * 进入研究段（决策 3：三段 Deadline 在此处确定）： attempt +1 并生成新 Attempt 身份。
	 *
	 * <p>
	 * 决策 2 核心语义：新执行段开始即**失效上一段的孤儿 RUNNING 步骤**（标 FAILED，可被新段重新认领）—— 旧 Attempt 即使迟到返回，也因
	 * status != RUNNING 而无法写回（集成测试 staleAttemptWriteBack 场景）。
	 * @return 本次执行段的 attemptId
	 */
	@Transactional
	public UUID startResearchSegment(UUID runId, Duration globalBudget, Duration finalizationReserve) {
		return startResearchSegment(runId, globalBudget, finalizationReserve, null);
	}

	@Transactional
	public UUID startResearchSegment(UUID runId, Duration globalBudget, Duration finalizationReserve,
			String leaseOwner) {
		if (!lockResearchRun(runId, leaseOwner)) {
			throw new IllegalStateException("研究执行权已失效");
		}
		UUID attemptId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		jdbc.update("""
				UPDATE rp_run_steps SET status = 'FAILED', result = '执行段中断（新 Attempt 启动）', updated_at = now()
				WHERE run_id = ? AND status = 'RUNNING'
				""", runId);
		jdbc.update(
				"""
						UPDATE rp_runs SET attempt = attempt + 1, global_deadline = COALESCE(global_deadline, ?),
							research_deadline = COALESCE(research_deadline, ?),
							finalization_deadline = COALESCE(finalization_deadline, ?), started_at = COALESCE(started_at, now()), updated_at = now()
						WHERE id = ?
						""",
				now.plus(globalBudget), now.plus(globalBudget).minus(finalizationReserve), now.plus(globalBudget),
				runId);
		events.log(runId, null, attemptId, RpEventType.RUN_STARTED, "RpRunService", null,
				Map.of("attempt", attemptId.toString(), "global_budget_s", globalBudget.toSeconds(), "reserve_s",
						finalizationReserve.toSeconds()));
		return attemptId;
	}

	/**
	 * 步骤认领：仅 PENDING / FAILED 步骤可被当前 Attempt 认领（RUNNING = 他人/旧段持有）。
	 */
	@Transactional
	public boolean claimStep(UUID runId, int stepIndex, UUID attemptId) {
		return claimStep(runId, stepIndex, attemptId, null);
	}

	@Transactional
	public boolean claimStep(UUID runId, int stepIndex, UUID attemptId, String leaseOwner) {
		if (!lockResearchRun(runId, leaseOwner)) {
			return false;
		}
		int rows = jdbc.update("""
				UPDATE rp_run_steps SET status = 'RUNNING', active_attempt_id = ?, attempt_count = attempt_count + 1,
					updated_at = now()
				WHERE run_id = ? AND step_index = ? AND status IN ('PENDING', 'FAILED')
				""", attemptId, runId, stepIndex);
		if (rows == 0) {
			return false;
		}
		events.log(runId, stepOf(runId, stepIndex), attemptId, RpEventType.STEP_SCHEDULED, "RpRunService", null,
				Map.of());
		return true;
	}

	/**
	 * 步骤提交（决策 2 核心）：只有当前有效 Attempt 在 RUNNING 状态下可提交；同一调用方事务内完成。
	 * @return false = 过期 Attempt 迟到写回（保留其 Trace，不接纳结果）
	 */
	@Transactional
	public boolean commitStep(UUID runId, int stepIndex, UUID attemptId, StepStatus finalStatus, String result) {
		return commitStep(runId, stepIndex, attemptId, finalStatus, result, null);
	}

	@Transactional
	public boolean commitStep(UUID runId, int stepIndex, UUID attemptId, StepStatus finalStatus, String result,
			String leaseOwner) {
		if (finalStatus != StepStatus.COMPLETED && finalStatus != StepStatus.FAILED
				&& finalStatus != StepStatus.SKIPPED) {
			throw new IllegalArgumentException("步骤只能提交为终态: " + finalStatus);
		}
		if (!lockResearchRun(runId, leaseOwner)) {
			return false;
		}
		int rows = jdbc.update("""
				UPDATE rp_run_steps SET status = ?, result = ?, updated_at = now()
				WHERE run_id = ? AND step_index = ? AND active_attempt_id = ? AND status = 'RUNNING'
				  AND EXISTS (SELECT 1 FROM rp_runs r WHERE r.id = ? AND r.status = 'RESEARCHING'
				    AND ((?::text IS NULL AND r.worker_id IS NULL)
				      OR (r.worker_id = ? AND r.lease_until > now())))
				""", finalStatus.name(), result, runId, stepIndex, attemptId, runId, leaseOwner, leaseOwner);
		if (rows == 0) {
			logger.warn("过期 Attempt 写回被拒绝: run={} step={} attempt={}", runId, stepIndex, attemptId);
			return false;
		}
		if (finalStatus == StepStatus.COMPLETED) {
			jdbc.update("""
					UPDATE rp_evidence SET evidence_status = 'ACCEPTED'
					WHERE run_id = ? AND attempt_id = ? AND evidence_status = 'CANDIDATE'
					  AND step_id = (SELECT step_id FROM rp_run_steps WHERE run_id = ? AND step_index = ?)
					""", runId, attemptId, runId, stepIndex);
			jdbc.update("""
					UPDATE rp_runs SET execution_cursor = GREATEST(execution_cursor, ?), updated_at = now()
					WHERE id = ?
					""", stepIndex + 1, runId);
		}
		events.log(runId, stepOf(runId, stepIndex), attemptId,
				finalStatus == StepStatus.COMPLETED ? RpEventType.STEP_COMPLETED : RpEventType.STEP_FAILED,
				"RpRunService", null, Map.of("final_status", finalStatus.name()));
		return true;
	}

	public Optional<Map<String, Object>> nextPendingStep(UUID runId) {
		List<Map<String, Object>> rows = jdbc.queryForList("""
				SELECT step_index, step_id, status, attempt_count FROM rp_run_steps
				WHERE run_id = ? AND status IN ('PENDING', 'FAILED') ORDER BY step_index LIMIT 1
				""", runId);
		return rows.stream().findFirst();
	}

	public List<Map<String, Object>> loadSteps(UUID runId) {
		return jdbc.queryForList("SELECT * FROM rp_run_steps WHERE run_id = ? ORDER BY step_index", runId);
	}

	/**
	 * Reopen one failed step while retaining the run deadline and all other step results.
	 */
	@Transactional
	public boolean retryFailedStep(UUID runId, int stepIndex) {
		List<Map<String, Object>> runs = jdbc.queryForList("SELECT status FROM rp_runs WHERE id = ? FOR UPDATE", runId);
		if (runs.isEmpty() || !RunStatus.valueOf(String.valueOf(runs.get(0).get("status"))).isTerminal()) {
			return false;
		}
		int changed = jdbc.update("""
				UPDATE rp_run_steps SET status = 'PENDING', active_attempt_id = NULL,
					result = '用户请求重试此步骤', updated_at = now()
				WHERE run_id = ? AND step_index = ? AND status = 'FAILED'
				""", runId, stepIndex);
		if (changed != 1) {
			return false;
		}
		jdbc.update("DELETE FROM rp_reports WHERE run_id = ?", runId);
		jdbc.update(
				"UPDATE rp_runs SET status = 'RESEARCHING', budget_reason = NULL, pause_requested = FALSE, updated_at = now() WHERE id = ?",
				runId);
		events.log(runId, stepOf(runId, stepIndex), null, RpEventType.RUN_RESUMED, "RpRunService", null,
				Map.of("manual_retry", true, "step_index", stepIndex));
		return true;
	}

	@Transactional
	public boolean skipUnstartedStep(UUID runId, int stepIndex, String reason) {
		return skipUnstartedStep(runId, stepIndex, reason, null);
	}

	@Transactional
	public boolean skipUnstartedStep(UUID runId, int stepIndex, String reason, String leaseOwner) {
		if (!lockResearchRun(runId, leaseOwner)) {
			return false;
		}
		return jdbc.update("""
				UPDATE rp_run_steps SET status = 'SKIPPED', result = ?, updated_at = now()
				WHERE run_id = ? AND step_index = ? AND status IN ('PENDING', 'FAILED')
				  AND EXISTS (SELECT 1 FROM rp_runs WHERE id = ? AND status = 'RESEARCHING')
				""", reason, runId, stepIndex, runId) > 0;
	}

	// Lock the Run before its steps so takeover cannot interleave with a committed
	// mutation.
	private boolean lockResearchRun(UUID runId, String leaseOwner) {
		return !jdbc.queryForList("""
				SELECT id FROM rp_runs WHERE id = ? AND status = 'RESEARCHING'
				  AND ((?::text IS NULL AND worker_id IS NULL)
				    OR (worker_id = ? AND lease_until > now())) FOR UPDATE
				""", runId, leaseOwner, leaseOwner).isEmpty();
	}

	/**
	 * Keep deterministic finalization and its report writes in one fenced transaction.
	 */
	@Transactional
	public boolean finalizeWithLease(UUID runId, String leaseOwner, Runnable finalization) {
		if (!lockResearchRun(runId, leaseOwner)) {
			return false;
		}
		finalization.run();
		return true;
	}

	@Transactional
	public boolean skipRemainingSteps(UUID runId, String leaseOwner) {
		if (!lockResearchRun(runId, leaseOwner)) {
			return false;
		}
		jdbc.update("""
				UPDATE rp_run_steps SET status = 'SKIPPED', result = '用户停止，未执行', updated_at = now()
				WHERE run_id = ? AND status IN ('RUNNING', 'PENDING', 'FAILED')
				""", runId);
		return true;
	}

	// ---------- 恢复 ----------

	/**
	 * 重启恢复扫描：RESEARCHING 状态的 Run 可凭 execution_cursor 续跑（attempt+1）。
	 */
	public List<Map<String, Object>> recoveryScan() {
		return jdbc.queryForList(
				"SELECT id, thread_id, execution_cursor, attempt FROM rp_runs WHERE status = ? AND NOT pause_requested AND (lease_until IS NULL OR lease_until < now())",
				RunStatus.RESEARCHING.name());
	}

	private String toScopeJson(List<String> paperIds) {
		if (paperIds == null || paperIds.isEmpty()) {
			return null;
		}
		try {
			return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(paperIds);
		}
		catch (Exception e) {
			return null;
		}
	}

	/** 读取范围快照（决策：勾选仅影响下一次请求，Run 开始后冻结）。 */
	@SuppressWarnings("unchecked")
	public List<String> paperScope(UUID runId) {
		Object v = jdbc.queryForObject("SELECT paper_scope::text FROM rp_runs WHERE id = ?", String.class, runId);
		if (v == null) {
			return List.of();
		}
		try {
			return new com.fasterxml.jackson.databind.ObjectMapper().readValue((String) v, List.class);
		}
		catch (Exception e) {
			return List.of();
		}
	}

	private String stepOf(UUID runId, int stepIndex) {
		return jdbc.queryForObject("SELECT step_id FROM rp_run_steps WHERE run_id = ? AND step_index = ?", String.class,
				runId, stepIndex);
	}

}
