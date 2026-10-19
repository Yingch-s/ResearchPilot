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

package com.alibaba.cloud.ai.example.deepresearch.researchpilot.llm;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** P0-B：LLM 限流退避 —— 只重试 429/503/rate limit，重试耗尽抛最后异常，其他异常立即抛出。 */
class LlmBackoffTest {

	private static RuntimeException rateLimited() {
		return new IllegalStateException(
				"LLM 请求失败: 429 - {\"error\":{\"message\":\"rate limit reached\",\"code\":\"429\"}}");
	}

	@Test
	void retriesOn429ThenSucceeds() {
		AtomicInteger calls = new AtomicInteger();
		String result = LlmBackoff.call(() -> {
			if (calls.incrementAndGet() < 3) {
				throw rateLimited();
			}
			return "ok";
		}, 3, 1); // baseDelay=1ms，测试不等真实退避
		assertEquals("ok", result);
		assertEquals(3, calls.get());
	}

	@Test
	void exhaustsRetriesThenThrowsLast() {
		AtomicInteger calls = new AtomicInteger();
		assertThrows(IllegalStateException.class, () -> LlmBackoff.call(() -> {
			calls.incrementAndGet();
			throw rateLimited();
		}, 3, 1));
		assertEquals(3, calls.get());
	}

	@Test
	void doesNotRetryOtherErrors() {
		AtomicInteger calls = new AtomicInteger();
		assertThrows(IllegalArgumentException.class, () -> LlmBackoff.call(() -> {
			calls.incrementAndGet();
			throw new IllegalArgumentException("bad input");
		}, 3, 1));
		assertEquals(1, calls.get());
	}

	@Test
	void detects429InCauseChain() {
		AtomicInteger calls = new AtomicInteger();
		String r = LlmBackoff.call(() -> {
			if (calls.incrementAndGet() == 1) {
				throw new RuntimeException("wrapped", rateLimited());
			}
			return "ok";
		}, 3, 1);
		assertEquals("ok", r);
		assertEquals(2, calls.get());
	}

	@Test
	void maxAttemptsBelowOneStillCallsOnce() {
		AtomicInteger calls = new AtomicInteger();
		assertThrows(IllegalStateException.class, () -> LlmBackoff.call(() -> {
			calls.incrementAndGet();
			throw rateLimited();
		}, 0, 1));
		assertEquals(1, calls.get());
	}

}
