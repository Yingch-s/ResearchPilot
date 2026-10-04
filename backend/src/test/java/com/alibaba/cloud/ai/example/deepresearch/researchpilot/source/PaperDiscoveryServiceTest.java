package com.alibaba.cloud.ai.example.deepresearch.researchpilot.source;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PaperDiscoveryServiceTest {

	@Test
	void normalizesDoiAndRejectsNonDoiSearchTerms() {
		assertEquals("10.1234/example", PaperDiscoveryService.normalizeDoi("https://doi.org/10.1234/example"));
		assertEquals("10.5555/a.b", PaperDiscoveryService.normalizeDoi("10.5555/a.b"));
		assertNull(PaperDiscoveryService.normalizeDoi("retrieval augmented generation"));
	}

}
