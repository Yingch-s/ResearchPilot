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

import com.alibaba.cloud.ai.example.deepresearch.researchpilot.sidecar.SidecarClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ResearchPilot Phase E：双层引用校验器（决策记录 2026-10-02，项目所有者设计）。
 *
 * <p>
 * 设计动机：单纯把 n-gram 换成 embedding 相似度是错的 —— 语义相关 ≠ 事实支持 （"模型 A 优于模型 B"
 * 与其逆命题高度相似但结论相反）。因此分两层：
 *
 * <ol>
 * <li><b>Layer 1 · Quotation Verification</b>（确定性，零成本）：归一化 n-gram 重叠 + 句级原文包含检查 ——
 * 验证"真正的原文引用"；</li>
 * <li><b>Layer 2 · Claim Support Verification</b>（跨语言归纳才触发）：bge-m3 相关性预过滤 → DeepSeek 批量
 * NLI 语义支持判定 → SUPPORTED / CONTRADICTED / INSUFFICIENT。</li>
 * </ol>
 *
 * <p>
 * 校准约定：Layer 2 的阈值与判定结果属"机器初判"，阈值待评测期 L2（SciFact）样本校准修正； 每条 verification JSON 记录 method
 * 与原始判定，评测期可按层分桶分析 —— 不把模型判断当绝对事实。
 */
@Service
public class RpClaimVerifier {

	private static final Logger logger = LoggerFactory.getLogger(RpClaimVerifier.class);

	/** Layer 2 相关性预过滤阈值（bge-m3 跨语言余弦，初值保守，待 L2 校准）。 */
	private static final double RELATEDNESS_THRESHOLD = 0.55;

	/** NLI 判定时的候选原文截断长度（控制 token 成本）。 */
	private static final int EXCERPT_MAX_CHARS = 1500;

	/** 相关性低于阈值的候选不再送 LLM（成本护栏）。 */
	private static final int MAX_LLM_CANDIDATES = 6;

	private final SidecarClient sidecar;

	// 惰性构建：Layer 1 纯逻辑（单测）不依赖 ChatModel
	private final ChatModel chatModel;

	private volatile ChatClient nliClient;

	private final ObjectMapper mapper = new ObjectMapper();

	public RpClaimVerifier(SidecarClient sidecar, ChatModel chatModel) {
		this.sidecar = sidecar;
		this.chatModel = chatModel;
	}

	private ChatClient nliClient() {
		if (nliClient == null) {
			synchronized (this) {
				if (nliClient == null) {
					nliClient = ChatClient.builder(chatModel)
						.defaultOptions(OpenAiChatOptions.builder().temperature(0.1).build())
						.build();
				}
			}
		}
		return nliClient;
	}

	// ---------- Layer 1：确定性引用校验 ----------

	/**
	 * 归一化：小写 + 去掉非字母数字（中英文通用粗粒度归一）。
	 */
	private String normalize(String s) {
		if (s == null) {
			return "";
		}
		return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").replaceAll("\\s+", " ").trim();
	}

	private Set<String> nGrams(String text, int n) {
		String[] tokens = normalize(text).split(" ");
		Set<String> grams = new java.util.HashSet<>();
		for (int i = 0; i + n <= tokens.length; i++) {
			grams.add(String.join(" ", java.util.Arrays.copyOfRange(tokens, i, i + n)));
		}
		return grams;
	}

	/**
	 * 句级包含检查：研究文本的任一子句（含逗号切分，≥20 字符）完整出现在原文中， 视为直接引用（位置检查）。中英文标点统一作为切分边界。
	 */
	private boolean sentenceContained(String researchText, String chunkText) {
		String normChunk = normalize(chunkText);
		if (normChunk.isEmpty()) {
			return false;
		}
		for (String sentence : researchText.split("[。！？!?；;，,\\n]+")) {
			String norm = normalize(sentence);
			if (norm.length() >= 20 && normChunk.contains(norm)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Layer 1 结果：8-gram 命中（降级 5-gram）或句级包含。
	 */
	public record Layer1Result(boolean supported, List<String> hitSamples, boolean sentenceContained) {
	}

	public Layer1Result verifyQuote(String researchText, String chunkText) {
		if (researchText == null || chunkText == null) {
			return new Layer1Result(false, List.of(), false);
		}
		List<String> hits = nGrams(chunkText, 8).stream()
			.filter(nGrams(researchText, 8)::contains)
			.limit(5)
			.collect(Collectors.toList());
		boolean supported = !hits.isEmpty();
		if (!supported) {
			supported = nGrams(chunkText, 5).stream().filter(nGrams(researchText, 5)::contains).count() >= 2;
		}
		boolean contained = sentenceContained(researchText, chunkText);
		return new Layer1Result(supported || contained, hits, contained);
	}

	// ---------- Layer 2：Claim Support Verification ----------

	public enum NliVerdict {

		SUPPORTED, CONTRADICTED, INSUFFICIENT, ERROR

	}

	public record Layer2Result(double relatedness, NliVerdict verdict, String reason) {
	}

	/**
	 * H3（硬规则 3）：引用真实性与结论支持性**独立**判级。
	 *
	 * <ul>
	 * <li>{@code quoteStatus}：确定性 Layer 1 判定 —— 引用是否真实出现在原文位置；</li>
	 * <li>{@code claimStatus}：语义 Layer 2 判定 —— 证据是否支持结论；校准前为待复核候选， SUPPORTED/CONTRADICTED
	 * 不得作为最终已验证结论展示；</li>
	 * <li>{@code legacyStatus}：兼容矩阵 API 的合并判级（旧 status 列），H5 收尾后退役。</li>
	 * </ul>
	 */
	public record CandidateVerdict(RpEvidenceService.KbCandidate candidate, Layer1Result layer1, Layer2Result layer2,
			QuoteStatus quoteStatus, ClaimSupportStatus claimStatus, String legacyStatus, String method) {
	}

	/** 引用真实性（确定性 Layer 1）。 */
	public enum QuoteStatus {

		QUOTE_SUPPORTED, QUOTE_WEAK, QUOTE_NOT_FOUND

	}

	/** 结论支持性（语义 Layer 2，校准前待复核）。 */
	public enum ClaimSupportStatus {

		SUPPORTED, CONTRADICTED, INSUFFICIENT, NOT_EVALUATED

	}

	/**
	 * 双层校验入口（H3 重构）：Layer 1 全部候选执行；Layer 2 **对全部候选**执行 —— 引用真实 不代表结论被支持（治理
	 * §四.1"第二层不应只在第一层失败时执行"）。embedding 仅用于 筛选进入 NLI 的候选（成本护栏），不直接决定 Claim 成立。
	 * @return 与 candidates 等长的判级列表
	 */
	public List<CandidateVerdict> verify(String researchText, List<RpEvidenceService.KbCandidate> candidates) {
		// Layer 1：全部候选
		List<Layer1Result> l1 = new ArrayList<>();
		for (var c : candidates) {
			l1.add(verifyQuote(researchText, c.text()));
		}

		// 相关性（全部候选，校准数据用）
		List<Integer> pending = new ArrayList<>();
		List<double[]> vecs = List.of();
		try {
			List<String> toEmbed = new ArrayList<>();
			toEmbed.add(researchText == null ? "" : researchText);
			for (int i = 0; i < candidates.size(); i++) {
				pending.add(i);
				toEmbed.add(candidates.get(i).text());
			}
			if (!pending.isEmpty()) {
				vecs = sidecar.embed(toEmbed);
			}
		}
		catch (Exception e) {
			logger.warn("相关性计算失败（claim 判级降级 NOT_EVALUATED）: {}", e.getMessage());
			pending.clear();
		}

		// NLI 批量（成本护栏内）：L1 通过者必入（引用真实仍需判结论）；其余按相关性阈值筛
		List<Integer> nliTargets = new ArrayList<>();
		double[] rel = new double[candidates.size()];
		for (int p = 0; p < pending.size(); p++) {
			int idx = pending.get(p);
			rel[idx] = vecs.size() > p + 1 ? cosine(vecs.get(0), vecs.get(p + 1)) : 0;
			if ((l1.get(idx).supported() || rel[idx] >= RELATEDNESS_THRESHOLD)
					&& nliTargets.size() < MAX_LLM_CANDIDATES) {
				nliTargets.add(idx);
			}
		}

		List<Layer2Result> nliResults = runNli(researchText,
				nliTargets.stream().map(i -> candidates.get(i).text()).toList());

		// 合成判级（nliResults 与 nliTargets 按位置一一对应）
		List<CandidateVerdict> out = new ArrayList<>();
		for (int i = 0; i < candidates.size(); i++) {
			Layer1Result l1r = l1.get(i);
			QuoteStatus qs = l1r.supported()
					? (l1r.sentenceContained() ? QuoteStatus.QUOTE_SUPPORTED : QuoteStatus.QUOTE_SUPPORTED)
					: (l1r.hitSamples().isEmpty() ? QuoteStatus.QUOTE_NOT_FOUND : QuoteStatus.QUOTE_WEAK);
			ClaimSupportStatus cs;
			Layer2Result l2 = null;
			int t = nliTargets.indexOf(i);
			if (t >= 0 && t < nliResults.size()) {
				l2 = new Layer2Result(rel[i], nliResults.get(t).verdict(), nliResults.get(t).reason());
				cs = switch (l2.verdict()) {
					case SUPPORTED -> ClaimSupportStatus.SUPPORTED;
					case CONTRADICTED -> ClaimSupportStatus.CONTRADICTED;
					default -> ClaimSupportStatus.INSUFFICIENT;
				};
			}
			else {
				cs = ClaimSupportStatus.NOT_EVALUATED;
				l2 = new Layer2Result(rel[i], NliVerdict.INSUFFICIENT,
						rel[i] > 0 ? "相关性低于阈值 " + RELATEDNESS_THRESHOLD : "未进入语义判定");
			}
			// 兼容矩阵 API 的合并判级（旧 status 列）
			String legacy = switch (cs) {
				case SUPPORTED -> "SUPPORTED";
				case CONTRADICTED -> "CONTRADICTED";
				default -> qs == QuoteStatus.QUOTE_SUPPORTED ? "SUPPORTED" : "NEEDS_REVIEW";
			};
			String method = "L1_" + qs.name() + "|L2_" + cs.name();
			out.add(new CandidateVerdict(candidates.get(i), l1r, l2, qs, cs, legacy, method));
		}
		return out;
	}

	private double cosine(double[] a, double[] b) {
		double dot = 0, na = 0, nb = 0;
		for (int i = 0; i < a.length; i++) {
			dot += a[i] * b[i];
			na += a[i] * a[i];
			nb += b[i] * b[i];
		}
		return na == 0 || nb == 0 ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
	}

	/**
	 * 批量 NLI：一次 LLM 调用判定全部候选。任何异常返回空列表（调用方降级为 NEEDS_REVIEW）。
	 */
	private List<Layer2Result> runNli(String claim, List<String> excerpts) {
		if (excerpts.isEmpty()) {
			return List.of();
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < excerpts.size(); i++) {
			String ex = excerpts.get(i);
			if (ex.length() > EXCERPT_MAX_CHARS) {
				ex = ex.substring(0, EXCERPT_MAX_CHARS);
			}
			sb.append("[").append(i).append("] ").append(ex).append("\n\n");
		}
		String prompt = """
				You are verifying whether excerpts from a scientific paper support a research claim.
				The claim may be written in Chinese while the paper is in English — judge the meaning, not the language.

				For each excerpt, output exactly one verdict:
				- SUPPORTED: the excerpt contains information that supports the claim
				- CONTRADICTED: the excerpt supports the opposite of the claim (high similarity but reversed conclusion must be CONTRADICTED)
				- INSUFFICIENT: the excerpt is related but does not establish the claim

				Respond ONLY with a JSON array, no markdown:
				[{"id":0,"verdict":"SUPPORTED","reason":"brief English reason"}, ...]

				Claim:
				"""
				+ claim + "\n\nExcerpts:\n" + sb;
		try {
			String raw = nliClient().prompt().user(prompt).call().content();
			if (raw == null) {
				return List.of();
			}
			String json = raw.trim();
			if (json.startsWith("```")) {
				json = json.replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("```\\s*$", "").trim();
			}
			ArrayNode arr = (ArrayNode) mapper.readTree(json);
			List<Layer2Result> results = new ArrayList<>();
			for (int i = 0; i < arr.size(); i++) {
				ObjectNode item = (ObjectNode) arr.get(i);
				NliVerdict v;
				try {
					v = NliVerdict.valueOf(item.path("verdict").asText("INSUFFICIENT").toUpperCase(Locale.ROOT));
				}
				catch (IllegalArgumentException e) {
					v = NliVerdict.INSUFFICIENT;
				}
				results.add(new Layer2Result(-1, v, item.path("reason").asText("")));
			}
			return results;
		}
		catch (Exception e) {
			logger.warn("NLI 批量判定失败（降级 NEEDS_REVIEW）: {}", e.getMessage());
			return List.of();
		}
	}

	/**
	 * 单条候选的 verification JSON（持久化到 rp_evidence.verification，过程可重放）。
	 */
	public String verificationJson(CandidateVerdict v) {
		try {
			ObjectNode root = mapper.createObjectNode();
			ObjectNode l1 = root.putObject("layer1");
			l1.put("supported", v.layer1().supported());
			l1.put("sentence_contained", v.layer1().sentenceContained());
			ArrayNode hits = l1.putArray("hit_samples");
			v.layer1().hitSamples().forEach(hits::add);
			if (v.layer2() != null) {
				ObjectNode l2 = root.putObject("layer2");
				if (v.layer2().relatedness() >= 0) {
					l2.put("relatedness", Math.round(v.layer2().relatedness() * 1000) / 1000.0);
				}
				l2.put("verdict", v.layer2().verdict().name());
				if (v.layer2().reason() != null && !v.layer2().reason().isBlank()) {
					l2.put("reason", v.layer2().reason());
				}
			}
			root.put("method", v.method());
			root.put("quote_status", v.quoteStatus().name());
			root.put("claim_status", v.claimStatus().name());
			root.put("status", v.legacyStatus());
			return mapper.writeValueAsString(root);
		}
		catch (Exception e) {
			return "{\"error\":\"verification_json_failed\"}";
		}
	}

}
