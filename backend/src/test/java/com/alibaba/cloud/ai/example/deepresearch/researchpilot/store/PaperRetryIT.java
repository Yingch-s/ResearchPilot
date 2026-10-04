package com.alibaba.cloud.ai.example.deepresearch.researchpilot.store;

import com.alibaba.cloud.ai.example.deepresearch.TestDatabase;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaperRetryIT {

	private static JdbcTemplate jdbc;

	private static RpRepository repo;

	private static TransactionTemplate transaction;

	@BeforeAll
	static void setup() {
		var ds = new DriverManagerDataSource(TestDatabase.url(), "researchpilot", "researchpilot");
		try (var ignored = ds.getConnection()) {
		}
		catch (Exception e) {
			Assumptions.assumeTrue(false, "PostgreSQL unavailable");
		}
		jdbc = new JdbcTemplate(ds);
		repo = new RpRepository(jdbc);
		transaction = new TransactionTemplate(new DataSourceTransactionManager(ds));
	}

	@Test
	void failedPaperRetryReusesFileAndIsIdempotent() {
		transaction.executeWithoutResult(tx -> {
			tx.setRollbackOnly();
			UUID topic = UUID.randomUUID();
			jdbc.update("INSERT INTO rp_topics (id, name) VALUES (?, 'retry-test')", topic);
			var paper = repo.createPaperWithVersion(topic.toString(), "Same name", "sha-" + UUID.randomUUID(),
					"test/original.txt", 20);
			String version = paper.get("document_version_id").toString();
			jdbc.update("UPDATE rp_document_versions SET parse_status = 'FAILED' WHERE id = ?::uuid", version);
			jdbc.update(
					"UPDATE rp_ingestion_jobs SET status = 'FAILED', last_error = 'embedding unavailable' WHERE document_version_id = ?::uuid",
					version);
			var failed = repo.listPapers(topic.toString()).get(0);
			assertEquals("FAILED", failed.get("parse_status"));
			assertEquals("embedding unavailable", failed.get("last_error"));
			var first = repo.retryPaper(paper.get("paper_id").toString());
			var second = repo.retryPaper(paper.get("paper_id").toString());
			assertEquals(first.get("job_id"), second.get("job_id"));
			assertEquals(true, second.get("idempotent"));
			assertEquals("UPLOADED", repo.listPapers(topic.toString()).get(0).get("parse_status"));
			assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM rp_document_versions WHERE paper_id = ?::uuid",
					Integer.class, paper.get("paper_id")));
			assertEquals(1, jdbc.queryForObject(
					"SELECT count(*) FROM rp_ingestion_jobs WHERE document_version_id = ?::uuid AND status = 'QUEUED'",
					Integer.class, version));
		});
	}

	@Test
	void partialPaperCanRetryButReadyOrRemovedPaperCannot() {
		transaction.executeWithoutResult(tx -> {
			tx.setRollbackOnly();
			UUID topic = UUID.randomUUID();
			jdbc.update("INSERT INTO rp_topics (id, name) VALUES (?, 'partial-test')", topic);
			var paper = repo.createPaperWithVersion(topic.toString(), "Partial", "sha-" + UUID.randomUUID(),
					"test/original.txt", 20);
			String version = paper.get("document_version_id").toString();
			jdbc.update("UPDATE rp_ingestion_jobs SET status = 'SUCCEEDED' WHERE document_version_id = ?::uuid",
					version);
			jdbc.update("UPDATE rp_document_versions SET parse_status = 'READY' WHERE id = ?::uuid", version);
			assertEquals(409,
					assertThrows(ResponseStatusException.class, () -> repo.retryPaper(paper.get("paper_id").toString()))
						.getStatusCode()
						.value());
			jdbc.update("UPDATE rp_document_versions SET parse_status = 'PARTIAL' WHERE id = ?::uuid", version);
			assertEquals(false, repo.retryPaper(paper.get("paper_id").toString()).get("idempotent"));
			repo.softDeletePaper(paper.get("paper_id").toString());
			assertEquals(404,
					assertThrows(ResponseStatusException.class, () -> repo.retryPaper(paper.get("paper_id").toString()))
						.getStatusCode()
						.value());
		});
	}

	@Test
	void archivedTopicCanBeRestoredAndResearchRecordsPreventDeletion() {
		transaction.executeWithoutResult(tx -> {
			tx.setRollbackOnly();
			UUID topic = UUID.randomUUID();
			jdbc.update("INSERT INTO rp_topics (id, name) VALUES (?, 'archive-test')", topic);
			assertTrue(repo.archiveTopic(topic));
			assertFalse(repo.archiveTopic(topic));
			assertFalse(repo.listTopics().stream().anyMatch(t -> topic.toString().equals(t.get("id"))));
			assertTrue(repo.listTopics(true).stream().anyMatch(t -> topic.toString().equals(t.get("id"))));
			assertTrue(repo.restoreTopic(topic));
			assertFalse(repo.restoreTopic(topic));
			assertTrue(repo.listTopics().stream().anyMatch(t -> topic.toString().equals(t.get("id"))));
			UUID run = UUID.randomUUID();
			jdbc.update(
					"INSERT INTO rp_runs (id, thread_id, topic_id, query, status) VALUES (?, ?, ?, 'completed study', 'COMPLETED')",
					run, "topic-test-" + run, topic);
			assertFalse(repo.deleteEmptyTopic(topic));
			jdbc.update("DELETE FROM rp_runs WHERE id = ?", run);
			assertTrue(repo.deleteEmptyTopic(topic));
		});
	}

	@Test
	void terminalFailureRetainsPublishedParseForManualRetry() {
		transaction.executeWithoutResult(tx -> {
			tx.setRollbackOnly();
			UUID topic = UUID.randomUUID();
			jdbc.update("INSERT INTO rp_topics (id, name) VALUES (?, 'parse-checkpoint-test')", topic);
			var paper = repo.createPaperWithVersion(topic.toString(), "Parsed paper", "sha-" + UUID.randomUUID(),
					"test/original.pdf", 20);
			String version = paper.get("document_version_id").toString();
			repo.saveParseCheckpoint(version, "normalized/saved.json", "parser-2", new String[] { "OCR_UNCERTAIN" });
			repo.finishJob(paper.get("job_id").toString(), version, false, "TERMINAL_FAILED", "FAILED",
					"index unavailable", null, null, new String[0], 0);
			assertEquals("normalized/saved.json", repo.parseCheckpoint(version));
			var stored = jdbc.queryForMap(
					"SELECT parser_version, array_to_string(quality_flags, ',') AS flags FROM rp_document_versions WHERE id = ?::uuid",
					version);
			assertEquals("parser-2", stored.get("parser_version"));
			assertEquals("OCR_UNCERTAIN", stored.get("flags"));
			repo.retryPaper(paper.get("paper_id").toString());
			assertEquals("normalized/saved.json", repo.parseCheckpoint(version));
		});
	}

	@Test
	void sameTitleIsAllowedButSameContentIsRejected() {
		transaction.executeWithoutResult(tx -> {
			tx.setRollbackOnly();
			UUID topic = UUID.randomUUID();
			jdbc.update("INSERT INTO rp_topics (id, name) VALUES (?, 'duplicate-test')", topic);
			String hash = "sha-" + UUID.randomUUID();
			repo.createPaperWithVersion(topic.toString(), "Same title", hash, "test/first.txt", 20);
			repo.createPaperWithVersion(topic.toString(), "Same title", "sha-" + UUID.randomUUID(), "test/second.txt",
					20);
			assertEquals(2, repo.listPapers(topic.toString()).size());
			assertThrows(RpRepository.DuplicateDocumentException.class,
					() -> repo.createPaperWithVersion(topic.toString(), "Different title", hash, "test/third.txt", 20));
		});
	}

}
