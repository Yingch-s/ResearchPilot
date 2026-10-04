package com.alibaba.cloud.ai.example.deepresearch.researchpilot.ask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ContextCompactorTest {

	@Test
	void preservesRequiredAnchorsBeforeOptionalHistory() {
		var result = ContextCompactor.compact(
				List.of(new ContextCompactor.Segment("history", "history", "普通历史内容很多", 100),
						new ContextCompactor.Segment("goal", "goal", "原始目标：比较方法", 1),
						new ContextCompactor.Segment("citation", "citation", "引用：[KB-1]", 1)),
				Set.of("原始目标：比较方法", "[KB-1]"), 55);
		assertTrue(result.preservesRequiredContext());
		assertEquals(List.of("goal", "citation"), result.includedIds());
	}

	@Test
	void reportsImpossibleBudgetInsteadOfSilentlyDroppingAnchors() {
		var result = ContextCompactor.compact(List.of(new ContextCompactor.Segment("goal", "goal", "原始目标：很长的目标", 1)),
				Set.of("原始目标：很长的目标"), 2);
		assertTrue(result.missingAnchors().isEmpty());
		assertTrue(!result.withinBudget());
	}

	@Test
	void acceptsModelSpecificBudgetCounter() {
		var result = ContextCompactor.compact(
				List.of(new ContextCompactor.Segment("goal", "goal", "原始目标：比较方法", 1),
						new ContextCompactor.Segment("optional", "history", "补充背景", 1)),
				Set.of("原始目标：比较方法"), 8, text -> text.codePointCount(0, text.length()));
		assertTrue(result.preservesRequiredContext());
		assertEquals(List.of("goal"), result.includedIds());
		assertEquals("原始目标：比较方法".codePointCount(0, "原始目标：比较方法".length()), result.bytes());
	}

	@Test
	void bundledTokenizerCountsRecognizedModelsAndFallsBackForUnknownNames() {
		assertTrue(ContextTokenCounter.jtokkit("gpt-4o").count("中文 context") > 0);
		assertTrue(ContextTokenCounter.jtokkit("vendor-private-model").count("中文 context") > 0);
	}

}
