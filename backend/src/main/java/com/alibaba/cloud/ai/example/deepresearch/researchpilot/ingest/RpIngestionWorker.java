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

package com.alibaba.cloud.ai.example.deepresearch.researchpilot.ingest;

import com.alibaba.cloud.ai.example.deepresearch.researchpilot.ResearchPilotConfig;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.sidecar.SidecarClient;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.store.RpObjectStore;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.store.RpRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ResearchPilot Phase B：摄取 Worker（@Scheduled 轮询 + SKIP LOCKED 租约认领）。
 *
 * <p>
 * 状态机（平移 Phase 1 Python 设计）：UPLOADED -> PARSING -> READY/PARTIAL/FAILED。
 * 验收红线：FAILED/PARTIAL 必须落库可见，绝不默默 READY。
 */
@Component
public class RpIngestionWorker {

	private static final Logger logger = LoggerFactory.getLogger(RpIngestionWorker.class);

	private final RpRepository repo;

	private final RpObjectStore objectStore;

	private final SidecarClient sidecar;

	private final RpChunker chunker;

	private final RpEsIndexer indexer;

	private final ResearchPilotConfig.KbProperties props;

	private final String workerId;

	public RpIngestionWorker(RpRepository repo, RpObjectStore objectStore, SidecarClient sidecar, RpChunker chunker,
			RpEsIndexer indexer, ResearchPilotConfig.KbProperties props) throws Exception {
		this.repo = repo;
		this.objectStore = objectStore;
		this.sidecar = sidecar;
		this.chunker = chunker;
		this.indexer = indexer;
		this.props = props;
		this.workerId = InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID().toString().substring(0, 8);
		logger.info("RP 摄取 Worker 就绪: {}", workerId);
	}

	@Scheduled(fixedDelay = 2000)
	public void poll() {
		Map<String, Object> job;
		try {
			job = repo.claimQueuedJob(workerId, 600);
		}
		catch (DataAccessException e) {
			logger.warn("认领任务数据库异常: {}", e.getMessage());
			return;
		}
		if (job == null) {
			return;
		}
		String jobId = (String) job.get("id");
		String versionId = (String) job.get("document_version_id");
		int attempts = ((Number) job.get("attempts")).intValue();
		int maxAttempts = ((Number) job.get("max_attempts")).intValue();
		logger.info("认领任务 job={} version={} attempts={}/{}", jobId, versionId, attempts, maxAttempts);

		try {
			process(jobId, versionId);
		}
		catch (Exception e) {
			handleFailure(jobId, versionId, attempts, maxAttempts, e);
		}
	}

	private void process(String jobId, String versionId) throws Exception {
		var ref = repo.getVersionObjectKey(versionId).orElse(null);
		if (ref == null) {
			// 文献已删（级联删除版本）：任务作废
			repo.finishJob(jobId, versionId, false, "FAILED", "FAILED", "document_version 不存在（文献已删除）", null, null,
					new String[0], 0);
			return;
		}

		repo.setStage(jobId, versionId, "PARSING", "PARSING");

		// 1. 解析：PDF/DOCX/PPTX/HTML/图片 → Docling sidecar；TXT/MD → 原生文本合成（无需解析）
		JsonNode normalized = loadParseCheckpoint(versionId);
		if (normalized == null) {
			byte[] raw = objectStore.get(ref.objectKey());
			String keyLower = ref.objectKey().toLowerCase(java.util.Locale.ROOT);
			if (keyLower.endsWith(".txt") || keyLower.endsWith(".md") || keyLower.endsWith(".markdown")) {
				normalized = plainTextDocument(new String(raw, java.nio.charset.StandardCharsets.UTF_8));
			}
			else {
				normalized = sidecar.parse(raw, versionId, ref.objectKey());
			}
		}
		validateParsedDocument(normalized);
		int blockCount = normalized.get("blocks").size();
		List<String> qualityFlags = new ArrayList<>();
		normalized.get("quality_flags").forEach(f -> qualityFlags.add(f.asText()));
		String parserVersion = normalized.path("parser_version").asText("");
		String normalizedKey = "normalized/" + versionId + ".json";
		// Publish the reusable parse before any embedding/indexing calls can fail.
		objectStore.put(normalizedKey, normalized.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
		repo.saveParseCheckpoint(versionId, normalizedKey, parserVersion, qualityFlags.toArray(new String[0]));

		// 2. 结构感知切分（Parent-Child）
		repo.setStage(jobId, versionId, "CHUNKING", "PARSING");
		var scope = repo.getScope(versionId);
		var parentChild = chunker.chunk(normalized, scope.topicId(), scope.paperId(), versionId);
		repo.saveSectionContexts(versionId, parentChild.parents());

		// 3. 批量 embedding
		repo.setStage(jobId, versionId, "EMBEDDING", "PARSING");
		indexer.ensureIndex();
		List<RpChunker.ParentChild.Child> children = parentChild.children();
		List<RpEsIndexer.ChildDoc> esDocs = new ArrayList<>();
		String embeddingIdentity = sidecar.embeddingIdentity();
		var batchCheckpoint = new EmbeddingBatchCheckpoint(objectStore);
		for (int i = 0; i < children.size(); i += props.embedBatchSize()) {
			var slice = children.subList(i, Math.min(children.size(), i + props.embedBatchSize()));
			List<String> texts = slice.stream().map(RpChunker.ParentChild.Child::text).toList();
			List<double[]> vecs = batchCheckpoint.getOrCompute(versionId, embeddingIdentity, props.embedDim(), texts,
					() -> sidecar.embed(texts));
			for (int j = 0; j < slice.size(); j++) {
				var child = slice.get(j);
				esDocs.add(new RpEsIndexer.ChildDoc(child.childId(), child.parentId(), child.text(),
						child.contentType(), child.page(), child.sectionPath(), toList(vecs.get(j))));
			}
		}

		// 4. ES 双路索引（同版本先删后写，重试幂等）
		repo.setStage(jobId, versionId, "INDEXING", "PARSING");
		indexer.indexChunks(scope.topicId(), scope.paperId(), versionId, esDocs);

		// H4 READY 门禁：解析产物 / Chunk 元数据 / 检索索引三者一致才允许 READY，
		// 不一致视为索引部分失败（重试幂等，不会产生重复 chunk）
		long indexed = indexer.countByVersion(versionId);
		if (indexed != esDocs.size()) {
			throw new IllegalStateException("索引计数不一致（READY 门禁拒绝）: expected=" + esDocs.size() + " actual=" + indexed);
		}

		// 5. 终结：按质量标记定级 READY/PARTIAL
		boolean partial = !qualityFlags.isEmpty();
		repo.finishJob(jobId, versionId, true, partial ? "PARTIAL" : "READY", partial ? "PARTIAL" : "READY", null,
				parserVersion, normalizedKey, qualityFlags.toArray(new String[0]), esDocs.size());
		logger.info("job {} 完成: blocks={} chunks={} flags={}", jobId, blockCount, esDocs.size(), qualityFlags);
	}

	private JsonNode loadParseCheckpoint(String versionId) {
		String key = repo.parseCheckpoint(versionId);
		if (key == null || !objectStore.exists(key)) {
			return null;
		}
		try {
			JsonNode saved = new com.fasterxml.jackson.databind.ObjectMapper().readTree(objectStore.get(key));
			validateParsedDocument(saved);
			logger.info("复用解析检查点: version={}", versionId);
			return saved;
		}
		catch (Exception e) {
			logger.warn("解析检查点损坏，重新解析: version={} reason={}", versionId, e.getMessage());
			return null;
		}
	}

	private void validateParsedDocument(JsonNode parsed) {
		if (parsed == null || !parsed.path("blocks").isArray() || !parsed.path("quality_flags").isArray()
				|| parsed.path("parser_version").asText().isBlank()) {
			throw new IllegalArgumentException("解析产物缺少 blocks/quality_flags/parser_version");
		}
	}

	// V1 多模态：TXT/MD 直接按段落合成 blocks（md 标题作为 section_path）
	private JsonNode plainTextDocument(String content) {
		var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		var root = mapper.createObjectNode();
		var blocks = root.putArray("blocks");
		String currentSection = "正文";
		int page = 1;
		for (String para : content.split("\n\\s*\n")) {
			String text = para.strip();
			if (text.isEmpty()) {
				continue;
			}
			if (text.startsWith("#")) {
				String cleaned = text.replaceAll("^#+\\s*", "");
				currentSection = cleaned.isBlank() ? currentSection : cleaned;
				continue;
			}
			var b = blocks.addObject();
			b.put("text", text);
			b.put("section_path", currentSection);
			b.put("content_type", "text");
			b.put("page", page++);
		}
		if (blocks.isEmpty()) {
			var b = blocks.addObject();
			b.put("text", content.strip());
			b.put("section_path", "正文");
			b.put("content_type", "text");
			b.put("page", 1);
		}
		root.put("parser_version", "plaintext-1.0");
		root.putArray("quality_flags");
		return root;
	}

	private List<Double> toList(double[] vec) {
		var list = new ArrayList<Double>(vec.length);
		for (double v : vec) {
			list.add(v);
		}
		return list;
	}

	private void handleFailure(String jobId, String versionId, int attempts, int maxAttempts, Exception e) {
		logger.error("job {} 处理失败: {}", jobId, e.getMessage(), e);
		if (attempts < maxAttempts) {
			// H4：RETRYABLE_FAILED —— 可重试失败，租约与重试计数可见
			repo.requeue(jobId, versionId, e.getMessage());
			return;
		}
		// H4：TERMINAL_FAILED —— 重试耗尽的终态失败（绝不默默 READY）
		repo.finishJob(jobId, versionId, false, "TERMINAL_FAILED", "FAILED", e.getMessage(), null, null, new String[0],
				0);
	}

}
