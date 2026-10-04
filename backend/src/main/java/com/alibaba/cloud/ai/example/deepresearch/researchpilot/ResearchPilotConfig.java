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

package com.alibaba.cloud.ai.example.deepresearch.researchpilot;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.sidecar.SidecarClient;
import org.apache.http.HttpHost;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

/**
 * ResearchPilot Phase B：论文知识库摄取链路配置。
 *
 * <p>
 * 职责边界：业务真值在 PostgreSQL（rp_* 表），检索副本在本专属 ES 索引； 与底座 RagVectorStoreConfiguration 的 ES
 * 客户端相互独立，不受 rag.enabled 开关影响。
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({ ResearchPilotConfig.KbProperties.class,
		ResearchPilotConfig.ResearchBudgetProperties.class })
public class ResearchPilotConfig {

	/**
	 * ResearchPilot Phase E：统一 Research Budget 配置（决策记录 2026-10-02）。
	 *
	 * <p>
	 * 预算维度：全局超时 / 研究循环轮次上限 / 无进展轮次上限。 max_tool_calls 与 max_tokens
	 * 本迭代以循环轮次上限近似（每轮最多并行节点数步），如需精确计量在评测期引入 usage 统计。
	 */
	@ConfigurationProperties(prefix = "researchpilot.research-budget")
	public record ResearchBudgetProperties(long globalTimeoutSeconds, int maxLoopVisits, int noProgressRounds) {

		public ResearchBudgetProperties {
			if (globalTimeoutSeconds <= 0) {
				globalTimeoutSeconds = 360;
			}
			if (maxLoopVisits <= 0) {
				maxLoopVisits = 12;
			}
			if (noProgressRounds <= 0) {
				noProgressRounds = 1;
			}
		}
	}

	/**
	 * 论文知识库摄取配置。
	 */
	@ConfigurationProperties(prefix = "researchpilot.kb")
	public record KbProperties(String objectStorePath, String sidecarBaseUrl, String esIndex, int embedDim,
			int embedBatchSize) {

		public KbProperties {
			if (objectStorePath == null) {
				objectStorePath = "./data/rp-objects";
			}
			if (sidecarBaseUrl == null) {
				sidecarBaseUrl = "http://localhost:8100";
			}
			if (esIndex == null) {
				esIndex = "rp_chunks_v1";
			}
			if (embedDim <= 0) {
				embedDim = 1024;
			}
			if (embedBatchSize <= 0) {
				embedBatchSize = 32;
			}
		}
	}

	/**
	 * 调用 Python sidecar（/parse 与 /v1/embeddings）的 HTTP 客户端。
	 *
	 * <p>
	 * 解析一篇论文在 CPU 上可能耗时 1 分钟以上，超时必须放宽。
	 */
	@Bean
	public SidecarClient sidecarClient(KbProperties props) {
		RestClient restClient = RestClient.builder()
			.baseUrl(props.sidecarBaseUrl())
			.requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {
				{
					setConnectTimeout(10_000);
					setReadTimeout(600_000);
				}
			})
			.build();
		return new SidecarClient(restClient);
	}

	/**
	 * 论文知识库专属 ES 客户端（rp_chunks 索引读写）。
	 */
	@Bean
	public ElasticsearchClient rpElasticsearchClient() {
		String host = System.getProperty("rp.es.host", System.getenv().getOrDefault("RP_ES_HOST", "localhost"));
		var restClient = org.elasticsearch.client.RestClient.builder(new HttpHost(host, 9200, "http")).build();
		return new ElasticsearchClient(
				new RestClientTransport(restClient, new co.elastic.clients.json.jackson.JacksonJsonpMapper(
						new com.fasterxml.jackson.databind.ObjectMapper())));
	}

}
