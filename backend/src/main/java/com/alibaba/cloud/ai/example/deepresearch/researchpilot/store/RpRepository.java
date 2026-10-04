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

package com.alibaba.cloud.ai.example.deepresearch.researchpilot.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * ResearchPilot Phase B：业务真值仓储（JdbcTemplate + 显式 SQL）。
 *
 * <p>
 * 设计平移自 Phase 1 Python repository.py：租约认领（SKIP LOCKED）、哈希去重、 状态机（UPLOADED -> PARSING ->
 * READY/PARTIAL/FAILED）语义保持一致。
 */
@Repository
public class RpRepository {

	private final JdbcTemplate jdbc;

	public RpRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public class DuplicateDocumentException extends RuntimeException {

		public DuplicateDocumentException(String message) {
			super(message);
		}

	}

	// ---------- Topic ----------

	public Map<String, Object> createTopic(String name, String researchGoal) {
		var id = jdbc.queryForObject("INSERT INTO rp_topics (name, research_goal) VALUES (?, ?) RETURNING id",
				UUID.class, name, researchGoal);
		return Map.of("id", id.toString(), "name", name, "research_goal", researchGoal == null ? "" : researchGoal);
	}

	public List<Map<String, Object>> listTopics() {
		return listTopics(false);
	}

	public List<Map<String, Object>> listTopics(boolean archived) {
		return jdbc.queryForList("""
				SELECT t.id::text, t.name, t.research_goal, t.archived_at,
				       (SELECT count(*) FROM rp_papers p WHERE p.topic_id = t.id AND p.status = 'ACTIVE') AS paper_count
				FROM rp_topics t WHERE (t.archived_at IS NOT NULL) = ? ORDER BY t.created_at
				""", archived);
	}

	public boolean updateTopic(UUID topicId, String name, String researchGoal) {
		return jdbc.update("UPDATE rp_topics SET name = ?, research_goal = ? WHERE id = ? AND archived_at IS NULL",
				name, researchGoal, topicId) == 1;
	}

	public boolean archiveTopic(UUID topicId) {
		return jdbc.update("UPDATE rp_topics SET archived_at = now() WHERE id = ? AND archived_at IS NULL",
				topicId) == 1;
	}

	public boolean restoreTopic(UUID topicId) {
		return jdbc.update("UPDATE rp_topics SET archived_at = NULL WHERE id = ? AND archived_at IS NOT NULL",
				topicId) == 1;
	}

	@Transactional
	public boolean deleteEmptyTopic(UUID topicId) {
		if (jdbc.queryForList("SELECT id FROM rp_topics WHERE id = ? FOR UPDATE", topicId).isEmpty()) {
			return false;
		}
		Integer papers = jdbc.queryForObject("SELECT count(*) FROM rp_papers WHERE topic_id = ? AND status = 'ACTIVE'",
				Integer.class, topicId);
		Integer runs = jdbc.queryForObject("SELECT count(*) FROM rp_runs WHERE topic_id = ?", Integer.class, topicId);
		if (papers != null && papers > 0 || runs != null && runs > 0) {
			return false;
		}
		return jdbc.update("DELETE FROM rp_topics WHERE id = ?", topicId) == 1;
	}

	// ---------- 上传：Paper + Version + Job 单事务 ----------

	@Transactional
	public void saveSectionContexts(String versionId,
			List<com.alibaba.cloud.ai.example.deepresearch.researchpilot.ingest.RpChunker.ParentChild.Parent> parents) {
		if (jdbc.queryForList("SELECT id FROM rp_document_versions WHERE id = ?::uuid FOR UPDATE", versionId).isEmpty())
			throw new IllegalArgumentException("文档版本不存在");
		jdbc.update("DELETE FROM rp_section_contexts WHERE document_version_id = ?::uuid", versionId);
		for (var parent : parents) {
			jdbc.update(
					"INSERT INTO rp_section_contexts(parent_id, document_version_id, section_path, content) VALUES (?, ?::uuid, ?, ?)",
					parent.parentId(), versionId, parent.sectionPath(), parent.text());
		}
		jdbc.update("UPDATE rp_document_versions SET section_context_ready = TRUE WHERE id = ?::uuid", versionId);
	}

	@Transactional
	public Map<String, Object> createPaperWithVersion(String topicId, String title, String sha256, String objectKey,
			long fileSize) {
		if (jdbc.queryForList("SELECT id FROM rp_topics WHERE id = ?::uuid FOR UPDATE", topicId).isEmpty()) {
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.NOT_FOUND, "专题不存在");
		}
		Boolean dupInTopic = jdbc.queryForObject("""
				SELECT EXISTS(SELECT 1 FROM rp_document_versions dv
					JOIN rp_papers p ON p.id = dv.paper_id
					WHERE p.topic_id = ?::uuid AND dv.file_sha256 = ? AND p.status = 'ACTIVE')
				""", Boolean.class, UUID.fromString(topicId), sha256);
		if (Boolean.TRUE.equals(dupInTopic)) {
			throw new DuplicateDocumentException("本专题已存在相同文件（哈希一致）");
		}

		String dupGlobal = jdbc.queryForObject("""
				SELECT COALESCE((SELECT p.title FROM rp_document_versions dv
					JOIN rp_papers p ON p.id = dv.paper_id
					WHERE dv.file_sha256 = ? AND p.topic_id <> ?::uuid AND p.status = 'ACTIVE' LIMIT 1), '')
				""", String.class, sha256, UUID.fromString(topicId));

		UUID paperId = jdbc.queryForObject("INSERT INTO rp_papers (topic_id, title) VALUES (?::uuid, ?) RETURNING id",
				UUID.class, UUID.fromString(topicId), title);
		UUID versionId = jdbc.queryForObject("""
				INSERT INTO rp_document_versions (paper_id, version, file_sha256, object_key, file_size)
				VALUES (?, 1, ?, ?, ?) RETURNING id
				""", UUID.class, paperId, sha256, objectKey, fileSize);
		UUID jobId = jdbc.queryForObject("INSERT INTO rp_ingestion_jobs (document_version_id) VALUES (?) RETURNING id",
				UUID.class, versionId);

		Map<String, Object> result = new HashMap<>();
		result.put("paper_id", paperId.toString());
		result.put("document_version_id", versionId.toString());
		result.put("job_id", jobId.toString());
		result.put("warning", dupGlobal.isEmpty() ? null : "疑似与其他专题文献《" + dupGlobal + "》为同一文件");
		return result;
	}

	public List<Map<String, Object>> listPapers(String topicId) {
		// V1 修复：quality_flags 为 PG TEXT[]，JDBC Array 无法被 JSON 序列化（列表接口 500、
		// 前端轮询永远失败——上传进度不可见的根因）；改 array_to_string 输出，
		// 并带出最新摄取 stage（PARSING/CHUNKING/EMBEDDING/INDEXING）供进度条使用。
		return jdbc.queryForList(
				"""
						SELECT p.id::text AS id, p.title, p.created_at, p.metadata::text AS metadata, p.status AS paper_status, dv.parse_status,
						       array_to_string(dv.quality_flags, ',') AS quality_flags,
						       dv.file_size, dv.parser_version, dv.chunk_count,
						       latest_job.stage AS ingest_stage, latest_job.last_error,
						       latest_job.status AS job_status, dv.id::text AS version_id
						FROM rp_papers p
						JOIN rp_document_versions dv ON dv.id = (
							SELECT id FROM rp_document_versions v
							WHERE v.paper_id = p.id ORDER BY v.version DESC LIMIT 1)
						LEFT JOIN LATERAL (
							SELECT stage, status, last_error FROM rp_ingestion_jobs j
							WHERE j.document_version_id = dv.id ORDER BY j.created_at DESC LIMIT 1
						) latest_job ON true
						WHERE p.topic_id = ?::uuid AND p.status = 'ACTIVE'
						ORDER BY p.created_at
						""",
				topicId);
	}

	/**
	 * H5/F1：论文详情（最新版本）—— 论文阅读器与文件下载的数据源。object_key 不出服务层。
	 */
	public Map<String, Object> paperDetail(String paperId) {
		List<Map<String, Object>> rows = jdbc.queryForList(
				"""
						SELECT p.id::text AS paper_id, p.title, p.metadata::text AS metadata, dv.id::text AS version_id, dv.parse_status,
						       dv.chunk_count,
						       COALESCE((SELECT left(sc.content, 6000) FROM rp_section_contexts sc
						        JOIN rp_document_versions av ON av.id = sc.document_version_id
						        WHERE av.paper_id = p.id AND av.parse_status = 'READY'
						          AND lower(sc.section_path) LIKE '%abstract%'
						        ORDER BY length(sc.content) DESC LIMIT 1), '') AS abstract_text,
						       lower(reverse(split_part(reverse(dv.object_key), '.', 1))) AS file_ext
						FROM rp_papers p
						JOIN rp_document_versions dv ON dv.id = (
							SELECT id FROM rp_document_versions v
							WHERE v.paper_id = p.id ORDER BY v.version DESC LIMIT 1)
						WHERE p.id = ?::uuid AND p.status = 'ACTIVE'
						""",
				paperId);
		return rows.isEmpty() ? null : rows.get(0);
	}

	public boolean softDeletePaper(String paperId) {
		return jdbc.update("UPDATE rp_papers SET status = 'REMOVED' WHERE id = ?::uuid AND status = 'ACTIVE'",
				paperId) > 0;
	}

	public boolean updatePaper(String paperId, String title, String metadataJson) {
		return jdbc.update(
				"UPDATE rp_papers SET title = ?, metadata = ?::jsonb WHERE id = ?::uuid AND status = 'ACTIVE'", title,
				metadataJson == null ? "{}" : metadataJson, paperId) == 1;
	}

	@Transactional
	public Map<String, Object> retryPaper(String paperId) {
		List<Map<String, Object>> versions = jdbc.queryForList("""
				SELECT dv.id FROM rp_document_versions dv JOIN rp_papers p ON p.id = dv.paper_id
				WHERE p.id = ?::uuid AND p.status = 'ACTIVE'
				ORDER BY dv.version DESC LIMIT 1 FOR UPDATE OF dv
				""", paperId);
		if (versions.isEmpty()) {
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.NOT_FOUND, "资料不存在");
		}
		Object versionId = versions.get(0).get("id");
		List<Map<String, Object>> active = jdbc.queryForList("""
				SELECT id::text AS job_id FROM rp_ingestion_jobs
				WHERE document_version_id = ? AND status IN ('QUEUED', 'RUNNING')
				ORDER BY created_at DESC LIMIT 1
				""", versionId);
		if (!active.isEmpty()) {
			return Map.of("job_id", active.get(0).get("job_id"), "paper_id", paperId, "idempotent", true);
		}
		String status = jdbc.queryForObject("SELECT parse_status FROM rp_document_versions WHERE id = ?", String.class,
				versionId);
		if (!List.of("FAILED", "PARTIAL").contains(status)) {
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.CONFLICT, "仅失败或部分解析的资料可以重试");
		}
		UUID jobId = jdbc.queryForObject("INSERT INTO rp_ingestion_jobs (document_version_id) VALUES (?) RETURNING id",
				UUID.class, versionId);
		jdbc.update("UPDATE rp_document_versions SET parse_status = 'UPLOADED' WHERE id = ?", versionId);
		return Map.of("job_id", jobId.toString(), "paper_id", paperId, "idempotent", false);
	}

	// ---------- Job 租约认领（SKIP LOCKED）与状态推进 ----------

	/**
	 * 多 Worker 并发安全认领：QUEUED 或租约过期的任务；返回 null 表示当前无任务。
	 */
	public Map<String, Object> claimQueuedJob(String workerId, int leaseSeconds) {
		List<Map<String, Object>> rows = jdbc.queryForList("""
				UPDATE rp_ingestion_jobs SET
					status = 'RUNNING', attempts = attempts + 1,
					lease_until = now() + make_interval(secs => ?), worker_id = ?, updated_at = now()
				WHERE id = (
					SELECT id FROM rp_ingestion_jobs
					WHERE status = 'QUEUED' OR (status = 'RUNNING' AND lease_until < now())
					ORDER BY created_at
					FOR UPDATE SKIP LOCKED LIMIT 1
				)
				RETURNING id::text, document_version_id::text, stage, attempts, max_attempts
				""", leaseSeconds, workerId);
		return rows.isEmpty() ? null : rows.get(0);
	}

	public record VersionRef(UUID versionId, String objectKey) {
	}

	public record ChunkScope(String topicId, String paperId) {
	}

	/**
	 * 摄取时的 Scope 信息（ES 文档冗余存储，检索层 Scope 过滤的依据）。
	 */
	public ChunkScope getScope(String versionId) {
		return jdbc.queryForObject("""
				SELECT p.topic_id::text, p.id::text FROM rp_document_versions dv
				JOIN rp_papers p ON p.id = dv.paper_id WHERE dv.id = ?::uuid
				""", (rs, n) -> new ChunkScope(rs.getString(1), rs.getString(2)), versionId);
	}

	public Optional<VersionRef> getVersionObjectKey(String versionId) {
		return jdbc.query("""
				SELECT id, object_key FROM rp_document_versions WHERE id = ?::uuid
				""", rs -> {
			if (!rs.next()) {
				return Optional.empty();
			}
			return Optional.of(new VersionRef(UUID.fromString(rs.getString("id")), rs.getString("object_key")));
		}, versionId);
	}

	public String parseCheckpoint(String versionId) {
		return jdbc.queryForObject("SELECT normalized_object_key FROM rp_document_versions WHERE id = ?::uuid",
				String.class, versionId);
	}

	public void saveParseCheckpoint(String versionId, String key, String parserVersion, String[] flags) {
		jdbc.update("""
				UPDATE rp_document_versions SET normalized_object_key = ?, parser_version = ?, quality_flags = ?
				WHERE id = ?::uuid
				""", key, parserVersion, flags, versionId);
	}

	public void setStage(String jobId, String versionId, String stage, String parseStatus) {
		jdbc.update("UPDATE rp_ingestion_jobs SET stage = ?, updated_at = now() WHERE id = ?::uuid", stage, jobId);
		jdbc.update("UPDATE rp_document_versions SET parse_status = ? WHERE id = ?::uuid", parseStatus, versionId);
	}

	public void requeue(String jobId, String versionId, String error) {
		// H4：RETRYABLE_FAILED 可见于 job.stage —— 未耗尽重试次数的失败与终态失败明确区分
		jdbc.update("UPDATE rp_ingestion_jobs SET status = 'QUEUED', stage = 'RETRYABLE_FAILED', last_error = ?, "
				+ "updated_at = now() WHERE id = ?::uuid", error, jobId);
		jdbc.update("UPDATE rp_document_versions SET parse_status = 'UPLOADED' WHERE id = ?::uuid", versionId);
	}

	public void finishJob(String jobId, String versionId, boolean success, String stage, String parseStatus,
			String error, String parserVersion, String normalizedKey, String[] qualityFlags, int chunkCount) {
		jdbc.update(
				"UPDATE rp_ingestion_jobs SET status = ?, stage = ?, last_error = ?, updated_at = now() WHERE id = ?::uuid",
				success ? "SUCCEEDED" : "FAILED", stage, error, jobId);
		jdbc.update("""
				UPDATE rp_document_versions SET parse_status = ?, parser_version = COALESCE(?, parser_version),
					normalized_object_key = COALESCE(?, normalized_object_key),
					quality_flags = CASE WHEN ? THEN ? ELSE quality_flags END, chunk_count = ? WHERE id = ?::uuid
				""", parseStatus, parserVersion, normalizedKey, success, qualityFlags, chunkCount, versionId);
	}

	public Map<String, Object> getJob(String jobId) {
		List<Map<String, Object>> rows = jdbc.queryForList("""
				SELECT j.id::text, j.status, j.stage, j.attempts, j.max_attempts, j.last_error,
				       dv.parse_status, dv.quality_flags, p.title
				FROM rp_ingestion_jobs j
				JOIN rp_document_versions dv ON dv.id = j.document_version_id
				JOIN rp_papers p ON p.id = dv.paper_id
				WHERE j.id = ?::uuid
				""", jobId);
		if (rows.isEmpty()) {
			return null;
		}
		Map<String, Object> row = rows.get(0);
		Map<String, Object> out = new HashMap<>();
		out.put("id", row.get("id"));
		out.put("status", row.get("status"));
		out.put("stage", row.get("stage"));
		out.put("attempts", row.get("attempts"));
		out.put("max_attempts", row.get("max_attempts"));
		out.put("last_error", row.get("last_error"));
		out.put("parse_status", row.get("parse_status"));
		Object flags = row.get("quality_flags");
		try {
			if (flags instanceof java.sql.Array arr) {
				out.put("quality_flags", java.util.Arrays.asList((String[]) arr.getArray()));
			}
			else {
				out.put("quality_flags", List.of());
			}
		}
		catch (java.sql.SQLException e) {
			out.put("quality_flags", List.of());
		}
		out.put("paper_title", row.get("title"));
		return out;
	}

}
