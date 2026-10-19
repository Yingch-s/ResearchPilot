package com.alibaba.cloud.ai.example.deepresearch.researchpilot.run;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RunListingService {

	private final JdbcTemplate jdbc;

	public RunListingService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Map<String, Object> page(UUID ownerId, UUID topicId, int limit, OffsetDateTime beforeTime, UUID beforeId,
			boolean activeOnly) {
		if (limit < 1 || limit > 100 || (beforeTime == null) != (beforeId == null)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页数量应为 1–100，游标必须包含时间和任务 ID");
		}
		// P0-A：归属过滤（off 模式 ownerId=admin，存量数据已归属 admin，行为不变）
		List<Map<String, Object>> rows = jdbc.queryForList("""
				SELECT id::text, topic_id::text, session_id, thread_id, query, status, execution_cursor,
				  attempt, created_at, started_at, finished_at, pause_requested
				FROM rp_runs
				WHERE owner_user_id = ?
				  AND (?::uuid IS NULL OR topic_id = ?::uuid)
				  AND (NOT ? OR status NOT IN ('COMPLETED', 'PARTIAL_COMPLETED', 'FAILED', 'CANCELLED'))
				  AND (?::timestamptz IS NULL OR (created_at, id) < (?::timestamptz, ?::uuid))
				ORDER BY created_at DESC, id DESC LIMIT ?
				""", ownerId, topicId, topicId, activeOnly, beforeTime, beforeTime, beforeId, limit + 1);
		boolean hasMore = rows.size() > limit;
		List<Map<String, Object>> items = hasMore ? rows.subList(0, limit) : rows;
		Map<String, Object> cursor = Map.of();
		if (hasMore) {
			Map<String, Object> last = items.get(items.size() - 1);
			cursor = Map.of("before_time", cursorTime(last.get("created_at")), "before_id", last.get("id"));
		}
		return Map.of("items", items, "has_more", hasMore, "next_cursor", cursor);
	}

	private String cursorTime(Object value) {
		if (value instanceof java.time.OffsetDateTime offset) {
			return offset.toString();
		}
		if (value instanceof java.sql.Timestamp timestamp) {
			return timestamp.toInstant().toString();
		}
		return java.time.OffsetDateTime.parse(value.toString().replace(' ', 'T') + "Z").toString();
	}

}
