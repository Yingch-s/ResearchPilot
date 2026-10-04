package com.alibaba.cloud.ai.example.deepresearch.researchpilot.ask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic context compaction policy. Budget counting is deliberately isolated so a
 * model tokenizer can replace the UTF-8 fallback later.
 */
public final class ContextCompactor {

	private ContextCompactor() {
	}

	public record Segment(String id, String kind, String text, int priority) {
		public Segment {
			id = id == null ? "segment" : id;
			kind = kind == null ? "history" : kind;
			text = text == null ? "" : text;
		}
	}

	public record Result(String text, List<String> includedIds, Set<String> missingAnchors, int bytes,
			boolean withinBudget) {
		public boolean preservesRequiredContext() {
			return missingAnchors.isEmpty();
		}
	}

	public static Result compact(List<Segment> segments, Set<String> requiredAnchors, int maxBytes) {
		return compact(segments, requiredAnchors, maxBytes, ContextTokenCounter.utf8Bytes());
	}

	public static Result compact(List<Segment> segments, Set<String> requiredAnchors, int maxBytes,
			ContextTokenCounter counter) {
		ContextTokenCounter budget = counter == null ? ContextTokenCounter.utf8Bytes() : counter;
		List<Segment> candidates = segments == null ? List.of()
				: segments.stream().filter(s -> s != null && !s.text().isBlank()).toList();
		Set<String> anchors = requiredAnchors == null ? Set.of() : new LinkedHashSet<>(requiredAnchors);
		List<Segment> selected = new ArrayList<>();
		Set<String> selectedIds = new LinkedHashSet<>();
		// Required anchors are selected first, regardless of their normal priority.
		for (Segment segment : candidates) {
			if (anchors.stream()
				.anyMatch(anchor -> anchor != null && !anchor.isBlank() && segment.text().contains(anchor))) {
				selected.add(segment);
				selectedIds.add(segment.id());
			}
		}
		List<Segment> optional = candidates.stream()
			.filter(s -> !selectedIds.contains(s.id()))
			.sorted(Comparator.comparingInt(Segment::priority).reversed())
			.toList();
		int bytes = size(selected, budget);
		for (Segment segment : optional) {
			int next = bytes + size(segment, budget);
			if (maxBytes >= 0 && next > maxBytes)
				continue;
			selected.add(segment);
			selectedIds.add(segment.id());
			bytes = next;
		}
		String text = selected.stream().map(Segment::text).reduce((a, b) -> a + "\n\n" + b).orElse("");
		Set<String> missing = new LinkedHashSet<>();
		for (String anchor : anchors) {
			if (anchor != null && !anchor.isBlank() && !text.contains(anchor))
				missing.add(anchor);
		}
		return new Result(text, List.copyOf(selectedIds), Set.copyOf(missing), budget.count(text),
				maxBytes < 0 || budget.count(text) <= maxBytes);
	}

	private static int size(List<Segment> segments, ContextTokenCounter counter) {
		return counter.count(segments.stream().map(Segment::text).reduce((a, b) -> a + "\n\n" + b).orElse(""));
	}

	private static int size(Segment segment, ContextTokenCounter counter) {
		return counter.count(segment.text()) + 2;
	}

}
