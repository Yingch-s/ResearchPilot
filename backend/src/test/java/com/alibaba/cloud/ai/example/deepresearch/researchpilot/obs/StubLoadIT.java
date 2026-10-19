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

package com.alibaba.cloud.ai.example.deepresearch.researchpilot.obs;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * P1-D：mock LLM 端到端 —— 在 OpenAI API 边界做假服务（可控延迟）， 全管道（planning→research→finalize）无真实 API
 * 走完。
 * <p>
 * 可信度边界：只代表调度/DB/队列路径；不反映模型延迟分布、限流与重试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "researchpilot.model.base-url=http://localhost:18085",
		"researchpilot.model.api-key=test-stub-key", "spring.ai.openai.base-url=http://localhost:18085",
		"spring.ai.openai.api-key=test-stub-key" })
class StubLoadIT {

	private static HttpServer fakeOpenAi;

	@BeforeAll
	static void startFakeLlm() throws Exception {
		fakeOpenAi = HttpServer.create(new InetSocketAddress(18085), 0);
		fakeOpenAi.createContext("/v1/chat/completions", StubLoadIT::handle);
		fakeOpenAi.createContext("/chat/completions", StubLoadIT::handle);
		fakeOpenAi.start();
	}

	private static void handle(HttpExchange exchange) throws java.io.IOException {
		byte[] body = exchange.getRequestBody().readAllBytes();
		String req = new String(body, StandardCharsets.UTF_8);
		String content = req.contains("步骤")
				? "{\"steps\":[{\"title\":\"步骤一：梳理核心方法\"},{\"title\":\"步骤二：对比策略\"},"
						+ "{\"title\":\"步骤三：汇总演进\"},{\"title\":\"步骤四：核验来源\"}],"
						+ "\"dimensions\":[\"核心方法\",\"实验设置\"],\"clarifying_questions\":[]}"
				: "（stub 研究发现）依据资料片段归纳，结论有直接支撑。";
		String resp = "{\"id\":\"stub\",\"object\":\"chat.completion\",\"created\":0,\"model\":\"stub-model\","
				+ "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":" + json(content)
				+ "},\"finish_reason\":\"stop\"}],"
				+ "\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":50,\"total_tokens\":150}}";
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		byte[] out = resp.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(200, out.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(out);
		}
	}

	private static String json(String s) {
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
	}

	@AfterAll
	static void stopFakeLlm() {
		if (fakeOpenAi != null) {
			fakeOpenAi.stop(0);
		}
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private io.micrometer.core.instrument.MeterRegistry meterRegistry;

	@Test
	void fullPipelineCompletesWithFakeLlm() throws Exception {
		UUID topic = UUID.randomUUID();
		jdbc.update("INSERT INTO rp_topics (id, name, owner_user_id) VALUES (?, 'stub-e2e', "
				+ "(SELECT id FROM rp_users WHERE username = 'admin'))", topic);
		try {
			MvcResult created = mvc
				.perform(post("/api/rp/research").contentType("application/json")
					.content("{\"query\":\"stub 端到端研究\",\"topic_id\":\"" + topic + "\","
							+ "\"client_request_id\":\"stub-e2e-" + topic + "\"}"))
				.andReturn();
			String runId = String.valueOf(field(created.getResponse().getContentAsString(), "run_id"));
			assertTrue(runId.length() > 10, "应返回 run_id: " + created.getResponse().getContentAsString());

			String status = "";
			String lastBody = "";
			for (int i = 0; i < 60; i++) {
				Thread.sleep(1000);
				MvcResult s = mvc.perform(get("/api/rp/research/" + runId)).andReturn();
				lastBody = s.getResponse().getContentAsString();
				status = String.valueOf(field(lastBody, "status"));
				if (java.util.List.of("COMPLETED", "PARTIAL_COMPLETED", "FAILED").contains(status)) {
					break;
				}
			}
			assertEquals("COMPLETED", status, "mock 管道应完整收敛（无真实 API）；lastBody=" + lastBody);
			MvcResult report = mvc.perform(get("/api/rp/research/" + runId + "/report")).andReturn();
			assertTrue(report.getResponse().getContentAsString().contains("stub"), "报告应包含 mock 内容");
			// I5：研究段 token 必须计入 Micrometer（研究段是消耗主体）
			double total = meterRegistry.find("rp_llm_tokens_total")
				.counters()
				.stream()
				.mapToDouble(c -> c.count())
				.sum();
			assertTrue(total > 0, "研究段 token 计数应 > 0，实际 " + total);
		}
		finally {
			jdbc.update("DELETE FROM rp_reports WHERE run_id IN (SELECT id FROM rp_runs WHERE query = 'stub 端到端研究')");
			jdbc.update("DELETE FROM rp_run_steps WHERE run_id IN (SELECT id FROM rp_runs WHERE query = 'stub 端到端研究')");
			jdbc.update("DELETE FROM rp_runs WHERE query = 'stub 端到端研究'");
			jdbc.update("DELETE FROM rp_topics WHERE id = ?", topic);
		}
	}

	private Object field(String json, String name) {
		try {
			return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).path(name).asText("");
		}
		catch (Exception e) {
			return "";
		}
	}

}
