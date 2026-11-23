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

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic post-processing for quick answers.
 *
 * <p>
 * The model is allowed to write citation markers, but it is not allowed to invent the
 * citation namespace. This class keeps that boundary outside the prompt so malformed
 * markers cannot become misleading UI links.
 * </p>
 */
public final class AnswerQualityGate {

	private static final Pattern CITATION = Pattern.compile("(?:\\[(\\d+)\\]|【(\\d+)】)");

	private AnswerQualityGate() {
	}

	public record Result(String answer, Set<Integer> referenced, Set<Integer> invalid) {

		public boolean verified() {
			return invalid.isEmpty();
		}

		public boolean hasReferences() {
			return !referenced.isEmpty();
		}
	}

	/**
	 * Normalize full-width citation markers and remove references that do not exist in
	 * the source pack. The answer text remains readable so the user can still inspect a
	 * partial result.
	 */
	public static Result validate(String answer, int sourceCount) {
		String value = answer == null ? "" : answer.trim();
		Set<Integer> referenced = new LinkedHashSet<>();
		Set<Integer> invalid = new LinkedHashSet<>();
		Matcher matcher = CITATION.matcher(value);
		StringBuffer normalized = new StringBuffer();
		while (matcher.find()) {
			String raw = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
			int number = Integer.parseInt(raw);
			if (number > 0 && number <= sourceCount) {
				referenced.add(number);
				matcher.appendReplacement(normalized, Matcher.quoteReplacement("[" + number + "]"));
			}
			else {
				invalid.add(number);
				matcher.appendReplacement(normalized, "");
			}
		}
		matcher.appendTail(normalized);
		String sanitized = normalized.toString().replaceAll("[ \\t]{2,}", " ").trim();
		if (!invalid.isEmpty()) {
			sanitized += "\n\n部分表述未能在当前来源中定位，建议打开引用来源核对。";
		}
		return new Result(sanitized, Set.copyOf(referenced), Set.copyOf(invalid));
	}

}
