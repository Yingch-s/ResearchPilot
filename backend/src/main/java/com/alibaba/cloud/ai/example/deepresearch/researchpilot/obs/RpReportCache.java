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

import java.time.Duration;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * P1-D：报告 Cache-Aside（读多写少，报告一经生成不可变 → TTL 失效足够，无需写失效）。
 * researchpilot.redis.enabled=false（默认）时整体不装配，端点直查 PG。
 */
@Component
@ConditionalOnProperty(name = "researchpilot.redis.enabled", havingValue = "true")
public class RpReportCache {

	/** I2：报告可被 retry/重收敛覆盖（不可变前提不成立）——重写路径必须调用此静态失效。 */
	private static volatile RpReportCache instance;

	private static final Duration TTL = Duration.ofMinutes(10);

	private final StringRedisTemplate redis;

	public RpReportCache(StringRedisTemplate redis) {
		this.redis = redis;
		instance = this;
	}

	/** 报告被覆盖/删除时失效缓存（无实例 = 未启用，静默）。 */
	public static void evict(UUID runId) {
		if (instance != null) {
			instance.redis.delete(key(runId));
		}
	}

	public String get(UUID runId) {
		return redis.opsForValue().get(key(runId));
	}

	public void put(UUID runId, String report) {
		redis.opsForValue().set(key(runId), report, TTL);
	}

	private static String key(UUID runId) {
		return "rp:report:" + runId;
	}

}
