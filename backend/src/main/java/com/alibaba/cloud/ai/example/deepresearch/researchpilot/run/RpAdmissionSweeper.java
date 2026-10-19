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

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.alibaba.cloud.ai.example.deepresearch.researchpilot.web.RpExecutionLauncher;

/**
 * P0-B：排队补位 —— 周期扫描 QUEUED 队列，有空槽则 FIFO 提升并启动。 顺序由 created_at 决定（DB 行，重启不丢）；异常不逃逸出定时任务。
 */
@Component
public class RpAdmissionSweeper {

	private static final Logger logger = LoggerFactory.getLogger(RpAdmissionSweeper.class);

	private final RpAdmissionService admissionService;

	private final RpExecutionLauncher launcher;

	private final int batch;

	public RpAdmissionSweeper(RpAdmissionService admissionService, RpExecutionLauncher launcher,
			@Value("${researchpilot.admission.promote-batch:5}") int batch) {
		this.admissionService = admissionService;
		this.launcher = launcher;
		this.batch = batch;
	}

	@Scheduled(fixedDelayString = "${researchpilot.admission.promote-interval-ms:5000}")
	public void sweep() {
		try {
			List<UUID> promoted = admissionService.promoteNext(batch);
			if (!promoted.isEmpty()) {
				logger.info("补位启动 {} 个排队任务: {}", promoted.size(), promoted);
				launcher.launchAll(promoted);
			}
		}
		catch (Exception e) {
			logger.error("补位扫描异常", e); // 定时任务不允许异常逃逸
		}
	}

}
