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

import com.alibaba.cloud.ai.toolcalling.tavily.TavilySearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * V1 UX：网络补充搜索（Tavily 封装）。
 *
 * <p>
 * 职责边界：论文库检索不足时的补充来源，**永远不冒充论文证据**——每条结果带 URL， 在证据与回答中明确标注"网络来源"（source_type=WEB）。Tavily
 * 未配置时本服务静默降级为不可用。
 */
@Service
public class RpWebSearchService {

	private static final Logger logger = LoggerFactory.getLogger(RpWebSearchService.class);

	public record WebResult(String title, String url, String content) {
	}

	private final ObjectProvider<TavilySearchService> tavilyProvider;

	public RpWebSearchService(ObjectProvider<TavilySearchService> tavilyProvider) {
		this.tavilyProvider = tavilyProvider;
	}

	public boolean available() {
		TavilySearchService s = tavilyProvider.getIfAvailable();
		return s != null;
	}

	/**
	 * 网络搜索；失败或不可用返回空列表（调用方降级为仅论文来源）。
	 */
	public List<WebResult> search(String query, int maxResults) {
		return searchInternal(query, maxResults, false);
	}

	/**
	 * Required source-policy variant used by deep research. Unlike quick chat's optional
	 * web supplement, a failed required search must become visible to the run and must
	 * never be replaced by local-only evidence.
	 */
	public List<WebResult> searchRequired(String query, int maxResults) {
		return searchInternal(query, maxResults, true);
	}

	private List<WebResult> searchInternal(String query, int maxResults, boolean required) {
		TavilySearchService tavily = tavilyProvider.getIfAvailable();
		if (tavily == null) {
			if (required)
				throw new IllegalStateException("联网搜索服务不可用");
			return List.of();
		}
		try {
			var request = new TavilySearchService.Request(query, "general", "basic", null, maxResults, null, null,
					false, false, false, false, null, null, false);
			var response = tavily.apply(request);
			List<WebResult> out = new ArrayList<>();
			if (response != null && response.getSearchResult() != null
					&& response.getSearchResult().results() != null) {
				response.getSearchResult()
					.results()
					.forEach(r -> out.add(new WebResult(r.title(), r.url(), r.content())));
			}
			return out;
		}
		catch (Exception e) {
			if (required)
				throw new IllegalStateException("联网搜索失败：" + (e.getMessage() == null ? "未知错误" : e.getMessage()), e);
			logger.warn("网络搜索失败（快速问答降级为仅论文来源）: {}", e.getMessage());
			return List.of();
		}
	}

}
