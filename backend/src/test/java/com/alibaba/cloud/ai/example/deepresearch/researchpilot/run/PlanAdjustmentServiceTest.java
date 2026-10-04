package com.alibaba.cloud.ai.example.deepresearch.researchpilot.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class PlanAdjustmentServiceTest {

	@Test
	void addsBoundedGapStepAndMarksApproval() {
		var proposal = PlanAdjustmentService.propose(List.of("比较方法", "检查实验"),
				List.of(new PlanAdjustmentService.EvidenceSignal("比较方法", "GAP", "缺少中文场景")));
		assertTrue(proposal.requiresApproval());
		assertEquals(3, proposal.steps().size());
		assertTrue(proposal.steps().get(2).contains("中文场景"));
	}

	@Test
	void conflictRevisesExistingStepWithoutUnboundedExpansion() {
		var proposal = PlanAdjustmentService.propose(List.of("比较方法"),
				List.of(new PlanAdjustmentService.EvidenceSignal("比较方法", "CONFLICT", "数字结果不一致")));
		assertEquals(1, proposal.steps().size());
		assertTrue(proposal.steps().get(0).contains("复核冲突"));
	}

}
