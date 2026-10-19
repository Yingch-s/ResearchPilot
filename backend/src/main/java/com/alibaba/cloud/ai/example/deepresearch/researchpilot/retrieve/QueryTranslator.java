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

package com.alibaba.cloud.ai.example.deepresearch.researchpilot.retrieve;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

/**
 * V3：查询翻译前置 —— 中文研究查询 → 英文检索查询。
 *
 * <p>
 * 背景（zh_en 专项实测）：中文直查英文语料召回保持率仅 ~50%，BM25 通道零词面重叠 只产生噪声。翻译前置把 BM25/Dense 都拉回英文上界水位。
 *
 * <p>
 * 语义：非中文查询原样返回（同一引用，不调模型）；中文查询经 LLM 翻译， 结果缓存（进程内，TTL）；翻译失败降级返回原查询（调用方据此跳过 BM25）。
 */
@org.springframework.stereotype.Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
		name = "researchpilot.retrieve.query-translation", havingValue = "true")
public class QueryTranslator {

	private static final Pattern CJK = Pattern.compile("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]");

	private static final long CACHE_TTL_MS = Duration.ofMinutes(30).toMillis();

	private final ChatClient chatClient;

	private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

	public QueryTranslator(ChatModel chatModel) {
		this.chatClient = ChatClient.builder(chatModel).build();
	}

	/** 是否包含 CJK 字符（粗粒度中文判定）。 */
	public static boolean isChinese(String query) {
		return query != null && CJK.matcher(query).find();
	}

	/**
	 * 中文查询 → 英文检索查询。非中文/翻译失败返回原引用（调用方用引用相等判断是否降级）。
	 */
	public String translate(String query) {
		if (!isChinese(query)) {
			return query;
		}
		var cached = cache.get(query);
		if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
			return cached.translation;
		}
		if (cached != null) {
			cache.remove(query, cached);
		}
		String translation = doTranslate(query);
		cache.put(query, new CacheEntry(translation, Instant.now().plusMillis(CACHE_TTL_MS)));
		return translation;
	}

	private String doTranslate(String query) {
		try {
			String text = chatClient.prompt().user("将下面的中文学术检索查询翻译成英文检索查询。只输出英文翻译，不要任何解释：\n" + query).call().content();
			if (text == null || text.isBlank()) {
				return query;
			}
			return text.trim();
		}
		catch (Exception e) {
			// 翻译失败降级：返回原查询（调用方用引用相等判断降级并跳过 BM25）
			return query;
		}
	}

	private record CacheEntry(String translation, Instant expiresAt) {
	}

}
