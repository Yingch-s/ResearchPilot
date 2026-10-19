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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * P0-A：注册/登录/登出/me。会话 = Spring Security 标准上下文（HttpSession 存储）， 后续请求由 Security
 * 过滤链恢复认证态。登录失败统一 401（不区分用户不存在/密码错误）。
 */
@RestController
@RequestMapping("/api/rp/auth")
public class RpAuthController {

	public record Credentials(String username, String password) {
	}

	private final RpUserService userService;

	private final RateLimiter rateLimiter;

	private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

	public RpAuthController(RpUserService userService, RateLimiter rateLimiter) {
		this.userService = userService;
		this.rateLimiter = rateLimiter;
	}

	@PostMapping("/register")
	public ResponseEntity<Map<String, Object>> register(@RequestBody Credentials req) {
		try {
			java.util.UUID id = userService.createUser(req.username(), req.password());
			return ResponseEntity.ok(Map.of("username", req.username(), "id", id.toString()));
		}
		catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
		}
	}

	@PostMapping("/login")
	public ResponseEntity<Map<String, Object>> login(@RequestBody Credentials req, HttpServletRequest httpReq,
			HttpServletResponse httpRes) {
		if (!rateLimiter.tryAcquire(req.username())) {
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header("Retry-After", "60")
				.body(Map.of("error", "尝试过于频繁，请稍后再试"));
		}
		var user = userService.authenticate(req.username(), req.password());
		if (user.isEmpty()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "用户名或密码错误"));
		}
		var auth = new UsernamePasswordAuthenticationToken(user.get(), null,
				List.of(new SimpleGrantedAuthority("ROLE_" + user.get().role().toUpperCase())));
		// I6：会话固定防护 —— 登录成功后更换 session id（旧 id 即使被诱导注入也随即失效）
		var oldSession = httpReq.getSession(false);
		if (oldSession != null) {
			httpReq.changeSessionId();
		}
		SecurityContextHolder.getContext().setAuthentication(auth);
		contextRepository.saveContext(SecurityContextHolder.getContext(), httpReq, httpRes);
		rateLimiter.reset(req.username());
		return ResponseEntity.ok(Map.of("username", user.get().username(), "role", user.get().role()));
	}

	@PostMapping("/logout")
	public ResponseEntity<Map<String, Object>> logout(HttpServletRequest httpReq) {
		var session = httpReq.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		SecurityContextHolder.clearContext();
		return ResponseEntity.ok(Map.of("logout", true));
	}

	@GetMapping("/me")
	public ResponseEntity<Map<String, Object>> me() {
		var auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof RpUserService.RpUser user)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		return ResponseEntity.ok(Map.of("username", user.username(), "role", user.role()));
	}

}
