package com.alibaba.cloud.ai.example.deepresearch.researchpilot.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentPreviewService {

	private final JdbcTemplate jdbc;

	private final RpObjectStore store;

	public DocumentPreviewService(JdbcTemplate jdbc, RpObjectStore store) {
		this.jdbc = jdbc;
		this.store = store;
	}

	public Map<String, Object> preview(UUID versionId, int offset) throws java.io.IOException {
		if (offset < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "预览位置无效");
		}
		var rows = jdbc.queryForList("""
				SELECT dv.normalized_object_key FROM rp_document_versions dv
				JOIN rp_papers p ON p.id = dv.paper_id WHERE dv.id = ? AND p.status = 'ACTIVE'
				""", versionId);
		if (rows.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "资料不存在或已移除");
		}
		Object key = rows.get(0).get("normalized_object_key");
		if (key == null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "资料尚无解析文本，请下载原文件");
		}
		var normalized = new com.fasterxml.jackson.databind.ObjectMapper().readTree(store.get(key.toString()));
		var blocks = normalized.path("blocks");
		var result = new ArrayList<Map<String, Object>>();
		for (int i = offset; i < Math.min(blocks.size(), (long) offset + 100); i++) {
			var block = blocks.get(i);
			result.add(Map.of("text", block.path("text").asText(""), "page", block.path("page").asInt(0),
					"section_path", block.path("section_path").asText("")));
		}
		return Map.of("blocks", result, "has_more", (long) offset + result.size() < blocks.size(), "total_blocks",
				blocks.size());
	}

	public static String mime(String key) {
		String ext = key.substring(key.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT);
		return switch (ext) {
			case "pdf" -> "application/pdf";
			case "png" -> "image/png";
			case "jpg", "jpeg" -> "image/jpeg";
			case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
			case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
			case "html", "htm" -> "application/octet-stream";
			case "txt", "md", "markdown" -> "text/plain; charset=utf-8";
			default -> "application/octet-stream";
		};
	}

}
