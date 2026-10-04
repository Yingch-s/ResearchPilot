package com.alibaba.cloud.ai.example.deepresearch.researchpilot.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Deterministic second pass for worker claims before they enter the evidence store. */
public final class EvidencePacketReviewer {

	private static final Pattern NUMBER = Pattern.compile("(?:\\d+(?:[.,]\\d+)?%?|\\d+\\s*(?:ms|s|秒|分钟|小时))",
			Pattern.CASE_INSENSITIVE);

	private EvidencePacketReviewer() {
	}

	public static List<ReviewFlag> review(EvidencePacketMerger.MergeResult merged) {
		List<ReviewFlag> flags = new ArrayList<>();
		if (merged == null) {
			return List.of();
		}
		for (EvidencePacketMerger.Claim claim : merged.claims()) {
			List<String> reasons = new ArrayList<>();
			if (claim.sourceIds().isEmpty()) {
				reasons.add("missing_source");
			}
			if (hasNumber(claim.claim()) && !hasNumber(claim.support())) {
				reasons.add("numeric_value_not_found_in_support");
			}
			if (!reasons.isEmpty()) {
				flags.add(new ReviewFlag(claim, List.copyOf(reasons)));
			}
		}
		if (!merged.conflicts().isEmpty()) {
			flags.add(new ReviewFlag(null, List.of("conflicting_worker_support")));
		}
		return List.copyOf(flags);
	}

	private static boolean hasNumber(String value) {
		return value != null && NUMBER.matcher(value).find();
	}

	public record ReviewFlag(EvidencePacketMerger.Claim claim, List<String> reasons) {
	}

}
