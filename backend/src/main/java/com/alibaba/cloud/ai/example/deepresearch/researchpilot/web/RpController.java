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

import com.alibaba.cloud.ai.example.deepresearch.researchpilot.retrieve.RpRetrievalService;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.store.RpObjectStore;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.store.RpRepository;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.sidecar.SidecarClient;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.step.DeterministicReportComposer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Set;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ResearchPilot Phase B：论文知识库 API（专题 / 上传 / 目录 / 任务状态）。
 *
 * <p>
 * 202 而非 201：摄取异步执行，此处只代表任务受理。
 */
@CrossOrigin(origins = "*")
@RestController
public class RpController {

	private static final Logger logger = LoggerFactory.getLogger(RpController.class);

	private static final long MAX_FILE_SIZE = 100 * 1024 * 1024;

	private static final int UPLOAD_CHUNK_SIZE = 4 * 1024 * 1024;

	private final RpRepository repo;

	private final RpObjectStore objectStore;

	private final RpRetrievalService retrieval;

	private final com.alibaba.cloud.ai.example.deepresearch.researchpilot.evidence.RpEvidenceService evidence;

	private final org.springframework.jdbc.core.JdbcTemplate jdbc;

	private final SidecarClient sidecar;

	private final java.util.concurrent.ExecutorService askStreamExecutor = java.util.concurrent.Executors
		.newCachedThreadPool(r -> {
			Thread thread = new Thread(r, "rp-ask-stream");
			thread.setDaemon(true);
			return thread;
		});

	private final ConcurrentHashMap<String, Future<?>> activeAskStreams = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<String, Object> askStreamLocks = new ConcurrentHashMap<>();

	/** The stream owns persistence and only commits after its complete event. */
	private final ThreadLocal<Boolean> suppressAskPersistence = ThreadLocal.withInitial(() -> false);

	public RpController(RpRepository repo, RpObjectStore objectStore, RpRetrievalService retrieval,
			com.alibaba.cloud.ai.example.deepresearch.researchpilot.evidence.RpEvidenceService evidence,
			org.springframework.jdbc.core.JdbcTemplate jdbc, SidecarClient sidecar) {
		this.repo = repo;
		this.objectStore = objectStore;
		this.retrieval = retrieval;
		this.evidence = evidence;
		this.jdbc = jdbc;
		this.sidecar = sidecar;
	}

	// V1 UX-1：快速提问服务
	@org.springframework.beans.factory.annotation.Autowired
	private transient com.alibaba.cloud.ai.example.deepresearch.researchpilot.ask.RpAskService askService;

	@org.springframework.beans.factory.annotation.Autowired
	private transient com.alibaba.cloud.ai.example.deepresearch.researchpilot.source.PaperDiscoveryService paperDiscovery;

	@org.springframework.beans.factory.annotation.Autowired
	private com.alibaba.cloud.ai.example.deepresearch.researchpilot.ask.ConversationContextService conversationContext;

	@org.springframework.beans.factory.annotation.Autowired
	private com.alibaba.cloud.ai.example.deepresearch.researchpilot.store.DocumentPreviewService documentPreview;

	@org.springframework.beans.factory.annotation.Autowired
	private transient com.alibaba.cloud.ai.example.deepresearch.researchpilot.ResearchPilotConfig.ResearchBudgetProperties researchBudget = new com.alibaba.cloud.ai.example.deepresearch.researchpilot.ResearchPilotConfig.ResearchBudgetProperties(
			1200, 12, 2);

	// ---------- Topic ----------

	@PostMapping("/api/rp/topics")
	public Map<String, Object> createTopic(@RequestParam String name,
			@RequestParam(required = false) String researchGoal) {
		return repo.createTopic(name, researchGoal);
	}

	public record TopicUpdate(String name, String research_goal) {
	}

	@org.springframework.web.bind.annotation.PatchMapping("/api/rp/topics/{topicId}")
	public ResponseEntity<?> updateTopic(@PathVariable String topicId,
			@org.springframework.web.bind.annotation.RequestBody TopicUpdate req) {
		if (req.name() == null || req.name().isBlank() || req.name().length() > 160) {
			return ResponseEntity.badRequest()
				.body(Map.of("code", "INVALID_TOPIC", "message", "专题名称不能为空且不能超过 160 个字符"));
		}
		boolean updated = repo.updateTopic(UUID.fromString(topicId), req.name().trim(), req.research_goal());
		return updated ? ResponseEntity.ok(Map.of("updated", true)) : ResponseEntity.notFound().build();
	}

	@PostMapping("/api/rp/topics/{topicId}/archive")
	public ResponseEntity<?> archiveTopic(@PathVariable String topicId) {
		return repo.archiveTopic(UUID.fromString(topicId)) ? ResponseEntity.ok(Map.of("archived", true))
				: ResponseEntity.notFound().build();
	}

	@PostMapping("/api/rp/topics/{topicId}/restore")
	public ResponseEntity<?> restoreTopic(@PathVariable UUID topicId) {
		return repo.restoreTopic(topicId) ? ResponseEntity.ok(Map.of("restored", true))
				: ResponseEntity.notFound().build();
	}

	@DeleteMapping("/api/rp/topics/{topicId}")
	public ResponseEntity<?> deleteTopic(@PathVariable String topicId) {
		if (repo.deleteEmptyTopic(UUID.fromString(topicId))) {
			return ResponseEntity.ok(Map.of("deleted", true));
		}
		return error(HttpStatus.CONFLICT, "TOPIC_NOT_EMPTY", "专题仍有资料或研究记录，请归档以保留成果", false);
	}

	@GetMapping("/api/rp/topics")
	public List<Map<String, Object>> listTopics(@RequestParam(defaultValue = "false") boolean archived) {
		return repo.listTopics(archived);
	}

	@GetMapping("/api/rp/topics/{topicId}/artifacts")
	public List<Map<String, Object>> listSavedArtifacts(@PathVariable UUID topicId) {
		return jdbc.queryForList("""
				SELECT id, run_id, kind, title, created_at
				FROM rp_saved_artifacts WHERE topic_id = ? ORDER BY created_at DESC
				""", topicId);
	}

	public record SavedArtifactRequest(UUID run_id, String kind, String title) {
	}

	@PostMapping("/api/rp/topics/{topicId}/artifacts")
	public ResponseEntity<?> saveArtifact(@PathVariable UUID topicId, @RequestBody SavedArtifactRequest req) {
		if (req == null || req.run_id() == null || req.kind() == null
				|| !Set.of("REPORT", "MATRIX").contains(req.kind().trim().toUpperCase())) {
			return error(HttpStatus.BAD_REQUEST, "INVALID_ARTIFACT", "成果类型或研究任务无效", false);
		}
		String title = req.title() == null || req.title().isBlank() ? "研究成果" : req.title().trim();
		if (title.length() > 200) {
			return error(HttpStatus.BAD_REQUEST, "INVALID_ARTIFACT", "成果标题不能超过 200 个字符", false);
		}
		Integer owned = jdbc.queryForObject("SELECT count(*) FROM rp_runs WHERE id = ? AND topic_id = ?", Integer.class,
				req.run_id(), topicId);
		if (owned == null || owned == 0) {
			return error(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "研究任务不存在", false);
		}
		UUID id = jdbc.queryForObject("""
				INSERT INTO rp_saved_artifacts(topic_id, run_id, kind, title) VALUES (?, ?, ?, ?)
				ON CONFLICT (topic_id, run_id, kind) DO UPDATE SET title = EXCLUDED.title
				RETURNING id
				""", UUID.class, topicId, req.run_id(), req.kind().trim().toUpperCase(), title);
		return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id, "saved", true));
	}

	@DeleteMapping("/api/rp/topics/{topicId}/artifacts/{artifactId}")
	public ResponseEntity<?> deleteArtifact(@PathVariable UUID topicId, @PathVariable UUID artifactId) {
		int deleted = jdbc.update("DELETE FROM rp_saved_artifacts WHERE id = ? AND topic_id = ?", artifactId, topicId);
		return deleted == 0 ? ResponseEntity.notFound().build() : ResponseEntity.ok(Map.of("deleted", true));
	}

	@GetMapping("/api/rp/health")
	public Map<String, String> health() {
		Map<String, String> out = new HashMap<>();
		try {
			jdbc.queryForObject("SELECT 1", Integer.class);
			out.put("database", "ok");
		}
		catch (Exception e) {
			out.put("database", "error");
		}
		out.put("retrieval", retrieval.healthy() ? "ok" : "error");
		out.put("sidecar", sidecar.healthy() ? "ok" : "error");
		out.put("budget_timeout_seconds", Long.toString(researchBudget.globalTimeoutSeconds()));
		out.put("budget_max_loop_visits", Integer.toString(researchBudget.maxLoopVisits()));
		out.put("budget_no_progress_rounds", Integer.toString(researchBudget.noProgressRounds()));
		return out;
	}

	public record WebSnapshotRequest(String url) {
		public WebSnapshotRequest(String url, String ignored) {
			this(url);
		}
	}

	@GetMapping("/api/rp/topics/{topicId}/web-snapshots")
	public List<Map<String, Object>> webSnapshots(@PathVariable UUID topicId) {
		return jdbc.queryForList(
				"SELECT id::text, paper_id::text, url, final_url, title, content_type, content_sha256, fetched_at, length(extracted_text) AS content_length FROM rp_web_snapshots WHERE topic_id = ? ORDER BY fetched_at DESC",
				topicId);
	}

	@GetMapping("/api/rp/web-snapshots/{snapshotId}")
	public ResponseEntity<?> webSnapshot(@PathVariable UUID snapshotId) {
		List<Map<String, Object>> rows = jdbc.queryForList(
				"SELECT id::text, topic_id::text, paper_id::text, url, final_url, title, content_type, extracted_text AS content, content_sha256, fetched_at FROM rp_web_snapshots WHERE id = ?",
				snapshotId);
		return rows.isEmpty() ? error(HttpStatus.NOT_FOUND, "SNAPSHOT_NOT_FOUND", "网页快照不存在", false)
				: ResponseEntity.ok(rows.get(0));
	}

	@org.springframework.beans.factory.annotation.Autowired
	private com.alibaba.cloud.ai.example.deepresearch.researchpilot.store.WebSourceFetcher webFetcher;

	@PostMapping("/api/rp/topics/{topicId}/web-snapshots")
	@org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
	public ResponseEntity<?> saveWebSnapshot(@PathVariable UUID topicId, @RequestBody WebSnapshotRequest req)
			throws Exception {
		if (req.url() == null || req.url().isBlank())
			return error(HttpStatus.BAD_REQUEST, "INVALID_URL", "URL 不能为空", false);
		var snapshot = webFetcher.fetch(req.url());
		byte[] contentBytes = snapshot.raw().getBytes(java.nio.charset.StandardCharsets.UTF_8);
		String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contentBytes));
		if (jdbc.queryForList("SELECT id FROM rp_topics WHERE id = ? FOR UPDATE", topicId).isEmpty())
			return error(HttpStatus.NOT_FOUND, "TOPIC_NOT_FOUND", "专题不存在", false);
		var existing = jdbc.queryForList(
				"SELECT id::text, paper_id::text, title, url, final_url, fetched_at FROM rp_web_snapshots WHERE topic_id = ? AND url = ? AND content_sha256 = ?",
				topicId, req.url().trim(), hash);
		if (!existing.isEmpty() && existing.get(0).get("paper_id") != null)
			return ResponseEntity.ok(existing.get(0));
		UUID id = existing.isEmpty() ? UUID.randomUUID() : UUID.fromString(existing.get(0).get("id").toString());
		byte[] text = ("# " + snapshot.title() + "\n\n来源：" + snapshot.finalUrl() + "\n\n" + snapshot.text())
			.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		String textHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text));
		String key = "web-snapshots/" + id + "/source.txt";
		objectStore.put(key, text);
		Map<String, Object> paper;
		try {
			paper = repo.createPaperWithVersion(topicId.toString(), snapshot.title(), textHash, key, text.length);
		}
		catch (RpRepository.DuplicateDocumentException duplicate) {
			paper = jdbc.queryForMap(
					"SELECT p.id::text AS paper_id, dv.id::text AS document_version_id FROM rp_papers p JOIN rp_document_versions dv ON dv.paper_id = p.id WHERE p.topic_id = ? AND p.status = 'ACTIVE' AND dv.file_sha256 = ? LIMIT 1",
					topicId, textHash);
		}
		UUID paperId = UUID.fromString(paper.get("paper_id").toString());
		var metadata = new com.fasterxml.jackson.databind.ObjectMapper()
			.writeValueAsString(Map.of("source_type", "WEB", "source_url", snapshot.finalUrl(), "snapshot_id",
					id.toString(), "fetched_at", java.time.Instant.now().toString(), "provenance", "WEB_SNAPSHOT"));
		jdbc.update("UPDATE rp_papers SET metadata = metadata || ?::jsonb WHERE id = ?", metadata, paperId);
		jdbc.update(
				"INSERT INTO rp_web_snapshots (id, topic_id, url, final_url, title, content_type, content, content_sha256, extracted_text, paper_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (topic_id, url, content_sha256) DO UPDATE SET title = EXCLUDED.title, extracted_text = EXCLUDED.extracted_text, paper_id = EXCLUDED.paper_id",
				id, topicId, req.url().trim(), snapshot.finalUrl(), snapshot.title(), snapshot.contentType(),
				snapshot.raw(), hash, snapshot.text(), paperId);
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(jdbc.queryForMap(
					"SELECT id::text, paper_id::text, title, url, final_url, content_type, content_sha256, fetched_at FROM rp_web_snapshots WHERE id = ?",
					id));
	}

	// ---------- 上传 / 目录 / 删除 ----------

	public record UploadSessionRequest(String filename, String content_type, long total_size, String sha256) {
	}

	@PostMapping("/api/rp/topics/{topicId}/upload-sessions")
	public ResponseEntity<?> createUploadSession(@PathVariable UUID topicId, @RequestBody UploadSessionRequest req) {
		if (req.filename() == null || req.filename().isBlank() || req.total_size() <= 0 || req.filename().length() > 300
				|| req.total_size() > MAX_FILE_SIZE || req.sha256() == null
				|| !req.sha256().matches("(?i)[0-9a-f]{64}"))
			return error(HttpStatus.BAD_REQUEST, "INVALID_UPLOAD", "文件名或文件大小无效", false);
		UUID id = UUID.randomUUID();
		jdbc.update(
				"INSERT INTO rp_upload_sessions (id, topic_id, filename, content_type, total_size, chunk_size, expected_sha256) VALUES (?, ?, ?, ?, ?, ?, ?)",
				id, topicId, req.filename(), req.content_type(), req.total_size(), UPLOAD_CHUNK_SIZE,
				req.sha256().toLowerCase(java.util.Locale.ROOT));
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(Map.of("upload_id", id.toString(), "chunk_size", UPLOAD_CHUNK_SIZE));
	}

	@GetMapping("/api/rp/upload-sessions/{uploadId}")
	public ResponseEntity<?> uploadStatus(@PathVariable UUID uploadId) {
		List<Map<String, Object>> sessions = jdbc.queryForList(
				"SELECT id::text, filename, total_size, chunk_size, expected_sha256, status FROM rp_upload_sessions WHERE id = ?",
				uploadId);
		if (sessions.isEmpty())
			return error(HttpStatus.NOT_FOUND, "UPLOAD_NOT_FOUND", "上传会话不存在", false);
		Map<String, Object> result = new HashMap<>(sessions.get(0));
		result.put("received", jdbc.queryForList(
				"SELECT chunk_index, size, sha256 FROM rp_upload_chunks WHERE session_id = ? ORDER BY chunk_index",
				uploadId));
		return ResponseEntity.ok(result);
	}

	@org.springframework.web.bind.annotation.PutMapping(
			value = "/api/rp/upload-sessions/{uploadId}/chunks/{chunkIndex}",
			consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	@org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
	public ResponseEntity<?> uploadChunk(@PathVariable UUID uploadId, @PathVariable int chunkIndex,
			@RequestBody byte[] bytes) throws Exception {
		if (chunkIndex < 0 || bytes.length > UPLOAD_CHUNK_SIZE)
			return error(HttpStatus.BAD_REQUEST, "INVALID_CHUNK", "分片大小或序号无效", false);
		List<Map<String, Object>> rows = jdbc.queryForList(
				"SELECT total_size, chunk_size, status FROM rp_upload_sessions WHERE id = ? FOR UPDATE", uploadId);
		if (rows.isEmpty())
			return error(HttpStatus.NOT_FOUND, "UPLOAD_NOT_FOUND", "上传会话不存在", false);
		Map<String, Object> session = rows.get(0);
		if (!"UPLOADING".equals(session.get("status")))
			return error(HttpStatus.CONFLICT, "UPLOAD_NOT_ACTIVE", "上传会话已结束", false);
		long totalSize = ((Number) session.get("total_size")).longValue();
		long offset = (long) chunkIndex * UPLOAD_CHUNK_SIZE;
		if (offset >= totalSize || bytes.length != Math.min(UPLOAD_CHUNK_SIZE, totalSize - offset))
			return error(HttpStatus.BAD_REQUEST, "INVALID_CHUNK", "分片大小或序号与文件不匹配", false);
		String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		List<String> existing = jdbc.queryForList(
				"SELECT sha256 FROM rp_upload_chunks WHERE session_id = ? AND chunk_index = ?", String.class, uploadId,
				chunkIndex);
		if (!existing.isEmpty() && !existing.get(0).equals(hash))
			return error(HttpStatus.CONFLICT, "CHUNK_CONFLICT", "已接收的分片内容不一致", false);
		jdbc.update(
				"INSERT INTO rp_upload_chunks (session_id, chunk_index, content, size, sha256) VALUES (?, ?, ?, ?, ?) ON CONFLICT (session_id, chunk_index) DO UPDATE SET content = EXCLUDED.content, size = EXCLUDED.size, sha256 = EXCLUDED.sha256",
				uploadId, chunkIndex, bytes, bytes.length, hash);
		jdbc.update("UPDATE rp_upload_sessions SET updated_at = now() WHERE id = ?", uploadId);
		return ResponseEntity.ok(Map.of("upload_id", uploadId.toString(), "chunk_index", chunkIndex, "size",
				bytes.length, "sha256", hash));
	}

	@PostMapping("/api/rp/upload-sessions/{uploadId}/complete")
	@org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
	public ResponseEntity<?> completeUpload(@PathVariable UUID uploadId, @RequestParam(required = false) String title)
			throws Exception {
		List<Map<String, Object>> rows = jdbc.queryForList(
				"SELECT topic_id, filename, total_size, expected_sha256, status, result::text AS result FROM rp_upload_sessions WHERE id = ? FOR UPDATE",
				uploadId);
		if (rows.isEmpty())
			return error(HttpStatus.NOT_FOUND, "UPLOAD_NOT_FOUND", "上传会话不存在", false);
		Map<String, Object> session = rows.get(0);
		if ("COMPLETED".equals(session.get("status")))
			return ResponseEntity.ok(new com.fasterxml.jackson.databind.ObjectMapper()
				.readValue(String.valueOf(session.get("result")), Map.class));
		if (!"UPLOADING".equals(session.get("status")))
			return error(HttpStatus.CONFLICT, "UPLOAD_NOT_ACTIVE", "上传会话已结束", false);
		long totalSize = ((Number) session.get("total_size")).longValue();
		List<Integer> indices = jdbc.queryForList(
				"SELECT chunk_index FROM rp_upload_chunks WHERE session_id = ? ORDER BY chunk_index", Integer.class,
				uploadId);
		if (indices.size() != (totalSize + UPLOAD_CHUNK_SIZE - 1) / UPLOAD_CHUNK_SIZE)
			return error(HttpStatus.CONFLICT, "UPLOAD_INCOMPLETE", "仍有分片未上传", true);
		for (int index = 0; index < indices.size(); index++) {
			if (indices.get(index) != index)
				return error(HttpStatus.CONFLICT, "UPLOAD_INCOMPLETE", "分片序号不连续", true);
		}
		List<byte[]> chunks = jdbc.query(
				"SELECT content FROM rp_upload_chunks WHERE session_id = ? ORDER BY chunk_index",
				(rs, row) -> rs.getBytes(1), uploadId);
		long size = chunks.stream().mapToLong(b -> b.length).sum();
		if (size != ((Number) session.get("total_size")).longValue())
			return error(HttpStatus.CONFLICT, "UPLOAD_INCOMPLETE", "仍有分片未上传", true);
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		for (byte[] bytes : chunks)
			out.write(bytes);
		byte[] data = out.toByteArray();
		String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
		if (session.get("expected_sha256") != null && !String.valueOf(session.get("expected_sha256")).isBlank()
				&& !sha256.equalsIgnoreCase(String.valueOf(session.get("expected_sha256"))))
			return error(HttpStatus.UNPROCESSABLE_ENTITY, "UPLOAD_HASH_MISMATCH", "文件哈希校验失败", false);
		String filename = String.valueOf(session.get("filename")).toLowerCase(java.util.Locale.ROOT);
		String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.') + 1) : "bin";
		if (!Set.of("pdf", "txt", "md", "markdown", "docx", "pptx", "html", "htm", "png", "jpg", "jpeg").contains(ext))
			return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", "不支持的格式", false);
		if ("pdf".equals(ext)
				&& (data.length < 4 || data[0] != '%' || data[1] != 'P' || data[2] != 'D' || data[3] != 'F'))
			return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "INVALID_PDF", "不是有效的 PDF 文件", false);
		String objectKey = "papers/" + uploadId + "/" + sha256.substring(0, 12) + "." + ext;
		objectStore.put(objectKey, data);
		Map<String, Object> result = repo.createPaperWithVersion(String.valueOf(session.get("topic_id")),
				title == null || title.isBlank() ? String.valueOf(session.get("filename")) : title, sha256, objectKey,
				data.length);
		jdbc.update(
				"UPDATE rp_upload_sessions SET object_key = ?, result = ?::jsonb, status = 'COMPLETED', updated_at = now() WHERE id = ?",
				objectKey, new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result), uploadId);
		jdbc.update("DELETE FROM rp_upload_chunks WHERE session_id = ?", uploadId);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
	}

	@org.springframework.web.bind.annotation.DeleteMapping("/api/rp/upload-sessions/{uploadId}")
	@org.springframework.transaction.annotation.Transactional
	public ResponseEntity<?> cancelUpload(@PathVariable UUID uploadId) {
		int changed = jdbc.update(
				"UPDATE rp_upload_sessions SET status = 'CANCELLED', updated_at = now() WHERE id = ? AND status = 'UPLOADING'",
				uploadId);
		if (changed == 1)
			jdbc.update("DELETE FROM rp_upload_chunks WHERE session_id = ?", uploadId);
		return changed == 1 ? ResponseEntity.ok(Map.of("cancelled", true))
				: error(HttpStatus.NOT_FOUND, "UPLOAD_NOT_ACTIVE", "上传会话不存在或已结束", false);
	}

	@org.springframework.scheduling.annotation.Scheduled(fixedDelay = 3_600_000)
	public void cleanupExpiredUploads() {
		jdbc.update(
				"DELETE FROM rp_upload_sessions WHERE status IN ('UPLOADING', 'CANCELLED') AND updated_at < now() - interval '24 hours'");
	}

	@PostMapping(value = "/api/rp/topics/{topicId}/papers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<?> uploadPaper(@PathVariable String topicId, @RequestParam MultipartFile file,
			@RequestParam(required = false) String title) throws Exception {
		String contentType = file.getContentType() == null ? "" : file.getContentType();
		byte[] data = file.getBytes();
		if (data.length > MAX_FILE_SIZE) {
			return error(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "文件超过 100MB 上限", false);
		}

		// V1 多模态：PDF（Docling）/ txt+md（原生文本）/ docx+pptx+html+图片（Docling OCR）
		String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
		String lower = original.toLowerCase();
		String ext = lower.contains(".") ? lower.substring(lower.lastIndexOf('.') + 1) : "";
		boolean isPdf = "pdf".equals(ext) || "application/pdf".equals(contentType);
		boolean isText = Set.of("txt", "md", "markdown").contains(ext);
		boolean isDoc = Set.of("docx", "pptx", "html", "htm", "png", "jpg", "jpeg").contains(ext);
		if (!isPdf && !isText && !isDoc) {
			return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE",
					"不支持的格式：请上传 PDF / TXT / MD / DOCX / PPTX / HTML / 图片", false);
		}
		if (isPdf && (data.length < 4 || data[0] != '%' || data[1] != 'P' || data[2] != 'D' || data[3] != 'F')) {
			return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "INVALID_PDF", "不是有效的 PDF 文件", false);
		}

		String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
		String paperId = UUID.randomUUID().toString();
		String objectKey = "papers/" + paperId + "/" + sha256.substring(0, 12) + "." + ext;
		String displayTitle = title != null && !title.isBlank() ? title
				: original.replaceAll("(?i)\\.(pdf|txt|md|markdown|docx|pptx|html?|png|jpe?g)$", "").trim();

		Map<String, Object> result;
		// Publish the object before its queued job becomes visible to an ingestion
		// worker.
		objectStore.put(objectKey, data);
		try {
			result = repo.createPaperWithVersion(topicId, displayTitle, sha256, objectKey, data.length);
		}
		catch (RpRepository.DuplicateDocumentException e) {
			return error(HttpStatus.CONFLICT, "DUPLICATE_DOCUMENT", e.getMessage(), false);
		}

		Map<String, Object> body = new HashMap<>(result);
		body.put("file_sha256", sha256);
		return ResponseEntity.status(202).body(body);
	}

	@GetMapping("/api/rp/topics/{topicId}/papers")
	public List<Map<String, Object>> listPapers(@PathVariable String topicId) {
		return repo.listPapers(topicId);
	}

	/**
	 * Persist a completed research report as a normal Markdown source in the notebook.
	 */
	@PostMapping("/api/rp/research/{runId}/save-as-paper")
	public ResponseEntity<?> saveResearchReport(@PathVariable UUID runId,
			@RequestBody(required = false) Map<String, Object> request) throws Exception {
		List<Map<String, Object>> runs = jdbc
			.queryForList("SELECT topic_id::text AS topic_id, query, status FROM rp_runs WHERE id = ?", runId);
		if (runs.isEmpty())
			return ResponseEntity.notFound().build();
		List<Map<String, Object>> existing = jdbc.queryForList(
				"SELECT id::text AS paper_id FROM rp_papers WHERE metadata->>'report_run_id' = ? AND status = 'ACTIVE' LIMIT 1",
				runId.toString());
		if (!existing.isEmpty())
			return ResponseEntity.ok(existing.get(0));
		String status = String.valueOf(runs.get(0).get("status"));
		if (!List.of("COMPLETED", "PARTIAL_COMPLETED").contains(status)) {
			return error(HttpStatus.CONFLICT, "REPORT_NOT_READY", "研究尚未完成，暂不能归档报告", true);
		}
		List<Map<String, Object>> reports = jdbc.queryForList("SELECT report FROM rp_reports WHERE run_id = ?", runId);
		if (reports.isEmpty() || reports.get(0).get("report") == null)
			return error(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "研究报告尚未生成", true);
		String markdown = com.alibaba.cloud.ai.example.deepresearch.researchpilot.step.DeterministicReportComposer
			.sanitizePersisted(String.valueOf(reports.get(0).get("report")));
		String title = request == null || request.get("title") == null || String.valueOf(request.get("title")).isBlank()
				? "研究报告 · " + String.valueOf(runs.get(0).get("query")) : String.valueOf(request.get("title"));
		byte[] data = markdown.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
		String paperId = UUID.randomUUID().toString();
		String objectKey = "papers/" + paperId + "/" + sha256.substring(0, 12) + ".md";
		objectStore.put(objectKey, data);
		Map<String, Object> result = repo.createPaperWithVersion(String.valueOf(runs.get(0).get("topic_id")), title,
				sha256, objectKey, data.length);
		jdbc.update(
				"UPDATE rp_papers SET metadata = jsonb_build_object('category','研究报告','report_run_id',?::text,'source_type','RESEARCH_REPORT') WHERE id = ?::uuid",
				runId, result.get("paper_id"));
		return ResponseEntity.status(HttpStatus.CREATED).body(result);
	}

	@GetMapping(value = "/api/rp/papers/{paperId}/citation", produces = "text/plain;charset=UTF-8")
	public ResponseEntity<String> citation(@PathVariable UUID paperId,
			@RequestParam(defaultValue = "bibtex") String format) {
		if (!List.of("bibtex", "ris").contains(format.toLowerCase(java.util.Locale.ROOT)))
			return ResponseEntity.badRequest().body("Unsupported citation format");
		var rows = jdbc.queryForList("SELECT title, metadata FROM rp_papers WHERE id = ? AND status = 'ACTIVE'",
				paperId);
		if (rows.isEmpty())
			return ResponseEntity.notFound().build();
		var row = rows.get(0);
		com.fasterxml.jackson.databind.JsonNode metadata;
		try {
			metadata = new com.fasterxml.jackson.databind.ObjectMapper().readTree(String.valueOf(row.get("metadata")));
		}
		catch (Exception e) {
			metadata = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
		}
		String title = String.valueOf(row.get("title"));
		String authors = metadata.path("authors").asText("").trim();
		String year = metadata.path("year").isIntegralNumber() ? metadata.path("year").asText() : "";
		String doi = metadata.path("doi").asText("").trim();
		String key = "researchpilot_" + paperId.toString().replace("-", "").substring(0, 12);
		String body = "ris".equalsIgnoreCase(format)
				? "TY  - GEN\nTI  - " + ris(title) + "\n" + risAuthors(authors)
						+ (year.isBlank() ? "" : "PY  - " + year + "\n")
						+ (doi.isBlank() ? "" : "DO  - " + ris(doi) + "\n") + "ER  -\n"
				: "@misc{" + key + ",\n  title = {" + bib(title) + "},\n"
						+ (authors.isBlank() ? ""
								: "  author = {" + bib(authors.replaceAll("\\s*(?:;|；|\\|)\\s*", " and ")) + "},\n")
						+ (year.isBlank() ? "" : "  year = {" + year + "},\n")
						+ (doi.isBlank() ? "" : "  doi = {" + bib(doi) + "},\n") + "}\n";
		return ResponseEntity.ok()
			.header("Content-Disposition",
					"attachment; filename=\"" + key + ("ris".equalsIgnoreCase(format) ? ".ris" : ".bib") + "\"")
			.body(body);
	}

	private String bib(String value) {
		StringBuilder escaped = new StringBuilder();
		for (char c : value.toCharArray()) {
			escaped.append(switch (c) {
				case '\\' -> "\\textbackslash{}";
				case '{', '}', '%', '&', '_', '#' -> "\\" + c;
				case '\r', '\n' -> " ";
				default -> String.valueOf(c);
			});
		}
		return escaped.toString();
	}

	private String ris(String value) {
		return value.replaceAll("[\\r\\n]+", " ").trim();
	}

	private String risAuthors(String authors) {
		if (authors.isBlank())
			return "";
		return java.util.Arrays.stream(authors.split("\\s*(?:;|；|\\|| and )\\s*"))
			.filter(a -> !a.isBlank())
			.map(a -> "AU  - " + ris(a) + "\n")
			.collect(java.util.stream.Collectors.joining());
	}

	@PostMapping("/api/rp/papers/{paperId}/retry")
	public Map<String, Object> retryPaper(@PathVariable UUID paperId) {
		return repo.retryPaper(paperId.toString());
	}

	/**
	 * V1 UX-1：快速提问 —— 基于勾选且已就绪的资料回答，带编号引用。不走研究计划。
	 */
	@PostMapping("/api/rp/notebooks/{topicId}/ask")
	@org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
	public Map<String, Object> ask(@PathVariable String topicId,
			@org.springframework.web.bind.annotation.RequestBody AskRequest req) {
		UUID sessionId = parseSessionId(req.session_id());
		UUID messageId = parseSessionId(req.message_id());
		if (req.question() == null || req.question().isBlank() || req.question().length() > 32_000)
			throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,
					"问题不能为空且不能超过 32000 个字符");
		if (sessionId != null) {
			var owners = jdbc.queryForList("SELECT topic_id::text FROM rp_chat_sessions WHERE id = ?", sessionId);
			if (!owners.isEmpty() && !topicId.equals(owners.get(0).get("topic_id")))
				throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "会话不属于当前专题");
		}
		UUID parentSessionId = parseSessionId(req.parent_session_id());
		UUID branchMessageId = parseSessionId(req.branch_message_id());
		if (parentSessionId != null) {
			Integer parentOwns = jdbc.queryForObject(
					"SELECT count(*) FROM rp_chat_sessions WHERE id = ? AND topic_id = ?::uuid", Integer.class,
					parentSessionId, topicId);
			if (parentOwns == null || parentOwns != 1)
				throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,
						"分支来源会话不属于当前专题");
		}
		if (sessionId != null && messageId != null) {
			if (!Boolean.TRUE.equals(suppressAskPersistence.get())) {
				jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", Object.class,
						sessionId + ":" + messageId);
			}
			var existing = jdbc.queryForList(
					"SELECT role, content, citations FROM rp_chat_messages WHERE session_id = ? AND message_id = ? ORDER BY role DESC",
					sessionId, messageId);
			if (!existing.isEmpty()) {
				var assistant = existing.stream()
					.filter(row -> "assistant".equals(row.get("role")))
					.findFirst()
					.orElse(existing.get(0));
				Map<String, Object> out = new HashMap<>();
				out.put("answer", visibleText(assistant.get("content")));
				out.put("citations", sanitizeCitations(assistant.get("citations")));
				out.put("used_papers", 0);
				return out;
			}
		}
		String modelQuestion = req.question();
		if (sessionId != null) {
			try {
				Integer ownsSession = jdbc.queryForObject(
						"SELECT count(*) FROM rp_chat_sessions WHERE id = ? AND topic_id = ?::uuid", Integer.class,
						sessionId, topicId);
				Integer sessionExists = jdbc.queryForObject("SELECT count(*) FROM rp_chat_sessions WHERE id = ?",
						Integer.class, sessionId);
				if (sessionExists != null && sessionExists > 0 && (ownsSession == null || ownsSession != 1)) {
					throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,
							"会话不存在或不属于当前专题");
				}
				modelQuestion = conversationContext.assemble(UUID.fromString(topicId), sessionId, req.question());
			}
			catch (Exception e) {
				if (e instanceof org.springframework.web.server.ResponseStatusException responseStatusException) {
					throw responseStatusException;
				}
				logger.debug("读取会话上下文失败，按单轮问题回答: {}", e.getMessage());
			}
		}
		var result = askService.ask(topicId, req.paper_ids(), req.question(), Boolean.TRUE.equals(req.allow_general()),
				Boolean.TRUE.equals(req.allow_web()), modelQuestion);
		String visibleAnswer = visibleText(result.answer());
		if (sessionId != null && !Boolean.TRUE.equals(suppressAskPersistence.get())) {
			try {
				jdbc.update(
						"""
								INSERT INTO rp_chat_sessions (id, topic_id, title, parent_session_id, branch_message_id) VALUES (?, ?::uuid, ?, ?, ?)
										ON CONFLICT (topic_id, id) DO UPDATE SET updated_at = now(),
											title = CASE WHEN rp_chat_sessions.title = '新对话' THEN EXCLUDED.title ELSE rp_chat_sessions.title END
										""",
						sessionId, topicId, req.question().substring(0, Math.min(40, req.question().length())),
						parentSessionId, branchMessageId);
				jdbc.update(
						"INSERT INTO rp_chat_messages (session_id, role, content, citations) VALUES (?, 'user', ?, NULL)",
						sessionId, req.question());
				try {
					String citationJson = new com.fasterxml.jackson.databind.ObjectMapper()
						.writeValueAsString(result.citations());
					jdbc.update(
							"INSERT INTO rp_chat_messages (session_id, role, content, citations, message_id) VALUES (?, 'assistant', ?, ?::jsonb, ?)",
							sessionId, visibleAnswer, citationJson, messageId);
				}
				catch (Exception e) {
					jdbc.update(
							"INSERT INTO rp_chat_messages (session_id, role, content, message_id) VALUES (?, 'assistant', ?, ?)",
							sessionId, visibleAnswer, messageId);
				}
			}
			catch (Exception e) {
				throw new org.springframework.web.server.ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
						"回答保存失败，请使用同一请求重试", e);
			}
		}
		Map<String, Object> out = new HashMap<>();
		out.put("answer", visibleText(result.answer()));
		out.put("citations", DeterministicReportComposer.sanitizeStructured(
				new com.fasterxml.jackson.databind.ObjectMapper().convertValue(result.citations(), Object.class)));
		out.put("used_papers", result.usedPapers());
		return out;
	}

	/**
	 * Recoverable SSE envelope for quick answers. Persistence and message_id idempotency
	 * remain owned by the regular ask path; reconnecting with the same request gets the
	 * already persisted answer instead of creating another message.
	 */
	@PostMapping(value = "/api/rp/notebooks/{topicId}/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter askStream(@PathVariable String topicId, @RequestBody AskRequest req) {
		if (parseSessionId(topicId) == null) {
			throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,
					"专题 ID 无效，请返回专题列表重新打开");
		}
		SseEmitter emitter = new SseEmitter(300_000L);
		String requestId = req.message_id() == null ? UUID.randomUUID().toString() : req.message_id();
		Future<?> task = askStreamExecutor.submit(() -> {
			suppressAskPersistence.set(true);
			Object requestLock = askStreamLocks.computeIfAbsent(requestId, ignored -> new Object());
			try {
				Map<String, Object> result;
				synchronized (requestLock) {
					result = ask(topicId, req);
				}
				if (Thread.currentThread().isInterrupted()) {
					emitter.complete();
					return;
				}
				Map<String, Object> visibleResult = new HashMap<>(result);
				visibleResult.put("answer", visibleText(result.getOrDefault("answer", "")));
				visibleResult.put("citations",
						DeterministicReportComposer.sanitizeStructured(result.getOrDefault("citations", List.of())));
				String answer = String.valueOf(visibleResult.getOrDefault("answer", ""));
				for (int start = 0; start < answer.length();) {
					if (Thread.currentThread().isInterrupted()) {
						emitter.complete();
						return;
					}
					int end = Math.min(answer.length(), start + 180);
					if (end < answer.length() && Character.isHighSurrogate(answer.charAt(end - 1)))
						end--;
					emitter.send(SseEmitter.event().name("delta").data(Map.of("text", answer.substring(start, end))));
					start = end;
				}
				if (!Thread.currentThread().isInterrupted() && req.session_id() != null && req.message_id() != null) {
					persistAskResult(topicId, req, visibleResult);
				}
				emitter.send(SseEmitter.event().name("complete").data(visibleResult));
				emitter.complete();
			}
			catch (Exception error) {
				try {
					emitter.send(SseEmitter.event()
						.name("error")
						.data(Map.of("code", "ASK_STREAM_FAILED", "message", "回答生成失败，可使用相同消息 ID 重试")));
				}
				catch (Exception ignored) {
				}
				emitter.completeWithError(error);
			}
			finally {
				suppressAskPersistence.remove();
				activeAskStreams.remove(requestId);
				askStreamLocks.remove(requestId, requestLock);
			}
		});
		activeAskStreams.put(requestId, task);
		return emitter;
	}

	/** Persist a completed stream only after the client-visible complete event. */
	private void persistAskResult(String topicId, AskRequest req, Map<String, Object> result) {
		UUID sessionId = parseSessionId(req.session_id());
		UUID messageId = parseSessionId(req.message_id());
		if (sessionId == null || messageId == null) {
			return;
		}
		try {
			jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", Object.class,
					sessionId + ":" + messageId);
			Integer existing = jdbc.queryForObject(
					"SELECT count(*) FROM rp_chat_messages WHERE session_id = ? AND message_id = ?", Integer.class,
					sessionId, messageId);
			if (existing != null && existing > 0) {
				return;
			}
			jdbc.update(
					"INSERT INTO rp_chat_sessions (id, topic_id, title) VALUES (?, ?::uuid, ?) "
							+ "ON CONFLICT (topic_id, id) DO UPDATE SET updated_at = now()",
					sessionId, topicId, req.question().substring(0, Math.min(40, req.question().length())));
			String citationJson = new com.fasterxml.jackson.databind.ObjectMapper()
				.writeValueAsString(result.getOrDefault("citations", List.of()));
			jdbc.update("INSERT INTO rp_chat_messages (session_id, role, content) VALUES (?, 'user', ?)", sessionId,
					req.question());
			jdbc.update(
					"INSERT INTO rp_chat_messages (session_id, role, content, citations, message_id) "
							+ "VALUES (?, 'assistant', ?, ?::jsonb, ?)",
					sessionId, String.valueOf(result.getOrDefault("answer", "")), citationJson, messageId);
		}
		catch (Exception e) {
			throw new IllegalStateException("回答保存失败，请使用同一消息 ID 重试", e);
		}
	}

	@PostMapping("/api/rp/notebooks/{topicId}/ask/stream/{messageId}/cancel")
	public ResponseEntity<?> cancelAskStream(@PathVariable String topicId, @PathVariable String messageId) {
		Future<?> task = activeAskStreams.remove(messageId);
		if (task == null)
			return ResponseEntity.ok(Map.of("cancelled", false, "message_id", messageId));
		task.cancel(true);
		return ResponseEntity.ok(Map.of("cancelled", true, "message_id", messageId));
	}

	private UUID parseSessionId(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(value);
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}

	public record AskRequest(String question, List<String> paper_ids, Boolean allow_general, Boolean allow_web,
			String session_id, String message_id, String parent_session_id, String branch_message_id) {

		/**
		 * Backward-compatible constructor for callers created before persisted branches.
		 */
		public AskRequest(String question, List<String> paper_ids, Boolean allow_general, Boolean allow_web,
				String session_id, String message_id) {
			this(question, paper_ids, allow_general, allow_web, session_id, message_id, null, null);
		}
	}

	/** Create an empty persisted chat session before the first question is sent. */
	@PostMapping("/api/rp/notebooks/{topicId}/sessions")
	public ResponseEntity<?> createSession(@PathVariable String topicId,
			@RequestBody(required = false) Map<String, Object> body) {
		try {
			UUID topicUuid = UUID.fromString(topicId);
			Integer topicExists = jdbc.queryForObject("SELECT count(*) FROM rp_topics WHERE id = ?", Integer.class,
					topicUuid);
			if (topicExists == null || topicExists == 0)
				return ResponseEntity.notFound().build();
			UUID sessionId = UUID.randomUUID();
			String title = body == null ? null : String.valueOf(body.getOrDefault("title", "新对话"));
			if (title == null || title.isBlank() || "null".equalsIgnoreCase(title))
				title = "新对话";
			jdbc.update("INSERT INTO rp_chat_sessions (id, topic_id, title) VALUES (?, ?, ?)", sessionId, topicUuid,
					title);
			return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", sessionId.toString(), "title", title));
		}
		catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("error", "专题 ID 无效"));
		}
	}

	@GetMapping("/api/rp/notebooks/{topicId}/sessions")
	public List<Map<String, Object>> sessions(@PathVariable String topicId) {
		return jdbc.queryForList(
				"SELECT id::text, title, parent_session_id::text, branch_message_id::text, created_at, updated_at, "
						+ "(SELECT count(*) FROM rp_chat_messages m WHERE m.session_id = s.id) AS message_count "
						+ "FROM rp_chat_sessions s WHERE topic_id = ?::uuid ORDER BY updated_at DESC",
				topicId);
	}

	public record MemoryRequest(String kind, String content) {
		public MemoryRequest(String content) {
			this("STABLE_PREFERENCE", content);
		}
	}

	/**
	 * Harness-managed cross-notebook memory. The local owner key is replaced by auth
	 * later.
	 */
	@GetMapping("/api/rp/user-memory")
	public List<Map<String, Object>> userMemory() {
		return jdbc.queryForList("""
				SELECT id::text, kind, content, source, status, source_message_id::text, supersedes_id::text,
				       created_at, updated_at
				FROM rp_user_memory WHERE owner_key = 'local' AND status = 'ACTIVE' ORDER BY updated_at DESC
				""");
	}

	@PostMapping("/api/rp/user-memory")
	public ResponseEntity<?> addUserMemory(@RequestBody MemoryRequest req) {
		if (req == null || req.content() == null || req.content().isBlank() || req.content().length() > 2000)
			return error(HttpStatus.BAD_REQUEST, "INVALID_MEMORY", "记忆内容不能为空且不能超过 2000 字", false);
		String kind = req.kind() == null ? "PREFERENCE" : req.kind().trim().toUpperCase();
		if (!Set.of("PREFERENCE", "CONSTRAINT").contains(kind))
			return error(HttpStatus.BAD_REQUEST, "INVALID_MEMORY_KIND", "不支持的用户记忆类型", false);
		UUID id = UUID.randomUUID();
		jdbc.update("INSERT INTO rp_user_memory (id, kind, content) VALUES (?, ?, ?)", id, kind, req.content().trim());
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(Map.of("id", id.toString(), "kind", kind, "content", req.content().trim(), "status", "ACTIVE"));
	}

	@DeleteMapping("/api/rp/user-memory/{memoryId}")
	public ResponseEntity<?> dismissUserMemory(@PathVariable UUID memoryId) {
		int changed = jdbc.update(
				"UPDATE rp_user_memory SET status = 'DISMISSED', updated_at = now() WHERE id = ? AND owner_key = 'local' AND status = 'ACTIVE'",
				memoryId);
		return changed == 0 ? ResponseEntity.notFound().build() : ResponseEntity.ok(Map.of("dismissed", true));
	}

	public record DiscoveryRequest(String query, Integer limit) {
	}

	public record DiscoveryImportRequest(String url) {
	}

	@PostMapping("/api/rp/topics/{topicId}/discover")
	public ResponseEntity<?> discoverPapers(@PathVariable String topicId, @RequestBody DiscoveryRequest request) {
		if (request == null || request.query() == null || request.query().isBlank())
			return error(HttpStatus.BAD_REQUEST, "DISCOVERY_QUERY_REQUIRED", "请输入 DOI、URL 或检索关键词", false);
		if (paperDiscovery == null)
			return error(HttpStatus.SERVICE_UNAVAILABLE, "DISCOVERY_UNAVAILABLE", "论文发现服务暂不可用", true);
		return ResponseEntity.ok(Map.of("topic_id", topicId, "candidates",
				paperDiscovery.discover(request.query(), request.limit() == null ? 5 : request.limit()), "imported",
				false));
	}

	@PostMapping("/api/rp/topics/{topicId}/discover/import")
	public ResponseEntity<?> importDiscoveredPaper(@PathVariable UUID topicId,
			@RequestBody DiscoveryImportRequest request) throws Exception {
		if (request == null || request.url() == null || request.url().isBlank())
			return error(HttpStatus.BAD_REQUEST, "DISCOVERY_URL_REQUIRED", "候选缺少可导入 URL", false);
		return saveWebSnapshot(topicId, new WebSnapshotRequest(request.url().trim()));
	}

	@GetMapping("/api/rp/topics/{topicId}/memory")
	public ResponseEntity<?> memory(@PathVariable UUID topicId) {
		return ResponseEntity.ok(jdbc.queryForList(
				"SELECT id::text, kind, content, source, created_at, updated_at FROM rp_topic_memory WHERE topic_id = ? ORDER BY updated_at DESC",
				topicId));
	}

	@PostMapping("/api/rp/topics/{topicId}/memory")
	public ResponseEntity<?> addMemory(@PathVariable UUID topicId, @RequestBody MemoryRequest req) {
		if (req.content() == null || req.content().isBlank() || req.content().length() > 4000
				|| !List.of("STABLE_PREFERENCE", "CONFIRMED_FACT").contains(req.kind()))
			return error(HttpStatus.BAD_REQUEST, "INVALID_MEMORY", "记忆类型或内容无效", false);
		if (jdbc.queryForList("SELECT id FROM rp_topics WHERE id = ?", topicId).isEmpty())
			return error(HttpStatus.NOT_FOUND, "TOPIC_NOT_FOUND", "专题不存在", false);
		UUID id = UUID.randomUUID();
		jdbc.update("INSERT INTO rp_topic_memory (id, topic_id, kind, content) VALUES (?, ?, ?, ?)", id, topicId,
				req.kind(), req.content().trim());
		return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString(), "created", true));
	}

	@DeleteMapping("/api/rp/topics/{topicId}/memory/{memoryId}")
	public ResponseEntity<?> deleteMemory(@PathVariable UUID topicId, @PathVariable UUID memoryId) {
		int changed = jdbc.update("DELETE FROM rp_topic_memory WHERE id = ? AND topic_id = ?", memoryId, topicId);
		return changed == 1 ? ResponseEntity.ok(Map.of("deleted", true))
				: error(HttpStatus.NOT_FOUND, "MEMORY_NOT_FOUND", "记忆不存在", false);
	}

	public record NoteRequest(String title, String body, String run_id, String evidence_id, String source_quote) {
		public NoteRequest(String title, String body, String evidence_id, String source_quote) {
			this(title, body, null, evidence_id, source_quote);
		}
	}

	@GetMapping("/api/rp/topics/{topicId}/notes")
	public ResponseEntity<?> notes(@PathVariable UUID topicId) {
		return ResponseEntity.ok(jdbc.queryForList(
				"""
						SELECT id::text, topic_id::text, run_id::text, evidence_id, title, body, source_quote, favorite, created_at, updated_at
						FROM rp_notes WHERE topic_id = ? ORDER BY updated_at DESC
						""",
				topicId));
	}

	@PostMapping("/api/rp/topics/{topicId}/notes")
	public ResponseEntity<?> createNote(@PathVariable UUID topicId,
			@org.springframework.web.bind.annotation.RequestBody NoteRequest req) {
		if (req.body() == null || req.body().isBlank() || req.body().length() > 100_000) {
			return error(HttpStatus.BAD_REQUEST, "INVALID_NOTE", "笔记内容不能为空且不能超过 100KB", false);
		}
		UUID runId = parseSessionId(req.run_id());
		if (req.run_id() != null && !req.run_id().isBlank() && runId == null)
			return error(HttpStatus.BAD_REQUEST, "INVALID_NOTE_RUN", "关联研究 ID 无效", false);
		if (runId != null
				&& jdbc.queryForList("SELECT id FROM rp_runs WHERE id = ? AND topic_id = ?", runId, topicId).isEmpty())
			return error(HttpStatus.BAD_REQUEST, "INVALID_NOTE_RUN", "关联研究不属于当前专题", false);
		// Evidence IDs created by the current API are UUIDs. Validate those
		// bindings at write time; legacy chunk/source identifiers remain accepted
		// for notes created by older clients and are resolved when the source opens.
		if (req.evidence_id() != null && !req.evidence_id().isBlank()) {
			try {
				UUID evidenceId = UUID.fromString(req.evidence_id());
				String evidenceSql = "SELECT id FROM rp_evidence WHERE id = ? AND topic_id = ?"
						+ (runId == null ? "" : " AND (run_id IS NULL OR run_id = ?)");
				List<Map<String, Object>> evidence = runId == null ? jdbc.queryForList(evidenceSql, evidenceId, topicId)
						: jdbc.queryForList(evidenceSql, evidenceId, topicId, runId);
				if (evidence.isEmpty())
					return error(HttpStatus.BAD_REQUEST, "INVALID_NOTE_EVIDENCE", "引用证据不存在或不属于当前专题", false);
			}
			catch (IllegalArgumentException ignored) {
				// Non-UUID source_chunk_id is a supported legacy source reference.
			}
		}
		if (req.source_quote() != null && req.source_quote().length() > 100_000)
			return error(HttpStatus.BAD_REQUEST, "NOTE_QUOTE_TOO_LARGE", "原文摘录不能超过 100000 个字符", false);
		String title = req.title() == null || req.title().isBlank() ? "未命名笔记" : req.title().trim();
		if (title.length() > 160)
			return error(HttpStatus.BAD_REQUEST, "INVALID_NOTE_TITLE", "笔记标题不能超过 160 个字符", false);
		UUID id = UUID.randomUUID();
		jdbc.update(
				"INSERT INTO rp_notes (id, topic_id, run_id, evidence_id, title, body, source_quote) VALUES (?, ?, ?, ?, ?, ?, ?)",
				id, topicId, runId, req.evidence_id(), title, req.body(), req.source_quote());
		return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString(), "created", true));
	}

	@org.springframework.web.bind.annotation.PatchMapping("/api/rp/notes/{noteId}")
	public ResponseEntity<?> updateNote(@PathVariable UUID noteId,
			@org.springframework.web.bind.annotation.RequestBody NoteRequest req) {
		if (req.body() == null || req.body().isBlank() || req.body().length() > 100_000)
			return error(HttpStatus.BAD_REQUEST, "INVALID_NOTE", "笔记内容不能为空且不能超过 100KB", false);
		String title = req.title() == null || req.title().isBlank() ? "未命名笔记" : req.title().trim();
		if (title.length() > 160)
			return error(HttpStatus.BAD_REQUEST, "INVALID_NOTE_TITLE", "笔记标题不能超过 160 个字符", false);
		int changed = jdbc.update("UPDATE rp_notes SET title = ?, body = ?, updated_at = now() WHERE id = ?", title,
				req.body(), noteId);
		return changed == 1 ? ResponseEntity.ok(Map.of("updated", true))
				: error(HttpStatus.NOT_FOUND, "NOTE_NOT_FOUND", "笔记不存在", false);
	}

	@DeleteMapping("/api/rp/notes/{noteId}")
	public ResponseEntity<?> deleteNote(@PathVariable UUID noteId) {
		int changed = jdbc.update("DELETE FROM rp_notes WHERE id = ?", noteId);
		return changed == 1 ? ResponseEntity.ok(Map.of("deleted", true))
				: error(HttpStatus.NOT_FOUND, "NOTE_NOT_FOUND", "笔记不存在", false);
	}

	@PostMapping("/api/rp/notes/{noteId}/favorite")
	public ResponseEntity<?> favoriteNote(@PathVariable UUID noteId, @RequestBody Map<String, Boolean> req) {
		Boolean favorite = req == null ? null : req.get("favorite");
		if (favorite == null)
			return error(HttpStatus.BAD_REQUEST, "INVALID_FAVORITE", "favorite 必须为布尔值", false);
		int changed = jdbc.update("UPDATE rp_notes SET favorite = ?, updated_at = now() WHERE id = ?", favorite,
				noteId);
		return changed == 1 ? ResponseEntity.ok(Map.of("favorite", favorite))
				: error(HttpStatus.NOT_FOUND, "NOTE_NOT_FOUND", "笔记不存在", false);
	}

	@GetMapping("/api/rp/notebooks/{topicId}/sessions/{sessionId}/messages")
	public ResponseEntity<?> messages(@PathVariable String topicId, @PathVariable UUID sessionId) {
		List<Map<String, Object>> rows = jdbc.queryForList(
				"SELECT m.id::text, m.role, m.content, m.citations::text AS citations, m.metadata::text AS metadata, m.created_at "
						+ "FROM rp_chat_messages m JOIN rp_chat_sessions s ON s.id = m.session_id "
						+ "WHERE s.topic_id = ?::uuid AND s.id = ? ORDER BY m.created_at",
				topicId, sessionId);
		rows.forEach(row -> {
			if ("assistant".equals(row.get("role")))
				row.put("content", visibleText(row.get("content")));
			if (row.get("citations") != null) {
				try {
					row.put("citations", new com.fasterxml.jackson.databind.ObjectMapper()
						.writeValueAsString(sanitizeCitations(row.get("citations"))));
				}
				catch (Exception ignored) {
					row.put("citations", "[]");
				}
			}
		});
		return ResponseEntity.ok(rows);
	}

	/** Keep internal model/tool protocol out of every user-visible chat boundary. */
	private static String visibleText(Object value) {
		return value == null ? null : DeterministicReportComposer.sanitizePersisted(String.valueOf(value));
	}

	private static Object sanitizeCitations(Object raw) {
		if (raw == null)
			return List.of();
		try {
			Object parsed = new com.fasterxml.jackson.databind.ObjectMapper().readValue(raw.toString(), Object.class);
			return DeterministicReportComposer.sanitizeStructured(parsed);
		}
		catch (Exception ignored) {
			return List.of();
		}
	}

	@GetMapping("/api/rp/notebooks/{topicId}/sessions/{sessionId}/draft")
	public ResponseEntity<?> draft(@PathVariable String topicId, @PathVariable UUID sessionId) {
		List<Map<String, Object>> rows = jdbc.queryForList(
				"SELECT draft::text AS draft FROM rp_chat_sessions WHERE id = ? AND topic_id = ?::uuid", sessionId,
				topicId);
		if (rows.isEmpty()) {
			return error(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "会话不存在或不属于当前专题", false);
		}
		return ResponseEntity.ok(rows.get(0));
	}

	@PostMapping("/api/rp/notebooks/{topicId}/sessions/{sessionId}/draft")
	public ResponseEntity<?> saveDraft(@PathVariable String topicId, @PathVariable UUID sessionId,
			@org.springframework.web.bind.annotation.RequestBody Map<String, Object> draft) {
		try {
			String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(draft);
			if (json.length() > 100_000) {
				return error(HttpStatus.PAYLOAD_TOO_LARGE, "DRAFT_TOO_LARGE", "草稿超过 100KB", false);
			}
			int changed = jdbc.update(
					"UPDATE rp_chat_sessions SET draft = ?::jsonb, updated_at = now() WHERE id = ? AND topic_id = ?::uuid",
					json, sessionId, topicId);
			return changed == 1 ? ResponseEntity.accepted().body(Map.of("saved", true))
					: error(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "会话不存在或不属于当前专题", false);
		}
		catch (Exception e) {
			return error(HttpStatus.BAD_REQUEST, "INVALID_DRAFT", "草稿格式无效", false);
		}
	}

	/** CTX-06：展示并保存本次会话可控的上下文排除项。原始数据不删除，只改变后续装配。 */
	@GetMapping("/api/rp/notebooks/{topicId}/sessions/{sessionId}/context")
	public ResponseEntity<?> context(@PathVariable String topicId, @PathVariable UUID sessionId) {
		List<Map<String, Object>> sessions = jdbc.queryForList("""
				SELECT context_exclusions::text AS exclusions FROM rp_chat_sessions
				WHERE id = ? AND topic_id = ?::uuid
				""", sessionId, topicId);
		if (sessions.isEmpty()) {
			return error(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "会话不存在或不属于当前专题", false);
		}
		List<Map<String, Object>> messages = jdbc.queryForList("""
				SELECT id::text, role, LEFT(content, 300) AS content, created_at
				FROM rp_chat_messages WHERE session_id = ? ORDER BY created_at DESC, id DESC LIMIT 30
				""", sessionId);
		List<Map<String, Object>> memories = jdbc.queryForList("""
				SELECT id::text, kind, LEFT(content, 300) AS content, updated_at
				FROM rp_topic_memory WHERE topic_id = ?::uuid ORDER BY updated_at DESC LIMIT 30
				""", topicId);
		List<Map<String, Object>> runs = jdbc.queryForList("""
				SELECT id::text, query, status, updated_at FROM rp_runs
				WHERE session_id = ? AND topic_id = ?::uuid ORDER BY updated_at DESC LIMIT 20
				""", sessionId.toString(), topicId);
		Object exclusions;
		try {
			exclusions = new com.fasterxml.jackson.databind.ObjectMapper()
				.readTree(String.valueOf(sessions.get(0).getOrDefault("exclusions", "{}")));
		}
		catch (Exception ignored) {
			exclusions = Map.of();
		}
		return ResponseEntity
			.ok(Map.of("messages", messages, "memories", memories, "runs", runs, "exclusions", exclusions));
	}

	@PostMapping("/api/rp/notebooks/{topicId}/sessions/{sessionId}/context")
	public ResponseEntity<?> updateContext(@PathVariable String topicId, @PathVariable UUID sessionId,
			@org.springframework.web.bind.annotation.RequestBody Map<String, Object> body) {
		try {
			if (body == null) {
				return error(HttpStatus.BAD_REQUEST, "INVALID_CONTEXT", "上下文排除项格式无效", false);
			}
			Set<String> messages = boundedIds(body.get("message_ids"));
			Set<String> memories = boundedIds(body.get("memory_ids"));
			Set<String> runs = boundedIds(body.get("run_ids"));
			int messageOwner = (int) messages.stream().filter(id -> ownsMessage(sessionId, id)).count();
			int memoryOwner = (int) memories.stream().filter(id -> ownsMemory(topicId, id)).count();
			int runOwner = (int) runs.stream().filter(id -> ownsRun(sessionId, topicId, id)).count();
			if (messageOwner != messages.size() || memoryOwner != memories.size() || runOwner != runs.size()) {
				return error(HttpStatus.BAD_REQUEST, "INVALID_CONTEXT_ID", "上下文排除项不属于当前会话或专题", false);
			}
			Map<String, Object> exclusions = Map.of("message_ids", messages, "memory_ids", memories, "run_ids", runs);
			String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(exclusions);
			int changed = jdbc.update("""
					UPDATE rp_chat_sessions SET context_exclusions = ?::jsonb,
					context_summary = NULL, context_summary_version = context_summary_version + 1,
					context_compacted_through = NULL, context_compacted_message_id = NULL, updated_at = now()
					WHERE id = ? AND topic_id = ?::uuid
					""", json, sessionId, topicId);
			return changed == 1 ? ResponseEntity.accepted().body(Map.of("saved", true, "exclusions", exclusions))
					: error(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "会话不存在或不属于当前专题", false);
		}
		catch (IllegalArgumentException e) {
			return error(HttpStatus.BAD_REQUEST, "INVALID_CONTEXT", e.getMessage(), false);
		}
		catch (Exception e) {
			return error(HttpStatus.BAD_REQUEST, "INVALID_CONTEXT", "上下文排除项格式无效", false);
		}
	}

	private Set<String> boundedIds(Object raw) {
		if (raw == null) {
			return Set.of();
		}
		if (!(raw instanceof List<?> values) || values.size() > 100) {
			throw new IllegalArgumentException("每类上下文排除项最多 100 条");
		}
		Set<String> ids = new java.util.LinkedHashSet<>();
		for (Object value : values) {
			try {
				ids.add(UUID.fromString(String.valueOf(value)).toString());
			}
			catch (Exception e) {
				throw new IllegalArgumentException("上下文排除项必须是 UUID");
			}
		}
		return ids;
	}

	private boolean ownsMessage(UUID sessionId, String id) {
		return jdbc.queryForObject("SELECT count(*) FROM rp_chat_messages WHERE session_id = ? AND id = ?::uuid",
				Integer.class, sessionId, id) == 1;
	}

	private boolean ownsMemory(String topicId, String id) {
		return jdbc.queryForObject("SELECT count(*) FROM rp_topic_memory WHERE topic_id = ?::uuid AND id = ?::uuid",
				Integer.class, topicId, id) == 1;
	}

	private boolean ownsRun(UUID sessionId, String topicId, String id) {
		return jdbc.queryForObject(
				"SELECT count(*) FROM rp_runs WHERE session_id = ? AND topic_id = ?::uuid AND id = ?::uuid",
				Integer.class, sessionId.toString(), topicId, id) == 1;
	}

	@PostMapping("/api/rp/notebooks/{topicId}/sessions/{sessionId}/rename")
	public ResponseEntity<?> renameSession(@PathVariable String topicId, @PathVariable UUID sessionId,
			@org.springframework.web.bind.annotation.RequestBody SessionTitle req) {
		String title = req.title() == null ? "" : req.title().trim();
		if (title.isBlank() || title.length() > 120) {
			return error(HttpStatus.BAD_REQUEST, "INVALID_SESSION_TITLE", "标题长度必须为 1-120 个字符", false);
		}
		int changed = jdbc.update(
				"UPDATE rp_chat_sessions SET title = ?, updated_at = now() " + "WHERE id = ? AND topic_id = ?::uuid",
				title, sessionId, topicId);
		return changed == 1 ? ResponseEntity.ok(Map.of("renamed", true, "title", title))
				: error(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "会话不存在或不属于当前专题", false);
	}

	@DeleteMapping("/api/rp/notebooks/{topicId}/sessions/{sessionId}")
	public ResponseEntity<?> deleteSession(@PathVariable String topicId, @PathVariable UUID sessionId) {
		int changed = jdbc.update("DELETE FROM rp_chat_sessions WHERE id = ? AND topic_id = ?::uuid", sessionId,
				topicId);
		return changed == 1 ? ResponseEntity.ok(Map.of("deleted", true))
				: error(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "会话不存在或不属于当前专题", false);
	}

	public record SessionTitle(String title) {
	}

	@PostMapping("/api/rp/notebooks/{topicId}/sessions/{sessionId}/messages")
	public ResponseEntity<?> appendSessionMessage(@PathVariable String topicId, @PathVariable UUID sessionId,
			@org.springframework.web.bind.annotation.RequestBody SessionMessage req) {
		if (req.content() == null || req.content().isBlank()
				|| !List.of("user", "assistant", "system").contains(req.role())) {
			return error(HttpStatus.BAD_REQUEST, "INVALID_MESSAGE", "消息角色或内容无效", false);
		}
		int owns = jdbc.queryForObject("SELECT count(*) FROM rp_chat_sessions WHERE id = ? AND topic_id = ?::uuid",
				Integer.class, sessionId, topicId);
		if (owns != 1) {
			return error(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "会话不存在或不属于当前专题", false);
		}
		jdbc.update("INSERT INTO rp_chat_messages (session_id, role, content) VALUES (?, ?, ?)", sessionId, req.role(),
				req.content());
		jdbc.update("UPDATE rp_chat_sessions SET updated_at = now() WHERE id = ?", sessionId);
		return ResponseEntity.accepted().body(Map.of("saved", true));
	}

	public record SessionMessage(String role, String content) {
	}

	/** H5/F1：论文详情（阅读器数据源）。 */
	@GetMapping("/api/rp/papers/{paperId}")
	public ResponseEntity<?> paperDetail(@PathVariable String paperId) {
		Map<String, Object> d = repo.paperDetail(paperId);
		return d == null ? error(HttpStatus.NOT_FOUND, "PAPER_NOT_FOUND", "论文不存在或已删除", false) : ResponseEntity.ok(d);
	}

	/** H5/F4：PDF 原文下载（论文阅读器 iframe 数据源，inline 展示）。 */
	@GetMapping("/api/rp/versions/{versionId}/file")
	public ResponseEntity<byte[]> versionFile(@PathVariable String versionId,
			@RequestParam(defaultValue = "false") boolean download) throws Exception {
		var ref = repo.getVersionObjectKey(versionId).orElse(null);
		if (ref == null) {
			return ResponseEntity.notFound().build();
		}
		byte[] data = objectStore.get(ref.objectKey());
		String mime = com.alibaba.cloud.ai.example.deepresearch.researchpilot.store.DocumentPreviewService
			.mime(ref.objectKey());
		String filename = java.nio.file.Path.of(ref.objectKey()).getFileName().toString();
		boolean attachment = download || mime.equals("application/octet-stream") || mime.contains("openxmlformats");
		return ResponseEntity.ok()
			.header("Content-Type", mime)
			.header("X-Content-Type-Options", "nosniff")
			.header("Content-Disposition", (attachment ? "attachment" : "inline") + "; filename=\"" + filename + "\"")
			.body(data);
	}

	@GetMapping("/api/rp/versions/{versionId}/preview")
	public Map<String, Object> versionPreview(@PathVariable UUID versionId,
			@RequestParam(defaultValue = "0") int offset) throws java.io.IOException {
		return documentPreview.preview(versionId, offset);
	}

	@DeleteMapping("/api/rp/papers/{paperId}")
	public ResponseEntity<?> deletePaper(@PathVariable String paperId) throws Exception {
		if (!repo.softDeletePaper(paperId)) {
			return error(HttpStatus.NOT_FOUND, "PAPER_NOT_FOUND", "文献不存在或已删除", false);
		}
		// 设计文档 V0.1 §3.3：删除文献立即禁止检索命中 —— 同步清理 ES 副本
		long purged = retrieval.purgeByPaper(paperId);
		return ResponseEntity.ok(Map.of("deleted", true, "es_purged", purged));
	}

	public record PaperUpdate(String title, String metadata) {
		public PaperUpdate(String title) {
			this(title, "{}");
		}
	}

	@org.springframework.web.bind.annotation.PatchMapping("/api/rp/papers/{paperId}")
	public ResponseEntity<?> updatePaper(@PathVariable String paperId,
			@org.springframework.web.bind.annotation.RequestBody PaperUpdate req) {
		if (req.title() == null || req.title().isBlank() || req.title().length() > 300) {
			return ResponseEntity.badRequest()
				.body(Map.of("code", "INVALID_PAPER_TITLE", "message", "资料名称不能为空且不能超过 300 个字符", "retryable", false));
		}
		String metadata = req.metadata() == null || req.metadata().isBlank() ? "{}" : req.metadata();
		try {
			var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
			var parsed = mapper.readTree(metadata);
			if (parsed == null || !parsed.isObject() || metadata.length() > 16000) {
				throw new IllegalArgumentException("Metadata must be a bounded object");
			}
			for (String field : List.of("authors", "category", "doi")) {
				if (parsed.has(field)
						&& (!parsed.get(field).isTextual() || parsed.get(field).asText().length() > 500)) {
					throw new IllegalArgumentException("Invalid metadata text field");
				}
			}
			if (parsed.has("year") && !parsed.get("year").isNull() && (!parsed.get("year").isIntegralNumber()
					|| parsed.get("year").asInt() < 1500 || parsed.get("year").asInt() > 2100)) {
				throw new IllegalArgumentException("Invalid publication year");
			}
			if (parsed.has("tags")) {
				if (!parsed.get("tags").isArray() || parsed.get("tags").size() > 20) {
					throw new IllegalArgumentException("Invalid tags");
				}
				for (var tag : parsed.get("tags")) {
					if (!tag.isTextual() || tag.asText().isBlank() || tag.asText().length() > 80) {
						throw new IllegalArgumentException("Invalid tag");
					}
				}
			}
			((com.fasterxml.jackson.databind.node.ObjectNode) parsed).put("provenance", "USER_CONFIRMED");
			((com.fasterxml.jackson.databind.node.ObjectNode) parsed).put("confirmed_at",
					java.time.Instant.now().toString());
			metadata = mapper.writeValueAsString(parsed);
		}
		catch (Exception e) {
			return ResponseEntity.badRequest()
				.body(Map.of("code", "INVALID_METADATA", "message", "元数据须为对象；标签最多20个、每个80字；年份须为1500–2100", "retryable",
						false));
		}
		return repo.updatePaper(paperId, req.title().trim(), metadata) ? ResponseEntity.ok(Map.of("updated", true))
				: ResponseEntity.notFound().build();
	}

	// ---------- 检索（B2：混合检索 + Scope 强制过滤，评测 harness 直接打此接口） ----------

	@PostMapping("/api/rp/retrieve")
	public ResponseEntity<?> retrieve(@org.springframework.web.bind.annotation.RequestBody RetrieveRequest req)
			throws Exception {
		if (req.query() == null || req.query().isBlank()) {
			return error(HttpStatus.BAD_REQUEST, "INVALID_QUERY", "query 不能为空", false);
		}
		var candidates = retrieval.hybridRetrieve(req.topicId(), req.paperIds(), req.query(),
				req.topK() == null ? 10 : req.topK(), true);
		var parentMap = req.withParents() != Boolean.FALSE
				? retrieval.expandParents(candidates.stream().map(RpRetrievalService.Candidate::parentId).toList(),
						req.topicId(), req.paperIds())
				: Map.<String, String>of();
		return ResponseEntity.ok(Map.of("candidates", candidates, "parents", parentMap));
	}

	@GetMapping("/api/rp/research/{taskId}/matrix")
	public Map<String, Object> matrix(@PathVariable String taskId) {
		return evidence.buildMatrix(taskId);
	}

	@GetMapping("/api/rp/research/{taskId}/evidence")
	public List<Map<String, Object>> evidence(@PathVariable String taskId) {
		return jdbc.queryForList(
				"SELECT id::text, schema_field_id, field_title, paper_id::text, document_version_id::text, value, status, origin, "
						+ "source_chunk_id, section_path, page, verification, quote_status, claim_status, claim_id, claim_text, claim_index, source_type, source_url FROM rp_evidence WHERE task_id = ? "
						+ "ORDER BY created_at",
				taskId);
	}

	@GetMapping("/api/rp/topics/{topicId}/coverage")
	public Map<String, Long> coverage(@PathVariable String topicId) throws Exception {
		return retrieval.coverage(topicId);
	}

	public record RetrieveRequest(@JsonProperty("topic_id") String topicId,
			@JsonProperty("paper_ids") List<String> paperIds, String query, @JsonProperty("top_k") Integer topK,
			@JsonProperty("with_parents") Boolean withParents) {
	}

	// ---------- 任务状态 ----------

	@GetMapping("/api/rp/jobs/{jobId}")
	public ResponseEntity<?> getJob(@PathVariable String jobId) {
		Map<String, Object> job = repo.getJob(jobId);
		if (job == null) {
			return error(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "任务不存在", false);
		}
		return ResponseEntity.ok(job);
	}

	private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message,
			boolean retryable) {
		return ResponseEntity.status(status).body(Map.of("code", code, "message", message, "retryable", retryable));
	}

}
