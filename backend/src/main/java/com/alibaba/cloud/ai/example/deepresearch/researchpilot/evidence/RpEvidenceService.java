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

package com.alibaba.cloud.ai.example.deepresearch.researchpilot.evidence;

import com.alibaba.cloud.ai.example.deepresearch.researchpilot.retrieve.RpRetrievalService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ResearchPilot Phase C：Evidence Store —— 证据登记、确定性引用校验、矩阵聚合。
 *
 * <p>
 * 核心原则（《RAG 设计 V0.1》§6）： 1. 检索候选（Candidate）不自动等于证据（Evidence）—— 引用支持关系必须确定性校验； 2.
 * NOT_FOUND ≠ NOT_REPORTED；证据六状态不可混写； 3. 研究文本是模型归纳（origin=SUMMARIZED），原文片段才是 EXTRACTED ——
 * 二者分开登记。
 */
@Service
public class RpEvidenceService {

	private final JdbcTemplate jdbc;

	private final RpRetrievalService retrieval;

	// ResearchPilot Phase E：双层校验器（Layer1 确定性引用 / Layer2 相关性过滤+NLI 语义支持）
	private final RpClaimVerifier verifier;

	public RpEvidenceService(JdbcTemplate jdbc, RpRetrievalService retrieval, RpClaimVerifier verifier) {
		this.jdbc = jdbc;
		this.retrieval = retrieval;
		this.verifier = verifier;
		// ResearchPilot Phase D：注册 plan 快照兜底加载器（HITL 恢复路径 state 缺 current_plan）
		com.alibaba.cloud.ai.example.deepresearch.util.StateUtil.setPlanSnapshotLoader(threadId -> {
			try {
				var rows = jdbc.queryForList("SELECT plan::text FROM rp_plan_snapshots WHERE thread_id = ?", threadId);
				if (rows.isEmpty()) {
					return null;
				}
				return new com.fasterxml.jackson.databind.ObjectMapper().readValue((String) rows.get(0).get("plan"),
						com.alibaba.cloud.ai.example.deepresearch.model.dto.Plan.class);
			}
			catch (Exception e) {
				return null;
			}
		});
	}

	// ---------- 引用校验（已迁移至 RpClaimVerifier 双层校验器） ----------
	//
	// Layer 1 · Quotation Verification：归一化 n-gram 重叠 + 句级包含（确定性，零成本）
	// Layer 2 · Claim Support Verification：bge-m3 相关性预过滤 → 批量 NLI 语义支持判定
	// 判级：L1 通过 → SUPPORTED；L2 SUPPORTED → SUPPORTED；CONTRADICTED → CONTRADICTED；其余 →
	// NEEDS_REVIEW
	// 动机与校准约定见 RpClaimVerifier 类注释（语义相关 ≠ 事实支持）。

	// ---------- 证据登记 ----------

	public record KbCandidate(String chunkId, String paperId, String text, String sectionPath, Integer page,
			String sourceType, String sourceUrl) {

		public KbCandidate(String chunkId, String paperId, String text, String sectionPath, Integer page) {
			this(chunkId, paperId, text, sectionPath, page, null, null);
		}
	}

	/**
	 * 研究步骤完成：登记该步骤的归纳证据（SUMMARIZED）+ 每条论文库候选的原文证据 （EXTRACTED，经确定性校验定级 SUPPORTED /
	 * NEEDS_REVIEW）。
	 */
	public void registerStepEvidence(String taskId, String topicId, String fieldId, String fieldTitle,
			String researchText, List<KbCandidate> candidates) {
		// 1. 归纳证据（模型产出，可追溯其引用的 chunk）
		jdbc.update("""
				INSERT INTO rp_evidence (task_id, topic_id, schema_field_id, field_title, value, origin, status)
				VALUES (?, ?::uuid, ?, ?, ?, 'SUMMARIZED', 'NEEDS_REVIEW')
				""", taskId, topicId, fieldId, fieldTitle, researchText);

		// 2. 原文证据：双层校验（L1 确定性引用 → L2 相关性过滤 + NLI 语义支持），H3 起记录
		// quote_status / claim_status 独立判级（旧 status 列为兼容聚合）
		for (RpClaimVerifier.CandidateVerdict v : verifier.verify(researchText, candidates)) {
			KbCandidate c = v.candidate();
			String verification = verifier.verificationJson(v);
			jdbc.update("""
					INSERT INTO rp_evidence (task_id, topic_id, paper_id, schema_field_id, field_title,
						value, supporting_quote, source_chunk_id, section_path, page, origin, status, verification,
						quote_status, claim_status)
					VALUES (?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?, 'EXTRACTED', ?, ?, ?, ?)
					""", taskId, topicId, c.paperId(), fieldId, fieldTitle, c.text(), c.text(), c.chunkId(),
					c.sectionPath(), c.page(), v.legacyStatus(), verification, v.quoteStatus().name(),
					v.claimStatus().name());
		}
	}

	// ---------- V0.4 H1.2：Run 归属的证据登记（决策 2） ----------

	/**
	 * Run 路径的步骤证据登记：带 run_id / step_id / attempt_id 归属，evidence_status 初始为 CANDIDATE
	 * ——步骤提交（commitStep COMPLETED）时由调度器统一升级 ACCEPTED，过期 Attempt 的 CANDIDATE 证据对账时标
	 * REJECTED。 task_id 同时写入 runId 字符串，兼容现有 matrix API。
	 */
	public void registerRunStepEvidence(String runId, String topicId, String attemptId, String stepId,
			String fieldTitle, String researchText, List<KbCandidate> candidates) {
		List<ClaimEvidenceBinder.Claim> claims = ClaimEvidenceBinder.split(researchText);
		if (claims.isEmpty()) {
			claims = List.of(new ClaimEvidenceBinder.Claim("clm_empty", 0, researchText, List.of()));
		}
		for (ClaimEvidenceBinder.Claim claim : claims) {
			jdbc.update(
					"""
							INSERT INTO rp_evidence (task_id, run_id, attempt_id, step_id, topic_id, schema_field_id, field_title,
								value, origin, status, evidence_status, claim_id, claim_text, claim_index)
							VALUES (?, ?::uuid, ?::uuid, ?, ?::uuid, ?, ?, ?, 'SUMMARIZED', 'NEEDS_REVIEW', 'CANDIDATE', ?, ?, ?)
							""",
					runId, runId, attemptId, stepId, topicId, stepId, fieldTitle, claim.text(), claim.id(),
					claim.text(), claim.index());

			List<KbCandidate> scoped = claim.sourceIndexes().isEmpty() ? candidates
					: claim.sourceIndexes()
						.stream()
						.filter(i -> i >= 0 && i < candidates.size())
						.map(candidates::get)
						.toList();
			for (RpClaimVerifier.CandidateVerdict v : verifier.verify(claim.text(), scoped)) {
				KbCandidate c = v.candidate();
				String verification = verifier.verificationJson(v);
				// V1 UX：异构来源 —— chunkId 以 "web|" 前缀标识网页证据（URL 溯源），论文证据为 PAPER
				boolean isWeb = "WEB".equalsIgnoreCase(c.sourceType())
						|| (c.chunkId() != null && c.chunkId().startsWith("web|"));
				String sourceType = isWeb ? "WEB" : "PAPER";
				String sourceUrl = isWeb ? c.sourceUrl() : null;
				if (sourceUrl == null && isWeb && c.chunkId() != null && c.chunkId().startsWith("web|"))
					sourceUrl = c.chunkId().substring(4);
				if (!isWeb && c.paperId() != null) {
					var rows = jdbc.queryForList(
							"SELECT metadata->>'source_url' AS url FROM rp_papers WHERE id = ?::uuid AND metadata->>'source_type' = 'WEB'",
							c.paperId());
					if (!rows.isEmpty()) {
						sourceType = "WEB";
						sourceUrl = (String) rows.get(0).get("url");
					}
				}
				jdbc.update("""
						INSERT INTO rp_evidence (task_id, run_id, attempt_id, step_id, topic_id, paper_id,
							schema_field_id, field_title, value, supporting_quote, source_chunk_id, section_path, page,
							origin, status, verification, evidence_status, quote_status, claim_status,
							source_type, source_url, claim_id, claim_text, claim_index)
						VALUES (?, ?::uuid, ?::uuid, ?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?,
							'EXTRACTED', ?, ?, 'CANDIDATE', ?, ?, ?, ?, ?, ?, ?)
						""", runId, runId, attemptId, stepId, topicId, c.paperId(), stepId, fieldTitle, claim.text(),
						c.text(), c.chunkId(), c.sectionPath(), c.page(), v.legacyStatus(), verification,
						v.quoteStatus().name(), v.claimStatus().name(), sourceType, sourceUrl, claim.id(), claim.text(),
						claim.index());
			}
		}
	}

	/**
	 * 步骤提交成功后接纳该步骤全部 CANDIDATE 证据（决策 2：ACCEPTED 才进矩阵）。
	 */
	public void acceptRunStepEvidence(String runId, String stepId, String attemptId) {
		jdbc.update(
				"""
						UPDATE rp_evidence e SET evidence_status = 'ACCEPTED'
						WHERE e.run_id = ?::uuid AND e.step_id = ? AND e.attempt_id = ?::uuid AND e.evidence_status = 'CANDIDATE'
						  AND EXISTS (SELECT 1 FROM rp_run_steps s WHERE s.run_id = e.run_id
						    AND s.step_id = e.step_id AND s.active_attempt_id = e.attempt_id AND s.status = 'COMPLETED')
						""",
				runId, stepId, attemptId);
	}

	// ---------- 矩阵聚合 ----------

	/**
	 * 证据矩阵（Phase C 简版）：行 = 论文，列 = 研究字段（步骤）。 单元格 = 该论文在该字段下的全部证据（含状态）；无证据的论文×字段组合显式标
	 * NOT_FOUND。
	 */
	public Map<String, Object> buildMatrix(String taskId) {
		List<Map<String, Object>> rows = jdbc.queryForList("""
				SELECT e.paper_id::text AS paper_id, e.schema_field_id, e.field_title,
				       e.id::text AS evidence_id, e.value, e.status, e.origin,
				       e.document_version_id::text AS document_version_id, e.section_path, e.page, e.source_chunk_id,
				       e.verification, e.quote_status, e.claim_status, e.source_type, e.source_url
				FROM rp_evidence e
				WHERE e.task_id = ? AND e.paper_id IS NOT NULL
				  AND (e.run_id IS NULL OR e.evidence_status = 'ACCEPTED')
				ORDER BY e.schema_field_id, e.paper_id, e.created_at
				""", taskId);

		// V1 UX：网页补充证据（paper_id 为空、source_type=WEB）合成为 "__web__" 伪行，不与论文混排
		List<Map<String, Object>> webRows = jdbc.queryForList("""
				SELECT '__web__' AS paper_id, e.schema_field_id, e.field_title,
				       e.id::text AS evidence_id, e.value, e.status, e.origin,
				       e.document_version_id::text AS document_version_id, e.section_path, e.page, e.source_url,
				       e.quote_status, e.claim_status, e.source_type
				FROM rp_evidence e
				WHERE e.task_id = ? AND e.source_type = 'WEB' AND e.paper_id IS NULL
				  AND (e.run_id IS NULL OR e.evidence_status = 'ACCEPTED')
				ORDER BY e.schema_field_id, e.created_at
				""", taskId);

		List<Map<String, Object>> summaries = jdbc.queryForList("""
				SELECT schema_field_id, field_title, value FROM rp_evidence
				WHERE task_id = ? AND origin = 'SUMMARIZED'
				  AND (run_id IS NULL OR evidence_status = 'ACCEPTED')
				ORDER BY created_at
				""", taskId);

		// 组织：field -> paper -> evidence[]
		Map<String, Map<String, List<Map<String, Object>>>> matrix = new java.util.LinkedHashMap<>();
		Map<String, String> fieldTitles = new java.util.LinkedHashMap<>();
		Map<String, String> paperIds = new java.util.LinkedHashMap<>();
		for (Map<String, Object> row : rows) {
			String field = (String) row.get("schema_field_id");
			String paper = (String) row.get("paper_id");
			fieldTitles.put(field, String.valueOf(row.get("field_title")));
			paperIds.put(paper, paper);
			matrix.computeIfAbsent(field, k -> new java.util.LinkedHashMap<>())
				.computeIfAbsent(paper, k -> new ArrayList<>())
				.add(row);
		}
		if (!webRows.isEmpty()) {
			paperIds.put("__web__", "__web__");
			for (Map<String, Object> row : webRows) {
				String field = (String) row.get("schema_field_id");
				fieldTitles.putIfAbsent(field, String.valueOf(row.get("field_title")));
				matrix.computeIfAbsent(field, k -> new java.util.LinkedHashMap<>())
					.computeIfAbsent("__web__", k -> new ArrayList<>())
					.add(row);
			}
		}

		List<Map<String, Object>> cells = new ArrayList<>();
		for (var fieldEntry : matrix.entrySet()) {
			for (String paper : paperIds.keySet()) {
				List<Map<String, Object>> evs = fieldEntry.getValue().get(paper);
				Map<String, Object> cell = new HashMap<>();
				cell.put("field_id", fieldEntry.getKey());
				cell.put("field_title", fieldTitles.get(fieldEntry.getKey()));
				cell.put("paper_id", paper);
				if (evs == null || evs.isEmpty()) {
					// 诚实状态：该论文在此字段无证据 —— 不虚构
					cell.put("status", "NOT_FOUND");
					cell.put("evidence", List.of());
				}
				else {
					cell.put("evidence", evs);
					cell.put("status", evs.stream().anyMatch(e -> "SUPPORTED".equals(e.get("status"))) ? "SUPPORTED"
							: "NEEDS_REVIEW");
				}
				cells.add(cell);
			}
		}

		Map<String, Object> result = new HashMap<>();
		result.put("task_id", taskId);
		result.put("fields", fieldTitles);
		result.put("papers", paperIds.keySet());
		result.put("cells", cells);
		result.put("summaries", summaries);
		result.put("note", "矩阵证据状态为确定性引用校验（n-gram 重叠）+ 机器登记，未经人工审阅");
		return result;
	}

}
