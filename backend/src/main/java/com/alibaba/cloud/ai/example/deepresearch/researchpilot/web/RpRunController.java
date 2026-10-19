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

package com.alibaba.cloud.ai.example.deepresearch.researchpilot.web;

import com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpPlanningService;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpRunService;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RunStatus;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.step.ResearchScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V0.4 H1.4：业务层 Run API —— HITL 与恢复走持久化状态机（决策 1/2），不再依赖 Graph checkpoint。
 *
 * <p>
 * 前端契约：创建研究后凭 run_id 轮询/查询状态；审批/恢复/取消均为状态转换， 重复提交幂等（第二次被状态机拒绝）。旧 /chat/stream 路径保留为对照组，新
 * Run 不进入旧图。
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/rp/research")
public class RpRunController {

	private static final Logger logger = LoggerFactory.getLogger(RpRunController.class);

	private final RpRunService runService;

	private final RpPlanningService planningService;

	private final ResearchScheduler scheduler;

	private final JdbcTemplate jdbc;

	private final com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpEventLogger eventLogger;

	private final com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RunListingService runListing;

	private final com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpAdmissionService admissionService;

	private final RpExecutionLauncher executionLauncher;

	private final com.alibaba.cloud.ai.example.deepresearch.researchpilot.auth.UserContext userContext;

	@org.springframework.beans.factory.annotation.Autowired(required = false)
	private com.alibaba.cloud.ai.example.deepresearch.researchpilot.obs.RpReportCache reportCache;

	// P1-D：创建接口每用户令牌公平性限流（内存窗口；与 P0-B 全局准入互补——
	// 准入管系统总容量，此限流管单用户提交速率。Redis 化为 Phase 4 后续项）
	private final com.alibaba.cloud.ai.example.deepresearch.researchpilot.auth.LoginRateLimiter createRateLimiter = new com.alibaba.cloud.ai.example.deepresearch.researchpilot.auth.LoginRateLimiter();

	private final long promoteIntervalMs;

	public RpRunController(RpRunService runService, RpPlanningService planningService, ResearchScheduler scheduler,
			JdbcTemplate jdbc, com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpEventLogger eventLogger,
			com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RunListingService runListing,
			com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpAdmissionService admissionService,
			RpExecutionLauncher executionLauncher,
			com.alibaba.cloud.ai.example.deepresearch.researchpilot.auth.UserContext userContext,
			@org.springframework.beans.factory.annotation.Value("${researchpilot.admission.promote-interval-ms:5000}") long promoteIntervalMs) {
		this.runService = runService;
		this.planningService = planningService;
		this.scheduler = scheduler;
		this.jdbc = jdbc;
		this.eventLogger = eventLogger;
		this.runListing = runListing;
		this.admissionService = admissionService;
		this.executionLauncher = executionLauncher;
		this.userContext = userContext;
		this.promoteIntervalMs = promoteIntervalMs;
	}

	public record CreateRequest(String query, String topic_id, String session_id, java.util.List<String> dimensions,
			java.util.List<String> steps, java.util.List<String> paper_ids, java.util.List<String> clarifications,
			java.util.List<String> note_ids, Boolean auto_accept_plan, String client_request_id, Boolean allow_web,
			java.util.List<String> assumptions, java.util.Map<String, Object> constraints, Boolean assumptions_accepted,
			Boolean skip_clarification) {
	}

	public record ContinueRequest(String gap, String client_request_id) {
	}

	public record BranchRequest(String query, String client_request_id) {
	}

	public record PlanAdjustmentRequest(
			List<com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.PlanAdjustmentService.EvidenceSignal> signals) {
	}

	public record PlanAdjustmentApplyRequest(List<String> steps) {
	}

	/**
	 * V1 UX-2：计划预览（不建 Run）—— 展示研究步骤/范围，用户确认后才创建正式任务。
	 */
	@PostMapping("/plan-draft")
	public Map<String, Object> planDraft(@RequestBody CreateRequest req) {
		var pd = planningService.planDraft(withConstraints(req.query(), req.constraints()), req.topic_id(),
				req.dimensions() == null ? java.util.List.of() : req.dimensions(),
				req.clarifications() == null ? java.util.List.of() : req.clarifications(),
				Boolean.TRUE.equals(req.skip_clarification()));
		return Map.of("steps", pd.steps(), "dimensions", pd.dimensions(), "clarifying_questions",
				pd.clarifyingQuestions(), "assumptions", pd.assumptions(), "scope_papers",
				req.paper_ids() == null ? java.util.List.of() : req.paper_ids());
	}

	/**
	 * Continue a completed/partial run for one explicit evidence gap, preserving scope
	 * and provenance.
	 */
	@PostMapping("/{runId}/continue")
	@org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
	public ResponseEntity<?> continueGap(@PathVariable UUID runId, @RequestBody ContinueRequest req) {
		if (req.gap() == null || req.gap().isBlank() || req.gap().length() > 4000)
			return ResponseEntity.badRequest().body(Map.of("error", "研究缺口不能为空且不能超过 4000 字符"));
		ResponseEntity<Map<String, Object>> denied = foreignRun(runId);
		if (denied != null) {
			return denied;
		}
		Map<String, Object> parent = runService.loadRun(runId);
		String status = String.valueOf(parent.get("status"));
		if (!List.of("COMPLETED", "PARTIAL_COMPLETED").contains(status))
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "只有已完成或部分完成的研究可以继续"));
		UUID topicId = parent.get("topic_id") == null ? null : UUID.fromString(parent.get("topic_id").toString());
		String scope = parent.get("paper_scope") == null ? "[]" : parent.get("paper_scope").toString();
		List<String> papers;
		try {
			papers = new com.fasterxml.jackson.databind.ObjectMapper().readValue(scope,
					new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
					});
		}
		catch (Exception e) {
			papers = List.of();
		}
		String childQuery = String.valueOf(parent.get("query")) + "\n针对证据缺口：" + req.gap().trim();
		UUID child = runService.createRun("rp-continuation-" + UUID.randomUUID(),
				parent.get("session_id") == null ? null : parent.get("session_id").toString(), topicId, childQuery,
				papers, req.client_request_id(), Boolean.TRUE.equals(parent.get("allow_web")));
		jdbc.update("UPDATE rp_runs SET parent_run_id = ?, continuation_reason = ? WHERE id = ?", runId,
				req.gap().trim(), child);
		if (!runService.transition(child, RunStatus.CREATED, RunStatus.PLANNING, "continuation"))
			return ResponseEntity.ok(Map.of("run_id", child.toString(), "idempotent", true));
		runService.initSteps(child, List.of("针对证据缺口补充取证：" + req.gap().trim()));
		runService.savePlanSnapshot(child,
				Map.of("version", UUID.randomUUID().toString(), "query", childQuery, "steps",
						List.of("针对证据缺口补充取证：" + req.gap().trim()), "paper_ids", papers, "parent_run_id",
						runId.toString(), "continuation_reason", req.gap().trim()));
		// Copy only accepted evidence as read-only background; the child still records
		// new evidence separately.
		jdbc.update(
				"""
						INSERT INTO rp_evidence (task_id, run_id, topic_id, paper_id, document_version_id, schema_field_id,
							field_title, value, supporting_quote, source_chunk_id, section_path, page, status, origin, verification,
							evidence_status, quote_status, claim_status, source_type, source_url)
						SELECT ?::text, ?, topic_id, paper_id, document_version_id, schema_field_id, field_title, value,
							supporting_quote, source_chunk_id, section_path, page, status, origin, verification, 'ACCEPTED',
							quote_status, claim_status, source_type, source_url
						FROM rp_evidence WHERE run_id = ? AND evidence_status = 'ACCEPTED'
						""",
				child.toString(), child, runId);
		runService.setOwner(child, userContext.currentOwnerId().orElse(null));
		return admitAndLaunch(child, Map.of("parent_run_id", runId.toString()));
	}

	/** P0-A：归属守卫 —— 非本人（含不存在）返回 404（不泄露存在性），否则 null 放行。 */
	private ResponseEntity<Map<String, Object>> foreignRun(UUID runId) {
		var ownerId = userContext.currentOwnerId();
		if (ownerId.isEmpty() || runService.loadRunForOwner(runId, ownerId.get()).isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "资源不存在"));
		}
		return null;
	}

	/**
	 * P0-B 统一准入+启动入口。STARTED → 启动执行并返回 RESEARCHING；满载 → 排队返回 QUEUED； 队列满 → 429 +
	 * Retry-After。竞态丢失（状态已被并发改变）按 409 幂等处理。
	 */
	ResponseEntity<Map<String, Object>> admitAndLaunch(UUID runId) {
		return admitAndLaunch(runId, Map.of());
	}

	ResponseEntity<Map<String, Object>> admitAndLaunch(UUID runId, Map<String, Object> extraBody) {
		try {
			var decision = admissionService.tryAdmit(runId);
			boolean started = decision == com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpAdmissionService.AdmitDecision.STARTED;
			if (started) {
				executionLauncher.launch(runId);
			}
			var body = new java.util.LinkedHashMap<String, Object>();
			body.put("run_id", runId.toString());
			body.putAll(extraBody);
			body.put("status", started ? "RESEARCHING" : "QUEUED");
			return ResponseEntity.accepted().body(body);
		}
		catch (com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpAdmissionQueueFullException e) {
			com.alibaba.cloud.ai.example.deepresearch.researchpilot.obs.RpMetricsBinder.rejected();
			var body = new java.util.LinkedHashMap<String, Object>();
			body.put("run_id", runId.toString());
			body.putAll(extraBody);
			body.put("status", "QUEUE_FULL");
			body.put("error", "并发研究任务已达上限，请稍后重试");
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header("Retry-After", String.valueOf(Math.max(1, promoteIntervalMs / 1000)))
				.body(body);
		}
		catch (IllegalStateException e) {
			// 准入转移被并发竞争拒绝（run 状态已被改变）——幂等返回当前状态
			return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(Map.of("run_id", runId.toString(), "status", runService.currentStatus(runId).name(), "note",
						"状态已被并发改变"));
		}
	}

	/**
	 * Create an explicit goal branch. The parent run remains immutable and its accepted
	 * evidence is copied as read-only context for the new objective.
	 */
	@PostMapping("/{runId}/branch")
	@org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
	public ResponseEntity<?> branch(@PathVariable UUID runId, @RequestBody BranchRequest req) {
		if (req.query() == null || req.query().isBlank() || req.query().length() > 8000) {
			return ResponseEntity.badRequest().body(Map.of("error", "新的研究目标不能为空且不能超过 8000 字符"));
		}
		ResponseEntity<Map<String, Object>> denied = foreignRun(runId);
		if (denied != null) {
			return denied;
		}
		Map<String, Object> parent = runService.loadRun(runId);
		String parentStatus = String.valueOf(parent.get("status"));
		if ("CANCELLED".equals(parentStatus)) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "已取消的研究不能创建分支"));
		}
		UUID topicId = parent.get("topic_id") == null ? null : UUID.fromString(parent.get("topic_id").toString());
		String scope = parent.get("paper_scope") == null ? "[]" : parent.get("paper_scope").toString();
		List<String> papers;
		try {
			papers = new com.fasterxml.jackson.databind.ObjectMapper().readValue(scope,
					new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
					});
		}
		catch (Exception e) {
			papers = List.of();
		}
		UUID child = runService.createRun("rp-branch-" + UUID.randomUUID(),
				parent.get("session_id") == null ? null : parent.get("session_id").toString(), topicId,
				req.query().trim(), papers, req.client_request_id(), Boolean.TRUE.equals(parent.get("allow_web")));
		jdbc.update("UPDATE rp_runs SET parent_run_id = ?, continuation_reason = ? WHERE id = ?", runId, "goal_changed",
				child);
		if (!runService.transition(child, RunStatus.CREATED, RunStatus.PLANNING, "goal_branch")) {
			return ResponseEntity
				.ok(Map.of("run_id", child.toString(), "parent_run_id", runId.toString(), "idempotent", true));
		}
		String step = "围绕新的研究目标建立证据：" + req.query().trim();
		runService.initSteps(child, List.of(step));
		runService.savePlanSnapshot(child,
				Map.of("version", UUID.randomUUID().toString(), "query", req.query().trim(), "steps", List.of(step),
						"paper_ids", papers, "parent_run_id", runId.toString(), "branch_reason", "goal_changed"));
		jdbc.update(
				"""
						INSERT INTO rp_evidence (task_id, run_id, topic_id, paper_id, document_version_id, schema_field_id,
							field_title, value, supporting_quote, source_chunk_id, section_path, page, status, origin, verification,
							evidence_status, quote_status, claim_status, source_type, source_url)
						SELECT ?::text, ?, topic_id, paper_id, document_version_id, schema_field_id, field_title, value,
							supporting_quote, source_chunk_id, section_path, page, status, origin, verification, 'ACCEPTED',
							quote_status, claim_status, source_type, source_url
						FROM rp_evidence WHERE run_id = ? AND evidence_status = 'ACCEPTED'
						""",
				child.toString(), child, runId);
		runService.setOwner(child, userContext.currentOwnerId().orElse(null));
		return admitAndLaunch(child, Map.of("parent_run_id", runId.toString(), "branch_reason", "goal_changed"));
	}

	/**
	 * V1 UX-2：优雅停止 —— 结束研究并保留当前成果（区别于删除）。正在执行的步骤完成后， 未完成步骤按 SKIPPED 收敛为
	 * PARTIAL_COMPLETED；若无执行段在跑则直接收敛。
	 */
	@PostMapping("/{runId}/stop")
	public Object stop(@PathVariable UUID runId) {
		ResponseEntity<Map<String, Object>> denied = foreignRun(runId);
		if (denied != null) {
			return denied;
		}
		// P0-B：排队中的 run 无执行段，stop 直接 QUEUED → CANCELLED（不经 RESEARCHING 优雅停止）
		if (runService.transition(runId, RunStatus.QUEUED, RunStatus.CANCELLED, "stopped")) {
			return Map.of("stopped", true, "status", "CANCELLED");
		}
		return Map.of("stopped", scheduler.stopRun(runId));
	}

	@PostMapping("/{runId}/pause")
	public Object pause(@PathVariable UUID runId) {
		ResponseEntity<Map<String, Object>> denied = foreignRun(runId);
		if (denied != null) {
			return denied;
		}
		boolean paused = scheduler.pauseRun(runId);
		return Map.of("paused", paused, "run_id", runId.toString());
	}

	/**
	 * 创建研究 Run：幂等（重复请求返回既有 run），审批中暂停于 WAITING_APPROVAL。
	 */
	/**
	 * Run 列表（最近优先）—— 研究任务页数据源。
	 */
	@GetMapping
	public List<Map<String, Object>> listRuns() {
		return jdbc.queryForList(
				"SELECT id::text, topic_id::text AS topic_id, session_id, thread_id, query, status, execution_cursor, attempt, "
						+ "created_at FROM rp_runs WHERE owner_user_id = ?::uuid ORDER BY created_at DESC LIMIT 50",
				userContext.currentOwnerId().orElse(null).toString());
	}

	@GetMapping("/page")
	public Map<String, Object> listRunPage(
			@org.springframework.web.bind.annotation.RequestParam(required = false) UUID topic_id,
			@org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int limit,
			@org.springframework.web.bind.annotation.RequestParam(required = false) String before_time,
			@org.springframework.web.bind.annotation.RequestParam(required = false) UUID before_id,
			@org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean active_only) {
		java.time.OffsetDateTime before = null;
		if (before_time != null) {
			try {
				before = java.time.OffsetDateTime.parse(before_time);
			}
			catch (java.time.format.DateTimeParseException e) {
				throw new org.springframework.web.server.ResponseStatusException(
						org.springframework.http.HttpStatus.BAD_REQUEST, "分页游标时间无效");
			}
		}
		return runListing.page(userContext.currentOwnerId().orElse(null), topic_id, limit, before, before_id,
				active_only);
	}

	@PostMapping
	public ResponseEntity<Map<String, Object>> create(@RequestBody CreateRequest req) {
		if (req.query() == null || req.query().isBlank()) {
			return ResponseEntity.badRequest().body(Map.of("error", "query 不能为空"));
		}
		if (req.session_id() != null) {
			try {
				UUID sessionId = UUID.fromString(req.session_id());
				jdbc.update(
						"INSERT INTO rp_chat_sessions (id, topic_id) VALUES (?, ?::uuid) ON CONFLICT (topic_id, id) DO NOTHING",
						sessionId, req.topic_id());
			}
			catch (Exception ignored) {
				// Older clients may not send a UUID session; task creation remains valid.
			}
		}
		if (req.client_request_id() != null) {
			var existing = runService.findRunByClientRequest(req.client_request_id());
			if (existing.isPresent()) {
				UUID prior = existing.get();
				// I1：搁浅 PLANNING（此前 429 未准入成功）——幂等重试重新走准入，解除搁浅
				if (runService.currentStatus(prior) == RunStatus.PLANNING) {
					return admitAndLaunch(prior, Map.of("idempotent", true));
				}
				return ResponseEntity.ok(Map.of("run_id", prior.toString(), "status",
						runService.currentStatus(prior).name(), "idempotent", true));
			}
		}
		// P1-D：创建接口每用户限速（5/分钟）—— 在建 run 之前拒绝，避免 429 搁浅行
		var createOwnerId = userContext.currentOwnerId().orElse(null);
		if (createOwnerId != null && !createRateLimiter.tryAcquire("create:" + createOwnerId)) {
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header("Retry-After", "60")
				.body(Map.of("error", "提交过于频繁，请稍后再试"));
		}
		String threadId = "rp-" + UUID.randomUUID();
		UUID runId = runService.createRun(threadId, req.session_id(),
				req.topic_id() == null ? null : UUID.fromString(req.topic_id()), req.query(), req.paper_ids(),
				req.client_request_id(), Boolean.TRUE.equals(req.allow_web()));
		if (!runService.transition(runId, RunStatus.CREATED, RunStatus.PLANNING, null)) {
			return ResponseEntity.ok(Map.of("run_id", runId.toString(), "status",
					runService.currentStatus(runId).name(), "idempotent", true));
		}
		// I7：归属写入提前到规划前 —— planDraft 失败也不留 NULL owner 幽灵行（off 模式 = admin）
		runService.setOwner(runId, userContext.currentOwnerId().orElse(null));

		// V1 交互：create 接收用户确认过的计划步骤（plan-draft → 计划页可改 → 确认），避免二次规划分歧
		List<String> stepIds = (req.steps() != null && !req.steps().isEmpty()) ? req.steps()
				: planningService
					.planDraft(withConstraints(req.query(), req.constraints()), req.topic_id(),
							req.dimensions() == null ? java.util.List.of() : req.dimensions(),
							req.clarifications() == null ? java.util.List.of() : req.clarifications())
					.steps();
		if (stepIds.isEmpty()) {
			runService.transition(runId, RunStatus.PLANNING, RunStatus.FAILED, "计划生成失败");
			return ResponseEntity.ok(Map.of("run_id", runId.toString(), "status", "FAILED", "reason", "计划生成失败"));
		}
		runService.initSteps(runId, stepIds);
		runService.savePlanSnapshot(runId,
				Map.ofEntries(Map.entry("version", UUID.randomUUID().toString()), Map.entry("query", req.query()),
						Map.entry("dimensions", req.dimensions() == null ? List.of() : req.dimensions()),
						Map.entry("steps", stepIds),
						Map.entry("clarifications", req.clarifications() == null ? List.of() : req.clarifications()),
						Map.entry("paper_ids", req.paper_ids() == null ? List.of() : req.paper_ids()),
						Map.entry("allow_web", Boolean.TRUE.equals(req.allow_web())),
						Map.entry("note_ids", req.note_ids() == null ? List.of() : req.note_ids()),
						Map.entry("assumptions", req.assumptions() == null ? List.of() : req.assumptions()),
						Map.entry("constraints", req.constraints() == null ? Map.of() : req.constraints()),
						Map.entry("assumptions_accepted", Boolean.TRUE.equals(req.assumptions_accepted()))));

		boolean autoAccept = req.auto_accept_plan() == null || req.auto_accept_plan();
		if (autoAccept) {
			return admitAndLaunch(runId);
		}
		runService.transition(runId, RunStatus.PLANNING, RunStatus.WAITING_APPROVAL, null);
		return ResponseEntity.accepted().body(Map.of("run_id", runId.toString(), "status", "WAITING_APPROVAL"));
	}

	/**
	 * 审批通过：WAITING_APPROVAL → RESEARCHING（幂等——重复审批第二次被状态机拒绝，不产生第二个执行）。
	 */
	@PostMapping("/{runId}/approve")
	public ResponseEntity<Map<String, Object>> approve(@PathVariable UUID runId) {
		ResponseEntity<Map<String, Object>> denied = foreignRun(runId);
		if (denied != null) {
			return denied;
		}
		if (runService.currentStatus(runId) == RunStatus.WAITING_APPROVAL) {
			// P0-B：审批后走统一准入 —— 满载时排队（QUEUED），不绕过并发限额
			return admitAndLaunch(runId);
		}
		return ResponseEntity.ok(Map.of("run_id", runId.toString(), "status", runService.currentStatus(runId).name(),
				"note", "当前状态不可审批（幂等拒绝）"));
	}

	@PostMapping("/{runId}/reject")
	public ResponseEntity<Map<String, Object>> reject(@PathVariable UUID runId) {
		ResponseEntity<Map<String, Object>> denied = foreignRun(runId);
		if (denied != null) {
			return denied;
		}
		boolean ok = runService.transition(runId, RunStatus.WAITING_APPROVAL, RunStatus.CANCELLED, "rejected");
		return ResponseEntity.ok(Map.of("run_id", runId.toString(), "cancelled", ok));
	}

	/**
	 * 恢复：进程重启后凭 run_id 续跑（从 execution_cursor 继续，已完成步骤不重跑）。
	 */
	@PostMapping("/{runId}/resume")
	public ResponseEntity<Map<String, Object>> resume(@PathVariable UUID runId) {
		ResponseEntity<Map<String, Object>> denied = foreignRun(runId);
		if (denied != null) {
			return denied;
		}
		if (runService.currentStatus(runId) != RunStatus.RESEARCHING) {
			return ResponseEntity.badRequest().body(Map.of("error", "Run 不在 RESEARCHING 状态"));
		}
		runService.clearPause(runId);
		return admitAndLaunch(runId);
	}

	public record RequirementRequest(String requirement) {
	}

	@PostMapping("/{runId}/requirements")
	public ResponseEntity<?> addRequirement(@PathVariable UUID runId, @RequestBody RequirementRequest req) {
		ResponseEntity<Map<String, Object>> denied = foreignRun(runId);
		if (denied != null) {
			return denied;
		}
		if (req.requirement() == null || req.requirement().isBlank() || req.requirement().length() > 10_000) {
			return ResponseEntity.badRequest()
				.body(Map.of("code", "INVALID_REQUIREMENT", "message", "补充要求不能为空且不能超过 10000 个字符", "retryable", false));
		}
		UUID id = runService.addRequirement(runId, req.requirement());
		return ResponseEntity.accepted()
			.body(Map.of("id", id.toString(), "run_id", runId.toString(), "status", "PENDING"));
	}

	@GetMapping("/{runId}/requirements")
	public Object requirements(@PathVariable UUID runId) {
		ResponseEntity<Map<String, Object>> denied = foreignRun(runId);
		if (denied != null) {
			return denied;
		}
		return jdbc.queryForList(
				"SELECT id::text, requirement, status, created_at FROM rp_run_requirements WHERE run_id = ? ORDER BY created_at",
				runId);
	}

	@PostMapping("/{runId}/steps/{stepIndex}/retry")
	public ResponseEntity<Map<String, Object>> retryStep(@PathVariable UUID runId, @PathVariable int stepIndex) {
		ResponseEntity<Map<String, Object>> denied = foreignRun(runId);
		if (denied != null) {
			return denied;
		}
		if (stepIndex < 0) {
			return ResponseEntity.badRequest()
				.body(Map.of("code", "INVALID_STEP", "message", "步骤序号无效", "retryable", false));
		}
		boolean retried = runService.retryFailedStep(runId, stepIndex);
		if (retried) {
			return admitAndLaunch(runId, Map.of("step_index", stepIndex, "note", "步骤已重置为可执行"));
		}
		return ResponseEntity.status(409)
			.body(Map.of("code", "STEP_NOT_RETRYABLE", "message", "步骤不是可重试的失败状态", "retryable", false));
	}

	/**
	 * Run 状态与步骤列表（前端恢复订阅的数据源——SSE 不是业务状态来源）。
	 */
	@GetMapping("/{runId}")
	public ResponseEntity<Map<String, Object>> status(@PathVariable UUID runId) {
		ResponseEntity<Map<String, Object>> denied = foreignRun(runId);
		if (denied != null) {
			return denied;
		}
		Map<String, Object> run = new HashMap<>(runService.loadRun(runId));
		run.put("steps", runService.loadSteps(runId));
		return ResponseEntity.ok(run);
	}

	@GetMapping("/{runId}/plan-versions")
	public ResponseEntity<?> planVersions(@PathVariable UUID runId) {
		ResponseEntity<Map<String, Object>> denied = foreignRun(runId);
		if (denied != null) {
			return denied;
		}
		return ResponseEntity.ok(runService.planVersions(runId));
	}

	/** Preview a bounded plan revision from explicit or persisted evidence signals. */
	@PostMapping("/{runId}/plan-adjust/preview")
	public ResponseEntity<?> previewPlanAdjustment(@PathVariable UUID runId,
			@RequestBody(required = false) PlanAdjustmentRequest req) {
		ResponseEntity<Map<String, Object>> denied = foreignRun(runId);
		if (denied != null) {
			return denied;
		}
		Map<String, Object> run = runService.loadRun(runId);
		List<String> steps = runService.loadSteps(runId)
			.stream()
			.map(step -> String.valueOf(step.get("step_id")))
			.toList();
		List<com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.PlanAdjustmentService.EvidenceSignal> signals = req == null
				|| req.signals() == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(req.signals());
		if (signals.isEmpty()) {
			List<Map<String, Object>> rows = jdbc.queryForList(
					"SELECT step_id, claim_status, evidence_status, value FROM rp_evidence WHERE run_id = ?::uuid AND (claim_status IN ('CONTRADICTED','INSUFFICIENT') OR evidence_status IN ('NEEDS_REVIEW','INSUFFICIENT'))",
					runId.toString());
			for (Map<String, Object> row : rows) {
				String kind = "CONTRADICTED".equals(row.get("claim_status")) ? "CONFLICT" : "GAP";
				signals.add(
						new com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.PlanAdjustmentService.EvidenceSignal(
								String.valueOf(row.get("step_id")), kind,
								String.valueOf(row.getOrDefault("value", "需要复核原文"))));
			}
		}
		var proposal = com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.PlanAdjustmentService.propose(steps,
				signals);
		return ResponseEntity.ok(Map.of("run_id", runId.toString(), "status", run.get("status"), "steps",
				proposal.steps(), "reasons", proposal.reasons(), "requires_approval", proposal.requiresApproval()));
	}

	/** Apply an explicitly reviewed plan proposal; executed steps remain fenced. */
	@PostMapping("/{runId}/plan-adjust/apply")
	public ResponseEntity<?> applyPlanAdjustment(@PathVariable UUID runId,
			@RequestBody PlanAdjustmentApplyRequest req) {
		ResponseEntity<Map<String, Object>> denied = foreignRun(runId);
		if (denied != null) {
			return denied;
		}
		try {
			return ResponseEntity.accepted()
				.body(runService.applyPlanAdjustment(runId, req == null ? null : req.steps()));
		}
		catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest()
				.body(Map.of("code", "INVALID_PLAN_ADJUSTMENT", "message", e.getMessage(), "retryable", false));
		}
		catch (IllegalStateException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(Map.of("code", "PLAN_ADJUSTMENT_CONFLICT", "message", e.getMessage(), "retryable", false));
		}
	}

	/**
	 * 执行事件（JSONL 尾部倒序，治理 §三.1）——事件是观测数据，业务状态以 Run 状态机为准。
	 */
	@GetMapping("/{runId}/events")
	public Object events(@PathVariable UUID runId,
			@org.springframework.web.bind.annotation.RequestParam(defaultValue = "50") int limit,
			@org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") long after) {
		ResponseEntity<Map<String, Object>> denied = foreignRun(runId);
		if (denied != null) {
			return denied;
		}
		return after > 0 ? eventLogger.after(runId, after, limit) : eventLogger.tail(runId, limit);
	}

	@GetMapping("/{runId}/report")
	public ResponseEntity<Map<String, Object>> report(@PathVariable UUID runId) {
		ResponseEntity<Map<String, Object>> denied = foreignRun(runId);
		if (denied != null) {
			return denied;
		}
		// P1-D Cache-Aside：报告不可变，命中即回（越权守卫在前，缓存不构成越权通道）
		if (reportCache != null) {
			String cached = reportCache.get(runId);
			if (cached != null) {
				return ResponseEntity
					.ok(Map.of("report", cached, "run_status", runService.currentStatus(runId).name(), "cache", "hit"));
			}
		}
		List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM rp_reports WHERE run_id = ?::uuid",
				runId.toString());
		if (rows.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		String raw = rows.get(0).get("report") == null ? null : String.valueOf(rows.get(0).get("report"));
		String clean = com.alibaba.cloud.ai.example.deepresearch.researchpilot.step.DeterministicReportComposer
			.sanitizePersisted(raw);
		if (raw != null && !raw.equals(clean)) {
			jdbc.update("UPDATE rp_reports SET report = ? WHERE run_id = ?::uuid", clean, runId.toString());
			rows.get(0).put("report", clean);
		}
		if (reportCache != null && clean != null) {
			reportCache.put(runId, clean);
		}
		rows.get(0).put("run_status", runService.currentStatus(runId).name());
		return ResponseEntity.ok(rows.get(0));
	}

	@PostMapping("/{runId}/share")
	@org.springframework.transaction.annotation.Transactional
	public ResponseEntity<?> createShare(@PathVariable UUID runId) {
		ResponseEntity<Map<String, Object>> denied = foreignRun(runId);
		if (denied != null) {
			return denied;
		}
		String status = runService.currentStatus(runId).name();
		if (!List.of("COMPLETED", "PARTIAL_COMPLETED").contains(status))
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "只有已完成成果可以分享"));
		String raw = java.util.HexFormat.of().formatHex(SecureRandomHolder.RANDOM.generateSeed(32));
		String hash = sha256(raw);
		UUID id = UUID.randomUUID();
		jdbc.update("INSERT INTO rp_report_shares (id, run_id, token_hash) VALUES (?, ?, ?)", id, runId, hash);
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(Map.of("share_id", id.toString(), "token", raw, "path", "/shared/" + raw, "api_path",
					"/api/rp/research/shared/" + raw));
	}

	@DeleteMapping("/{runId}/share/{shareId}")
	public ResponseEntity<?> revokeShare(@PathVariable UUID runId, @PathVariable UUID shareId) {
		ResponseEntity<Map<String, Object>> denied = foreignRun(runId);
		if (denied != null) {
			return denied;
		}
		int changed = jdbc.update(
				"UPDATE rp_report_shares SET revoked_at = now() WHERE id = ? AND run_id = ? AND revoked_at IS NULL",
				shareId, runId);
		return changed == 1 ? ResponseEntity.ok(Map.of("revoked", true)) : ResponseEntity.notFound().build();
	}

	@GetMapping("/shared/{token}")
	public ResponseEntity<?> sharedReport(@PathVariable String token) {
		if (token == null || token.length() != 64)
			return ResponseEntity.notFound().build();
		List<Map<String, Object>> rows = jdbc.queryForList("""
				SELECT r.id::text AS run_id, r.status, r.query, p.report, p.completed_steps, p.total_steps
				FROM rp_report_shares s JOIN rp_runs r ON r.id = s.run_id JOIN rp_reports p ON p.run_id = r.id
				WHERE s.token_hash = ? AND s.revoked_at IS NULL AND (s.expires_at IS NULL OR s.expires_at > now())
				""", sha256(token));
		if (rows.isEmpty())
			return ResponseEntity.notFound().build();
		Object raw = rows.get(0).get("report");
		if (raw != null)
			rows.get(0)
				.put("report", com.alibaba.cloud.ai.example.deepresearch.researchpilot.step.DeterministicReportComposer
					.sanitizePersisted(String.valueOf(raw)));
		return ResponseEntity.ok(rows.get(0));
	}

	private String sha256(String value) {
		try {
			return java.util.HexFormat.of()
				.formatHex(java.security.MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		}
		catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}

	private static final class SecureRandomHolder {

		private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

	}

	@GetMapping("/{runId}/report-versions")
	public Object reportVersions(@PathVariable UUID runId) {
		ResponseEntity<Map<String, Object>> denied = foreignRun(runId);
		if (denied != null) {
			return denied;
		}
		List<Map<String, Object>> rows = jdbc.queryForList(
				"SELECT version, report, completed_steps, total_steps, source_ids::text AS source_ids, created_at FROM rp_report_versions WHERE run_id = ?::uuid ORDER BY version",
				runId.toString());
		for (Map<String, Object> row : rows) {
			Object raw = row.get("report");
			if (raw != null)
				row.put("report",
						com.alibaba.cloud.ai.example.deepresearch.researchpilot.step.DeterministicReportComposer
							.sanitizePersisted(String.valueOf(raw)));
		}
		return rows;
	}

	private String withConstraints(String query, Map<String, Object> constraints) {
		if (constraints == null || constraints.isEmpty()) {
			return query;
		}
		return query + "\n用户确认的高级约束（必须遵守）：" + constraints;
	}

}
