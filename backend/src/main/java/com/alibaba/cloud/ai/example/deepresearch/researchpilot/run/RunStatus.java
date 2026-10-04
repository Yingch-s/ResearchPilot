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

import java.util.Map;
import java.util.Set;

/**
 * V0.4 H1.1：ResearchRun 生命周期（决策 1）。
 *
 * <pre>
 * CREATED → PLANNING → WAITING_APPROVAL → RESEARCHING → VERIFYING → FINALIZING
 *                                            │                        ├── COMPLETED
 *                                            │                        ├── PARTIAL_COMPLETED
 *                                            │                        └── FAILED
 *                                            └──→ CANCELLED（允许取消的阶段）
 * </pre> 合法转移集中在单点（{@link #canTransitionTo}），执行由 RpRunService 乐观并发控制 （UPDATE ... WHERE
 * status = from），无双调度系统。
 */
public enum RunStatus {

	CREATED, PLANNING, WAITING_APPROVAL, RESEARCHING, VERIFYING, FINALIZING, COMPLETED, PARTIAL_COMPLETED, FAILED,
	CANCELLED;

	/**
	 * 合法转移表（唯一权威定义）。
	 */
	public static final Map<RunStatus, Set<RunStatus>> ALLOWED = Map.of(CREATED, Set.of(PLANNING, CANCELLED), PLANNING,
			Set.of(WAITING_APPROVAL, RESEARCHING, CANCELLED, FAILED), WAITING_APPROVAL, Set.of(RESEARCHING, CANCELLED),
			RESEARCHING, Set.of(VERIFYING, FINALIZING, CANCELLED, FAILED), VERIFYING, Set.of(FINALIZING, FAILED),
			FINALIZING, Set.of(COMPLETED, PARTIAL_COMPLETED, FAILED));

	public boolean canTransitionTo(RunStatus target) {
		return ALLOWED.getOrDefault(this, Set.of()).contains(target);
	}

	/** 是否终态（终态后不再接受任何转移与写回）。 */
	public boolean isTerminal() {
		return this == COMPLETED || this == PARTIAL_COMPLETED || this == FAILED || this == CANCELLED;
	}

}
