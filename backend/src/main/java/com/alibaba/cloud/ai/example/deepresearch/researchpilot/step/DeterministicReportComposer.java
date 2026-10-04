package com.alibaba.cloud.ai.example.deepresearch.researchpilot.step;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.stream.Collectors;

/** Builds a truthful report when model synthesis is unavailable. */
public final class DeterministicReportComposer {

	private DeterministicReportComposer() {
	}

	public static String compose(String query, List<String> scopeTitles, List<Map<String, Object>> steps,
			List<Map<String, Object>> acceptedSources, Object budgetReason) {
		scopeTitles = scopeTitles == null ? List.of()
				: scopeTitles.stream().filter(title -> !isInternalFixture(title)).collect(Collectors.toList());
		acceptedSources = acceptedSources == null ? List.of()
				: acceptedSources.stream()
					.filter(source -> !isInternalFixture(String.valueOf(source.get("title"))))
					.collect(Collectors.toList());
		steps = steps == null ? List.of() : steps;
		long completed = steps.stream().filter(s -> "COMPLETED".equals(s.get("status"))).count();
		long skipped = steps.stream().filter(s -> "SKIPPED".equals(s.get("status"))).count();
		long failed = steps.stream().filter(s -> "FAILED".equals(s.get("status"))).count();
		long contradicted = acceptedSources.stream().filter(DeterministicReportComposer::isContradicted).count();
		long review = acceptedSources.stream().filter(DeterministicReportComposer::needsReview).count();
		StringBuilder report = new StringBuilder("# 研究报告\n\n");
		report.append("## 直接回答\n\n");
		if (completed == 0) {
			report.append("当前没有足够的已完成证据回答这个问题。请补充资料或继续研究。\n\n");
		}
		else {
			report.append("本报告汇总了 ").append(completed).append(" 个已完成研究步骤的发现；逐项依据见下方证据与局限。\n\n");
		}
		report.append("**研究问题**：").append(query).append("\n\n");
		report.append("## 资料范围\n\n");
		if (scopeTitles.isEmpty()) {
			report.append("- 专题内全部就绪资料（未限定单篇）\n");
		}
		else {
			scopeTitles.forEach(title -> report.append("- ").append(title).append("\n"));
		}
		report.append("\n## 逐项发现\n\n");
		for (Map<String, Object> step : steps) {
			report.append("### ")
				.append(step.get("step_index") instanceof Number n ? n.intValue() + 1 : step.get("step_index"))
				.append(". ")
				.append(step.get("step_id"))
				.append("（")
				.append(step.get("status"))
				.append("）\n\n");
			if (step.get("result") != null) {
				report.append(cleanModelText(String.valueOf(step.get("result")))).append("\n\n");
			}
		}
		report.append("## 证据与分歧\n\n");
		if (acceptedSources.isEmpty()) {
			report.append("- 尚无已接纳证据；以下内容不能视为已验证结论。\n");
		}
		else {
			acceptedSources.forEach(source -> report.append("- ")
				.append(source.get("title"))
				.append("（")
				.append(source.get("status"))
				.append("）\n"));
			if (contradicted > 0) {
				report.append("- 检出 ").append(contradicted).append(" 条存在反向支持关系的来源，需要单独核对条件和适用范围。\n");
			}
			if (review > 0) {
				report.append("- 有 ").append(review).append(" 条来源仍待引用复核，不能作为无条件结论。\n");
			}
		}
		report.append("\n## 覆盖度与缺口\n\n");
		report.append("- 已检查来源：")
			.append(acceptedSources.size())
			.append(" 条；已完成步骤：")
			.append(completed)
			.append(" / ")
			.append(steps.size())
			.append("。\n");
		if (skipped + failed > 0) {
			report.append("- 未解决项：").append(skipped + failed).append(" 个步骤未完成，报告没有根据缺失内容推断结论。\n");
		}
		if (contradicted == 0 && !acceptedSources.isEmpty()) {
			report.append("- 当前已接纳来源没有标记为反向证据；这不等于已经证明不存在冲突。\n");
		}
		if (skipped + failed > 0 || budgetReason != null) {
			report.append("\n## 局限与未完成项\n\n- ")
				.append(skipped)
				.append(" 个步骤跳过、")
				.append(failed)
				.append(" 个步骤失败；相关结论证据不足，应视为未报告而非推断依据。\n");
			if (budgetReason != null) {
				report.append("- 停止原因：").append(budgetReason).append("\n");
			}
		}
		report.append("\n## 来源清单\n\n");
		if (acceptedSources.isEmpty()) {
			report.append("-（本 run 未收集到已接纳证据）\n");
		}
		else {
			acceptedSources.forEach(source -> report.append("- ").append(source.get("title")).append("\n"));
		}
		return report.toString();
	}

	/** Keep internal tool protocol and scratchpad traces out of the user report. */
	private static String cleanModelText(String value) {
		if (value == null || value.isBlank())
			return "";
		// DeepSeek can emit both ASCII and full-width DSML delimiters. Treat the
		// whole protocol segment as non-user content before applying line filters.
		String cleaned = sanitizePersisted(value);
		return cleaned.isBlank() ? "（该步骤完成了检索，但没有生成可展示的归纳。）" : cleaned;
	}

	/** Public boundary sanitizer used when older persisted reports are read. */
	public static String sanitizePersisted(String report) {
		if (report == null || report.isBlank())
			return report;
		return report.replaceAll("(?is)<(?!/)[^>]*DSML[^>]*\\bcalls\\s*>.*?</[^>]*DSML[^>]*\\bcalls\\s*>", "")
			// A malformed legacy protocol must not swallow subsequent report sections.
			.replaceAll("(?is)<(?!/)[^>]*DSML[^>]*>.*?(?=\\n#{1,6}\\s|\\z)", "")
			.replaceAll(
					"(?mi)^\\s*(?:.*DSML.*|invoke name=|parameter name=|calls>|let me search\\.?|I'll research this task\\.?|I['’]ll research this task\\.?).*$",
					"")
			.replaceAll(
					"(?i)\\b(?:let me search\\.?|let me start searching\\.?|I'll research this task\\.?|I['’]ll research this task\\.?)",
					"")
			.replaceAll("(?i)\\b(?:test-note|非法转移测试|请求幂等测试|状态转移测试|内部验证专题)\\b", "")
			.replaceAll("(?m)^\\s*[-*]\\s*$", "")
			.replaceAll("\\n{3,}", "\\n\\n")
			.trim();
	}

	/** Sanitize nested user-visible payloads such as citation quotes. */
	public static Object sanitizeStructured(Object value) {
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> clean = new LinkedHashMap<>();
			map.forEach((key, item) -> clean.put(String.valueOf(key), sanitizeStructured(item)));
			return clean;
		}
		if (value instanceof List<?> list) {
			return list.stream().map(DeterministicReportComposer::sanitizeStructured).toList();
		}
		return value instanceof String text ? sanitizePersisted(text) : value;
	}

	private static boolean isInternalFixture(String value) {
		String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		return normalized.equals("test-note") || normalized.equals("非法转移测试") || normalized.equals("请求幂等测试")
				|| normalized.equals("状态转移测试") || normalized.equals("内部验证专题");
	}

	private static boolean isContradicted(Map<String, Object> source) {
		return "CONTRADICTED".equals(source.get("claim_status")) || "CONTRADICTED".equals(source.get("status"));
	}

	private static boolean needsReview(Map<String, Object> source) {
		return "NEEDS_REVIEW".equals(source.get("claim_status")) || "INSUFFICIENT".equals(source.get("claim_status"));
	}

}
