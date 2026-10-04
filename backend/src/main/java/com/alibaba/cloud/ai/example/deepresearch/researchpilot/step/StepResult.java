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

package com.alibaba.cloud.ai.example.deepresearch.researchpilot.step;

import com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.StepStatus;

/**
 * V0.4 H1.2：结构化步骤结果（决策 1）——Step Graph 只产出结果，跨步骤推进由业务层依据 持久化状态执行。Graph
 * 不再决定全局步骤认领，也不负责跨步骤执行游标。
 */
public record StepResult(int stepIndex, StepStatus status, String summary, int evidenceCount, String error) {

	public static StepResult completed(int stepIndex, String summary, int evidenceCount) {
		return new StepResult(stepIndex, StepStatus.COMPLETED, summary, evidenceCount, null);
	}

	public static StepResult failed(int stepIndex, String error) {
		return new StepResult(stepIndex, StepStatus.FAILED, null, 0, error);
	}

}
