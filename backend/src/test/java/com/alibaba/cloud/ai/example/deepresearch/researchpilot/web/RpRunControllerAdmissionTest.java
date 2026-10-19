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

import com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpAdmissionQueueFullException;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpAdmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0-B：控制器对准入结果的映射（429 / QUEUED / RESEARCHING），以及满载时审批不绕过限额。
 */
class RpRunControllerAdmissionTest {

	private RpRunController controller(RpAdmissionService admission) {
		// promoteIntervalMs 单位毫秒：5000 = 补位周期 5s → Retry-After 5
		return new RpRunController(null, null, null, null, null, null, admission, null, null, 5000);
	}

	@Test
	void queueFullMapsTo429WithRetryAfter() {
		RpAdmissionService admission = mock(RpAdmissionService.class);
		UUID runId = UUID.randomUUID();
		when(admission.tryAdmit(runId))
			.thenThrow(new RpAdmissionQueueFullException(new RpAdmissionService.AdmissionStats(4, 50, 4, 50)));
		ResponseEntity<Map<String, Object>> resp = controller(admission).admitAndLaunch(runId);
		assertEquals(429, resp.getStatusCode().value());
		assertEquals("5", resp.getHeaders().getFirst("Retry-After"));
		assertEquals("QUEUE_FULL", resp.getBody().get("status"));
	}

	@Test
	void queuedMapsTo202WithQueuedStatus() {
		RpAdmissionService admission = mock(RpAdmissionService.class);
		UUID runId = UUID.randomUUID();
		when(admission.tryAdmit(runId)).thenReturn(RpAdmissionService.AdmitDecision.QUEUED);
		ResponseEntity<Map<String, Object>> resp = controller(admission).admitAndLaunch(runId);
		assertEquals(202, resp.getStatusCode().value());
		assertEquals("QUEUED", resp.getBody().get("status"));
	}

	@Test
	void startedLaunchesAndMapsToResearching() {
		RpAdmissionService admission = mock(RpAdmissionService.class);
		RpExecutionLauncher launcher = mock(RpExecutionLauncher.class);
		UUID runId = UUID.randomUUID();
		when(admission.tryAdmit(runId)).thenReturn(RpAdmissionService.AdmitDecision.STARTED);
		RpRunController controller = new RpRunController(null, null, null, null, null, null, admission, launcher, null,
				5);
		ResponseEntity<Map<String, Object>> resp = controller.admitAndLaunch(runId);
		assertEquals(202, resp.getStatusCode().value());
		assertEquals("RESEARCHING", resp.getBody().get("status"));
		verify(launcher, times(1)).launch(runId);
	}

	@Test
	void waitingApprovalRunGetsQueuedNotBypass() {
		// Review Focus 5：满载时审批必须排队而非绕过 —— 走同一准入入口
		RpAdmissionService admission = mock(RpAdmissionService.class);
		UUID runId = UUID.randomUUID();
		when(admission.tryAdmit(runId)).thenReturn(RpAdmissionService.AdmitDecision.QUEUED);
		ResponseEntity<Map<String, Object>> resp = controller(admission).admitAndLaunch(runId);
		assertEquals("QUEUED", resp.getBody().get("status"));
		verify(admission, times(1)).tryAdmit(runId);
	}

	@Test
	void idempotentRetryReDrivesAdmissionForStrandedPlanning() {
		// I1：429 搁浅的 PLANNING run —— 同 client_request_id 重试时重新走准入（解除搁浅），而非原样返回
		RpAdmissionService admission = mock(RpAdmissionService.class);
		com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpRunService runService = mock(
				com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpRunService.class);
		RpExecutionLauncher launcher = mock(RpExecutionLauncher.class);
		UUID runId = UUID.randomUUID();
		when(runService.findRunByClientRequest("idem-1")).thenReturn(java.util.Optional.of(runId));
		when(runService.currentStatus(runId))
			.thenReturn(com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RunStatus.PLANNING);
		when(admission.tryAdmit(runId)).thenReturn(RpAdmissionService.AdmitDecision.STARTED);
		RpRunController controller = new RpRunController(runService, null, null, null, null, null, admission, launcher,
				null, 5000);
		var req = new RpRunController.CreateRequest("q", null, null, null, null, null, null, null, true, "idem-1", null,
				null, null, null, null);
		@SuppressWarnings("unchecked")
		ResponseEntity<Map<String, Object>> resp = (ResponseEntity<Map<String, Object>>) controller.create(req);
		assertEquals(202, resp.getStatusCode().value());
		assertEquals("RESEARCHING", resp.getBody().get("status"));
		verify(launcher).launch(runId);
	}

	@Test
	void stopCancelsQueuedRunDirectly() {
		// P0-B：排队中的 run 无执行段，stop 应直接 QUEUED → CANCELLED（而非走 scheduler 的 RESEARCHING
		// 优雅停止）
		RpAdmissionService admission = mock(RpAdmissionService.class);
		com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpRunService runService = mock(
				com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpRunService.class);
		com.alibaba.cloud.ai.example.deepresearch.researchpilot.step.ResearchScheduler scheduler = mock(
				com.alibaba.cloud.ai.example.deepresearch.researchpilot.step.ResearchScheduler.class);
		UUID runId = UUID.randomUUID();
		when(runService.transition(runId, com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RunStatus.QUEUED,
				com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RunStatus.CANCELLED, "stopped"))
			.thenReturn(true);
		var userContext = mock(com.alibaba.cloud.ai.example.deepresearch.researchpilot.auth.UserContext.class);
		when(userContext.currentOwnerId()).thenReturn(java.util.Optional.of(UUID.randomUUID()));
		when(runService.loadRunForOwner(any(UUID.class), any(UUID.class)))
			.thenReturn(java.util.Optional.of(java.util.Map.of("id", "x")));
		RpRunController controller = new RpRunController(runService, null, scheduler, null, null, null, admission, null,
				userContext, 5000);
		var resp = (Map<String, Object>) controller.stop(runId);
		assertEquals(true, resp.get("stopped"));
		assertEquals("CANCELLED", resp.get("status"));
		verify(scheduler, times(0)).stopRun(runId); // 不应进入 RESEARCHING 优雅停止路径
	}

	@Test
	void stopFallsBackToSchedulerForResearching() {
		RpAdmissionService admission = mock(RpAdmissionService.class);
		com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpRunService runService = mock(
				com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpRunService.class);
		com.alibaba.cloud.ai.example.deepresearch.researchpilot.step.ResearchScheduler scheduler = mock(
				com.alibaba.cloud.ai.example.deepresearch.researchpilot.step.ResearchScheduler.class);
		UUID runId = UUID.randomUUID();
		when(runService.transition(runId, com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RunStatus.QUEUED,
				com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RunStatus.CANCELLED, "stopped"))
			.thenReturn(false);
		when(scheduler.stopRun(runId)).thenReturn(true);
		var userContext = mock(com.alibaba.cloud.ai.example.deepresearch.researchpilot.auth.UserContext.class);
		when(userContext.currentOwnerId()).thenReturn(java.util.Optional.of(UUID.randomUUID()));
		when(runService.loadRunForOwner(any(UUID.class), any(UUID.class)))
			.thenReturn(java.util.Optional.of(java.util.Map.of("id", "x")));
		RpRunController controller = new RpRunController(runService, null, scheduler, null, null, null, admission, null,
				userContext, 5000);
		var resp = (Map<String, Object>) controller.stop(runId);
		assertEquals(true, resp.get("stopped"));
		verify(scheduler, times(1)).stopRun(runId);
	}

}
