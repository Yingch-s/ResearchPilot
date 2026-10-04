/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.example.deepresearch.researchpilot.evidence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RpClaimVerifier Layer 1（确定性引用校验）纯逻辑测试 —— 不依赖 Spring/DB/sidecar。
 *
 * <p>
 * Layer 2（embedding + NLI）依赖外部服务，归评测期 L2 集成验证，此处不测。
 */
class RpClaimVerifierLayer1Test {

	private final RpClaimVerifier verifier = new RpClaimVerifier(null, null);

	private static final String CHUNK = "Dense Passage Retrieval (DPR) uses a dual-encoder architecture. "
			+ "The question encoder and passage encoder are trained independently. "
			+ "In-batch negatives are used during training to improve efficiency.";

	@Test
	void directQuoteShouldPassLayer1() {
		String research = "本文引用原文：In-batch negatives are used during training to improve efficiency.";
		RpClaimVerifier.Layer1Result r = verifier.verifyQuote(research, CHUNK);
		assertTrue(r.supported());
		assertFalse(r.hitSamples().isEmpty());
	}

	@Test
	void chineseSummaryAgainstEnglishChunkShouldFailLayer1() {
		// 核心场景：中文归纳 vs 英文原文 —— n-gram 系统性无命中，须交由 Layer 2
		String research = "DPR 采用双编码器架构，训练时使用批内负样本提升效率。";
		RpClaimVerifier.Layer1Result r = verifier.verifyQuote(research, CHUNK);
		assertFalse(r.supported());
	}

	@Test
	void sentenceContainmentShouldPassEvenWithoutNGrams() {
		// 句级包含：归一化后中文标点差异不影响命中
		String research = "作者的结论是，In-batch negatives are used during training to improve efficiency。";
		RpClaimVerifier.Layer1Result r = verifier.verifyQuote(research, CHUNK);
		assertTrue(r.sentenceContained());
		assertTrue(r.supported());
	}

	@Test
	void unrelatedTextShouldFailLayer1() {
		RpClaimVerifier.Layer1Result r = verifier.verifyQuote("完全无关的内容草莓蛋糕怎么做", CHUNK);
		assertFalse(r.supported());
	}

	@Test
	void nullInputsShouldFailGracefully() {
		assertFalse(verifier.verifyQuote(null, CHUNK).supported());
		assertFalse(verifier.verifyQuote("text", null).supported());
	}

	@Test
	void verificationJsonShouldRecordLayer1Fields() {
		RpClaimVerifier.CandidateVerdict v = new RpClaimVerifier.CandidateVerdict(
				new RpEvidenceService.KbCandidate("c1", "p1", CHUNK, "sec", 3), verifier.verifyQuote(CHUNK, CHUNK),
				null, RpClaimVerifier.QuoteStatus.QUOTE_SUPPORTED, RpClaimVerifier.ClaimSupportStatus.SUPPORTED,
				"SUPPORTED", "L1_QUOTE_SUPPORTED|L2_Supported");
		String json = verifier.verificationJson(v);
		assertTrue(json.contains("\"sentence_contained\""));
		assertTrue(json.contains("\"hit_samples\""));
		assertTrue(json.contains("\"quote_status\":\"QUOTE_SUPPORTED\""));
		assertTrue(json.contains("\"claim_status\":\"SUPPORTED\""));
	}

	@Test
	void candidateVerdictStatusMappingContract() {
		// H3 判级契约：quote_status 只由 Layer1 决定；claim_status 只由 Layer2 决定；
		// 引用真实 ≠ 结论被支持 —— CONTRADICTED 优先于 quote 通过（legacy 聚合）。
		var cs = new RpClaimVerifier.CandidateVerdict(new RpEvidenceService.KbCandidate("c1", "p1", CHUNK, "sec", 3),
				verifier.verifyQuote(CHUNK, CHUNK), null, RpClaimVerifier.QuoteStatus.QUOTE_SUPPORTED,
				RpClaimVerifier.ClaimSupportStatus.CONTRADICTED, "CONTRADICTED", "M");
		assertEquals("CONTRADICTED", cs.legacyStatus());
		assertEquals(RpClaimVerifier.QuoteStatus.QUOTE_SUPPORTED, cs.quoteStatus());
		assertEquals(RpClaimVerifier.ClaimSupportStatus.CONTRADICTED, cs.claimStatus());
	}

}
