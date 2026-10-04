package com.alibaba.cloud.ai.example.deepresearch.researchpilot.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class EvidencePacketReviewerTest {

	@Test
	void flagsMissingSourcesAndUnanchoredNumbers() {
		var claim = new EvidencePacketMerger.Claim("延迟降低 20%", List.of(), "延迟有所降低");
		var merged = new EvidencePacketMerger.MergeResult(List.of(claim), List.of(), List.of(), List.of());

		var flags = EvidencePacketReviewer.review(merged);
		assertEquals(List.of("missing_source", "numeric_value_not_found_in_support"), flags.get(0).reasons());
	}

}
