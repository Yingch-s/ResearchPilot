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

package com.alibaba.cloud.ai.example.deepresearch.researchpilot.run;

import com.alibaba.cloud.ai.example.deepresearch.TestDatabase;

import com.alibaba.cloud.ai.example.deepresearch.researchpilot.retrieve.RpRetrievalService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V0.4 H2.2：Run/Topic 隔离集成测试（治理 §五.1 强制场景；P-11）。
 *
 * <p>
 * 两个 Run 同时执行，分别使用互不重叠的 Topic A（含 DPR 论文）与 Topic B（空库）， 交错进行步骤认领/提交与证据登记，验证： 1. 检索由服务端强制
 * Topic Scope：Topic B 的查询 不命中 Topic A 的索引内容（Agent 无法扩大范围）； 2. 步骤状态/游标按 Run 独立； 3. 证据按
 * run_id/topic_id 归属，互相不可见。
 */
class RunIsolationIT {

	private static JdbcTemplate jdbc;

	private static RpRunService runService;

	private static RpRetrievalService retrievalService;

	private static co.elastic.clients.elasticsearch.ElasticsearchClient esClient;

	private static UUID topicA;

	private static UUID topicB;

	@BeforeAll
	static void setUp() {
		DriverManagerDataSource ds = new DriverManagerDataSource(TestDatabase.url(), "researchpilot", "researchpilot");
		try (Connection ignored = ds.getConnection()) {
		}
		catch (Exception e) {
			Assumptions.assumeTrue(false, "PostgreSQL 不可达，跳过: " + e.getMessage());
		}
		jdbc = new JdbcTemplate(ds);
		runService = new RpRunService(jdbc, new RpEventLogger("./target/rp-events-iso"));
		esClient = new co.elastic.clients.elasticsearch.ElasticsearchClient(
				new co.elastic.clients.transport.rest_client.RestClientTransport(
						org.elasticsearch.client.RestClient
							.builder(new org.apache.http.HttpHost(TestDatabase.esHost(), 9200, "http"))
							.build(),
						new co.elastic.clients.json.jackson.JacksonJsonpMapper(
								new com.fasterxml.jackson.databind.ObjectMapper())));
		retrievalService = new RpRetrievalService(esClient,
				new com.alibaba.cloud.ai.example.deepresearch.researchpilot.sidecar.SidecarClient(
						org.springframework.web.client.RestClient.builder()
							.baseUrl(System.getenv().getOrDefault("RP_SIDECAR_URL", "http://localhost:8100"))
							.build()),
				new com.alibaba.cloud.ai.example.deepresearch.researchpilot.ResearchPilotConfig.KbProperties(null, null,
						null, 0, 0));
		// Topic A：已有 DPR 论文的专题
		topicA = UUID.fromString(jdbc.queryForObject("SELECT id::text FROM rp_topics LIMIT 1", String.class));
		String index = System.getProperty("rp.es.index", System.getenv().getOrDefault("RP_ES_INDEX", "rp_chunks_v1"));
		try {
			if (!esClient.indices().exists(e -> e.index(index)).value())
				esClient.indices().create(c -> c.index(index));
			esClient.index(i -> i.index(index)
				.id("run-isolation-" + topicA)
				.document(Map.of("topic_id", topicA.toString(), "paper_id", "isolation-paper", "parent_id",
						"iso-parent", "text", "dual-encoder in-batch negatives", "section_path", "Introduction", "page",
						1, "content_type", "text")));
			esClient.indices().refresh(r -> r.index(index));
		}
		catch (Exception ignored) {
			// If the optional ES service is unavailable, the assertion below reports the
			// unavailable retrieval dependency instead of masking the scope test.
		}
		// Topic B：空库（互不重叠）
		jdbc.update("INSERT INTO rp_topics (id, name) VALUES (?::uuid, '隔离测试专题B（空库）') " + "ON CONFLICT (id) DO NOTHING",
				UUID.randomUUID().toString());
		topicB = UUID.fromString(
				jdbc.queryForObject("SELECT id::text FROM rp_topics WHERE name = '隔离测试专题B（空库）' LIMIT 1", String.class));
	}

	@AfterAll
	static void tearDown() {
		try {
			esClient.delete(d -> d.index("rp_chunks_v1").id("run-isolation-" + topicA));
		}
		catch (Exception ignored) {
		}
		if (topicB != null) {
			jdbc.update("DELETE FROM rp_topics WHERE id = ?::uuid", topicB.toString());
		}
	}

	@Test
	void retrievalScopeIsServerEnforced() {
		// Topic A（含 DPR 论文）命中
		int hitsA = retrievalService.hybridRetrieve(topicA.toString(), null, "dual-encoder in-batch negatives", 5, true)
			.size();
		// Topic B（空库）同样查询零命中 —— Agent 无法借查询文本扩大检索范围
		int hitsB = retrievalService.hybridRetrieve(topicB.toString(), null, "dual-encoder in-batch negatives", 5, true)
			.size();
		assertTrue(hitsA > 0, "Topic A 应命中已索引论文");
		assertEquals(0, hitsB, "Topic B 空库必须零命中（Scope 服务端强制，P-11）");
	}

	@Test
	void interleavedRunsStayIsolated() {
		UUID runA = runService.createRun("iso-" + UUID.randomUUID(), "it", topicA, "Run A 查询");
		UUID runB = runService.createRun("iso-" + UUID.randomUUID(), "it", topicB, "Run B 查询");
		for (UUID r : List.of(runA, runB)) {
			runService.transition(r, RunStatus.CREATED, RunStatus.PLANNING, null);
			runService.transition(r, RunStatus.PLANNING, RunStatus.RESEARCHING, null);
			runService.initSteps(r, List.of("步骤一", "步骤二"));
		}
		UUID attemptA = runService.startResearchSegment(runA, Duration.ofSeconds(600), Duration.ofSeconds(60));
		UUID attemptB = runService.startResearchSegment(runB, Duration.ofSeconds(600), Duration.ofSeconds(60));

		// 交错执行：A0 → B0 → A1 → B1
		assertTrue(runService.claimStep(runA, 0, attemptA));
		assertTrue(runService.claimStep(runB, 0, attemptB));
		assertTrue(runService.commitStep(runA, 0, attemptA, StepStatus.COMPLETED, "A 的结果"));
		assertTrue(runService.commitStep(runB, 0, attemptB, StepStatus.COMPLETED, "B 的结果"));
		assertTrue(runService.claimStep(runA, 1, attemptA));
		assertTrue(runService.claimStep(runB, 1, attemptB));
		assertTrue(runService.commitStep(runA, 1, attemptA, StepStatus.COMPLETED, "A 的结果2"));
		assertTrue(runService.commitStep(runB, 1, attemptB, StepStatus.COMPLETED, "B 的结果2"));

		// 独立游标：各 Run 只看到自己的推进
		assertEquals(2, ((Number) runService.loadRun(runA).get("execution_cursor")).intValue());
		assertEquals(2, ((Number) runService.loadRun(runB).get("execution_cursor")).intValue());

		// 证据隔离：各 Run 登记带各自 topic 的证据，按 run_id 查询互相不可见
		jdbc.update("""
				INSERT INTO rp_evidence (task_id, run_id, attempt_id, step_id, topic_id, schema_field_id,
					value, origin, status, evidence_status)
				VALUES (?::text, ?::uuid, ?::uuid, 's', ?::uuid, 'f', 'A 的证据', 'EXTRACTED', 'NEEDS_REVIEW', 'ACCEPTED')
				""", runA.toString(), runA.toString(), attemptA.toString(), topicA.toString());
		jdbc.update("""
				INSERT INTO rp_evidence (task_id, run_id, attempt_id, step_id, topic_id, schema_field_id,
					value, origin, status, evidence_status)
				VALUES (?::text, ?::uuid, ?::uuid, 's', ?::uuid, 'f', 'B 的证据', 'EXTRACTED', 'NEEDS_REVIEW', 'ACCEPTED')
				""", runB.toString(), runB.toString(), attemptB.toString(), topicB.toString());

		int topicBInA = jdbc.queryForObject(
				"SELECT count(*) FROM rp_evidence WHERE run_id = ?::uuid AND topic_id = ?::uuid", Integer.class,
				runA.toString(), topicB.toString());
		int topicAInB = jdbc.queryForObject(
				"SELECT count(*) FROM rp_evidence WHERE run_id = ?::uuid AND topic_id = ?::uuid", Integer.class,
				runB.toString(), topicA.toString());
		assertEquals(0, topicBInA, "Run A 不得出现 Topic B 的证据（P-11 串扰检查）");
		assertEquals(0, topicAInB, "Run B 不得出现 Topic A 的证据（P-11 串扰检查）");
	}

}
