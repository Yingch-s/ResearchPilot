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

package com.alibaba.cloud.ai.example.deepresearch.researchpilot.web;

import jakarta.annotation.PreDestroy;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.alibaba.cloud.ai.example.deepresearch.researchpilot.step.ResearchScheduler;

/**
 * P0-B：执行启动的统一入口（原 RpRunController 私有 submitExecution 提升为组件，供补位 sweeper 复用）。
 * 线程池规格与原实现一致（fixed 2）。
 */
@Component
public class RpExecutionLauncher {

	private static final Logger logger = LoggerFactory.getLogger(RpExecutionLauncher.class);

	private final ExecutorService executor;

	private final ResearchScheduler scheduler;

	public RpExecutionLauncher(ResearchScheduler scheduler) {
		this.scheduler = scheduler;
		this.executor = Executors.newFixedThreadPool(2);
	}

	public void launch(UUID runId) {
		executor.submit(() -> {
			try {
				scheduler.startOrResumeRun(runId);
			}
			catch (Exception e) {
				logger.error("Run 执行异常: {}", runId, e);
			}
		});
	}

	public void launchAll(List<UUID> runIds) {
		runIds.forEach(this::launch);
	}

	@PreDestroy
	void shutdown() {
		executor.shutdownNow();
	}

}
