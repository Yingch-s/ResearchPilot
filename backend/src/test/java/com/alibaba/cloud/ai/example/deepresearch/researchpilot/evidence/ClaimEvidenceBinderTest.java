package com.alibaba.cloud.ai.example.deepresearch.researchpilot.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClaimEvidenceBinderTest {

	@Test
	void splitsClaimsAndKeepsExplicitSourceScope() {
		var claims = ClaimEvidenceBinder.split("方法降低延迟。[KB-1]\n\n成本保持稳定。[KB-2]");

		assertEquals(2, claims.size());
		assertEquals("clm_", claims.get(0).id().substring(0, 4));
		assertEquals(0, claims.get(0).sourceIndexes().get(0));
		assertEquals(1, claims.get(1).sourceIndexes().get(0));
	}

	@Test
	void leavesUnmarkedClaimsReviewableWithoutInventingSources() {
		var claims = ClaimEvidenceBinder.split("资料没有报告该指标。");

		assertEquals(1, claims.size());
		assertTrue(claims.get(0).sourceIndexes().isEmpty());
	}

}
