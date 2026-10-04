package com.alibaba.cloud.ai.example.deepresearch.researchpilot.web;

import com.alibaba.cloud.ai.example.deepresearch.TestDatabase;

import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

class CitationExportIT {

	private static JdbcTemplate jdbc;

	private static TransactionTemplate tx;

	private static RpController controller;

	@BeforeAll
	static void setup() {
		var ds = new DriverManagerDataSource(TestDatabase.url(), "researchpilot", "researchpilot");
		new ResourceDatabasePopulator(new org.springframework.core.io.ClassPathResource("rp_schema.sql")).execute(ds);
		jdbc = new JdbcTemplate(ds);
		tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
		controller = new RpController(
				new com.alibaba.cloud.ai.example.deepresearch.researchpilot.store.RpRepository(jdbc), null, null, null,
				jdbc, null);
	}

	@Test
	void exportsConfirmedMetadataWithoutInventingMissingFields() {
		tx.executeWithoutResult(status -> {
			status.setRollbackOnly();
			UUID topic = jdbc.queryForObject("INSERT INTO rp_topics(name) VALUES ('citation') RETURNING id",
					UUID.class);
			UUID paper = jdbc.queryForObject(
					"INSERT INTO rp_papers(topic_id,title,metadata) VALUES (?, '中文论文', '{\"authors\":\"张三;李四\",\"year\":2024,\"doi\":\"10.1234/x\"}'::jsonb) RETURNING id",
					UUID.class, topic);
			var bib = controller.citation(paper, "bibtex");
			var ris = controller.citation(paper, "ris");
			assertTrue(bib.getBody().contains("author = {张三 and 李四}"));
			assertTrue(bib.getBody().contains("doi = {10.1234/x}"));
			assertTrue(ris.getBody().contains("AU  - 张三"));
			assertTrue(ris.getBody().contains("AU  - 张三\nAU  - 李四\n"));
			jdbc.update("UPDATE rp_papers SET metadata = '{}'::jsonb WHERE id = ?", paper);
			String missing = controller.citation(paper, "bibtex").getBody();
			assertFalse(missing.contains("author ="));
			assertFalse(missing.contains("year ="));
			assertFalse(missing.contains("doi ="));
			assertEquals(400, controller.citation(paper, "endnote").getStatusCode().value());
		});
	}

}
