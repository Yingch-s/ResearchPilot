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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * P0-A：用户服务 —— BCrypt 哈希存储、唯一用户名、认证校验。 明文密码只在方法入参存活，不落库不打日志。
 */
@Service
public class RpUserService {

	public record RpUser(UUID id, String username, String role) {
	}

	private final JdbcTemplate jdbc;

	private final PasswordEncoder encoder;

	public RpUserService(JdbcTemplate jdbc, PasswordEncoder encoder) {
		this.jdbc = jdbc;
		this.encoder = encoder;
	}

	public UUID createUser(String username, String rawPassword) {
		if (username == null || username.isBlank() || rawPassword == null || rawPassword.length() < 6) {
			throw new IllegalArgumentException("用户名不能为空且密码至少 6 位");
		}
		Integer exists = jdbc.queryForObject("SELECT count(*) FROM rp_users WHERE username = ?", Integer.class,
				username);
		if (exists != null && exists > 0) {
			throw new IllegalArgumentException("用户名已存在");
		}
		UUID id = UUID.randomUUID();
		jdbc.update("INSERT INTO rp_users (id, username, password_hash, role) VALUES (?, ?, ?, 'user')", id, username,
				encoder.encode(rawPassword));
		return id;
	}

	public Optional<RpUser> findById(UUID id) {
		return find("id = ?", id);
	}

	public Optional<RpUser> findByUsername(String username) {
		return find("username = ?", username);
	}

	/** 认证：用户存在且 BCrypt 匹配才返回。用户不存在与密码错误同样返回 empty（不泄露存在性）。 */
	public Optional<RpUser> authenticate(String username, String rawPassword) {
		if (username == null || rawPassword == null) {
			return Optional.empty();
		}
		List<Map<String, Object>> rows = jdbc
			.queryForList("SELECT id, username, password_hash, role FROM rp_users WHERE username = ?", username);
		if (rows.isEmpty()) {
			// 抹平时序差：假哈希校验一次，防用户名枚举
			encoder.matches(rawPassword, "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
			return Optional.empty();
		}
		var row = rows.get(0);
		if (!encoder.matches(rawPassword, String.valueOf(row.get("password_hash")))) {
			return Optional.empty();
		}
		return Optional
			.of(new RpUser((UUID) row.get("id"), String.valueOf(row.get("username")), String.valueOf(row.get("role"))));
	}

	private Optional<RpUser> find(String predicate, Object arg) {
		List<Map<String, Object>> rows = jdbc
			.queryForList("SELECT id, username, role FROM rp_users WHERE " + predicate + " LIMIT 1", arg);
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		var row = rows.get(0);
		return Optional
			.of(new RpUser((UUID) row.get("id"), String.valueOf(row.get("username")), String.valueOf(row.get("role"))));
	}

}
