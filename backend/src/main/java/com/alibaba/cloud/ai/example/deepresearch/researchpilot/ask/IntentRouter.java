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

import java.util.Locale;

/**
 * Fast, deterministic first-pass intent routing for quick answers.
 *
 * <p>
 * Intent routing is a control decision, not a generation task. Calling the LLM for every
 * question made simple questions slower and allowed a malformed classifier response to
 * change the retrieval policy.
 * </p>
 */
public final class IntentRouter {

	private IntentRouter() {
	}

	public static String classify(String question) {
		String value = question == null ? "" : question.trim().toLowerCase(Locale.ROOT);
		if (value.isBlank()) {
			return "material";
		}
		if (value.matches(".*(你好|您好|嗨|hello|hi|谢谢|谢了|在吗|早上好|晚上好).*")) {
			return "chitchat";
		}
		if (value.matches(".*(哪里|在哪|位于|第几页|哪一节|原文|定位|出处|locate|where).*")) {
			return "locate";
		}
		if (value.matches(".*(这篇|该论文|论文中|文中|资料中|来源中|摘要|方法|实验|数据集|贡献|局限|比较|区别|原文|翻译).*")) {
			return "material";
		}
		return "general";
	}

}
