package com.alibaba.cloud.ai.example.deepresearch.researchpilot.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Deterministically merges bounded worker packets without hiding conflicts. */
public final class EvidencePacketMerger {

	private EvidencePacketMerger() {
	}

	public static MergeResult merge(List<EvidencePacket> packets) {
		Map<String, Claim> unique = new LinkedHashMap<>();
		Set<String> conflicts = new LinkedHashSet<>();
		Set<String> gaps = new LinkedHashSet<>();
		List<String> failedWorkers = new ArrayList<>();
		for (EvidencePacket packet : packets == null ? List.<EvidencePacket>of() : packets) {
			if (packet == null || packet.failed()) {
				if (packet != null && packet.workerId() != null) {
					failedWorkers.add(packet.workerId());
				}
				continue;
			}
			gaps.addAll(packet.gaps());
			for (Claim claim : packet.claims()) {
				String key = normalize(claim.claim()) + "|" + String.join(",", claim.sourceIds());
				Claim previous = unique.putIfAbsent(key, claim);
				if (previous != null && !previous.support().equalsIgnoreCase(claim.support())) {
					conflicts.add(claim.claim());
				}
			}
		}
		return new MergeResult(List.copyOf(unique.values()), List.copyOf(conflicts), List.copyOf(gaps),
				List.copyOf(failedWorkers));
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	public record Claim(String claim, List<String> sourceIds, String support) {

		public Claim {
			if (claim == null || claim.isBlank() || support == null || support.isBlank()) {
				throw new IllegalArgumentException("claim 和 support 不能为空");
			}
			sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
		}
	}

	public record EvidencePacket(String workerId, List<Claim> claims, List<String> gaps, boolean failed) {

		public EvidencePacket {
			if (workerId == null || workerId.isBlank()) {
				throw new IllegalArgumentException("workerId 不能为空");
			}
			claims = claims == null ? List.of() : List.copyOf(claims);
			gaps = gaps == null ? List.of() : List.copyOf(gaps);
		}
	}

	public record MergeResult(List<Claim> claims, List<String> conflicts, List<String> gaps,
			List<String> failedWorkers) {
	}

}
