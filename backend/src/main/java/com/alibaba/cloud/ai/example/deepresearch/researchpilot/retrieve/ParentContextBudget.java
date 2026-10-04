package com.alibaba.cloud.ai.example.deepresearch.researchpilot.retrieve;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Parent text is background context; citation identities remain attached to child
 * excerpts.
 */
public final class ParentContextBudget {

	private ParentContextBudget() {
	}

	public static String assemble(List<RpRetrievalService.Candidate> candidates, Map<String, String> parents,
			int maxChars) {
		StringBuilder out = new StringBuilder();
		var seen = new LinkedHashSet<String>();
		for (var candidate : candidates) {
			String id = candidate.parentId();
			if (id == null || !seen.add(id) || !parents.containsKey(id))
				continue;
			String header = "\n[章节背景 " + id + "] " + String.valueOf(candidate.sectionPath()) + "\n";
			int remaining = maxChars - out.length() - header.length();
			if (remaining <= 0)
				break;
			String text = parents.get(id);
			if (text == null || text.isBlank())
				continue;
			int end = Math.min(text.length(), Math.min(4000, remaining));
			if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1)))
				end--;
			out.append(header).append(text, 0, end);
		}
		return out.toString();
	}

}
