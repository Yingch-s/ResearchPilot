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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V0.4 H1.1：执行事件日志（JSONL，每 Run 一个文件）。
 *
 * <p>
 * 只追加、失败静默降级——事件日志是观测数据，任何写日志异常不得阻断研究执行。 H2 在此文件之上做耗时分析 / Reflection 统计 / 无进展根因诊断。
 */
@Component
public class RpEventLogger {

	private static final Logger logger = LoggerFactory.getLogger(RpEventLogger.class);

	private final Path eventDir;

	private final ObjectMapper mapper = new ObjectMapper();

	public RpEventLogger(@Value("${researchpilot.events.dir:./data/rp-events}") String eventDir) {
		this.eventDir = Path.of(eventDir);
	}

	/**
	 * 记录一条执行事件。runId 为 null 时忽略（无法归属的事件不落盘）。
	 */
	public synchronized void log(UUID runId, String stepId, UUID attemptId, RpEventType eventType, String node,
			Instant runStartedAt, Map<String, Object> detail) {
		if (runId == null) {
			return;
		}
		try {
			Files.createDirectories(eventDir);
			Path file = eventDir.resolve(runId + ".jsonl");
			try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.READ,
					StandardOpenOption.WRITE); FileLock ignored = channel.lock()) {
				long sequence = countLines(channel) + 1L;
				Map<String, Object> event = new LinkedHashMap<>();
				event.put("sequence", sequence);
				event.put("event_id", runId + ":" + sequence);
				event.put("run_id", runId.toString());
				if (stepId != null) {
					event.put("step_id", stepId);
				}
				if (attemptId != null) {
					event.put("attempt_id", attemptId.toString());
				}
				event.put("event_type", eventType.name());
				if (node != null) {
					event.put("node", node);
				}
				event.put("timestamp", Instant.now().toString());
				if (runStartedAt != null) {
					event.put("elapsed_ms", Duration.between(runStartedAt, Instant.now()).toMillis());
				}
				if (detail != null && !detail.isEmpty()) {
					event.put("detail", detail);
				}
				channel.position(channel.size());
				channel.write(StandardCharsets.UTF_8.encode(mapper.writeValueAsString(event) + "\n"));
			}
		}
		catch (IOException e) {
			logger.warn("执行事件写入失败（观测数据降级，不影响执行）: {}", e.getMessage());
		}
	}

	private long countLines(FileChannel channel) throws IOException {
		channel.position(0);
		long count = 0;
		var buffer = java.nio.ByteBuffer.allocate(8192);
		while (channel.read(buffer) > 0) {
			buffer.flip();
			while (buffer.hasRemaining()) {
				if (buffer.get() == '\n')
					count++;
			}
			buffer.clear();
		}
		return count;
	}

	/** Read events newer than a durable sequence for reconnect/backfill. */
	public List<Map<String, Object>> after(UUID runId, long afterSequence, int limit) {
		Path file = eventDir.resolve(runId + ".jsonl");
		if (!Files.exists(file)) {
			return List.of();
		}
		try {
			List<Map<String, Object>> out = new java.util.ArrayList<>();
			for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
				try {
					Map<String, Object> event = mapper.readValue(line, Map.class);
					if (event.get("sequence") instanceof Number n && n.longValue() > afterSequence)
						out.add(event);
				}
				catch (IOException ignored) {
					// Skip a partial line left by an interrupted append.
				}
			}
			if (out.size() > Math.max(1, limit))
				return out.subList(out.size() - Math.max(1, limit), out.size());
			return out;
		}
		catch (IOException e) {
			return List.of();
		}
	}

	/**
	 * 读取某 Run 的事件尾部（倒序，最多 limit 条）。文件不存在返回空列表。
	 */
	public List<Map<String, Object>> tail(UUID runId, int limit) {
		Path file = eventDir.resolve(runId + ".jsonl");
		if (!Files.exists(file)) {
			return List.of();
		}
		try {
			List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
			List<String> tail = lines.subList(Math.max(0, lines.size() - limit), lines.size());
			java.util.Collections.reverse(tail);
			List<Map<String, Object>> out = new java.util.ArrayList<>();
			var mapper2 = new ObjectMapper();
			for (String line : tail) {
				try {
					out.add(mapper2.readValue(line, Map.class));
				}
				catch (IOException ignored) {
					// 跳过损坏行（观测数据不中断业务）
				}
			}
			return out;
		}
		catch (IOException e) {
			return List.of();
		}
	}

}
