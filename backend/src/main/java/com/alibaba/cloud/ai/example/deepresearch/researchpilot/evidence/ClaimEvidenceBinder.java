/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.alibaba.cloud.ai.example.deepresearch.researchpilot.evidence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Splits a model summary into stable, source-scoped claims. */
public final class ClaimEvidenceBinder {

	private static final Pattern SOURCE = Pattern.compile("\\[KB-(\\d+)]");

	private ClaimEvidenceBinder() {
	}

	public record Claim(String id, int index, String text, List<Integer> sourceIndexes) {
	}

	/**
	 * Keep paragraphs short enough for evidence review and preserve explicit source
	 * markers. A claim without a marker is deliberately left unscoped and may be checked
	 * against all candidates, but is still distinguishable in the store.
	 */
	public static List<Claim> split(String summary) {
		if (summary == null || summary.isBlank()) {
			return List.of();
		}
		List<Claim> claims = new ArrayList<>();
		int index = 0;
		for (String paragraph : summary.replace("\r", "").split("\\n\\s*\\n+")) {
			String clean = paragraph.trim();
			if (clean.isBlank()) {
				continue;
			}
			for (String sentence : clean.split("(?<=[。！？.!?])\\s+")) {
				String text = sentence.trim();
				if (text.isBlank() || text.length() < 3) {
					continue;
				}
				List<Integer> sources = new ArrayList<>();
				Matcher matcher = SOURCE.matcher(text);
				while (matcher.find()) {
					try {
						sources.add(Integer.parseInt(matcher.group(1)) - 1);
					}
					catch (NumberFormatException ignored) {
						// Ignore malformed source markers; the claim remains reviewable.
					}
				}
				String id = "clm_" + sha256(text).substring(0, 16);
				claims.add(new Claim(id, index++, text, sources.stream().distinct().toList()));
			}
		}
		return claims;
	}

	private static String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder out = new StringBuilder();
			for (byte b : digest) {
				out.append(String.format("%02x", b));
			}
			return out.toString();
		}
		catch (Exception e) {
			throw new IllegalStateException("Unable to hash claim", e);
		}
	}

}
