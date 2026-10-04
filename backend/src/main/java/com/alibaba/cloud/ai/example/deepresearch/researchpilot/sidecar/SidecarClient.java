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

package com.alibaba.cloud.ai.example.deepresearch.researchpilot.sidecar;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * ResearchPilot Phase B：Python sidecar 客户端（Docling 解析 + bge-m3 embedding）。
 *
 * <p>
 * 契约见 sidecar/main.py —— 两个能力一个进程，OpenAI 兼容 embedding 协议。
 */
public class SidecarClient {

	private final RestClient restClient;

	public SidecarClient(RestClient restClient) {
		this.restClient = restClient;
	}

	public boolean healthy() {
		try {
			JsonNode result = restClient.get().uri("/health").retrieve().body(JsonNode.class);
			return result != null && (result.path("status").asText("ok").equalsIgnoreCase("ok")
					|| result.path("status").asText().equalsIgnoreCase("healthy"));
		}
		catch (Exception e) {
			return false;
		}
	}

	/**
	 * 解析 PDF，返回 NormalizedDocument JSON（blocks/quality_flags/parser_version）。
	 */
	public JsonNode parse(byte[] pdfBytes, String documentVersionId, String sourceObjectKey) {
		var parts = new LinkedMultiValueMap<String, Object>();
		parts.add("file", new ByteArrayResource(pdfBytes) {
			@Override
			public String getFilename() {
				return documentVersionId + ".pdf";
			}
		});
		JsonNode result = restClient.post()
			.uri(uri -> uri.path("/parse")
				.queryParam("document_version_id", documentVersionId)
				.queryParam("source_object_key", sourceObjectKey)
				.build())
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.body(parts)
			.retrieve()
			.body(JsonNode.class);
		if (result == null || !result.has("blocks")) {
			throw new IllegalStateException("sidecar /parse 返回异常");
		}
		return result;
	}

	/**
	 * 批量 embedding（OpenAI 兼容协议），返回与输入等长的向量列表。
	 */
	public List<double[]> embed(List<String> texts) {
		JsonNode result = restClient.post()
			.uri("/v1/embeddings")
			.contentType(MediaType.APPLICATION_JSON)
			.body(new EmbeddingRequest(texts))
			.retrieve()
			.body(JsonNode.class);
		if (result == null || !result.has("data")) {
			throw new IllegalStateException("sidecar /v1/embeddings 返回异常");
		}
		List<double[]> vecs = new ArrayList<>();
		result.get("data").forEach(d -> {
			var arr = d.get("embedding");
			double[] v = new double[arr.size()];
			for (int i = 0; i < arr.size(); i++) {
				v[i] = arr.get(i).asDouble();
			}
			vecs.add(v);
		});
		return vecs;
	}

	public String embeddingIdentity() {
		try {
			JsonNode identity = restClient.get().uri("/embedding-identity").retrieve().body(JsonNode.class);
			if (identity == null || identity.path("revision").asText().isBlank()) {
				return null;
			}
			return identity.toString();
		}
		catch (Exception e) {
			return null;
		}
	}

	public record EmbeddingRequest(List<String> input, String model) {

		public EmbeddingRequest(List<String> input) {
			this(input, "BAAI/bge-m3");
		}
	}

}
