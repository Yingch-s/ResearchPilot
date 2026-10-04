package com.alibaba.cloud.ai.example.deepresearch.researchpilot.agent;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvidencePacketMergerTest {

	@Test
	void workerContractBoundsBudgetAndRejectsRecursion() {
		assertThrows(IllegalArgumentException.class,
				() -> new BoundedWorkerContract("w", "compare", List.of(), 0, Duration.ofSeconds(5), false));
		assertThrows(IllegalArgumentException.class,
				() -> new BoundedWorkerContract("w", "compare", List.of(), 2, Duration.ofSeconds(5), true));
	}

	@Test
	void mergerDeduplicatesClaimsAndRetainsConflictGapAndFailure() {
		var a = new EvidencePacketMerger.EvidencePacket("a",
				List.of(new EvidencePacketMerger.Claim("同一结论", List.of("chunk-1"), "SUPPORTED")), List.of("缺少中文样本"),
				false);
		var b = new EvidencePacketMerger.EvidencePacket("b",
				List.of(new EvidencePacketMerger.Claim("同一结论", List.of("chunk-1"), "CONTRADICTED")), List.of(), false);
		var failed = new EvidencePacketMerger.EvidencePacket("c", List.of(), List.of(), true);
		var result = EvidencePacketMerger.merge(List.of(a, b, failed));
		assertEquals(1, result.claims().size());
		assertEquals(List.of("同一结论"), result.conflicts());
		assertEquals(List.of("缺少中文样本"), result.gaps());
		assertEquals(List.of("c"), result.failedWorkers());
	}

}
