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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-D：Redis Lua 固定窗口限速（真实 Redis；REDIS_PASSWORD 未设时跳过）。 验证：窗口内超限拒绝、不同用户名独立计数、Lua
 * 原子性（并发不超发）。
 */
class RedisRateLimiterIT {

	private static StringRedisTemplate redis;

	@BeforeAll
	static void setup() {
		String pw = System.getenv("REDIS_PASSWORD");
		Assumptions.assumeTrue(pw != null && !pw.isBlank(), "REDIS_PASSWORD 未设，跳过 Redis IT");
		LettuceConnectionFactory cf = new LettuceConnectionFactory("localhost", 6379);
		cf.setPassword(pw);
		cf.afterPropertiesSet();
		redis = new StringRedisTemplate(cf);
		try {
			redis.getConnectionFactory().getConnection().ping();
		}
		catch (Exception e) {
			Assumptions.assumeTrue(false, "Redis 不可达: " + e.getMessage());
		}
	}

	@AfterAll
	static void clean() {
		if (redis != null) {
			var keys = redis.keys("rp:ratelimit:login:rl-it-*");
			if (keys != null && !keys.isEmpty()) {
				redis.delete(keys);
			}
		}
	}

	@Test
	void windowLimitRejectsAfterLimit() {
		String user = "rl-it-" + UUID.randomUUID().toString().substring(0, 8);
		var limiter = new RedisRateLimiters.RedisRateLimiter(redis, 3, 60);
		assertTrue(limiter.tryAcquire(user));
		assertTrue(limiter.tryAcquire(user));
		assertTrue(limiter.tryAcquire(user));
		assertFalse(limiter.tryAcquire(user), "第 4 次应被拒");
	}

	@Test
	void usernamesAreIndependent() {
		String a = "rl-it-" + UUID.randomUUID().toString().substring(0, 8);
		String b = "rl-it-" + UUID.randomUUID().toString().substring(0, 8);
		var limiter = new RedisRateLimiters.RedisRateLimiter(redis, 1, 60);
		assertTrue(limiter.tryAcquire(a));
		assertFalse(limiter.tryAcquire(a));
		assertTrue(limiter.tryAcquire(b), "不同用户名互不影响");
	}

	@Test
	void concurrentAcquiresNeverExceedLimit() throws Exception {
		String user = "rl-it-" + UUID.randomUUID().toString().substring(0, 8);
		var limiter = new RedisRateLimiters.RedisRateLimiter(redis, 5, 60);
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(10);
		var ok = new java.util.concurrent.atomic.AtomicInteger();
		var latch = new java.util.concurrent.CountDownLatch(10);
		for (int i = 0; i < 10; i++) {
			pool.submit(() -> {
				try {
					if (limiter.tryAcquire(user)) {
						ok.incrementAndGet();
					}
				}
				finally {
					latch.countDown();
				}
			});
		}
		latch.await();
		pool.shutdownNow();
		assertNotEquals(0, ok.get());
		assertTrue(ok.get() <= 5, "Lua 原子性：放行不得超过 5，实际 " + ok.get());
	}

}
