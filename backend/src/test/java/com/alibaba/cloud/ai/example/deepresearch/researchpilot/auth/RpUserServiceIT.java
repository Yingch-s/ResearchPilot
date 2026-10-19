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

import com.alibaba.cloud.ai.example.deepresearch.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0-A：用户服务 —— BCrypt 哈希存储、唯一用户名、管理员种子行存在。
 */
class RpUserServiceIT {

	private static JdbcTemplate jdbc;

	private static RpUserService service;

	@BeforeAll
	static void setup() {
		DriverManagerDataSource ds;
		try {
			ds = new DriverManagerDataSource(TestDatabase.url(), "researchpilot", "researchpilot");
			jdbc = new JdbcTemplate(ds);
			jdbc.execute("SELECT 1");
		}
		catch (Exception e) {
			Assumptions.assumeTrue(false, "测试库不可用: " + e.getMessage());
			return;
		}
		service = new RpUserService(jdbc, new BCryptPasswordEncoder());
	}

	@AfterEach
	void clean() {
		jdbc.update("DELETE FROM rp_users WHERE username LIKE 'u-it-%'");
	}

	@Test
	void createUserStoresHashNotPassword() {
		UUID id = service.createUser("u-it-alice", "plain-secret");
		assertTrue(service.findById(id).isPresent());
		String hash = jdbc.queryForObject("SELECT password_hash FROM rp_users WHERE id = ?", String.class, id);
		assertNotEquals("plain-secret", hash);
		assertTrue(hash.startsWith("$2"), "应为 BCrypt 哈希");
		assertTrue(new BCryptPasswordEncoder().matches("plain-secret", hash));
	}

	@Test
	void duplicateUsernameRejected() {
		service.createUser("u-it-dup", "pw-x-1234");
		assertThrows(IllegalArgumentException.class, () -> service.createUser("u-it-dup", "pw-y-5678"));
	}

	@Test
	void findByUsernameAndPasswordVerifies() {
		service.createUser("u-it-bob", "pw-bob");
		Optional<RpUserService.RpUser> ok = service.authenticate("u-it-bob", "pw-bob");
		assertTrue(ok.isPresent());
		assertEquals("u-it-bob", ok.get().username());
		assertTrue(service.authenticate("u-it-bob", "wrong").isEmpty());
		assertFalse(service.authenticate("u-it-nobody", "x").isPresent());
	}

	@Test
	void adminSeedExists() {
		Integer n = jdbc.queryForObject("SELECT count(*) FROM rp_users WHERE username = 'admin'", Integer.class);
		assertEquals(1, n);
	}

}
