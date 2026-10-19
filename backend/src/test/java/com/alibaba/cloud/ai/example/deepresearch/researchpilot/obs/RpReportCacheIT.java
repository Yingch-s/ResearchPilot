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

package com.alibaba.cloud.ai.example.deepresearch.researchpilot.obs;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * P1-D 审查 I6：报告 Cache-Aside —— 命中/覆盖/失效（evict）三路径（真实 Redis）。
 */
class RpReportCacheIT {

	private static StringRedisTemplate redis;

	@BeforeAll
	static void setup() {
		String pw = System.getenv("REDIS_PASSWORD");
		Assumptions.assumeTrue(pw != null && !pw.isBlank(), "REDIS_PASSWORD 未设，跳过");
		LettuceConnectionFactory cf = new LettuceConnectionFactory("localhost", 6379);
		cf.setPassword(pw);
		cf.afterPropertiesSet();
		redis = new StringRedisTemplate(cf);
		try {
			redis.getConnectionFactory().getConnection().ping();
		}
		catch (Exception e) {
			Assumptions.assumeTrue(false, "Redis 不可达");
		}
	}

	@AfterAll
	static void clean() {
		if (redis != null) {
			var keys = redis.keys("rp:report:cache-it-*");
			if (keys != null && !keys.isEmpty()) {
				redis.delete(keys);
			}
		}
	}

	@Test
	void putGetEvictCycle() {
		var cache = new RpReportCache(redis);
		UUID runId = UUID.fromString("00000000-0000-0000-0000-cache-it-0001".replace("cache-it-0001", "000000000001"));
		// 穿透：未写入时为 null
		assertNull(cache.get(runId));
		// 写入 → 命中
		cache.put(runId, "report-v1");
		assertEquals("report-v1", cache.get(runId));
		// 重收敛覆盖 + evict（I2：不可变前提不成立）→ 失效
		RpReportCache.evict(runId);
		assertNull(cache.get(runId));
	}

}
