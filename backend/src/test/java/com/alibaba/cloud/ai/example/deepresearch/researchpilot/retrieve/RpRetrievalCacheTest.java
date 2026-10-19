package com.alibaba.cloud.ai.example.deepresearch.researchpilot.retrieve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class RpRetrievalCacheTest {

	@Test
	void cacheKeyIsStableForScopeOrderAndSeparatesIdentity() {
		String first = RpRetrievalService.cacheKey("topic", List.of("b", "a"), "  compare   methods ", 5, false,
				"rev-1", "bm25+dense");
		String same = RpRetrievalService.cacheKey("topic", List.of("a", "b"), "compare methods", 5, false, "rev-1",
				"bm25+dense");
		String changed = RpRetrievalService.cacheKey("topic", List.of("a", "b"), "compare methods", 5, false, "rev-2",
				"bm25+dense");

		assertEquals(first, same);
		assertNotEquals(first, changed);
	}

	@Test
	void explicitChineseAbstractRequestsUseAbstractSectionRouting() {
		assertEquals(true, RpRetrievalService.requestsAbstract("请给出摘要原文并翻译"));
		assertEquals(true, RpRetrievalService.requestsAbstract("translate the abstract"));
		assertEquals(false, RpRetrievalService.requestsAbstract("比较实验结果"));
	}

}
