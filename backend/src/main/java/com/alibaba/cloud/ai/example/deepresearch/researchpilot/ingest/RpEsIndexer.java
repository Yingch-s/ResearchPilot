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

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.ResearchPilotConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

/**
 * ResearchPilot Phase B：论文知识库 ES 索引器（检索副本，可随时从真值重建）。
 *
 * <p>
 * 索引 rp_chunks_v1：text（BM25）+ embedding（dense_vector 1024 cosine）双路； topic/paper/version
 * 元数据为 keyword，检索层 Scope 过滤的基础。
 */
@Component
public class RpEsIndexer {

	private static final Logger logger = LoggerFactory.getLogger(RpEsIndexer.class);

	private final ElasticsearchClient es;

	private final ResearchPilotConfig.KbProperties props;

	private final ObjectMapper objectMapper = new ObjectMapper();

	public RpEsIndexer(ElasticsearchClient es, ResearchPilotConfig.KbProperties props) {
		this.es = es;
		this.props = props;
	}

	/**
	 * 幂等建索引（dense_vector 维度来自配置，换 embedding 模型 = 换索引名重建）。
	 */
	public void ensureIndex() throws Exception {
		String index = props.esIndex();
		if (es.indices().exists(i -> i.index(index)).value()) {
			return;
		}
		String mapping = """
				{
				  "properties": {
				    "text":                {"type": "text"},
				    "content_type":        {"type": "keyword"},
				    "topic_id":            {"type": "keyword"},
				    "paper_id":            {"type": "keyword"},
				    "document_version_id": {"type": "keyword"},
				    "parent_id":           {"type": "keyword"},
				    "section_path":        {"type": "keyword"},
				    "page":                {"type": "integer"},
				    "embedding":           {"type": "dense_vector", "dims": %d, "index": true, "similarity": "cosine"}
				  }
				}
				""".formatted(props.embedDim());
		es.indices().create(c -> c.index(index).mappings(m -> m.withJson(new StringReader(mapping))));
		logger.info("RP 索引已创建: {} (dims={})", index, props.embedDim());
	}

	/**
	 * 批量写入 chunk 文档（同版本重复摄取：先按 version 删除旧文档保证幂等）。
	 */
	public void indexChunks(String topicId, String paperId, String documentVersionId, List<ChildDoc> docs)
			throws Exception {
		ensureIndex();
		es.deleteByQuery(d -> d.index(props.esIndex())
			.query(q -> q.term(t -> t.field("document_version_id").value(documentVersionId)))
			.refresh(true));

		var br = new BulkRequest.Builder();
		for (ChildDoc doc : docs) {
			Map<String, Object> docMap = Map.of("text", doc.text(), "content_type", doc.contentType(), "topic_id",
					topicId, "paper_id", paperId, "document_version_id", documentVersionId, "parent_id", doc.parentId(),
					"section_path", doc.sectionPath() == null ? "" : doc.sectionPath(), "page",
					doc.page() == null ? -1 : doc.page(), "embedding", doc.embedding());
			br.operations(op -> op.index(idx -> idx.index(props.esIndex()).id(doc.childId()).document(docMap)));
		}
		BulkResponse response = es.bulk(br.refresh(co.elastic.clients.elasticsearch._types.Refresh.True).build());
		if (response.errors()) {
			for (BulkResponseItem item : response.items()) {
				if (item.error() != null) {
					throw new IllegalStateException("ES bulk 错误: " + item.error().reason());
				}
			}
		}
		logger.info("RP 索引完成: version={} docs={}", documentVersionId, docs.size());
	}

	/**
	 * H4 READY 门禁：校验 ES 内该版本 chunk 数与预期一致（解析产物/Chunk 元数据/索引三者一致才允许 READY）。
	 */
	public long countByVersion(String documentVersionId) throws Exception {
		var response = es.count(c -> c.index(props.esIndex())
			.query(q -> q.term(t -> t.field("document_version_id").value(documentVersionId))));
		return response.count();
	}

	public record ChildDoc(String childId, String parentId, String text, String contentType, Integer page,
			String sectionPath, List<Double> embedding) {
	}

}
