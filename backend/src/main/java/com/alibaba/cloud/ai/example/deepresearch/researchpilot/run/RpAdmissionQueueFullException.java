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
 * P0-B：队列已满，准入被拒。控制器映射为 429 + Retry-After。
 */
public class RpAdmissionQueueFullException extends RuntimeException {

	private final RpAdmissionService.AdmissionStats stats;

	public RpAdmissionQueueFullException(RpAdmissionService.AdmissionStats stats) {
		super("任务队列已满: active " + stats.active() + "/" + stats.activeLimit() + ", queued " + stats.queued() + "/"
				+ stats.queueLimit());
		this.stats = stats;
	}

	public RpAdmissionService.AdmissionStats stats() {
		return stats;
	}

}
