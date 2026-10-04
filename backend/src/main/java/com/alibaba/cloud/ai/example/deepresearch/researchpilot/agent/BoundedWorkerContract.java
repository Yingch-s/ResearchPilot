package com.alibaba.cloud.ai.example.deepresearch.researchpilot.agent;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Contract for a bounded research worker; workers cannot recursively delegate. */
public record BoundedWorkerContract(String workerId, String objective, List<String> sourceIds, int maxCalls,
		Duration timeout, boolean allowDelegation) {

	public BoundedWorkerContract {
		if (workerId == null || workerId.isBlank() || objective == null || objective.isBlank()) {
			throw new IllegalArgumentException("workerId 和 objective 不能为空");
		}
		if (sourceIds == null || sourceIds.size() > 100) {
			throw new IllegalArgumentException("worker 资料范围最多 100 条");
		}
		if (maxCalls < 1 || maxCalls > 20) {
			throw new IllegalArgumentException("worker 调用预算必须在 1-20 之间");
		}
		Objects.requireNonNull(timeout, "timeout");
		if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(10)) > 0) {
			throw new IllegalArgumentException("worker 超时必须在 1 秒到 10 分钟之间");
		}
		if (allowDelegation) {
			throw new IllegalArgumentException("worker 不允许递归委派");
		}
		sourceIds = List.copyOf(sourceIds);
	}

}
