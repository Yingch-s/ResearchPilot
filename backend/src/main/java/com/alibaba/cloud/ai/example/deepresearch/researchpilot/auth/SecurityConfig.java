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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * P0-A：RP_AUTH_MODE 双模式。
 *
 * <p>
 * off（默认）：全部放行——现网客户端、D3 评测与本地开发零影响，数据归属视为 admin。 on：JSON 登录建 HttpSession，除 auth
 * 与公开分享端点外全部要求认证；越权在 Service 层按 owner 过滤后以 404 呈现（不泄露存在性）。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
		return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain researchSecurityFilterChain(HttpSecurity http,
			@Value("${researchpilot.auth.mode:${RP_AUTH_MODE:off}}") String mode) throws Exception {
		if ("off".equals(mode)) {
			http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().permitAll());
			return http.build();
		}
		http.csrf(c -> c.disable())
			.authorizeHttpRequests(
					a -> a.requestMatchers("/api/rp/auth/**", "/api/rp/research/shared/**", "/actuator/**")
						.permitAll()
						.anyRequest()
						.authenticated())
			.exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> res.sendError(401)))
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));
		return http.build();
	}

}
