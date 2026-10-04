package com.alibaba.cloud.ai.example.deepresearch.researchpilot.ask;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class ContextCompressionAuditTest {

	@Test
	void detectsDroppedGoalCorrectionPlanAndCitationAnchors() {
		var result = ContextCompressionAudit.audit("目标：比较方法。citation=KB-1",
				Set.of("目标：比较方法", "用户纠正：只看中文", "plan=v2", "KB-1"), 200);

		assertFalse(result.preservesRequiredContext());
		assertTrue(result.missingAnchors().contains("plan=v2"));
		assertTrue(result.withinBudget());
	}

	@Test
	void acceptsACompleteCompactedContextWithinBudget() {
		var result = ContextCompressionAudit.audit("目标：比较方法。用户纠正：只看中文。未完成：步骤2。plan=v2。KB-1",
				Set.of("目标：比较方法", "用户纠正：只看中文", "未完成：步骤2", "plan=v2", "KB-1"), 200);

		assertTrue(result.preservesRequiredContext());
		assertTrue(result.withinBudget());
	}

}
