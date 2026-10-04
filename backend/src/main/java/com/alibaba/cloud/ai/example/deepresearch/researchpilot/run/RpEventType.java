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

/**
 * V0.4 H1.1：执行事件协议（决策 4）——H1 冻结事件集，H2 实现指标聚合与诊断。
 *
 * <p>
 * 事件是执行观测数据；Run/Step 的实际状态以事务提交的业务状态为准， 不得依据事件日志反判任务完成。
 */
public enum RpEventType {

	// Run 生命周期
	RUN_STARTED, PLAN_CREATED,

	// 步骤调度
	STEP_SCHEDULED, STEP_STARTED, STEP_COMPLETED, STEP_FAILED,

	// 节点（步骤子图内部）
	NODE_STARTED, NODE_COMPLETED, NODE_FAILED,

	// 资源调用（H2 扩展耗时/Token 分析）
	LLM_CALL_STARTED, LLM_CALL_COMPLETED, RETRIEVAL_COMPLETED, WEB_SEARCH_STARTED, WEB_SEARCH_COMPLETED,

	// 证据
	EVIDENCE_ACCEPTED, RESEARCH_BLOCKED,

	// Run 级流转
	RUN_PAUSED, RUN_RESUMED, RUN_FINALIZING, RUN_COMPLETED, RUN_PARTIAL_COMPLETED, RUN_FAILED, RUN_CANCELLED

}
