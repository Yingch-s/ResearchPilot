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

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.ResearchPilotConfig;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.sidecar.SidecarClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ResearchPilot Phase B2：论文库混合检索服务。
 *
 * <p>
 * 设计约束（《RAG 系统设计 V0.1》§5）： 1. Scope 过滤在 ES 查询层强制执行（服务端 WHERE），不经 LLM； 2. BM25 与 Dense
 * 双路召回 + Java 端 RRF 融合（k=60）—— 不依赖 ES RRF retriever 的许可证； 3. Parent Expansion 受预算约束，将命中
 * chunk 的章节级上下文带回； 4. 候选（Candidate）不是证据（Evidence）—— 语义支持判定属后续 Evidence Agent 职责。
 */
@Service
public class RpRetrievalService {

	private static final Logger logger = LoggerFactory.getLogger(RpRetrievalService.class);

	private static final int RRF_K = 60;

	private final ElasticsearchClient es;

	private final SidecarClient sidecar;

	@org.springframework.beans.factory.annotation.Autowired(required = false)
	private QueryTranslator queryTranslator;

	private final ResearchPilotConfig.KbProperties props;

	private final ConcurrentHashMap<String, CacheEntry> retrievalCache = new ConcurrentHashMap<>();

	private static final long CACHE_TTL_SECONDS = 60;

	private static final int MAX_CACHE_ENTRIES = 128;

	@org.springframework.beans.factory.annotation.Autowired
	private org.springframework.jdbc.core.JdbcTemplate jdbc;

	public RpRetrievalService(ElasticsearchClient es, SidecarClient sidecar, ResearchPilotConfig.KbProperties props) {
		this.es = es;
		this.sidecar = sidecar;
		this.props = props;
	}

	public record Candidate(String chunkId, String paperId, String parentId, String text, String sectionPath,
			Integer page, String contentType, double fusedScore, List<String> channels) {
	}

	/**
	 * Lightweight dependency probe used by the settings page; no search or model call.
	 */
	public boolean healthy() {
		try {
			return es.info().version() != null;
		}
		catch (Exception e) {
			return false;
		}
	}

	/**
	 * 混合检索：Scope 过滤 -> 双路召回 -> RRF 融合 -> 可选 Parent Expansion。
	 * @param paperIds null 表示专题内全部文献（仍受 topic_id 过滤）
	 */
	public List<Candidate> hybridRetrieve(String topicId, List<String> paperIds, String query, int topK,
			boolean withParentExpansion) {
		return hybridRetrieve(topicId, paperIds, query, topK, withParentExpansion, null);
	}

	/**
	 * channels：检索通道选择（D1 评测对照用）——null 或含 "bm25"/"dense" 决定启用哪些通道。 null 等价于双通道全开。
	 */
	public List<Candidate> hybridRetrieve(String topicId, List<String> paperIds, String query, int topK,
			boolean withParentExpansion, List<String> channels) {
		String identity = sidecar.embeddingIdentity();
		if (identity != null) {
			String cacheKey = cacheKey(topicId, paperIds, query, topK, withParentExpansion, identity,
					channels == null ? "both" : String.join("+", channels));
			CacheEntry cached = retrievalCache.get(cacheKey);
			if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
				return cached.candidates();
			}
			if (cached != null) {
				retrievalCache.remove(cacheKey, cached);
			}
			List<Candidate> result = hybridRetrieveUncached(topicId, paperIds, query, topK, withParentExpansion,
					channels);
			if (retrievalCache.size() >= MAX_CACHE_ENTRIES) {
				retrievalCache.keySet().stream().findFirst().ifPresent(retrievalCache::remove);
			}
			retrievalCache.put(cacheKey,
					new CacheEntry(List.copyOf(result), Instant.now().plusSeconds(CACHE_TTL_SECONDS)));
			return result;
		}
		return hybridRetrieveUncached(topicId, paperIds, query, topK, withParentExpansion, channels);
	}

	private List<Candidate> hybridRetrieveUncached(String topicId, List<String> paperIds, String query, int topK,
			boolean withParentExpansion, List<String> channels) {
		// V3：查询翻译前置 —— 中文查询翻译为英文后走两通道；翻译失败（降级）跳过 BM25
		// （中文词面与英文语料零重叠，BM25 只产生噪声，zh_en 专项实测 R@10 保持率 47%）
		String effectiveQuery = query;
		boolean chineseQuery = queryTranslator != null && QueryTranslator.isChinese(query);
		if (chineseQuery) {
			effectiveQuery = queryTranslator.translate(query);
		}
		boolean useBm25 = channels == null || channels.contains("bm25");
		boolean useDense = channels == null || channels.contains("dense");
		if (chineseQuery) {
			useBm25 = useBm25 && !effectiveQuery.equals(query); // 翻译失败 → 跳过 BM25
		}
		int perChannel = Math.max(topK, 20);
		List<double[]> queryVec = useDense ? sidecar.embed(List.of(effectiveQuery)) : List.of();
		List<String> channelA = useBm25 ? bm25Search(topicId, paperIds, effectiveQuery, perChannel) : List.of();
		List<String> channelB = useDense
				? denseSearch(topicId, paperIds, queryVec.isEmpty() ? new double[0] : queryVec.get(0), perChannel)
				: List.of();

		// RRF 融合：score = Σ 1/(k + rank)
		Map<String, Double> fused = new LinkedHashMap<>();
		Map<String, List<String>> channelTags = new LinkedHashMap<>();
		accumulate(channelA, "bm25", fused, channelTags);
		accumulate(channelB, "dense", fused, channelTags);
		if (requestsAbstract(query)) {
			// Section lookup must not depend on semantic rank for an explicit request
			// to read/translate the abstract. The same scope filters still apply.
			for (String id : abstractSearch(topicId, paperIds)) {
				fused.merge(id, 1.0, Double::sum);
				channelTags.computeIfAbsent(id, k -> new ArrayList<>()).add("abstract-section");
			}
		}

		// 留出重排空间：RRF 只表示通道一致性，不能让参考文献列表压过摘要/引言。
		List<String> topIds = fused.entrySet()
			.stream()
			.sorted(Map.Entry.<String, Double>comparingByValue().reversed())
			// 跨语言问题的 dense rank 可能把摘要排到较后；扩大候选池后再按章节语义重排。
			.limit(Math.max(topK * 10, 60))
			.map(Map.Entry::getKey)
			.toList();
		if (topIds.isEmpty()) {
			return List.of();
		}
		Map<String, Map<String, Object>> docs = fetchByIds(topIds);

		List<Candidate> candidates = new ArrayList<>();
		for (String id : topIds) {
			Map<String, Object> src = docs.get(id);
			if (src == null) {
				continue;
			}
			String section = (String) src.get("section_path");
			// 参考文献条目默认不是回答论文贡献/方法问题的证据，避免污染引用卡片。
			if (isReferenceSection(section)) {
				continue;
			}
			candidates.add(new Candidate(id, (String) src.get("paper_id"), (String) src.get("parent_id"),
					(String) src.get("text"), (String) src.get("section_path"), (Integer) src.get("page"),
					(String) src.get("content_type"), fused.get(id), channelTags.get(id)));
		}
		candidates
			.sort(java.util.Comparator.<Candidate>comparingDouble(c -> c.fusedScore() + sectionBoost(c.sectionPath()))
				.reversed());
		if (candidates.size() > topK) {
			candidates = new ArrayList<>(candidates.subList(0, topK));
		}
		logger.info("RP 检索: bm25={} dense={} fused_top={} query='{}'", channelA.size(), channelB.size(),
				candidates.size(), query);
		return candidates;
	}

	private static boolean isReferenceSection(String section) {
		if (section == null) {
			return false;
		}
		String normalized = section.trim().toLowerCase(java.util.Locale.ROOT);
		return normalized.equals("references") || normalized.equals("bibliography")
				|| normalized.startsWith("references/") || normalized.startsWith("bibliography/");
	}

	private static double sectionBoost(String section) {
		if (section == null) {
			return 0;
		}
		String normalized = section.toLowerCase(java.util.Locale.ROOT);
		if (normalized.contains("abstract") || normalized.contains("contribution")
				|| normalized.contains("introduction")) {
			return 0.006;
		}
		if (normalized.contains("conclusion") || normalized.contains("method")) {
			return 0.003;
		}
		return 0;
	}

	static String cacheKey(String topicId, List<String> paperIds, String query, int topK, boolean withParentExpansion,
			String embeddingIdentity, String channelsKey) {
		List<String> scope = paperIds == null ? List.of() : paperIds.stream().sorted().toList();
		return String.join("|", String.valueOf(topicId), String.join(",", scope), normalizeQuery(query),
				String.valueOf(topK), String.valueOf(withParentExpansion), embeddingIdentity);
	}

	private static String normalizeQuery(String query) {
		return query == null ? "" : query.trim().replaceAll("\\s+", " ");
	}

	private record CacheEntry(List<Candidate> candidates, Instant expiresAt) {
	}

	/**
	 * Parent Expansion：取命中 chunk 所属章节的完整上下文（受预算约束的调用方负责截断）。
	 */
	public Map<String, String> expandParents(List<String> parentIds) throws Exception {
		return expandParents(parentIds, null, null);
	}

	public Map<String, String> expandParents(List<String> parentIds, String topicId, List<String> paperIds)
			throws Exception {
		List<String> ids = parentIds.stream().filter(java.util.Objects::nonNull).distinct().limit(20).toList();
		if (ids.isEmpty() || jdbc == null) {
			return Map.of();
		}
		Map<String, String> result = new LinkedHashMap<>();
		for (String id : ids) {
			var rows = jdbc.queryForList(
					"SELECT left(sc.content, 12000) AS content, p.id::text AS paper_id FROM rp_section_contexts sc JOIN rp_document_versions dv ON dv.id = sc.document_version_id JOIN rp_papers p ON p.id = dv.paper_id WHERE sc.parent_id = ? AND p.status = 'ACTIVE' AND dv.parse_status = 'READY' AND (?::uuid IS NULL OR p.topic_id = ?::uuid)",
					id, topicId, topicId);
			if (!rows.isEmpty() && paperIds != null && !paperIds.isEmpty()
					&& !paperIds.contains(rows.get(0).get("paper_id")))
				continue;
			if (!rows.isEmpty())
				result.put(id, String.valueOf(rows.get(0).get("content")));
		}
		return result;
	}

	private List<String> bm25Search(String topicId, List<String> paperIds, String query, int size) {
		try {
			String expandedQuery = lexicalExpansion(query);
			// ResearchPilot V0.4 H2.2 修复（隔离测试发现）：旧实现 searchWithScope(...).query(...)
			// 链式调用了两次 .query() —— builder 是 setter 语义，第二次调用整体替换了带 scope
			// filter 的 bool，导致 BM25 路从未真正按专题过滤。必须在同一个 bool 内合并 must 与 filter。
			var response = es
				.search(s -> s.index(props.esIndex()).size(props.embedBatchSize() * 2).query(q -> q.bool(b -> {
					b.must(m -> m.match(mt -> mt.field("text").query(expandedQuery)));
					b.filter(scopeFilters(topicId, paperIds));
					return b;
				})), Map.class);
			return hitIds(response);
		}
		catch (Exception e) {
			logger.warn("BM25 检索失败", e);
			return List.of();
		}
	}

	/**
	 * The local lexical index is English-oriented while users commonly ask in Chinese.
	 * Keep the original query and add a small intent vocabulary so BM25 remains useful
	 * when the embedding model is not multilingual.
	 */
	private static String lexicalExpansion(String query) {
		String q = query == null ? "" : query;
		StringBuilder expanded = new StringBuilder(q);
		if (requestsAbstract(q)) {
			expanded.append(" abstract");
		}
		if (q.contains("核心贡献") || q.contains("主要贡献") || q.contains("贡献")) {
			expanded.append(" core contribution contributions abstract introduction method");
		}
		if (q.contains("方法") || q.contains("怎么做") || q.contains("原理")) {
			expanded.append(" method approach framework algorithm");
		}
		if (q.contains("实验") || q.contains("结果") || q.contains("效果")) {
			expanded.append(" experiment results evaluation findings");
		}
		if (q.contains("总结") || q.contains("结论")) {
			expanded.append(" conclusion summary");
		}
		return expanded.toString();
	}

	static boolean requestsAbstract(String query) {
		return query != null && (query.contains("摘要") || query.toLowerCase(java.util.Locale.ROOT).contains("abstract"));
	}

	private List<String> abstractSearch(String topicId, List<String> paperIds) {
		try {
			var response = es.search(s -> s.index(props.esIndex())
				.size(60)
				.query(q -> q.bool(b -> b.filter(scopeFilters(topicId, paperIds))
					.must(m -> m.bool(section -> section
						.should(v -> v.wildcard(w -> w.field("section_path").value("*abstract*").caseInsensitive(true)))
						.should(v -> v.wildcard(w -> w.field("section_path").value("*摘要*")))
						.minimumShouldMatch("1"))))),
					Map.class);
			return hitIds(response);
		}
		catch (Exception e) {
			logger.warn("摘要章节检索失败", e);
			return List.of();
		}
	}

	private List<String> denseSearch(String topicId, List<String> paperIds, double[] queryVec, int size) {
		try {
			List<Float> vec = new ArrayList<>(queryVec.length);
			for (double v : queryVec) {
				vec.add((float) v);
			}
			var response = es.search(s -> s.index(props.esIndex())
				.size(props.embedBatchSize() * 2)
				.knn(k -> k.field("embedding")
					.queryVector(vec)
					.k(size)
					.numCandidates(Math.max(size * 5, 100))
					// ResearchPilot V0.4 H2.2 修复（隔离测试发现）：
					// ES8 请求级 knn 与 query 是并集关系，knn 结果不受 query filter 约束，
					// 必须给 knn 单独传同样的 scope filter —— 此前 dense 路绕过了 Topic Scope。
					.filter(scopeFilters(topicId, paperIds))), Map.class);
			return hitIds(response);
		}
		catch (Exception e) {
			logger.warn("Dense 检索失败", e);
			return List.of();
		}
	}

	/**
	 * Scope 过滤（单一来源）：topic_id 必过滤，paperIds 可选收紧。BM25 query 子句与 knn 通道共用 ——
	 * 两路都必须强制，否则任一路绕过即构成跨专题串扰。
	 */
	private List<co.elastic.clients.elasticsearch._types.query_dsl.Query> scopeFilters(String topicId,
			List<String> paperIds) {
		var filters = new ArrayList<co.elastic.clients.elasticsearch._types.query_dsl.Query>();
		filters.add(co.elastic.clients.elasticsearch._types.query_dsl.Query
			.of(q -> q.term(t -> t.field("topic_id").value(topicId))));
		if (paperIds != null && !paperIds.isEmpty()) {
			filters.add(co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q.terms(t -> t.field("paper_id")
				.terms(tv -> tv
					.value(paperIds.stream().map(co.elastic.clients.elasticsearch._types.FieldValue::of).toList())))));
		}
		return filters;
	}

	// searchWithScope 已删除（H2.2 修复）：链式二次 .query() 会静默替换 scope filter，
	// 各通道必须在自己的 bool 查询内直接调用 scopeFilters()。

	private List<String> hitIds(co.elastic.clients.elasticsearch.core.SearchResponse<Map> response) {
		return response.hits().hits().stream().map(Hit::id).toList();
	}

	private void accumulate(List<String> rankedIds, String channel, Map<String, Double> fused,
			Map<String, List<String>> channels) {
		for (int rank = 1; rank <= rankedIds.size(); rank++) {
			String id = rankedIds.get(rank - 1);
			fused.merge(id, 1.0 / (RRF_K + rank), Double::sum);
			channels.computeIfAbsent(id, k -> new ArrayList<>()).add(channel);
		}
	}

	private Map<String, Map<String, Object>> fetchByIds(List<String> ids) {
		try {
			var response = es.mget(m -> m.index(props.esIndex()).ids(ids), Map.class);
			Map<String, Map<String, Object>> result = new LinkedHashMap<>();
			response.docs().forEach(item -> {
				var hit = item.result();
				if (hit.source() != null) {
					@SuppressWarnings("unchecked")
					Map<String, Object> src = (Map<String, Object>) hit.source();
					result.put(hit.id(), src);
				}
			});
			return result;
		}
		catch (Exception e) {
			logger.warn("批量取回失败: {}", e.getMessage());
			return Map.of();
		}
	}

	/**
	 * Coverage（简版）：专题内各文献的已索引 chunk 数 —— NOT_REPORTED 判定的基础数据。
	 */
	public Map<String, Long> coverage(String topicId) throws Exception {
		var response = es.search(s -> s.index(props.esIndex())
			.size(0)
			.query(q -> q.term(t -> t.field("topic_id").value(topicId)))
			.aggregations("papers", a -> a.terms(t -> t.field("paper_id").size(100))), Map.class);
		Map<String, Long> result = new LinkedHashMap<>();
		response.aggregations()
			.get("papers")
			.sterms()
			.buckets()
			.array()
			.forEach(b -> result.put(b.key().stringValue(), b.docCount()));
		return result;
	}

	/**
	 * 删除文献的检索副本（软删除后必须同步执行 —— 删除文献立即禁止命中）。
	 */
	public long purgeByPaper(String paperId) throws Exception {
		var response = es.deleteByQuery(d -> d.index(props.esIndex())
			.query(q -> q.term(t -> t.field("paper_id").value(paperId)))
			.refresh(true));
		long deleted = response.deleted() == null ? 0 : response.deleted();
		retrievalCache.keySet().removeIf(key -> key.contains("," + paperId + ",") || key.endsWith("," + paperId));
		logger.info("RP 清理检索副本: paper={} deleted={}", paperId, deleted);
		return deleted;
	}

}
