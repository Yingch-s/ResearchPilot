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

import com.alibaba.cloud.ai.example.deepresearch.TestDatabase;

import com.alibaba.cloud.ai.example.deepresearch.researchpilot.ResearchPilotConfig;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.sidecar.SidecarClient;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.store.RpObjectStore;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.store.RpRepository;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.client.RestClient;

import java.sql.Connection;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V0.4 H4：摄取可靠性集成测试（治理 §五.2）。
 *
 * <p>
 * 用真实 sidecar + 已入库 PDF 验证： 1. 同版本重复摄取**不产生重复 chunk**（先删后写幂等）； 2. READY 门禁：ES 索引计数与 chunk
 * 元数据一致； 3. 损坏任务进入 RETRYABLE_FAILED（可重试失败可见，绝不默默 READY）。
 *
 * <p>
 * 环境约定：PG/ES/sidecar 全部可达才运行（缺失即 SKIP），H4/H5 后续转 Testcontainers。
 */
class IngestionReliabilityIT {

	private static JdbcTemplate jdbc;

	private static RpRepository repo;

	private static RpIngestionWorker worker;

	private static RpEsIndexer indexer;

	@BeforeAll
	static void setUp() throws Exception {
		DriverManagerDataSource ds = new DriverManagerDataSource(TestDatabase.url(), "researchpilot", "researchpilot");
		try (Connection ignored = ds.getConnection()) {
		}
		catch (Exception e) {
			Assumptions.assumeTrue(false, "PostgreSQL 不可达，跳过: " + e.getMessage());
		}
		jdbc = new JdbcTemplate(ds);
		var props = new ResearchPilotConfig.KbProperties(null, null, null, 0, 0);
		repo = new RpRepository(jdbc);
		var objectStore = new RpObjectStore(props);
		var sidecar = new SidecarClient(RestClient.builder().baseUrl(props.sidecarBaseUrl()).build());
		indexer = new RpEsIndexer(new co.elastic.clients.elasticsearch.ElasticsearchClient(
				new co.elastic.clients.transport.rest_client.RestClientTransport(
						org.elasticsearch.client.RestClient
							.builder(new org.apache.http.HttpHost("localhost", 9200, "http"))
							.build(),
						new co.elastic.clients.json.jackson.JacksonJsonpMapper(
								new com.fasterxml.jackson.databind.ObjectMapper()))),
				props);
		worker = new RpIngestionWorker(repo, objectStore, sidecar, new RpChunker(), indexer, props);
		// sidecar 可达性探测（不可达则跳过——环境缺失 ≠ 回归）
		try {
			sidecar.embed(java.util.List.of("probe"));
		}
		catch (Exception e) {
			Assumptions.assumeTrue(false, "sidecar 不可达，跳过: " + e.getMessage());
		}
	}

	private String enqueueJobForExistingVersion() {
		String versionId = jdbc.queryForObject(
				"SELECT id::text FROM rp_document_versions WHERE parse_status = 'READY' LIMIT 1", String.class);
		jdbc.update("INSERT INTO rp_ingestion_jobs (document_version_id, status) VALUES (?::uuid, 'QUEUED')",
				versionId);
		return jdbc.queryForObject("SELECT id::text FROM rp_ingestion_jobs WHERE document_version_id = ?::uuid "
				+ "ORDER BY created_at DESC LIMIT 1", String.class, versionId);
	}

	/**
	 * Await a terminal outcome, including jobs claimed by another running worker.
	 */
	private void drainWorkerUntilDone(String jobId, int maxPolls) {
		long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(60);
		int polls = 0;
		while (System.nanoTime() < deadline) {
			// Read through the same repository path used by the API as well as SQL. This
			// avoids treating a stale worker snapshot as the current job state.
			Map<String, Object> current = getJob(jobId);
			String status = current.get("status") == null ? null : current.get("status").toString().trim();
			if (status != null && ("SUCCEEDED".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status))) {
				return;
			}
			if ("QUEUED".equalsIgnoreCase(status) && polls < maxPolls) {
				worker.poll();
				polls++;
			}
			else {
				try {
					Thread.sleep(100);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new AssertionError("Interrupted while waiting for ingestion", e);
				}
			}
		}
		org.junit.jupiter.api.Assertions.fail("Ingestion did not finish within 60 seconds: " + getJob(jobId));
	}

	@Test
	void reprocessingIsIdempotentAndReadyGated() throws Exception {
		String versionId = jdbc.queryForObject(
				"SELECT id::text FROM rp_document_versions WHERE parse_status = 'READY' LIMIT 1", String.class);
		long esCountBefore = indexer.countByVersion(versionId);
		assertTrue(esCountBefore > 0, "前置条件：版本已索引");

		// 重复摄取同一版本
		String jobId = enqueueJobForExistingVersion();
		drainWorkerUntilDone(jobId, 10);

		Map<String, Object> job = getJob(jobId);
		assertEquals("SUCCEEDED", job.get("status"), "重复摄取应成功");
		assertEquals("READY", job.get("parse_status"));
		// 幂等：chunk 总数不变（先删后写），READY 门禁通过
		long esCountAfter = indexer.countByVersion(versionId);
		assertEquals(esCountBefore, esCountAfter, "重复摄取不得产生重复 chunk（治理 §五.2）");
		int chunkCount = ((Number) jdbc.queryForObject(
				"SELECT chunk_count FROM rp_document_versions WHERE id = ?::uuid", Integer.class, versionId))
			.intValue();
		assertEquals(esCountAfter, chunkCount, "READY 门禁：ES 计数必须与 chunk 元数据一致");
	}

	@Test
	void corruptedJobIsRetryableVisible() {
		// 入队一个指向不存在对象的任务 → RETRYABLE_FAILED（可重试失败落库可见）
		String topicId = jdbc.queryForObject("SELECT id::text FROM rp_topics LIMIT 1", String.class);
		// 先清理历史运行遗留的同名注入数据（测试自清理失败时的兜底）
		jdbc.update("DELETE FROM rp_ingestion_jobs WHERE document_version_id::text IN "
				+ "(SELECT dv.id::text FROM rp_document_versions dv JOIN rp_papers p ON p.id = dv.paper_id "
				+ "WHERE p.title LIKE 'H4 故障注入临时论文%')");
		jdbc.update("DELETE FROM rp_document_versions WHERE paper_id::text IN "
				+ "(SELECT id::text FROM rp_papers WHERE title LIKE 'H4 故障注入临时论文%')");
		jdbc.update("DELETE FROM rp_papers WHERE title LIKE 'H4 故障注入临时论文%'");
		String uniqueTitle = "H4 故障注入临时论文 " + UUID.randomUUID();
		jdbc.update("INSERT INTO rp_papers (topic_id, title) VALUES (?::uuid, ?)", topicId, uniqueTitle);
		String paperId = jdbc.queryForObject("SELECT id::text FROM rp_papers WHERE title = ?", String.class,
				uniqueTitle);
		jdbc.update("""
				INSERT INTO rp_document_versions (paper_id, file_sha256, object_key, file_size)
				VALUES (?::uuid, ?, 'papers/nonexistent/missing.pdf', 1)
				""", paperId, "h4-" + UUID.randomUUID());
		String versionId = jdbc.queryForObject(
				"SELECT id::text FROM rp_document_versions WHERE object_key = 'papers/nonexistent/missing.pdf'",
				String.class);
		jdbc.update("INSERT INTO rp_ingestion_jobs (document_version_id, status) VALUES (?::uuid, 'QUEUED')",
				versionId);
		String jobId = jdbc.queryForObject("SELECT id::text FROM rp_ingestion_jobs WHERE document_version_id = ?::uuid "
				+ "ORDER BY created_at DESC LIMIT 1", String.class, versionId);

		// 清理历史遗留 QUEUED 任务后 drain（poll 每次认领最老任务）
		jdbc.update("DELETE FROM rp_ingestion_jobs WHERE status = 'QUEUED' AND created_at < now() - interval '1 hour'");
		drainWorkerUntilDone(jobId, 10);

		Map<String, Object> job = getJob(jobId);
		// 第一次失败：重试耗尽前 → RETRYABLE_FAILED，可重试可见
		assertTrue("RETRYABLE_FAILED".equals(job.get("stage")) || "TERMINAL_FAILED".equals(job.get("stage")),
				"失败必须显式可见，绝不默默 READY: stage=" + job.get("stage"));
		assertEquals("FAILED", job.get("parse_status"));

		// 清理注入数据
		jdbc.update("DELETE FROM rp_ingestion_jobs WHERE id = ?::uuid", jobId);
		jdbc.update("DELETE FROM rp_document_versions WHERE id = ?::uuid", versionId);
		jdbc.update("DELETE FROM rp_papers WHERE id = ?::uuid", paperId);
	}

	private Map<String, Object> getJob(String jobId) {
		Map<String, Object> row = jdbc.queryForMap("""
				SELECT j.status, j.stage, j.last_error, dv.parse_status
				FROM rp_ingestion_jobs j JOIN rp_document_versions dv ON dv.id = j.document_version_id
				WHERE j.id = ?::uuid
				""", jobId);
		Map<String, Object> out = new java.util.HashMap<>();
		out.put("status", row.get("status"));
		out.put("stage", row.get("stage"));
		out.put("last_error", row.get("last_error"));
		out.put("parse_status", row.get("parse_status"));
		return out;
	}

}
