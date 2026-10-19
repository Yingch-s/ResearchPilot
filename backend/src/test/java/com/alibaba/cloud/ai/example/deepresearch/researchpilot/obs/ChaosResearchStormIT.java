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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Phase 5 演练 3b（审查 I7）：研究段风暴 —— 规划成功后所有研究段 LLM 调用 500。 spec 语义：步骤 attempt 耗尽 → run
 * PARTIAL_COMPLETED + 未完成项披露（不编造）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
		properties = { "researchpilot.model.base-url=http://localhost:18083", "researchpilot.model.api-key=test-storm2",
				"spring.ai.openai.base-url=http://localhost:18083", "spring.ai.openai.api-key=test-storm2" })
class ChaosResearchStormIT {

	private static HttpServer flakyLlm;

	private static final AtomicInteger CALLS = new AtomicInteger();

	@BeforeAll
	static void startFlakyLlm() throws Exception {
		flakyLlm = HttpServer.create(new InetSocketAddress(18083), 0);
		flakyLlm.createContext("/v1/chat/completions", ChaosResearchStormIT::handle);
		flakyLlm.createContext("/chat/completions", ChaosResearchStormIT::handle);
		flakyLlm.start();
	}

	/** 第 1 次（planner）成功；其后（研究段）全部 500。 */
	private static void handle(HttpExchange exchange) throws java.io.IOException {
		int call = CALLS.incrementAndGet();
		if (call == 1) {
			String content = "{\"steps\":[{\"title\":\"步骤一：梳理\"},{\"title\":\"步骤二：对比\"}],"
					+ "\"dimensions\":[\"核心\"],\"clarifying_questions\":[]}";
			String resp = "{\"id\":\"s\",\"object\":\"chat.completion\",\"created\":0,\"model\":\"stub\","
					+ "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":" + quote(content)
					+ "},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":10,"
					+ "\"completion_tokens\":5,\"total_tokens\":15}}";
			write(exchange, 200, resp);
			return;
		}
		write(exchange, 500, "{\"error\":{\"message\":\"overloaded\"}}");
	}

	private static void write(HttpExchange exchange, int code, String resp) throws java.io.IOException {
		byte[] out = resp.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(code, out.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(out);
		}
	}

	private static String quote(String s) {
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	@AfterAll
	static void stop() {
		if (flakyLlm != null) {
			flakyLlm.stop(0);
		}
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void researchStageStormConvergesToPartial() throws Exception {
		UUID topic = UUID.randomUUID();
		jdbc.update("INSERT INTO rp_topics (id, name, owner_user_id) VALUES (?, 'chaos-rstorm', "
				+ "(SELECT id FROM rp_users WHERE username = 'admin'))", topic);
		String runId = "";
		try {
			MvcResult created = mvc
				.perform(post("/api/rp/research").contentType("application/json")
					.content("{\"query\":\"研究段风暴演练\",\"topic_id\":\"" + topic + "\","
							+ "\"client_request_id\":\"chaos-rstorm-" + topic + "\"}"))
				.andReturn();
			runId = String.valueOf(field(created.getResponse().getContentAsString(), "run_id"));
			assertTrue(runId.length() > 10, "create 应成功（planner 成功）: " + created.getResponse().getContentAsString());

			String status = "";
			String body = "";
			long deadline = System.currentTimeMillis() + 600_000;
			while (System.currentTimeMillis() < deadline) {
				Thread.sleep(3000);
				MvcResult s = mvc.perform(get("/api/rp/research/" + runId)).andReturn();
				body = s.getResponse().getContentAsString();
				status = String.valueOf(field(body, "status"));
				if (java.util.List.of("COMPLETED", "PARTIAL_COMPLETED", "FAILED").contains(status)) {
					break;
				}
			}
			assertEquals("PARTIAL_COMPLETED", status, "研究段风暴应收敛为 PARTIAL（步骤尝试耗尽+披露），body=" + body);
			MvcResult report = mvc.perform(get("/api/rp/research/" + runId + "/report")).andReturn();
			String reportBody = report.getResponse().getContentAsString();
			assertTrue(reportBody.contains("未完成") || reportBody.contains("局限"), "报告应披露未完成项");
		}
		finally {
			jdbc.update("DELETE FROM rp_reports WHERE run_id IN (SELECT id FROM rp_runs WHERE query = '研究段风暴演练')");
			jdbc.update("DELETE FROM rp_run_steps WHERE run_id IN (SELECT id FROM rp_runs WHERE query = '研究段风暴演练')");
			jdbc.update("DELETE FROM rp_runs WHERE query = '研究段风暴演练'");
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
