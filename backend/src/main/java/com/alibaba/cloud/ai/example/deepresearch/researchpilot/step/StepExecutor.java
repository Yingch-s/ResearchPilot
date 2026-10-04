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

import com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.ExecutionContext;

/**
 * V0.4 H1.2：步骤执行器接口（决策 1 渐进迁移第 2 步）。
 *
 * <p>
 * 业务身份必须通过 {@link ExecutionContext} 显式传入，禁止 ThreadLocal / 全局变量。 实现方在步骤无法继续（进程被要求暂停 /
 * 恢复）时抛出 {@link StepAbortedException}， 由调度器区分"步骤失败"与"执行段中止"。
 */
public interface StepExecutor {

	StepResult execute(ExecutionContext ctx, int stepIndex, String query, String stepTitle, String stepDescription);

	/**
	 * 执行段中止（非步骤失败）：调度器保留现场等待恢复，不计入步骤重试。
	 */
	class StepAbortedException extends RuntimeException {

		public StepAbortedException(String message) {
			super(message);
		}

	}

}
