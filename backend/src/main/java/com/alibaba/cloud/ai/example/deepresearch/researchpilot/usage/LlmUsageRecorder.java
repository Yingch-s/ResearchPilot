/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.alibaba.cloud.ai.example.deepresearch.researchpilot.usage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/** Records one stable usage shape for every user-facing LLM call. */
@Component
public class LlmUsageRecorder {

	private static final Logger logger = LoggerFactory.getLogger(LlmUsageRecorder.class);

	private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

	public LlmUsageRecorder(io.micrometer.core.instrument.MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	public void record(String stage, long elapsedMs, Integer promptTokens, Integer completionTokens, boolean success,
			String errorCode) {
		// P1-D：token 与失败计数进 Micrometer（Prometheus 抓取）；usage 缺失时只计调用
		if (meterRegistry != null) {
			if (promptTokens != null) {
				meterRegistry.counter("rp_llm_tokens_total", "stage", stage, "type", "prompt").increment(promptTokens);
			}
			if (completionTokens != null) {
				meterRegistry.counter("rp_llm_tokens_total", "stage", stage, "type", "completion")
					.increment(completionTokens);
			}
			if (!success) {
				meterRegistry.counter("rp_llm_calls_failed", "stage", stage).increment();
			}
			meterRegistry.timer("rp_llm_call_duration", "stage", stage)
				.record(Math.max(0, elapsedMs), TimeUnit.MILLISECONDS);
		}
		Map<String, Object> event = new LinkedHashMap<>();
		event.put("event", "LLM_CALL_COMPLETED");
		event.put("stage", stage == null || stage.isBlank() ? "unknown" : stage);
		event.put("elapsed_ms", Math.max(0, elapsedMs));
		event.put("prompt_tokens", promptTokens);
		event.put("completion_tokens", completionTokens);
		event.put("total_tokens",
				promptTokens != null && completionTokens != null ? promptTokens + completionTokens : null);
		event.put("usage_reported", promptTokens != null || completionTokens != null);
		event.put("success", success);
		if (errorCode != null && !errorCode.isBlank()) {
			event.put("error_code", errorCode);
		}
		logger.info("rp_usage {}", event);
	}

	public Timer timer(String stage) {
		return new Timer(this, stage);
	}

	public static final class Timer implements AutoCloseable {

		private final LlmUsageRecorder recorder;

		private final String stage;

		private final long started = System.nanoTime();

		private boolean success;

		private Integer promptTokens;

		private Integer completionTokens;

		private String errorCode;

		private Timer(LlmUsageRecorder recorder, String stage) {
			this.recorder = recorder;
			this.stage = stage;
		}

		public Timer usage(Integer promptTokens, Integer completionTokens) {
			this.promptTokens = promptTokens;
			this.completionTokens = completionTokens;
			return this;
		}

		public Timer success() {
			this.success = true;
			return this;
		}

		public Timer failure(String errorCode) {
			this.errorCode = errorCode;
			return this;
		}

		@Override
		public void close() {
			recorder.record(stage, Duration.ofNanos(System.nanoTime() - started).toMillis(), promptTokens,
					completionTokens, success, errorCode);
		}

	}

}
