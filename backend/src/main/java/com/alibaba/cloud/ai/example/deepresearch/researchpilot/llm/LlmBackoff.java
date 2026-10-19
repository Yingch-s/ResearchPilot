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

import java.util.function.Supplier;

/**
 * P0-B：LLM 限流退避（429/503 指数退避 + jitter）。
 *
 * <p>
 * 10-06 事故：3 并发 run 触发 DeepSeek 限流，NLI 批量判定直接降级 NEEDS_REVIEW。 规则：只重试限流/过载类错误（按异常链消息匹配
 * 429/503/rate limit）；重试耗尽抛最后异常， 由调用方走既有降级路径。其他异常（参数错误、解析错误等）不重试。
 *
 * <p>
 * 明确不用于 {@code ResearchStepExecutor.callWithTimeout} 内层：步骤级已有 attempt
 * 重试（MAX_STEP_ATTEMPTS=2），且 callWithTimeout 的预算语义与内嵌 sleep 冲突。
 */
public final class LlmBackoff {

	private LlmBackoff() {
	}

	public static <T> T call(Supplier<T> fn, int maxAttempts, long baseDelayMs) {
		RuntimeException last = null;
		for (int attempt = 1; attempt <= Math.max(1, maxAttempts); attempt++) {
			try {
				return fn.get();
			}
			catch (RuntimeException e) {
				if (!isRateLimited(e) || attempt == Math.max(1, maxAttempts)) {
					throw e;
				}
				last = e;
			}
			long delay = baseDelayMs * (1L << (attempt - 1)) + (long) (Math.random() * Math.min(500, baseDelayMs));
			try {
				Thread.sleep(delay);
			}
			catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
				throw last != null ? last : new IllegalStateException("interrupted during backoff", ie);
			}
		}
		throw new IllegalStateException("unreachable");
	}

	static boolean isRateLimited(Throwable e) {
		for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
			String msg = String.valueOf(t.getMessage());
			if (msg.contains("429") || msg.contains("503") || msg.toLowerCase().contains("rate limit")
					|| msg.toLowerCase().contains("insufficient_quota")) {
				return true;
			}
		}
		return false;
	}

}
