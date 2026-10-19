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

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0-A：on 模式认证流程 —— 注册→登录→me→登出；错密码 401；明文/哈希不进响应。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "researchpilot.auth.mode=on" })
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RpAuthControllerIT {

	@Autowired
	private MockMvc mvc;

	// 每次运行随机用户名：用户表跨运行持久，注册流程测试不可依赖特定名字
	private static final String USER = "auth-it-" + java.util.UUID.randomUUID().toString().substring(0, 8);

	@Test
	@Order(1)
	void unauthenticatedIs401() throws Exception {
		mvc.perform(get("/api/rp/research")).andExpect(status().isUnauthorized());
	}

	@Test
	@Order(2)
	void registerLoginMeLogoutFlow() throws Exception {
		mvc.perform(post("/api/rp/auth/register").contentType("application/json")
			.content("{\"username\":\"" + USER + "\",\"password\":\"pw-it-123456\"}")).andExpect(status().isOk());
		// 重复注册 → 409
		mvc.perform(post("/api/rp/auth/register").contentType("application/json")
			.content("{\"username\":\"" + USER + "\",\"password\":\"pw-it-123456\"}")).andExpect(status().isConflict());

		MvcResult login = mvc
			.perform(post("/api/rp/auth/login").contentType("application/json")
				.content("{\"username\":\"" + USER + "\",\"password\":\"pw-it-123456\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value(USER))
			// 明文与哈希不进响应
			.andExpect(jsonPath("$.password").doesNotExist())
			.andExpect(jsonPath("$.password_hash").doesNotExist())
			.andReturn();
		// MockMvc 下用 session 对象延续会话（等价于浏览器携带 JSESSIONID）
		var session = (org.springframework.mock.web.MockHttpSession) login.getRequest().getSession(false);
		mvc.perform(get("/api/rp/auth/me").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value(USER));

		mvc.perform(get("/api/rp/research").session(session)).andExpect(status().isOk());

		mvc.perform(post("/api/rp/auth/logout").session(session)).andExpect(status().isOk());
		mvc.perform(get("/api/rp/auth/me").session(session)).andExpect(status().isUnauthorized());
	}

	@Test
	@Order(3)
	void wrongPasswordIs401AndNoSecretInBody() throws Exception {
		String body = mvc
			.perform(post("/api/rp/auth/login").contentType("application/json")
				.content("{\"username\":\"" + USER + "\",\"password\":\"totally-wrong\"}"))
			.andExpect(status().isUnauthorized())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertFalse(body.contains("pw-it-123456"));
	}

}
