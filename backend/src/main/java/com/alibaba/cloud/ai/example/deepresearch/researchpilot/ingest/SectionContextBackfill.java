package com.alibaba.cloud.ai.example.deepresearch.researchpilot.ingest;

import com.alibaba.cloud.ai.example.deepresearch.researchpilot.store.RpObjectStore;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.store.RpRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Restore parents from existing parsing artifacts without repeating model calls. */
@Component
public class SectionContextBackfill {

	private final JdbcTemplate jdbc;

	private final RpRepository repository;

	private final RpObjectStore objects;

	private final RpChunker chunker;

	private final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SectionContextBackfill.class);

	public SectionContextBackfill(JdbcTemplate jdbc, RpRepository repository, RpObjectStore objects,
			RpChunker chunker) {
		this.jdbc = jdbc;
		this.repository = repository;
		this.objects = objects;
		this.chunker = chunker;
	}

	@Scheduled(fixedDelay = 60000, initialDelay = 15000)
	public void backfill() {
		var versions = jdbc.queryForList("""
				SELECT dv.id::text, dv.normalized_object_key, p.id::text AS paper_id, p.topic_id::text
				FROM rp_document_versions dv JOIN rp_papers p ON p.id = dv.paper_id
				WHERE dv.parse_status = 'READY' AND p.status = 'ACTIVE' AND dv.normalized_object_key IS NOT NULL
				AND dv.section_context_ready = FALSE
				AND (dv.section_context_checked_at IS NULL OR dv.section_context_checked_at < now() - interval '1 hour')
				ORDER BY dv.created_at LIMIT 4
				""");
		for (var version : versions) {
			try {
				int claimed = jdbc.update(
						"UPDATE rp_document_versions SET section_context_checked_at = now() WHERE id = ?::uuid AND section_context_ready = FALSE AND (section_context_checked_at IS NULL OR section_context_checked_at < now() - interval '1 hour')",
						version.get("id"));
				if (claimed == 0)
					continue;
				var normalized = new com.fasterxml.jackson.databind.ObjectMapper()
					.readTree(objects.get(version.get("normalized_object_key").toString()));
				var sections = chunker.chunk(normalized, version.get("topic_id").toString(),
						version.get("paper_id").toString(), version.get("id").toString());
				repository.saveSectionContexts(version.get("id").toString(), sections.parents());
			}
			catch (Exception e) {
				logger.warn("章节上下文回填失败: version={}", version.get("id"));
			}
		}
	}

}
