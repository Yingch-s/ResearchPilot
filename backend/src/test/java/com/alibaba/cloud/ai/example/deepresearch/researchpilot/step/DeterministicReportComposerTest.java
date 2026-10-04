package com.alibaba.cloud.ai.example.deepresearch.researchpilot.step;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicReportComposerTest {

	@Test
	void reportAnswersFirstAndDisclosesEvidenceLimits() {
		String report = DeterministicReportComposer.compose("比较方法", List.of("论文 A"),
				List.of(Map.of("step_index", 0, "step_id", "方法比较", "status", "COMPLETED", "result", "A 优于 B"),
						Map.of("step_index", 1, "step_id", "局限", "status", "FAILED", "result", "暂不可用")),
				List.of(Map.of("title", "论文 A", "status", "ACCEPTED")), "预算耗尽");
		assertTrue(report.indexOf("## 直接回答") < report.indexOf("## 逐项发现"));
		assertTrue(report.contains("A 优于 B"));
		assertTrue(report.contains("## 证据与分歧"));
		assertTrue(report.contains("## 局限与未完成项"));
		assertTrue(report.contains("预算耗尽"));
	}

	@Test
	void emptyEvidenceNeverPretendsToHaveAConclusion() {
		String report = DeterministicReportComposer.compose("问题", List.of(), List.of(), List.of(), null);
		assertTrue(report.contains("没有足够的已完成证据"));
		assertTrue(report.contains("不能视为已验证结论"));
	}

	@Test
	void reportRemovesAsciiAndFullWidthDeepSeekProtocol() {
		String report = DeterministicReportComposer.compose("问题", List.of(),
				List.of(Map.of("step_index", 0, "step_id", "检索", "status", "COMPLETED", "result",
						"正常结论\n<｜｜DSML｜｜ calls>\n<｜｜DSML｜｜ invoke name=searchFilterTool>\n<｜｜DSML｜｜ calls>")),
				List.of(), null);
		assertTrue(report.contains("正常结论"));
		assertTrue(!report.contains("DSML"));
		assertTrue(!report.contains("searchFilterTool"));
	}

	@Test
	void protocolCleaningPreservesSubsequentConclusionsAndSourceLinks() {
		String raw = "原始结论\n<｜DSML｜calls><｜DSML｜invoke name=search>query</｜DSML｜invoke></｜DSML｜calls>\n结论仍在\n## 来源\n[论文](https://example.org/paper)";
		String cleaned = DeterministicReportComposer.sanitizePersisted(raw);
		assertTrue(cleaned.contains("结论仍在"));
		assertTrue(cleaned.contains("https://example.org/paper"));
		assertTrue(!cleaned.contains("DSML"));
		String malformed = DeterministicReportComposer.sanitizePersisted("<｜DSML｜calls>bad\n## 局限\n仍有不确定性");
		assertTrue(malformed.contains("仍有不确定性"));
	}

	@Test
	void nestedCitationCleaningPreservesIdentity() {
		Object cleaned = DeterministicReportComposer.sanitizeStructured(
				List.of(Map.of("number", 2, "url", "https://example.org/paper", "text", "let me search. 研究结论")));
		assertTrue(cleaned.toString().contains("number=2"));
		assertTrue(cleaned.toString().contains("https://example.org/paper"));
		assertTrue(!cleaned.toString().contains("let me search"));
	}

}
