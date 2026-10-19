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

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * P1-D：Redis 限速（Lua 固定窗口，原子）。
 *
 * <p>
 * 与内存实现的取舍：Redis 计数跨实例共享且不随 JVM 重启清零； 内存实现零依赖。由 researchpilot.redis.rate-limit-enabled
 * 切换。
 */
public final class RedisRateLimiters {

	private RedisRateLimiters() {
	}

	/**
	 * 固定窗口：INCR + 首次 EXPIRE，原子返回是否放行。 已知边界（文档化）：窗口切换瞬间新旧窗口各可放行 limit 次（最多 2x 突发）；
	 * 需要严格平滑时演进为滑动窗口（ZRANGE 方案）。
	 */
	public static final String FIXED_WINDOW_LUA = """
			local n = redis.call('INCR', KEYS[1])
			if n == 1 then
				redis.call('EXPIRE', KEYS[1], ARGV[2])
			end
			return n
			""";

	public static class RedisRateLimiter implements RateLimiter {

		private final StringRedisTemplate redis;

		private final int limit;

		private final int windowSeconds;

		private final DefaultRedisScript<Long> script = new DefaultRedisScript<>(FIXED_WINDOW_LUA, Long.class);

		public RedisRateLimiter(StringRedisTemplate redis, @Value("${researchpilot.auth.rate-limit:5}") int limit,
				@Value("${researchpilot.auth.rate-limit-window-seconds:60}") int windowSeconds) {
			this.redis = redis;
			this.limit = limit;
			this.windowSeconds = windowSeconds;
		}

		@Override
		public boolean tryAcquire(String username) {
			if (username == null || username.isBlank()) {
				return false;
			}
			try {
				long window = Instant.now().getEpochSecond() / windowSeconds;
				String key = "rp:ratelimit:login:" + username + ":" + window;
				Long n = redis.execute(script, List.of(key), String.valueOf(limit), String.valueOf(windowSeconds));
				return n != null && n <= limit;
			}
			catch (Exception e) {
				// I4：fail-open —— Redis 故障时放行（限流是公平性层不是安全层；
				// 准入/PG 仍保护系统容量）。记录并放行，避免限流层成为登录单点。
				org.slf4j.LoggerFactory.getLogger(RedisRateLimiter.class)
					.warn("Redis 限速不可用，fail-open: {}", e.getMessage());
				return true;
			}
		}

		@Override
		public void reset(String username) {
			// I3：成功登录清除计数（与内存实现语义一致——只限失败尝试）
			long window = Instant.now().getEpochSecond() / windowSeconds;
			redis.delete("rp:ratelimit:login:" + username + ":" + window);
		}

	}

	/** 装配：Redis 限速开启时用 Redis，否则内存实现。 */
	@Configuration
	public static class Wiring {

		@Bean
		@ConditionalOnProperty(name = "researchpilot.redis.rate-limit-enabled", havingValue = "true")
		public RateLimiter redisRateLimiter(StringRedisTemplate redis,
				@Value("${researchpilot.auth.rate-limit:5}") int limit,
				@Value("${researchpilot.auth.rate-limit-window-seconds:60}") int windowSeconds) {
			return new RedisRateLimiter(redis, limit, windowSeconds);
		}

		@Bean
		@org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(RateLimiter.class)
		public RateLimiter memoryRateLimiter() {
			return new LoginRateLimiter();
		}

	}

}
