package com.alibaba.cloud.ai.example.deepresearch.researchpilot.web;

import com.alibaba.cloud.ai.example.deepresearch.TestDatabase;

import java.util.UUID;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import static org.junit.jupiter.api.Assertions.*;

class NotesFavoriteIT {

	private static JdbcTemplate jdbc;

	private static RpController controller;

	private static org.springframework.transaction.support.TransactionTemplate transaction;

	@BeforeAll
	static void setup() {
		var ds = new DriverManagerDataSource(TestDatabase.url(), "researchpilot", "researchpilot");
		new ResourceDatabasePopulator(new ClassPathResource("rp_schema.sql")).execute(ds);
		jdbc = new JdbcTemplate(ds);
		transaction = new org.springframework.transaction.support.TransactionTemplate(
				new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
		controller = new RpController(
				new com.alibaba.cloud.ai.example.deepresearch.researchpilot.store.RpRepository(jdbc), null, null, null,
				jdbc, null);
	}

	@Test
	void notesCanBeEditedAndFavoritedWithoutChangingSourceBinding() {
		transaction.executeWithoutResult(tx -> {
			tx.setRollbackOnly();
			UUID topic = jdbc.queryForObject("INSERT INTO rp_topics(name) VALUES ('notes') RETURNING id", UUID.class);
			var created = controller.createNote(topic,
					new RpController.NoteRequest("old", "body", null, "ev-1", "quote"));
			UUID id = UUID.fromString(String.valueOf(((Map<?, ?>) created.getBody()).get("id")));
			assertEquals(200, controller.favoriteNote(id, Map.of("favorite", true)).getStatusCode().value());
			assertEquals(200,
					controller
						.updateNote(id, new RpController.NoteRequest("new", "edited", null, "ev-2", "tampered quote"))
						.getStatusCode()
						.value());
			var row = controller.notes(topic).getBody();
			assertTrue(row.toString().contains("favorite=true"));
			assertTrue(row.toString().contains("edited"));
			assertTrue(row.toString().contains("source_quote=quote"));
			assertTrue(row.toString().contains("evidence_id=ev-1"));
			assertEquals(400,
					controller.updateNote(id, new RpController.NoteRequest("x".repeat(161), "body", null, null, null))
						.getStatusCode()
						.value());
			assertEquals(400,
					controller
						.createNote(topic, new RpController.NoteRequest("title", "body", "invalid-id", null, null))
						.getStatusCode()
						.value());
			assertEquals(400,
					controller
						.createNote(topic,
								new RpController.NoteRequest("title", "body", null, UUID.randomUUID().toString(),
										"quote"))
						.getStatusCode()
						.value());
			assertEquals(201,
					controller.addMemory(topic, new RpController.MemoryRequest("STABLE_PREFERENCE", "优先使用中文解释"))
						.getStatusCode()
						.value());
			assertTrue(controller.memory(topic).getBody().toString().contains("优先使用中文解释"));
		});
	}

}
