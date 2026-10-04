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

package com.alibaba.cloud.ai.example.deepresearch.researchpilot.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ResearchPilot Phase B：结构感知切分器（Parent-Child）。
 *
 * <p>
 * Parent = 章节（保留完整上下文，供 Parent Expansion）；Child = 段落/表格片段（检索匹配单元）。
 * 切分参数（长度阈值等）先取保守默认值，Phase E 用标注集调参 —— 不预设"最佳值"。
 */
@Component
public class RpChunker {

	private static final int MAX_CHILD_CHARS = 1800;

	private static final int MERGE_THRESHOLD = 300;

	public record ParentChild(List<Parent> parents, List<Child> children) {

		public record Parent(String parentId, String sectionPath, String text) {
		}

		public record Child(String childId, String parentId, String text, String contentType, Integer page,
				String sectionPath) {
		}
	}

	/**
	 * 从 NormalizedDocument JSON 构建 Parent/Child 两级切分。
	 */
	public ParentChild chunk(JsonNode normalized, String topicId, String paperId, String documentVersionId) {
		// Parent：按 section_path 顶层段聚合（LinkedHashMap 保持阅读顺序）
		Map<String, StringBuilder> parents = new LinkedHashMap<>();
		List<JsonNode> blocks = new ArrayList<>();
		normalized.get("blocks").forEach(blocks::add);

		for (JsonNode b : blocks) {
			String section = b.path("section_path").asText("PREAMBLE");
			String top = section.split(" > ")[0];
			parents.computeIfAbsent(top, k -> new StringBuilder()).append(b.path("text").asText("")).append("\n");
		}
		Map<String, String> parentIds = new LinkedHashMap<>();
		parents.forEach((section, text) -> parentIds.put(section, "P-" + sha12(documentVersionId + ":" + section)));

		// Child：相邻短段合并至 MERGE_THRESHOLD，长段按 MAX_CHILD_CHARS 切分
		List<ParentChild.Child> children = new ArrayList<>();
		StringBuilder pending = new StringBuilder();
		Integer pendingPage = null;
		String pendingSection = null;

		for (JsonNode b : blocks) {
			String type = b.path("content_type").asText("other");
			if (type.equals("heading") || type.equals("picture")) {
				continue;
			}
			String section = b.path("section_path").asText("PREAMBLE");
			String top = section.split(" > ")[0];
			String text = switch (type) {
				case "table" -> {
					String t = b.path("text").asText("");
					int r = b.path("n_rows").asInt(0);
					int c = b.path("n_cols").asInt(0);
					yield "[表格 %d行x%d列]\n%s".formatted(r, c, t);
				}
				default -> b.path("text").asText("");
			};
			if (text.isBlank()) {
				continue;
			}
			if (text.length() > MAX_CHILD_CHARS) {
				for (String part : splitLong(text, MAX_CHILD_CHARS)) {
					children.add(new ParentChild.Child("C-" + sha12(documentVersionId + ":" + children.size()),
							parentIds.get(top), part, type, pageOf(b), top));
				}
				continue;
			}
			// 换章节则先落盘累积段
			if (pending.length() > 0 && !top.equals(pendingSection)) {
				children.add(new ParentChild.Child("C-" + sha12(documentVersionId + ":" + children.size()),
						parentIds.get(pendingSection), pending.toString(), "text", pendingPage, pendingSection));
				pending.setLength(0);
				pendingPage = null;
			}
			if (pending.length() + text.length() <= MERGE_THRESHOLD) {
				pending.append(text).append("\n");
				if (pendingPage == null) {
					pendingPage = pageOf(b);
				}
				pendingSection = top;
			}
			else {
				if (pending.length() > 0) {
					children.add(new ParentChild.Child("C-" + sha12(documentVersionId + ":" + children.size()),
							parentIds.get(pendingSection), pending.toString(), "text", pendingPage, pendingSection));
					pending.setLength(0);
					pendingPage = null;
				}
				pending.append(text).append("\n");
				pendingPage = pageOf(b);
				pendingSection = top;
			}
		}
		if (pending.length() > 0 && pendingSection != null) {
			children.add(new ParentChild.Child("C-" + sha12(documentVersionId + ":" + children.size()),
					parentIds.get(pendingSection), pending.toString(), "text", pendingPage, pendingSection));
		}

		List<ParentChild.Parent> parentList = parents.entrySet()
			.stream()
			.map(e -> new ParentChild.Parent(parentIds.get(e.getKey()), e.getKey(), e.getValue().toString()))
			.toList();
		return new ParentChild(parentList, children);
	}

	private Integer pageOf(JsonNode b) {
		return b.has("page") && !b.get("page").isNull() ? b.get("page").asInt() : null;
	}

	private List<String> splitLong(String text, int max) {
		List<String> parts = new ArrayList<>();
		for (int i = 0; i < text.length(); i += max) {
			parts.add(text.substring(i, Math.min(text.length(), i + max)));
		}
		return parts;
	}

	private String sha12(String s) {
		try {
			var md = java.security.MessageDigest.getInstance("SHA-256");
			var bytes = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			var sb = new StringBuilder();
			for (int i = 0; i < 6; i++) {
				sb.append(String.format("%02x", bytes[i]));
			}
			return sb.toString();
		}
		catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
