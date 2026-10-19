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

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0-A+：查询翻译前置 —— 中文检测、翻译缓存、失败降级。
 */
class QueryTranslatorTest {

	private ChatModel modelReturning(String content) {
		ChatModel model = mock(ChatModel.class);
		when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(
				List.of(new Generation(new org.springframework.ai.chat.messages.AssistantMessage(content)))));
		return model;
	}

	@Test
	void chineseDetection() {
		assertTrue(QueryTranslator.isChinese("DPR 的负样本策略是什么？"));
		assertTrue(QueryTranslator.isChinese("混合检索"));
		assertFalse(QueryTranslator.isChinese("dense retrieval evaluation"));
		assertFalse(QueryTranslator.isChinese("RocketQA denoising 123"));
	}

	@Test
	void translatesChineseToEnglish() {
		ChatModel model = modelReturning("How does DPR construct hard negatives?");
		QueryTranslator translator = new QueryTranslator(model);
		String en = translator.translate("DPR 的负样本策略是怎么做的？");
		assertEquals("How does DPR construct hard negatives?", en);
	}

	@Test
	void englishQueryBypassesModel() {
		ChatModel model = mock(ChatModel.class);
		QueryTranslator translator = new QueryTranslator(model);
		String q = "dense retrieval evaluation";
		assertSame(q, translator.translate(q)); // 原样返回（同一引用=未调用模型）
		verify(model, times(0)).call(any(Prompt.class));
	}

	@Test
	void secondCallHitsCache() {
		ChatModel model = modelReturning("cached translation");
		AtomicInteger counter = new AtomicInteger();
		when(model.call(any(Prompt.class))).thenAnswer(inv -> {
			counter.incrementAndGet();
			return new ChatResponse(List
				.of(new Generation(new org.springframework.ai.chat.messages.AssistantMessage("cached translation"))));
		});
		QueryTranslator translator = new QueryTranslator(model);
		translator.translate("唯一中文查询abc");
		translator.translate("唯一中文查询abc");
		assertEquals(1, counter.get(), "第二次应命中缓存，不调模型");
	}

	@Test
	void modelFailureFallsBackToOriginal() {
		ChatModel model = mock(ChatModel.class);
		when(model.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM down"));
		QueryTranslator translator = new QueryTranslator(model);
		String q = "中文查询降级测试";
		assertEquals(q, translator.translate(q), "翻译失败应降级返回原查询");
	}

}
