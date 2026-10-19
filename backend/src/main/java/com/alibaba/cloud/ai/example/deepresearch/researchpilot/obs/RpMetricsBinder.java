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

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

import com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpAdmissionService;

/**
 * P1-D：业务指标绑定 —— 长任务系统容量口径。
 *
 * <p>
 * Gauge 每次 scrape 时调用 admission.stats()（一次轻量 count 查询，实测 0.6ms@10 万行）。 静态计数器（429
 * 拒绝）走静态入口，供控制器在无注入场景调用。
 */
@Component
public class RpMetricsBinder {

	private static volatile io.micrometer.core.instrument.Counter rejectedCounter;

	public RpMetricsBinder(MeterRegistry registry, RpAdmissionService admissionService,
			org.springframework.jdbc.core.JdbcTemplate jdbc) {
		registry.gauge("rp_ingest_backlog", jdbc, j -> {
			Integer n = j.queryForObject("SELECT count(*) FROM rp_ingestion_jobs WHERE status = 'PENDING'",
					Integer.class);
			return n == null ? 0 : n;
		});
		registry.gauge("rp_active_runs", admissionService, s -> s.stats().active());
		registry.gauge("rp_queued_runs", admissionService, s -> s.stats().queued());
		rejectedCounter = io.micrometer.core.instrument.Counter.builder("rp_admission_rejected")
			.description("准入队列满被拒次数（429）")
			.register(registry);
	}

	public static void rejected() {
		if (rejectedCounter != null) {
			rejectedCounter.increment();
		}
	}

}
