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

package com.alibaba.cloud.ai.example.deepresearch.researchpilot.auth;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0-A Review Focus 1/4：归属隔离 —— 越权 404（不泄露存在性）、列表只见自己的。 直插两个用户的 run（不走 LLM 创建流程），登录后逐项断言。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "researchpilot.auth.mode=on" })
class RpOwnershipIT {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private static UUID runA;

	private static UUID runB;

	private static UUID topicA;

	private static String userA;

	private static String userB;

	@BeforeAll
	static void seed(@Autowired JdbcTemplate jdbc) {
		userA = "owner-a-" + UUID.randomUUID().toString().substring(0, 6);
		userB = "owner-b-" + UUID.randomUUID().toString().substring(0, 6);
		String hash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("pw-seed-123");
		UUID idA = jdbc.queryForObject("INSERT INTO rp_users (username, password_hash) VALUES (?, ?) RETURNING id",
				UUID.class, userA, hash);
		UUID idB = jdbc.queryForObject("INSERT INTO rp_users (username, password_hash) VALUES (?, ?) RETURNING id",
				UUID.class, userB, hash);
		topicA = UUID.randomUUID();
		runA = UUID.randomUUID();
		runB = UUID.randomUUID();
		jdbc.update("INSERT INTO rp_topics (id, name, owner_user_id) VALUES (?, 'own-it-a', ?)", topicA, idA);
		jdbc.update("""
				INSERT INTO rp_runs (id, thread_id, query, status, owner_user_id, topic_id)
				VALUES (?, 'own-it-a', 'q', 'COMPLETED', ?, ?)
				""", runA, idA, topicA);
		jdbc.update("""
				INSERT INTO rp_runs (id, thread_id, query, status, owner_user_id, topic_id)
				VALUES (?, 'own-it-b', 'q', 'COMPLETED', ?, NULL)
				""", runB, idB);
	}

	@AfterAll
	static void clean(@Autowired JdbcTemplate jdbc) {
		jdbc.update("DELETE FROM rp_runs WHERE thread_id LIKE 'own-it-%'");
		jdbc.update("DELETE FROM rp_topics WHERE name = 'own-it-a'");
		jdbc.update("DELETE FROM rp_users WHERE username IN (?, ?)", userA, userB);
	}

	private MockHttpSession login(String username) throws Exception {
		MvcResult result = mvc
			.perform(post("/api/rp/auth/login").contentType("application/json")
				.content("{\"username\":\"" + username + "\",\"password\":\"pw-seed-123\"}"))
			.andExpect(status().isOk())
			.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	@Test
	void foreignRunIs404() throws Exception {
		mvc.perform(get("/api/rp/research/" + runB).session(login(userA))).andExpect(status().isNotFound());
	}

	@Test
	void ownRunIs200() throws Exception {
		mvc.perform(get("/api/rp/research/" + runA).session(login(userA))).andExpect(status().isOk());
	}

	@Test
	void foreignStopIs404() throws Exception {
		mvc.perform(post("/api/rp/research/" + runB + "/stop").session(login(userA))).andExpect(status().isNotFound());
	}

	@Test
	void foreignShareIs404() throws Exception {
		// C2：分享是读他人报告的捷径，必须过归属守卫
		mvc.perform(post("/api/rp/research/" + runB + "/share").session(login(userA))).andExpect(status().isNotFound());
	}

	@Test
	void foreignEventsAndPlanVersionsAre404() throws Exception {
		// I3：事件流/计划版本泄露研究过程数据
		mvc.perform(get("/api/rp/research/" + runB + "/events").session(login(userA))).andExpect(status().isNotFound());
		mvc.perform(get("/api/rp/research/" + runB + "/plan-versions").session(login(userA)))
			.andExpect(status().isNotFound());
		mvc.perform(get("/api/rp/research/" + runB + "/report-versions").session(login(userA)))
			.andExpect(status().isNotFound());
	}

	@Test
	void foreignWritesAre404() throws Exception {
		// I4：取消/暂停/重试/审批他人 run 一律 404
		mvc.perform(post("/api/rp/research/" + runB + "/reject").session(login(userA)))
			.andExpect(status().isNotFound());
		mvc.perform(post("/api/rp/research/" + runB + "/pause").session(login(userA))).andExpect(status().isNotFound());
		mvc.perform(post("/api/rp/research/" + runB + "/steps/0/retry").session(login(userA)))
			.andExpect(status().isNotFound());
	}

	@Test
	void listShowsOnlyOwnRuns() throws Exception {
		String body = mvc.perform(get("/api/rp/research").session(login(userA)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertTrue(body.contains(runA.toString()), "自己的 run 应可见");
		assertFalse(body.contains(runB.toString()), "他人的 run 不可见");
	}

	@Test
	void pageShowsOnlyOwnRuns() throws Exception {
		String body = mvc.perform(get("/api/rp/research/page").session(login(userA)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertTrue(body.contains(runA.toString()));
		assertFalse(body.contains(runB.toString()));
	}

}
