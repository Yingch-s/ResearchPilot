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

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P0-A：登录限速（同用户名 5 次/分钟）。内存计数实现——单实例够用； 多实例或需要持久化时演进为 Redis 滑动窗口（改造计划 V2 Phase 4）。
 */
public class LoginRateLimiter implements RateLimiter {

	private static final int LIMIT = 5;

	private static final Duration WINDOW = Duration.ofMinutes(1);

	private final Clock clock;

	private final Map<String, Window> attempts = new ConcurrentHashMap<>();

	public LoginRateLimiter() {
		this(Clock.systemUTC());
	}

	public LoginRateLimiter(Clock clock) {
		this.clock = clock;
	}

	@Override
	public boolean tryAcquire(String username) {
		if (username == null || username.isBlank()) {
			return false;
		}
		long nowMs = clock.millis();
		Window w = attempts.compute(username, (k, cur) -> {
			if (cur == null || nowMs - cur.windowStart >= WINDOW.toMillis()) {
				return new Window(nowMs);
			}
			return cur;
		});
		return w.count.incrementAndGet() <= LIMIT;
	}

	@Override
	public void reset(String username) {
		attempts.remove(username);
	}

	private static final class Window {

		private final long windowStart;

		private final java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger();

		private Window(long windowStart) {
			this.windowStart = windowStart;
		}

	}

}
