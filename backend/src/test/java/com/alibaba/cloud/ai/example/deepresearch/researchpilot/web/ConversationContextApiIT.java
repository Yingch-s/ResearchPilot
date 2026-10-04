package com.alibaba.cloud.ai.example.deepresearch.researchpilot.web;

import com.alibaba.cloud.ai.example.deepresearch.TestDatabase;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.ask.ConversationContextService;
import static org.junit.jupiter.api.Assertions.*;

class ConversationContextApiIT {

	@Test
	void contextListsRunsAndPersistsExclusionsWithoutRemovingOriginalRecords() {
		var ds = new DriverManagerDataSource(TestDatabase.url(), "researchpilot", "researchpilot");
		var jdbc = new JdbcTemplate(ds);
		var controller = new RpController(null, null, null, null, jdbc, null);
		new TransactionTemplate(new DataSourceTransactionManager(ds)).executeWithoutResult(tx -> {
			tx.setRollbackOnly();
			UUID topic = jdbc.queryForObject("INSERT INTO rp_topics(name) VALUES ('context-api-test') RETURNING id",
					UUID.class);
			UUID session = UUID.randomUUID();
			jdbc.update("INSERT INTO rp_chat_sessions(id, topic_id) VALUES (?, ?)", session, topic);
			UUID message = jdbc.queryForObject(
					"INSERT INTO rp_chat_messages(session_id, role, content) VALUES (?, 'user', 'excluded-goal') RETURNING id",
					UUID.class, session);
			UUID memory = jdbc.queryForObject(
					"INSERT INTO rp_topic_memory(topic_id, kind, content) VALUES (?, 'CONFIRMED_FACT', 'excluded-memory') RETURNING id",
					UUID.class, topic);
			UUID run = jdbc.queryForObject(
					"INSERT INTO rp_runs(topic_id, session_id, thread_id, query, plan_snapshot) VALUES (?, ?, ?, 'research', ?::jsonb) RETURNING id",
					UUID.class, topic, session.toString(), UUID.randomUUID().toString(),
					"{\"goal\":\"excluded-plan\"}");
			var before = (Map<?, ?>) controller.context(topic.toString(), session).getBody();
			assertEquals(1, ((List<?>) before.get("messages")).size());
			assertEquals(1, ((List<?>) before.get("memories")).size());
			assertEquals(1, ((List<?>) before.get("runs")).size());
			var exclusions = Map.<String, Object>of("message_ids", List.of(message.toString()), "memory_ids",
					List.of(memory.toString()), "run_ids", List.of(run.toString()));
			assertEquals(202, controller.updateContext(topic.toString(), session, exclusions).getStatusCode().value());
			var service = new ConversationContextService(jdbc);
			String excluded = service.assemble(topic, session, "continue");
			assertFalse(excluded.contains("excluded-goal"));
			assertFalse(excluded.contains("excluded-memory"));
			assertFalse(excluded.contains("excluded-plan"));
			assertEquals(202, controller.updateContext(topic.toString(), session, Map.of()).getStatusCode().value());
			String restored = service.assemble(topic, session, "continue");
			assertTrue(restored.contains("excluded-goal"));
			assertTrue(restored.contains("excluded-memory"));
			assertTrue(restored.contains("excluded-plan"));
			assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM rp_chat_messages WHERE session_id = ?",
					Integer.class, session));
			jdbc.update("INSERT INTO rp_chat_messages(session_id, role, content) VALUES (?, 'assistant', ?)", session,
					"正常回答\n<｜｜DSML｜｜ calls>\n<｜｜DSML｜｜ invoke name=searchFilterTool>");
			@SuppressWarnings("unchecked")
			var messageRows = (List<Map<String, Object>>) controller.messages(topic.toString(), session).getBody();
			assertTrue(messageRows.stream()
				.map(row -> String.valueOf(row.get("content")))
				.noneMatch(content -> content.contains("DSML") || content.contains("invoke name=")));
		});
	}

}
