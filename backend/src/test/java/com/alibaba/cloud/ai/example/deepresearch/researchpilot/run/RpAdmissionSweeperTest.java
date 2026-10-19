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

import com.alibaba.cloud.ai.example.deepresearch.researchpilot.web.RpExecutionLauncher;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** P0-B：补位 sweeper —— promote 结果交给 launcher，FIFO 顺序保持；空提升不触发启动。 */
class RpAdmissionSweeperTest {

	@Test
	void promotedRunsLaunchedInFifoOrder() {
		RpAdmissionService admission = mock(RpAdmissionService.class);
		RpExecutionLauncher launcher = mock(RpExecutionLauncher.class);
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		when(admission.promoteNext(5)).thenReturn(List.of(first, second));
		RpAdmissionSweeper sweeper = new RpAdmissionSweeper(admission, launcher, 5);
		sweeper.sweep();
		verify(launcher).launchAll(argThat(ids -> ids.size() == 2 && ids.get(0).equals(first)));
	}

	@Test
	void emptyPromotionDoesNotLaunch() {
		RpAdmissionService admission = mock(RpAdmissionService.class);
		RpExecutionLauncher launcher = mock(RpExecutionLauncher.class);
		when(admission.promoteNext(5)).thenReturn(List.of());
		new RpAdmissionSweeper(admission, launcher, 5).sweep();
		verifyNoInteractions(launcher);
	}

	@Test
	void admissionExceptionDoesNotEscapeSweep() {
		// 定时任务不允许异常逃逸：异常被吞掉且不影响下次调度
		RpAdmissionService admission = mock(RpAdmissionService.class);
		RpExecutionLauncher launcher = mock(RpExecutionLauncher.class);
		when(admission.promoteNext(5)).thenThrow(new IllegalStateException("db down"));
		new RpAdmissionSweeper(admission, launcher, 5).sweep(); // 不应抛出
		verifyNoInteractions(launcher);
	}

}
