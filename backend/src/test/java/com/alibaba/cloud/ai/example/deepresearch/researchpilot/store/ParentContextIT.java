package com.alibaba.cloud.ai.example.deepresearch.researchpilot.store;

import com.alibaba.cloud.ai.example.deepresearch.TestDatabase;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.ingest.RpChunker;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.retrieve.RpRetrievalService;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.retrieve.ParentContextBudget;
import static org.junit.jupiter.api.Assertions.*;

class ParentContextIT {

	@Test
	void actualPersistedSectionsSurviveNewServiceAndRemovedPaperCannotExpand() throws Exception {
		var ds = new DriverManagerDataSource(TestDatabase.url(), "researchpilot", "researchpilot");
		new ResourceDatabasePopulator(new ClassPathResource("rp_schema.sql")).execute(ds);
		var jdbc = new JdbcTemplate(ds);
		new TransactionTemplate(new DataSourceTransactionManager(ds)).executeWithoutResult(tx -> {
			tx.setRollbackOnly();
			try {
				UUID topic = jdbc.queryForObject("INSERT INTO rp_topics(name) VALUES ('parent test') RETURNING id",
						UUID.class);
				var repo = new RpRepository(jdbc);
				var paper = repo.createPaperWithVersion(topic.toString(), "source", UUID.randomUUID().toString(),
						"parent-test.txt", 12);
				String version = paper.get("document_version_id").toString();
				jdbc.update("UPDATE rp_document_versions SET parse_status = 'READY' WHERE id = ?::uuid", version);
				String parent = "parent-" + UUID.randomUUID();
				repo.saveSectionContexts(version, List.of(new RpChunker.ParentChild.Parent(parent, "Methods",
						"Full section with limitations and counterexample")));
				var retrieval = new RpRetrievalService(null, null, null);
				ReflectionTestUtils.setField(retrieval, "jdbc", jdbc);
				assertEquals("Full section with limitations and counterexample",
						retrieval.expandParents(List.of(parent)).get(parent));
				assertTrue(retrieval.expandParents(List.of(parent), UUID.randomUUID().toString(), null).isEmpty());
				assertTrue(retrieval
					.expandParents(List.of(parent), topic.toString(), List.of(UUID.randomUUID().toString()))
					.isEmpty());
				assertFalse(retrieval
					.expandParents(List.of(parent), topic.toString(), List.of(paper.get("paper_id").toString()))
					.isEmpty());
				new RpRepository(jdbc).saveSectionContexts(version,
						List.of(new RpChunker.ParentChild.Parent(parent, "Methods", "Updated parse")));
				assertEquals("Updated parse", retrieval.expandParents(List.of(parent)).get(parent));
				jdbc.update("UPDATE rp_papers SET status = 'REMOVED' WHERE id = ?::uuid", paper.get("paper_id"));
				assertTrue(retrieval.expandParents(List.of(parent)).isEmpty());
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}

	@Test
	void oldParsedVersionsBackfillWithoutRepeatingParsingOrEmbedding() throws Exception {
		var ds = new DriverManagerDataSource(TestDatabase.url(), "researchpilot", "researchpilot");
		new ResourceDatabasePopulator(new ClassPathResource("rp_schema.sql")).execute(ds);
		var jdbc = new JdbcTemplate(ds);
		new TransactionTemplate(new DataSourceTransactionManager(ds)).executeWithoutResult(tx -> {
			tx.setRollbackOnly();
			UUID topic = jdbc.queryForObject("INSERT INTO rp_topics(name) VALUES ('old parent') RETURNING id",
					UUID.class);
			var repository = new RpRepository(jdbc);
			var paper = repository.createPaperWithVersion(topic.toString(), "old", UUID.randomUUID().toString(),
					"old.txt", 10);
			String version = paper.get("document_version_id").toString();
			jdbc.update(
					"UPDATE rp_document_versions SET parse_status='READY', normalized_object_key='backfill/test.json', created_at='1900-01-01' WHERE id=?::uuid",
					version);
			var objects = org.mockito.Mockito.mock(RpObjectStore.class);
			org.mockito.Mockito.when(objects.get("backfill/test.json"))
				.thenReturn(
						"{\"blocks\":[{\"section_path\":\"Methods\",\"content_type\":\"text\",\"text\":\"condition matters\"}]}"
							.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			var backfill = new com.alibaba.cloud.ai.example.deepresearch.researchpilot.ingest.SectionContextBackfill(
					jdbc, repository, objects, new RpChunker());
			backfill.backfill();
			backfill.backfill();
			assertEquals(1,
					jdbc.queryForObject("SELECT count(*) FROM rp_section_contexts WHERE document_version_id=?::uuid",
							Integer.class, version));
			org.mockito.Mockito.verify(objects, org.mockito.Mockito.times(1)).get("backfill/test.json");
		});
	}

	@Test
	void contextBudgetDeduplicatesParentsAndPreservesChildCitationIdentity() {
		var child = new RpRetrievalService.Candidate("child", "paper", "parent", "quote", "Methods", 1, "text", 1,
				List.of());
		String context = ParentContextBudget.assemble(List.of(child, child),
				java.util.Map.of("parent", "中文".repeat(10000)), 500);
		assertTrue(context.length() <= 500);
		assertEquals(1, context.split("章节背景", -1).length - 1);
		assertEquals("quote", child.text());
		assertEquals("child", child.chunkId());
	}

}
