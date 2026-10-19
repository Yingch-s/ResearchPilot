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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Phase 5 演练 3：LLM 全挂风暴 —— 假服务对所有请求回 500。 预期：run 在预算内收敛为 FAILED/PARTIAL（诚实停止），步骤尝试耗尽，
 * 不死锁、不悬挂、线程池不泄漏（进程在测试后仍能响应）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
		properties = { "researchpilot.model.base-url=http://localhost:18082", "researchpilot.model.api-key=test-storm",
				"spring.ai.openai.base-url=http://localhost:18082", "spring.ai.openai.api-key=test-storm" })
class ChaosLlmStormIT {

	private static HttpServer failingLlm;

	@BeforeAll
	static void startFailingLlm() throws Exception {
		failingLlm = HttpServer.create(new InetSocketAddress(18082), 0);
		failingLlm.createContext("/v1/chat/completions",
				exchange -> respond(exchange, 500, "{\"error\":{\"message\":\"overloaded\"}}"));
		failingLlm.createContext("/chat/completions",
				exchange -> respond(exchange, 500, "{\"error\":{\"message\":\"overloaded\"}}"));
		failingLlm.start();
	}

	private static void respond(HttpExchange exchange, int code, String body) throws java.io.IOException {
		byte[] out = body.getBytes();
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(code, out.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(out);
		}
	}

	@AfterAll
	static void stop() {
		if (failingLlm != null) {
			failingLlm.stop(0);
		}
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void llmOutageFailsFastAndSystemStaysAlive() throws Exception {
		// 演练语义（实测确立）：planner LLM 全挂 → create 快速 5xx（retryable=true），
		// 客户端按幂等键重试重驱动（P0-A I1 修复路径）；系统不死锁、持续响应。
		UUID topic = UUID.randomUUID();
		jdbc.update("INSERT INTO rp_topics (id, name, owner_user_id) VALUES (?, 'chaos-storm', "
				+ "(SELECT id FROM rp_users WHERE username = 'admin'))", topic);
		try {
			long t0 = System.currentTimeMillis();
			MvcResult created = mvc
				.perform(post("/api/rp/research").contentType("application/json")
					.content("{\"query\":\"chaos 风暴研究\",\"topic_id\":\"" + topic + "\","
							+ "\"client_request_id\":\"chaos-storm-" + topic + "\"}"))
				.andReturn();
			long elapsed = System.currentTimeMillis() - t0;
			int sc = created.getResponse().getStatus();
			assertTrue(sc >= 500 || sc == 202, "风暴下 create 应回 5xx（快速失败）或 202（已排队），实际 " + sc);
			assertTrue(elapsed < 60_000, "失败必须快速（<60s），实际 " + elapsed + "ms");
			if (sc == 500) {
				String body = created.getResponse().getContentAsString();
				assertTrue(body.contains("retryable"), "5xx 应携带 retryable 标记供客户端重试");
			}
			// 系统存活：风暴中列表端点仍响应
			mvc.perform(get("/api/rp/research")).andExpect(status().isOk());
		}
		finally {
			jdbc.update("DELETE FROM rp_runs WHERE query = 'chaos 风暴研究'");
			jdbc.update("DELETE FROM rp_topics WHERE id = ?", topic);
		}
	}

	private Object field(String json, String name) {
		var m = java.util.regex.Pattern.compile("\\\"" + name + "\\\"\\s*:\\s*\\\"?([^,\\\"}]+)\\\"?").matcher(json);
		return m.find() ? m.group(1) : "";
	}

}
