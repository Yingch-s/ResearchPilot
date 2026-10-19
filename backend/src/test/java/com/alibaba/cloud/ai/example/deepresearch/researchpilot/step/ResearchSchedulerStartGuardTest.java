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

import com.alibaba.cloud.ai.example.deepresearch.researchpilot.evidence.RpEvidenceService;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpEventLogger;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpRunService;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RunStatus;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import java.time.Duration;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0-B 启动守卫：startOrResumeRun 只接受 RESEARCHING。 补位与取消/停止竞争、executor 排队窗口幽灵执行段的最后一道防线（审查
 * C1/M1 深度防御）。
 */
class ResearchSchedulerStartGuardTest {

	private RpRunService runService;

	private ResearchScheduler scheduler() {
		runService = mock(RpRunService.class);
		return new ResearchScheduler(runService, mock(RpEvidenceService.class), mock(RpEventLogger.class),
				mock(StepExecutor.class), mock(JdbcTemplate.class), 1200, 120);
	}

	@Test
	void nonResearchingRunIsRejectedBeforeLease() {
		ResearchScheduler scheduler = scheduler();
		UUID runId = UUID.randomUUID();
		when(runService.currentStatus(runId)).thenReturn(RunStatus.CANCELLED);
		boolean started = scheduler.startOrResumeRun(runId);
		assertEquals(false, started);
		verify(runService, never()).acquireLease(any(UUID.class), any(String.class), any(Duration.class));
	}

	@Test
	void researchingRunPassesGuard() {
		ResearchScheduler scheduler = scheduler();
		UUID runId = UUID.randomUUID();
		when(runService.currentStatus(runId)).thenReturn(RunStatus.RESEARCHING);
		when(runService.acquireLease(any(UUID.class), any(String.class), any(Duration.class))).thenReturn(true);
		when(runService.startResearchSegment(any(UUID.class), any(), any(), any(String.class)))
			.thenReturn(UUID.randomUUID());
		// executeSteps 内部依赖真实调度逻辑——守卫之后的行为不在此测试范围，
		// 允许后续步骤失败，但守卫必须放行（acquireLease 已被调用即证明）
		try {
			scheduler.startOrResumeRun(runId);
		}
		catch (Exception ignored) {
			// mock 环境下执行段内部行为未定义，只验证守卫放行
		}
		verify(runService).acquireLease(any(UUID.class), any(String.class), any(Duration.class));
	}

}
