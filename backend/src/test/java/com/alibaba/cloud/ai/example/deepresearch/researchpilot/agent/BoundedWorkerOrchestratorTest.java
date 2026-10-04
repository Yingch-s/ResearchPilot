package com.alibaba.cloud.ai.example.deepresearch.researchpilot.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

class BoundedWorkerOrchestratorTest {

	@Test
	void enforcesTotalCallBudgetAndKeepsPartialResults() {
		var first = new BoundedWorkerContract("one", "比较方法", List.of("a"), 2, Duration.ofSeconds(1), false);
		var second = new BoundedWorkerContract("two", "比较成本", List.of("b"), 2, Duration.ofSeconds(1), false);
		var third = new BoundedWorkerContract("three", "比较限制", List.of("c"), 2, Duration.ofSeconds(1), false);
		try (var orchestrator = new BoundedWorkerOrchestrator(2, 4)) {
			var result = orchestrator.execute(List.of(first, second, third),
					contract -> new EvidencePacketMerger.EvidencePacket(contract.workerId(), List.of(), List.of(),
							false));
			assertEquals(2, result.packets().size());
			assertEquals(List.of("three"), result.rejectedWorkers());
			assertEquals(4, result.reservedCalls());
		}
	}

	@Test
	void timesOutOneWorkerWithoutBlockingTheOther() {
		var slow = new BoundedWorkerContract("slow", "慢任务", List.of(), 1, Duration.ofMillis(20), false);
		try (var orchestrator = new BoundedWorkerOrchestrator(1, 1)) {
			var result = orchestrator.execute(List.of(slow), contract -> {
				Thread.sleep(100);
				return null;
			});
			assertTrue(result.packets().get(0).failed());
		}
	}

	@Test
	void propagatesCancellationToAllPendingWorkers() {
		var first = new BoundedWorkerContract("one", "一", List.of(), 1, Duration.ofSeconds(1), false);
		var second = new BoundedWorkerContract("two", "二", List.of(), 1, Duration.ofSeconds(1), false);
		try (var orchestrator = new BoundedWorkerOrchestrator(2, 2)) {
			var result = orchestrator.execute(List.of(first, second), contract -> {
				Thread.sleep(1000);
				return null;
			}, () -> true);
			assertEquals(2, result.packets().size());
			assertTrue(result.packets().stream().allMatch(EvidencePacketMerger.EvidencePacket::failed));
		}
	}

}
