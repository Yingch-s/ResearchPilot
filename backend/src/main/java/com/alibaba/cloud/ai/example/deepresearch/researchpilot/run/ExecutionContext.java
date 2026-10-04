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

package com.alibaba.cloud.ai.example.deepresearch.researchpilot.run;

import java.time.Instant;
import java.util.UUID;

/**
 * V0.4 H1.1：统一执行上下文（决策 4）。
 *
 * <p>
 * 所有 Step Graph 入口显式携带，禁止通过 ThreadLocal、全局变量或临时拼接传递业务身份。 Deadline 字段对齐决策 3 的三段 Deadline
 * 机制。
 */
public record ExecutionContext(UUID runId, UUID topicId, String stepId, UUID attemptId, Instant globalDeadline,
		Instant stepDeadline, java.util.List<String> paperIds, boolean allowWeb) {

	public ExecutionContext(UUID runId, UUID topicId, String stepId, UUID attemptId, Instant globalDeadline,
			Instant stepDeadline, java.util.List<String> paperIds) {
		this(runId, topicId, stepId, attemptId, globalDeadline, stepDeadline, paperIds, false);
	}
}
