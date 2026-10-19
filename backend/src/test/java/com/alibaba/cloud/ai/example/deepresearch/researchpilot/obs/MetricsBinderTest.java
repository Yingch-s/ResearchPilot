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

package com.alibaba.cloud.ai.example.deepresearch.researchpilot.obs;

import com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpAdmissionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** P1-D：业务指标绑定 —— 长任务系统容量口径（active/queued gauge + 拒绝计数）。 */
class MetricsBinderTest {

	@Test
	void gaugesReflectAdmissionStats() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		RpAdmissionService admission = mock(RpAdmissionService.class);
		when(admission.stats()).thenReturn(new RpAdmissionService.AdmissionStats(3, 12, 4, 50));
		new RpMetricsBinder(registry, admission, mock(org.springframework.jdbc.core.JdbcTemplate.class));
		assertEquals(3.0, registry.get("rp_active_runs").gauge().value());
		assertEquals(12.0, registry.get("rp_queued_runs").gauge().value());
		assertNotNull(registry.find("rp_active_runs").gauge());
	}

	@Test
	void rejectedCounterAccumulates() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		new RpMetricsBinder(registry, mock(RpAdmissionService.class),
				mock(org.springframework.jdbc.core.JdbcTemplate.class));
		RpMetricsBinder.rejected();
		RpMetricsBinder.rejected();
		assertEquals(2.0, registry.get("rp_admission_rejected").counter().count());
	}

}
