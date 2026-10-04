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
 * V0.4 H1.1（决策 2）：取证记录是否被当前 Run 接纳。
 *
 * <p>
 * 与引用/语义验证状态（rp_evidence.status：SUPPORTED/NEEDS_REVIEW/…）**互相独立**——
 * 前者回答"这条取证属于哪次执行、是否被接纳"，后者回答"引用是否真实、结论是否被支持"。
 */
public enum EvidenceAcceptStatus {

	/** 已登记、尚未被步骤提交事务接纳 */
	CANDIDATE,

	/** 已被当前有效 Attempt 的步骤提交接纳，进入矩阵 */
	ACCEPTED,

	/** 被拒绝（过期 Attempt / 校验失败 / 取消） */
	REJECTED,

	/** 被后续 Attempt 的同类证据取代（保留可追溯） */
	SUPERSEDED

}
