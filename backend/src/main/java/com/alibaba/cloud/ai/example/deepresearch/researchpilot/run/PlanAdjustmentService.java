package com.alibaba.cloud.ai.example.deepresearch.researchpilot.run;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Produces a bounded, reviewable plan revision from evidence signals. It never mutates a
 * run and never expands the plan without a concrete gap or conflict.
 */
public final class PlanAdjustmentService {

	private static final int MAX_STEPS = 6;

	private PlanAdjustmentService() {
	}

	public record EvidenceSignal(String stepId, String kind, String detail) {
	}

	public record Proposal(List<String> steps, List<String> reasons, boolean requiresApproval) {
	}

	public static Proposal propose(List<String> currentSteps, List<EvidenceSignal> signals) {
		List<String> steps = new ArrayList<>(currentSteps == null ? List.of() : currentSteps);
		Set<String> reasons = new LinkedHashSet<>();
		if (signals != null) {
			for (EvidenceSignal signal : signals) {
				if (signal == null || signal.kind() == null || signal.kind().isBlank())
					continue;
				String detail = signal.detail() == null || signal.detail().isBlank() ? "需要补充核验"
						: signal.detail().trim();
				String target = signal.stepId() == null ? "当前研究" : signal.stepId();
				if ("CONFLICT".equalsIgnoreCase(signal.kind())) {
					reasons.add("发现冲突：" + target + "，" + detail);
					replaceOrAppend(steps, target, "复核冲突：" + detail);
				}
				else if ("GAP".equalsIgnoreCase(signal.kind())) {
					reasons.add("发现缺口：" + target + "，" + detail);
					if (steps.size() < MAX_STEPS && steps.stream().noneMatch(step -> step.contains(detail)))
						steps.add("补充取证：" + detail);
				}
			}
		}
		return new Proposal(List.copyOf(steps), List.copyOf(reasons), !reasons.isEmpty());
	}

	private static void replaceOrAppend(List<String> steps, String target, String suffix) {
		for (int i = 0; i < steps.size(); i++) {
			if (steps.get(i).equals(target) || steps.get(i).contains(target)) {
				if (!steps.get(i).contains(suffix))
					steps.set(i, steps.get(i) + "（" + suffix + "）");
				return;
			}
		}
		if (steps.size() < MAX_STEPS)
			steps.add(suffix);
	}

}
