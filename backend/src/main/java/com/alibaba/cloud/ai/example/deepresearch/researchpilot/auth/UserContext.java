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

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * P0-A：当前用户归属解析。
 *
 * <p>
 * on 模式：从 SecurityContext 取登录用户。off 模式：返回 admin 种子行（缓存）—— 存量数据已在迁移中归属 admin，因此 off/on
 * 两条路径共用「永远按 owner 过滤」的单一 代码路径，避免双分支漏过滤（Review Focus 4）。
 */
@Component
public class UserContext {

	private final RpUserService userService;

	private final boolean authOn;

	private volatile UUID offOwnerId;

	public UserContext(RpUserService userService,
			@Value("${researchpilot.auth.mode:${RP_AUTH_MODE:off}}") String mode) {
		this.userService = userService;
		this.authOn = "on".equals(mode);
	}

	/** 当前请求的数据归属者；off 模式恒为 admin。无登录态（on 模式理论不可达）返回 empty。 */
	public Optional<UUID> currentOwnerId() {
		if (!authOn) {
			return Optional.of(offAdminId());
		}
		var auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.getPrincipal() instanceof RpUserService.RpUser user) {
			return Optional.of(user.id());
		}
		return Optional.empty();
	}

	private UUID offAdminId() {
		if (offOwnerId == null) {
			synchronized (this) {
				if (offOwnerId == null) {
					offOwnerId = userService.findByUsername("admin")
						.map(RpUserService.RpUser::id)
						.orElseThrow(() -> new IllegalStateException("admin 种子用户缺失（迁移未执行？）"));
				}
			}
		}
		return offOwnerId;
	}

}
