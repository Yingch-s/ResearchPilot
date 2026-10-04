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

import com.alibaba.cloud.ai.example.deepresearch.researchpilot.evidence.RpEvidenceService;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.agent.BoundedWorkerContract;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.agent.BoundedWorkerOrchestrator;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.agent.EvidencePacketMerger;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.ExecutionContext;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpEventLogger;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpEventType;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpRunService;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RunStatus;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.StepStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * V0.4 H1.3：跨步骤业务调度器（决策 1）。
 *
 * <p>
 * 职责：选择下一个待执行步骤、判断研究是否结束、预算（三段 Deadline）强制、 步骤认领与持久化提交、暂停/恢复。跨步骤推进只认 rp_runs /
 * rp_run_steps 的事务提交状态， Graph 临时状态丢失不影响已持久化成果。
 *
 * <p>
 * 同一 Run 同时只允许一个调度循环（inProcess 注册表 + DB 状态机双保险）。
 */
@Service
public class ResearchScheduler {

	private static final Logger logger = LoggerFactory.getLogger(ResearchScheduler.class);

	/** 同一步骤最多尝试次数（超过转 SKIPPED，诚实计入未完成项）。 */
	static final int MAX_STEP_ATTEMPTS = 2;

	private final RpRunService runService;

	private final RpEvidenceService evidenceService;

	private final RpEventLogger events;

	private final StepExecutor stepExecutor;

	private final org.springframework.jdbc.core.JdbcTemplate jdbc;

	private final Duration globalBudget;

	private final Duration finalizationReserve;

	private final boolean parallelEnabled;

	private final int parallelism;

	/**
	 * Bounded worker boundary used by the parallel path; workers cannot delegate
	 * recursively.
	 */
	private final int workerCallBudget;

	// 同一 Run 的执行互斥（进程内；跨进程互斥由 DB 状态机承担）
	private final ConcurrentHashMap<UUID, Boolean> inProcess = new ConcurrentHashMap<>();

	private final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(1, r -> {
		Thread t = new Thread(r, "rp-run-heartbeat");
		t.setDaemon(true);
		return t;
	});

	private final String workerId = "research-" + UUID.randomUUID();

	private final Duration leaseDuration = Duration.ofSeconds(30);

	@org.springframework.beans.factory.annotation.Autowired
	public ResearchScheduler(RpRunService runService, RpEvidenceService evidenceService, RpEventLogger events,
			StepExecutor stepExecutor, org.springframework.jdbc.core.JdbcTemplate jdbc,
			@Value("${researchpilot.research-budget.global-timeout-seconds:360}") long globalBudgetSeconds,
			@Value("${researchpilot.research-budget.finalization-reserve-seconds:60}") long reserveSeconds,
			@Value("${researchpilot.research-budget.parallel-enabled:true}") boolean parallelEnabled,
			@Value("${researchpilot.research-budget.parallelism:2}") int parallelism) {
		this.runService = runService;
		this.evidenceService = evidenceService;
		this.events = events;
		this.stepExecutor = stepExecutor;
		this.jdbc = jdbc;
		this.globalBudget = Duration.ofSeconds(globalBudgetSeconds);
		this.finalizationReserve = Duration.ofSeconds(reserveSeconds);
		this.parallelEnabled = parallelEnabled;
		this.parallelism = Math.max(1, Math.min(4, parallelism));
		this.workerCallBudget = Math.max(1, this.parallelism * 2);
	}

	/** Compatibility constructor for deterministic unit/integration tests. */
	public ResearchScheduler(RpRunService runService, RpEvidenceService evidenceService, RpEventLogger events,
			StepExecutor stepExecutor, org.springframework.jdbc.core.JdbcTemplate jdbc, long globalBudgetSeconds,
			long reserveSeconds) {
		this(runService, evidenceService, events, stepExecutor, jdbc, globalBudgetSeconds, reserveSeconds, false, 1);
	}

	/**
	 * 启动或恢复一个 RESEARCHING 状态的 Run（幂等：同 Run 并发调用只执行一次；非 RESEARCHING 状态被拒）。
	 * @return 是否实际启动了执行段（false = 已在执行或状态不允许）
	 */
	public boolean startOrResumeRun(UUID runId) {
		if (inProcess.putIfAbsent(runId, Boolean.TRUE) != null) {
			logger.warn("Run 已在执行中，拒绝重复启动: {}", runId);
			return false;
		}
		String leaseOwner = workerId + ":" + UUID.randomUUID();
		if (!runService.acquireLease(runId, leaseOwner, leaseDuration)) {
			inProcess.remove(runId);
			logger.info("Run 租约已被其他 Worker 持有，跳过执行: {}", runId);
			return false;
		}
		var heartbeat = heartbeatExecutor.scheduleAtFixedRate(
				() -> runService.heartbeat(runId, leaseOwner, leaseDuration), 10, 10, TimeUnit.SECONDS);
		try {
			UUID attemptId = runService.startResearchSegment(runId, globalBudget, finalizationReserve, leaseOwner);
			Map<String, Object> run = runService.loadRun(runId);
			Instant globalDeadline = toInstant(run.get("global_deadline"));
			UUID topicId = run.get("topic_id") == null ? null : UUID.fromString(run.get("topic_id").toString());
			String query = String.valueOf(run.get("query"));
			boolean allowWeb = Boolean.TRUE.equals(run.get("allow_web"));
			executeSteps(runId, topicId, query, attemptId, globalDeadline, allowWeb, leaseOwner);
			return true;
		}
		finally {
			heartbeat.cancel(false);
			runService.releaseLease(runId, leaseOwner);
			inProcess.remove(runId);
		}
	}

	private void executeSteps(UUID runId, UUID topicId, String query, UUID attemptId, Instant globalDeadline,
			boolean allowWeb, String leaseOwner) {
		if (parallelEnabled) {
			executeParallelSteps(runId, topicId, query, attemptId, globalDeadline, allowWeb, leaseOwner);
			return;
		}
		// V1 UX-2：范围快照冻结 —— Run 创建时的 paper_scope 决定本次检索范围，
		// 之后用户勾选变化不影响进行中的研究
		List<String> paperScope = runService.paperScope(runId);
		while (true) {
			if (!runService.ownsLease(runId, leaseOwner)) {
				logger.warn("Run 租约已失效，停止旧执行段: {}", runId);
				return;
			}
			if (isPauseRequested(runId)) {
				logger.info("用户请求暂停，保留当前成果: run={}", runId);
				events.log(runId, null, attemptId, RpEventType.RUN_PAUSED, "ResearchScheduler", null,
						Map.of("reason", "pause_requested"));
				return;
			}
			// 优雅停止：用户请求结束后不再认领新步骤（在跑步骤完成后收敛）
			if (isStopRequested(runId)) {
				logger.info("用户请求停止，收敛研究: run={}", runId);
				if (runService.skipRemainingSteps(runId, leaseOwner)) {
					finalize(runId, attemptId, globalDeadline, leaseOwner);
				}
				return;
			}
			var next = runService.nextPendingStep(runId);
			if (next.isEmpty()) {
				finalize(runId, attemptId, globalDeadline, leaseOwner);
				return;
			}
			int stepIndex = ((Number) next.get().get("step_index")).intValue();
			String stepId = String.valueOf(next.get().get("step_id"));
			int attemptCount = ((Number) next.get().getOrDefault("attempt_count", 0)).intValue();
			if (attemptCount >= MAX_STEP_ATTEMPTS) {
				// 诚实停止：连续失败转 SKIPPED，计入未完成项
				runService.skipUnstartedStep(runId, stepIndex, "尝试次数耗尽（" + MAX_STEP_ATTEMPTS + "），计入未完成项", leaseOwner);
				continue;
			}
			// 研究窗口 Deadline：不足则跳过剩余步骤（Finalization Reserve 保护收尾）
			Map<String, Object> run = runService.loadRun(runId);
			Instant researchDeadline = toInstant(run.get("research_deadline"));
			if (researchDeadline != null && Instant.now().isAfter(researchDeadline)) {
				logger.warn("研究窗口截止，跳过剩余步骤: run={} step={}", runId, stepIndex);
				runService.skipUnstartedStep(runId, stepIndex, "研究窗口截止，未执行", leaseOwner);
				continue;
			}

			var stepQuery = runService.claimStepWithRequirements(runId, stepIndex, attemptId, leaseOwner, query);
			if (stepQuery.isEmpty()) {
				logger.warn("步骤认领失败（并发或状态不允许）: run={} step={}", runId, stepIndex);
				return;
			}
			events.log(runId, stepId, attemptId, RpEventType.STEP_STARTED, "ResearchScheduler", null, Map.of());

			Duration stepBudget = researchDeadline == null ? globalBudget
					: Duration.between(Instant.now(), researchDeadline);
			ExecutionContext ctx = new ExecutionContext(runId, topicId, stepId, attemptId, globalDeadline,
					Instant.now().plus(stepBudget), paperScope, allowWeb);
			StepResult result;
			try {
				String selectedNotes = runService.selectedNoteContext(runId);
				result = stepExecutor.execute(ctx, stepIndex, stepQuery.get() + selectedNotes, stepId, stepId);
			}
			catch (StepExecutor.StepAbortedException e) {
				// 执行段中止（暂停/恢复请求）：保留现场，不计步骤失败
				logger.info("执行段中止（保留现场待恢复）: run={}: {}", runId, e.getMessage());
				events.log(runId, stepId, attemptId, RpEventType.RUN_PAUSED, "ResearchScheduler", null,
						Map.of("reason", String.valueOf(e.getMessage())));
				return;
			}

			boolean accepted = runService.commitStep(runId, stepIndex, attemptId, result.status(), result.summary(),
					leaseOwner);
			if (accepted && result.status() == StepStatus.COMPLETED) {
				events.log(runId, stepId, attemptId, RpEventType.EVIDENCE_ACCEPTED, "ResearchScheduler", null,
						Map.of("evidence", result.evidenceCount()));
			}
			else if (!accepted) {
				logger.warn("步骤提交被拒（过期 Attempt）: run={} step={}", runId, stepIndex);
			}
		}
	}

	/**
	 * Execute a bounded batch of currently independent steps. Each step is still claimed
	 * and committed through the normal lease/attempt protocol; parallelism only changes
	 * when the expensive worker call happens.
	 */
	private void executeParallelSteps(UUID runId, UUID topicId, String query, UUID attemptId, Instant globalDeadline,
			boolean allowWeb, String leaseOwner) {
		List<String> paperScope = runService.paperScope(runId);
		while (true) {
			if (!runService.ownsLease(runId, leaseOwner) || isPauseRequested(runId))
				return;
			if (isStopRequested(runId)) {
				if (runService.skipRemainingSteps(runId, leaseOwner))
					finalize(runId, attemptId, globalDeadline, leaseOwner);
				return;
			}
			List<ClaimedStep> batch = new java.util.ArrayList<>();
			for (int i = 0; i < parallelism; i++) {
				var next = runService.nextPendingStep(runId);
				if (next.isEmpty())
					break;
				int stepIndex = ((Number) next.get().get("step_index")).intValue();
				String stepId = String.valueOf(next.get().get("step_id"));
				int attempts = ((Number) next.get().getOrDefault("attempt_count", 0)).intValue();
				if (attempts >= MAX_STEP_ATTEMPTS) {
					runService.skipUnstartedStep(runId, stepIndex, "尝试次数耗尽（" + MAX_STEP_ATTEMPTS + "），计入未完成项",
							leaseOwner);
					continue;
				}
				Map<String, Object> run = runService.loadRun(runId);
				Instant deadline = toInstant(run.get("research_deadline"));
				if (deadline != null && Instant.now().isAfter(deadline)) {
					runService.skipUnstartedStep(runId, stepIndex, "研究窗口截止，未执行", leaseOwner);
					continue;
				}
				var claimed = runService.claimStepWithRequirements(runId, stepIndex, attemptId, leaseOwner, query);
				if (claimed.isEmpty())
					break;
				events.log(runId, stepId, attemptId, RpEventType.STEP_STARTED, "ResearchScheduler", null,
						Map.of("parallel", true));
				Duration budget = deadline == null ? globalBudget : Duration.between(Instant.now(), deadline);
				ExecutionContext ctx = new ExecutionContext(runId, topicId, stepId, attemptId, globalDeadline,
						Instant.now().plus(budget), paperScope, allowWeb);
				batch.add(new ClaimedStep(stepIndex, stepId, claimed.get(), ctx));
			}
			if (batch.isEmpty()) {
				if (runService.nextPendingStep(runId).isEmpty())
					finalize(runId, attemptId, globalDeadline, leaseOwner);
				continue;
			}
			Map<String, StepResult> workerResults = new ConcurrentHashMap<>();
			List<BoundedWorkerContract> contracts = batch.stream().map(step -> {
				Duration remaining = step.context().stepDeadline() == null ? Duration.ofSeconds(120)
						: Duration.between(Instant.now(), step.context().stepDeadline());
				return new BoundedWorkerContract("step:" + step.stepId(), step.query(), paperScope, 1,
						Duration.ofSeconds(Math.max(1, Math.min(600, remaining.toSeconds()))), false);
			}).toList();
			try (var workers = new BoundedWorkerOrchestrator(parallelism, workerCallBudget)) {
				var workerResult = workers.execute(contracts, contract -> {
					ClaimedStep step = batch.stream()
						.filter(item -> ("step:" + item.stepId()).equals(contract.workerId()))
						.findFirst()
						.orElseThrow();
					String notes = runService.selectedNoteContext(runId);
					StepResult result = stepExecutor.execute(step.context(), step.index(), step.query() + notes,
							step.stepId(), step.stepId());
					workerResults.put(contract.workerId(), result);
					if (result.status() != StepStatus.COMPLETED || result.summary() == null
							|| result.summary().isBlank())
						return new EvidencePacketMerger.EvidencePacket(contract.workerId(), List.of(), List.of(), true);
					var claim = new EvidencePacketMerger.Claim("step " + step.stepId(), paperScope, result.summary());
					return new EvidencePacketMerger.EvidencePacket(contract.workerId(), List.of(claim), List.of(),
							false);
				}, () -> isPauseRequested(runId) || isStopRequested(runId)
						|| (globalDeadline != null && Instant.now().isAfter(globalDeadline)));
				for (var flag : workerResult.reviewFlags()) {
					events.log(runId, null, attemptId, RpEventType.NODE_FAILED, "EvidencePacketReviewer", null,
							Map.of("reasons", flag.reasons(), "parallel", true));
				}
				for (int i = 0; i < batch.size(); i++) {
					ClaimedStep step = batch.get(i);
					StepResult result = workerResults.getOrDefault("step:" + step.stepId(),
							StepResult.failed(step.index(), "Worker 未完成或被取消"));
					boolean accepted = runService.commitStep(runId, step.index(), attemptId, result.status(),
							result.summary(), leaseOwner);
					if (accepted && result.status() == StepStatus.COMPLETED)
						events.log(runId, step.stepId(), attemptId, RpEventType.EVIDENCE_ACCEPTED, "ResearchScheduler",
								null,
								Map.of("evidence", result.evidenceCount(), "parallel", true, "worker_boundary", true));
				}
			}
			catch (java.util.concurrent.CancellationException e) {
				if (isStopRequested(runId) && runService.skipRemainingSteps(runId, leaseOwner))
					finalize(runId, attemptId, globalDeadline, leaseOwner);
				return;
			}
			if (isPauseRequested(runId) || isStopRequested(runId)) {
				if (isStopRequested(runId) && runService.skipRemainingSteps(runId, leaseOwner))
					finalize(runId, attemptId, globalDeadline, leaseOwner);
				return;
			}
		}
	}

	private record ClaimedStep(int index, String stepId, String query, ExecutionContext context) {
	}

	/**
	 * 暂停：中止当前执行段（孤儿 RUNNING 步骤由下一段启动时失效），Run 保持 RESEARCHING 待恢复。
	 */
	public boolean pauseRun(UUID runId) {
		boolean requested = runService.requestPause(runId);
		if (!requested) {
			return false;
		}
		events.log(runId, null, null, RpEventType.RUN_PAUSED, "ResearchScheduler", null, Map.of("by", "user"));
		logger.info("Run 暂停请求已登记: {}", runId);
		return true;
	}

	/**
	 * V1 UX-2：优雅停止 —— 结束研究并保留当前成果。在跑步骤完成后收敛为 PARTIAL_COMPLETED （未完成步骤 SKIPPED
	 * 并在报告中披露）。若本进程没有该 Run 的执行段（如重启后），直接收敛。
	 */
	public boolean stopRun(UUID runId) {
		int changed = jdbc
			.update("UPDATE rp_runs SET budget_reason = 'stop_requested', pause_requested = FALSE, updated_at = now() "
					+ "WHERE id = ?::uuid AND status = 'RESEARCHING'", runId.toString());
		if (changed == 0) {
			return false;
		}
		events.log(runId, null, null, RpEventType.RUN_PAUSED, "ResearchScheduler", null, Map.of("stop", true));
		String stopOwner = workerId + ":stop:" + UUID.randomUUID();
		if (!inProcess.containsKey(runId) && runService.acquireLease(runId, stopOwner, leaseDuration)) {
			// 无执行段在跑（重启后恢复前）：直接收敛
			try {
				if (runService.skipRemainingSteps(runId, stopOwner)) {
					Map<String, Object> run = runService.loadRun(runId);
					finalize(runId, null, toInstant(run.get("global_deadline")), stopOwner);
				}
			}
			finally {
				runService.releaseLease(runId, stopOwner);
			}
			return true;
		}
		return true;
	}

	private boolean isStopRequested(UUID runId) {
		String reason = jdbc.queryForObject("SELECT COALESCE(budget_reason,'') FROM rp_runs WHERE id = ?::uuid",
				String.class, runId.toString());
		return "stop_requested".equals(reason);
	}

	private boolean isPauseRequested(UUID runId) {
		Boolean requested = jdbc.queryForObject("SELECT pause_requested FROM rp_runs WHERE id = ?", Boolean.class,
				runId);
		return Boolean.TRUE.equals(requested);
	}

	/**
	 * 终态收敛（决策 3 降级链的确定性兜底层）：RESEARCHING → VERIFYING → FINALIZING → 终态。 报告为确定性结构化输出（证据矩阵 +
	 * 步骤完成度 + 未完成项）——LLM 报告是后续增强，不阻塞终态。
	 */
	private void finalize(UUID runId, UUID attemptId, Instant globalDeadline, String leaseOwner) {
		runService.finalizeWithLease(runId, leaseOwner,
				() -> writeFinalReport(runId, attemptId, globalDeadline, leaseOwner));
	}

	private void writeFinalReport(UUID runId, UUID attemptId, Instant globalDeadline, String leaseOwner) {
		if (!runService.transition(runId, RunStatus.RESEARCHING, RunStatus.VERIFYING, null, leaseOwner)) {
			return;
		}
		events.log(runId, null, attemptId, RpEventType.RUN_FINALIZING, "ResearchScheduler", null, Map.of());
		runService.transition(runId, RunStatus.VERIFYING, RunStatus.FINALIZING, null);

		var steps = runService.loadSteps(runId);
		long completed = steps.stream().filter(s -> "COMPLETED".equals(s.get("status"))).count();
		long skipped = steps.stream().filter(s -> "SKIPPED".equals(s.get("status"))).count();
		long failed = steps.stream().filter(s -> "FAILED".equals(s.get("status"))).count();
		boolean allDone = completed == steps.size();

		// Deterministic fallback: answer first, then findings, evidence disagreement and
		// limits.
		Map<String, Object> run = runService.loadRun(runId);
		List<String> scope = runService.paperScope(runId);
		List<String> scopeTitles = new java.util.ArrayList<>();
		for (String pid : scope) {
			List<String> titles = jdbc.queryForList("SELECT title FROM rp_papers WHERE id = ?::uuid", String.class,
					pid);
			scopeTitles.add(titles.isEmpty() ? pid : titles.get(0));
		}
		List<Map<String, Object>> srcs = jdbc.queryForList(
				"""
						SELECT DISTINCT p.title AS title, e.evidence_status AS status, e.claim_status AS claim_status, COALESCE(e.source_type, 'PAPER') AS kind, e.source_url AS url FROM rp_evidence e
								  JOIN rp_papers p ON p.id = e.paper_id
								  WHERE e.run_id = ?::uuid AND e.paper_id IS NOT NULL AND e.evidence_status = 'ACCEPTED'
								UNION ALL
						SELECT DISTINCT COALESCE(section_path, '网络来源') AS title, e.evidence_status AS status, e.claim_status AS claim_status, 'WEB' AS kind, source_url AS url
						  FROM rp_evidence e WHERE run_id = ?::uuid AND source_type = 'WEB' AND paper_id IS NULL AND evidence_status = 'ACCEPTED'
								""",
				runId.toString(), runId.toString());
		String report = DeterministicReportComposer.compose(String.valueOf(run.get("query")), scopeTitles, steps, srcs,
				run.get("budget_reason"));
		List<String> sourceIds = jdbc.queryForList(
				"SELECT DISTINCT source_chunk_id FROM rp_evidence WHERE run_id = ?::uuid AND evidence_status = 'ACCEPTED' AND source_chunk_id IS NOT NULL ORDER BY source_chunk_id",
				String.class, runId.toString());
		String sourceIdsJson;
		try {
			sourceIdsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(sourceIds);
		}
		catch (Exception ignored) {
			sourceIdsJson = "[]";
		}
		Integer reportVersion = jdbc.queryForObject(
				"SELECT COALESCE(MAX(version), 0) + 1 FROM rp_report_versions WHERE run_id = ?::uuid", Integer.class,
				runId.toString());
		jdbc.update(
				"INSERT INTO rp_report_versions (run_id, version, report, completed_steps, total_steps, source_ids) VALUES (?::uuid, ?, ?, ?, ?, ?::jsonb)",
				runId.toString(), reportVersion, report, (int) completed, steps.size(), sourceIdsJson);
		jdbc.update(
				"""
							INSERT INTO rp_reports (run_id, report, completed_steps, total_steps)
							VALUES (?::uuid, ?, ?, ?)
							ON CONFLICT (run_id) DO UPDATE SET report = EXCLUDED.report,
								completed_steps = EXCLUDED.completed_steps, total_steps = EXCLUDED.total_steps, created_at = now()
						""",
				runId.toString(), report, (int) completed, steps.size());
		// 报告属于发起它的会话：只写入一次，恢复扫描重复收敛时不会制造重复消息。
		Object sessionId = run.get("session_id");
		UUID sessionUuid = null;
		if (sessionId != null && !String.valueOf(sessionId).isBlank()) {
			try {
				sessionUuid = UUID.fromString(String.valueOf(sessionId));
			}
			catch (IllegalArgumentException ignored) {
				logger.debug("Run session_id 不是持久会话 UUID，跳过报告消息关联: {}", sessionId);
			}
		}
		if (sessionUuid != null) {
			String metadata = "{\"type\":\"research_report\",\"run_id\":\"" + runId + "\"}";
			jdbc.update("""
						INSERT INTO rp_chat_messages (session_id, role, content, metadata)
						SELECT ?::uuid, 'assistant', ?, ?::jsonb
						WHERE EXISTS (SELECT 1 FROM rp_chat_sessions WHERE id = ?::uuid AND topic_id = ?::uuid)
						AND NOT EXISTS (
							SELECT 1 FROM rp_chat_messages WHERE session_id = ?::uuid
							  AND metadata->>'run_id' = ?)
					""", sessionUuid.toString(), report, metadata, sessionUuid.toString(), run.get("topic_id"),
					sessionUuid.toString(), runId.toString());
		}

		jdbc.update("UPDATE rp_runs SET finished_at = now(), updated_at = now() WHERE id = ? AND finished_at IS NULL",
				runId);
		boolean transitioned = runService.transition(runId, RunStatus.FINALIZING,
				allDone ? RunStatus.COMPLETED : RunStatus.PARTIAL_COMPLETED, allDone ? null : "部分步骤未完成");
		logger.info("Run 收敛完成: run={} 终态={}", runId, transitioned ? "已写入" : "竞争失败");
	}

	private static Instant toInstant(Object dbTime) {
		if (dbTime == null) {
			return null;
		}
		if (dbTime instanceof OffsetDateTime odt) {
			return odt.toInstant();
		}
		if (dbTime instanceof java.sql.Timestamp ts) {
			return ts.toInstant();
		}
		return null;
	}

}
