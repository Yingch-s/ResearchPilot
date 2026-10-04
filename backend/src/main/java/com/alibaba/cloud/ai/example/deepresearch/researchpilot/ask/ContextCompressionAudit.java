package com.alibaba.cloud.ai.example.deepresearch.researchpilot.ask;

import java.util.LinkedHashSet;
import java.util.Set;

/** Offline audit for context compaction: required anchors must survive the budget. */
public final class ContextCompressionAudit {

	private ContextCompressionAudit() {
	}

	public static Result audit(String compacted, Set<String> requiredAnchors, int maxBytes) {
		String text = compacted == null ? "" : compacted;
		Set<String> missing = new LinkedHashSet<>();
		if (requiredAnchors != null) {
			for (String anchor : requiredAnchors) {
				if (anchor != null && !anchor.isBlank() && !text.contains(anchor)) {
					missing.add(anchor);
				}
			}
		}
		return new Result(ConversationContextService.budgetSize(text),
				maxBytes >= 0 && ConversationContextService.budgetSize(text) <= maxBytes, Set.copyOf(missing));
	}

	public record Result(int bytes, boolean withinBudget, Set<String> missingAnchors) {

		public boolean preservesRequiredContext() {
			return missingAnchors.isEmpty();
		}

	}

}
