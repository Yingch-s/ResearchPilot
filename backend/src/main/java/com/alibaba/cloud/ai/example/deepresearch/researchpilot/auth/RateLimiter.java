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

/**
 * P1-D：登录限速统一接口 —— Redis Lua 固定窗口（researchpilot.redis.rate-limit-enabled=true）
 * 或内存实现（默认，单实例）。多实例演进路径：Redis 实现天然跨实例共享计数。
 */
public interface RateLimiter {

	/** 尝试计数；false = 本窗口已超限。成功登录后调用方应 reset。 */
	boolean tryAcquire(String username);

	void reset(String username);

}
