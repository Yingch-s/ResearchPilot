package com.alibaba.cloud.ai.example.deepresearch.researchpilot.run;

import com.alibaba.cloud.ai.example.deepresearch.researchpilot.retrieve.RpRetrievalService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;

/** Durable, query-fingerprinted work products for resumable research steps. */
@Service
public class RpStepArtifactService {

	private final JdbcTemplate jdbc;

	private final ObjectMapper mapper;

	public RpStepArtifactService(JdbcTemplate jdbc, ObjectMapper mapper) {
		this.jdbc = jdbc;
		this.mapper = mapper;
	}

	public void saveRetrieval(UUID runId, int stepIndex, UUID attemptId, UUID topicId, List<String> scope, String query,
			List<RpRetrievalService.Candidate> candidates) {
		try {
			String fingerprint = fingerprint(query);
			String payload = mapper.writeValueAsString(Map.of("query", query, "candidates", candidates));
			jdbc.update("""
					INSERT INTO rp_run_step_artifacts (run_id, step_index, attempt_id, kind, query_fingerprint, payload)
					VALUES (?, ?, ?, 'RETRIEVAL', ?, ?::jsonb)
					ON CONFLICT (run_id, step_index, kind, query_fingerprint)
					DO UPDATE SET attempt_id = EXCLUDED.attempt_id, payload = EXCLUDED.payload, created_at = now()
					""", runId, stepIndex, attemptId, fingerprint, payload);
			if (topicId != null) {
				jdbc.update(
						"""
								INSERT INTO rp_research_artifact_cache (topic_id, kind, query_fingerprint, scope_fingerprint, payload)
								VALUES (?, 'RETRIEVAL', ?, ?, ?::jsonb)
								ON CONFLICT (topic_id, kind, query_fingerprint, scope_fingerprint)
								DO UPDATE SET payload = EXCLUDED.payload, created_at = now()
								""",
						topicId, fingerprint, scopeFingerprint(scope), payload);
			}
		}
		catch (Exception e) {
			throw new IllegalStateException("检索检查点保存失败", e);
		}
	}

	public List<RpRetrievalService.Candidate> loadRetrieval(UUID runId, int stepIndex, UUID topicId, List<String> scope,
			String query) {
		List<String> payloads = jdbc.query("""
				SELECT payload::text FROM rp_run_step_artifacts
				WHERE run_id = ? AND step_index = ? AND kind = 'RETRIEVAL' AND query_fingerprint = ?
				ORDER BY created_at DESC LIMIT 1
				""", (rs, rowNum) -> rs.getString(1), runId, stepIndex, fingerprint(query));
		if (payloads.isEmpty() && topicId != null) {
			payloads = jdbc.query("""
					SELECT payload::text FROM rp_research_artifact_cache
					WHERE topic_id = ? AND kind = 'RETRIEVAL' AND query_fingerprint = ? AND scope_fingerprint = ?
					ORDER BY created_at DESC LIMIT 1
					""", (rs, rowNum) -> rs.getString(1), topicId, fingerprint(query), scopeFingerprint(scope));
		}
		if (payloads.isEmpty())
			return List.of();
		try {
			return mapper.readTree(payloads.get(0))
				.path("candidates")
				.traverse(mapper)
				.readValueAs(new TypeReference<List<RpRetrievalService.Candidate>>() {
				});
		}
		catch (Exception e) {
			return List.of();
		}
	}

	public void saveSummary(UUID runId, int stepIndex, UUID attemptId, String query, String summary) {
		try {
			String payload = mapper
				.writeValueAsString(Map.of("query", query, "summary", summary == null ? "" : summary));
			jdbc.update("""
					INSERT INTO rp_run_step_artifacts (run_id, step_index, attempt_id, kind, query_fingerprint, payload)
					VALUES (?, ?, ?, 'SUMMARY', ?, ?::jsonb)
					ON CONFLICT (run_id, step_index, kind, query_fingerprint)
					DO UPDATE SET attempt_id = EXCLUDED.attempt_id, payload = EXCLUDED.payload, created_at = now()
					""", runId, stepIndex, attemptId, fingerprint(query), payload);
		}
		catch (Exception e) {
			throw new IllegalStateException("归纳检查点保存失败", e);
		}
	}

	public List<Map<String, Object>> list(UUID runId, int stepIndex) {
		return jdbc.queryForList("""
				SELECT kind, query_fingerprint, attempt_id::text AS attempt_id, created_at
				FROM rp_run_step_artifacts WHERE run_id = ? AND step_index = ? ORDER BY created_at DESC
				""", runId, stepIndex);
	}

	private String fingerprint(String query) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of()
				.formatHex(digest.digest((query == null ? "" : query).getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception e) {
			throw new IllegalStateException("检查点指纹生成失败", e);
		}
	}

	private String scopeFingerprint(List<String> scope) {
		List<String> normalized = new ArrayList<>(scope == null ? List.of() : scope);
		normalized.sort(Comparator.naturalOrder());
		return fingerprint(String.join("\n", normalized));
	}

}
