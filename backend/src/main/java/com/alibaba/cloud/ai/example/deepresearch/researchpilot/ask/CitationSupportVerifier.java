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

package com.alibaba.cloud.ai.example.deepresearch.researchpilot.ask;

import com.alibaba.cloud.ai.example.deepresearch.researchpilot.sidecar.SidecarClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Advisory claim-to-source check for answers returned by the quick-ask path.
 *
 * <p>
 * Embedding relatedness is used as a cross-language pre-filter. It is not a truth
 * decision, so a low score only produces a review signal. The deep research path keeps
 * its existing NLI verifier for stronger claim decisions.
 * </p>
 */
@Service
public class CitationSupportVerifier {

	private static final Logger logger = LoggerFactory.getLogger(CitationSupportVerifier.class);

	private static final Pattern CITATION = Pattern.compile("(?:\\[(\\d+)\\]|【(\\d+)】)");

	private static final double REVIEW_THRESHOLD = 0.45;

	private static final int MAX_CLAIMS = 8;

	private static final int MAX_PAIRS = 16;

	private static final int MAX_TEXT_CHARS = 1200;

	private static final long EMBED_TIMEOUT_MS = 2500;

	private static final long FAILURE_COOLDOWN_MS = 30_000;

	private final SidecarClient sidecar;

	private final ExecutorService embedPool = Executors.newSingleThreadExecutor(thread -> {
		Thread worker = new Thread(thread, "rp-citation-support");
		worker.setDaemon(true);
		return worker;
	});

	private final AtomicBoolean embedInFlight = new AtomicBoolean();

	private volatile long disabledUntil;

	public CitationSupportVerifier(SidecarClient sidecar) {
		this.sidecar = sidecar;
	}

	public record Result(boolean evaluated, int claimCount, Map<Integer, Double> scores, Set<Integer> review,
			String method) {

		public Map<String, Object> asMap() {
			Map<String, Object> out = new LinkedHashMap<>();
			out.put("evaluated", evaluated);
			out.put("claim_count", claimCount);
			out.put("scores", scores);
			out.put("review", review);
			out.put("method", method);
			out.put("threshold", REVIEW_THRESHOLD);
			return out;
		}
	}

	/**
	 * Accept either the typed citation records from the controller or sanitized map
	 * values used by the streaming envelope.
	 */
	public Result verify(String answer, List<?> citations) {
		Map<Integer, String> sourceTexts = sourceTexts(citations);
		List<ClaimPair> pairs = claimPairs(answer, sourceTexts);
		if (pairs.isEmpty()) {
			return new Result(false, 0, Map.of(), Set.of(), "embedding_relatedness");
		}
		List<String> inputs = new ArrayList<>();
		for (ClaimPair pair : pairs) {
			inputs.add(limit(pair.claim()));
			inputs.add(limit(pair.source()));
		}
		try {
			List<double[]> vectors = embedWithTimeout(inputs);
			Map<Integer, Double> scores = new LinkedHashMap<>();
			Set<Integer> review = new LinkedHashSet<>();
			for (int i = 0; i < pairs.size() && i * 2 + 1 < vectors.size(); i++) {
				double score = cosine(vectors.get(i * 2), vectors.get(i * 2 + 1));
				ClaimPair pair = pairs.get(i);
				scores.merge(pair.number(), score, Math::max);
			}
			for (Map.Entry<Integer, Double> entry : scores.entrySet()) {
				if (entry.getValue() < REVIEW_THRESHOLD) {
					review.add(entry.getKey());
				}
			}
			return new Result(true, new LinkedHashSet<>(pairs.stream().map(ClaimPair::claim).toList()).size(), scores,
					review, "embedding_relatedness");
		}
		catch (Exception e) {
			logger.debug("引用语义相关性校验不可用，保留确定性引用结果: {}", e.getMessage());
			return new Result(false, pairs.stream().map(ClaimPair::claim).distinct().toList().size(), Map.of(),
					Set.of(), "embedding_relatedness");
		}
	}

	private List<double[]> embedWithTimeout(List<String> inputs) throws Exception {
		if (System.currentTimeMillis() < disabledUntil) {
			throw new IllegalStateException("embedding sidecar cooldown");
		}
		if (!embedInFlight.compareAndSet(false, true)) {
			throw new IllegalStateException("embedding sidecar busy");
		}
		var future = embedPool.submit(() -> {
			try {
				return sidecar.embed(inputs);
			}
			finally {
				embedInFlight.set(false);
			}
		});
		try {
			return future.get(EMBED_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		}
		catch (Exception e) {
			future.cancel(true);
			disabledUntil = System.currentTimeMillis() + FAILURE_COOLDOWN_MS;
			throw e;
		}
	}

	private Map<Integer, String> sourceTexts(List<?> citations) {
		Map<Integer, String> texts = new LinkedHashMap<>();
		if (citations == null) {
			return texts;
		}
		for (int i = 0; i < citations.size(); i++) {
			Object value = citations.get(i);
			int number = i + 1;
			String text = null;
			if (value instanceof RpAskService.Citation citation) {
				number = citation.n();
				text = citation.text();
			}
			else if (value instanceof Map<?, ?> map) {
				Object rawNumber = map.containsKey("n") ? map.get("n") : map.get("number");
				if (rawNumber != null) {
					try {
						number = Integer.parseInt(String.valueOf(rawNumber));
					}
					catch (NumberFormatException ignored) {
						// Fall back to the stable list position.
					}
				}
				Object rawText = map.containsKey("text") ? map.get("text") : map.get("content");
				text = rawText == null ? null : String.valueOf(rawText);
			}
			if (number > 0 && text != null && !text.isBlank()) {
				texts.put(number, text);
			}
		}
		return texts;
	}

	private List<ClaimPair> claimPairs(String answer, Map<Integer, String> sourceTexts) {
		List<ClaimPair> pairs = new ArrayList<>();
		if (answer == null || answer.isBlank()) {
			return pairs;
		}
		String[] claims = answer.split("(?<=[。！？!?；;\\n])");
		if (claims.length == 0) {
			claims = new String[] { answer };
		}
		Set<String> seenClaims = new LinkedHashSet<>();
		for (String claim : claims) {
			if (seenClaims.size() >= MAX_CLAIMS) {
				break;
			}
			Matcher matcher = CITATION.matcher(claim);
			Set<Integer> references = new LinkedHashSet<>();
			while (matcher.find()) {
				int number = Integer.parseInt(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
				if (sourceTexts.containsKey(number)) {
					references.add(number);
				}
			}
			String normalized = claim.replaceAll("(?:\\[(\\d+)\\]|【(\\d+)】)", "").trim();
			if (references.isEmpty() || normalized.isBlank() || !seenClaims.add(normalized)) {
				continue;
			}
			for (Integer number : references) {
				if (pairs.size() >= MAX_PAIRS) {
					return pairs;
				}
				pairs.add(new ClaimPair(number, normalized, sourceTexts.get(number)));
			}
		}
		return pairs;
	}

	private String limit(String value) {
		return value.length() <= MAX_TEXT_CHARS ? value : value.substring(0, MAX_TEXT_CHARS);
	}

	private double cosine(double[] first, double[] second) {
		if (first == null || second == null || first.length != second.length || first.length == 0) {
			return 0;
		}
		double dot = 0;
		double firstNorm = 0;
		double secondNorm = 0;
		for (int i = 0; i < first.length; i++) {
			dot += first[i] * second[i];
			firstNorm += first[i] * first[i];
			secondNorm += second[i] * second[i];
		}
		return firstNorm == 0 || secondNorm == 0 ? 0 : dot / (Math.sqrt(firstNorm) * Math.sqrt(secondNorm));
	}

	private record ClaimPair(int number, String claim, String source) {
	}

}
