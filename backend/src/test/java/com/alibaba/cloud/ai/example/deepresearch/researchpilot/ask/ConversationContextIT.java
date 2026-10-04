package com.alibaba.cloud.ai.example.deepresearch.researchpilot.ask;

import com.alibaba.cloud.ai.example.deepresearch.TestDatabase;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConversationContextIT {

	private static JdbcTemplate jdbc;

	private static ConversationContextService service;

	@BeforeAll
	static void setup() throws Exception {
		var ds = new DriverManagerDataSource(TestDatabase.url(), "researchpilot", "researchpilot");
		try (var ignored = ds.getConnection()) {
		}
		catch (Exception e) {
			Assumptions.assumeTrue(false, "PostgreSQL unavailable");
		}
		jdbc = new JdbcTemplate(ds);
		jdbc.execute(
				"ALTER TABLE rp_chat_sessions ADD COLUMN IF NOT EXISTS context_exclusions JSONB NOT NULL DEFAULT '{}'::jsonb");
		var proxy = new ProxyFactory(new ConversationContextService(jdbc));
		proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),
				new AnnotationTransactionAttributeSource()));
		service = (ConversationContextService) proxy.getProxy();
	}

	@Test
	void longHistoryIsBoundedIncrementalAndKeepsGoalAndCorrections() {
		UUID topic = UUID.randomUUID();
		UUID session = UUID.randomUUID();
		jdbc.update("INSERT INTO rp_topics (id, name) VALUES (?, 'context-test')", topic);
		try {
			jdbc.update("INSERT INTO rp_chat_sessions (id, topic_id) VALUES (?, ?)", session, topic);
			message(session, 0, "user", "目标：比较检索增强生成方法，必须保留负面证据。");
			for (int i = 1; i < 20; i++) {
				message(session, i, "assistant", "英文论文归纳与中文解释。".repeat(1000));
			}
			message(session, 20, "user", "纠正：只研究中文跨语言引用，不要讨论网页搜索。");
			for (int i = 21; i < 28; i++) {
				message(session, i, "assistant", "历史发现与未完成问题。".repeat(1000));
			}
			String context = service.assemble(topic, session, "请继续比较，并保留原文。" + "当前问题".repeat(500));
			assertTrue(ConversationContextService.budgetSize(context) <= ConversationContextService.MAX_CONTEXT_BYTES);
			assertTrue(context.contains("必须保留负面证据"));
			assertTrue(context.contains("不要讨论网页搜索"));
			assertTrue(context.contains("请继续比较，并保留原文。"));
			var state = jdbc.queryForMap("SELECT * FROM rp_chat_sessions WHERE id = ?", session);
			assertNotNull(state.get("context_compacted_message_id"));
			String again = service.assemble(topic, session, "请继续比较，并保留原文。" + "当前问题".repeat(500));
			assertEquals(context, again);
			assertEquals(state.get("context_summary_version"),
					jdbc.queryForMap("SELECT * FROM rp_chat_sessions WHERE id = ?", session)
						.get("context_summary_version"));
			assertEquals(28, jdbc.queryForObject("SELECT count(*) FROM rp_chat_messages WHERE session_id = ?",
					Integer.class, session));
			assertEquals("new topic question", service.assemble(UUID.randomUUID(), session, "new topic question"));
		}
		finally {
			jdbc.update("DELETE FROM rp_topics WHERE id = ?", topic);
		}
	}

	@Test
	void oversizedCurrentQuestionIsRejectedRatherThanSilentlyTruncated() {
		assertThrows(ResponseStatusException.class,
				() -> service.assemble(UUID.randomUUID(), UUID.randomUUID(), "中".repeat(5000)));
	}

	@Test
	void citationIdentitiesAndNewCoverageArePreservedWithoutRepeatingOldMessages() {
		UUID topic = UUID.randomUUID();
		UUID session = UUID.randomUUID();
		jdbc.update("INSERT INTO rp_topics (id, name) VALUES (?, 'citation-context-test')", topic);
		try {
			jdbc.update("INSERT INTO rp_chat_sessions (id, topic_id) VALUES (?, ?)", session, topic);
			for (int i = 0; i < 10; i++) {
				message(session, i, i % 2 == 0 ? "user" : "assistant", "unique-message-" + i);
			}
			jdbc.update(
					"UPDATE rp_chat_messages SET citations = ?::jsonb WHERE session_id = ? AND content = 'unique-message-9'",
					"[{\"chunk_id\":\"paper-42:page-7:chunk-2\"}]", session);
			String first = service.assemble(topic, session, "next question");
			assertTrue(first.contains("source_id=paper-42:page-7:chunk-2"));
			Integer version = jdbc.queryForObject("SELECT context_summary_version FROM rp_chat_sessions WHERE id = ?",
					Integer.class, session);
			message(session, 10, "user", "new correction");
			message(session, 11, "assistant", "new result");
			String second = service.assemble(topic, session, "next question");
			assertTrue(second.contains("new correction"));
			assertEquals(version + 1, jdbc.queryForObject(
					"SELECT context_summary_version FROM rp_chat_sessions WHERE id = ?", Integer.class, session));
			String summary = jdbc.queryForObject("SELECT context_summary FROM rp_chat_sessions WHERE id = ?",
					String.class, session);
			assertEquals(summary.indexOf("unique-message-1"), summary.lastIndexOf("unique-message-1"));
			assertTrue(summary.contains("unique-message-5"));
		}
		finally {
			jdbc.update("DELETE FROM rp_topics WHERE id = ?", topic);
		}
	}

	@Test
	void unicodeExcerptNeverSplitsCodePointsOrExceedsBudget() {
		String text = "中文🧪English".repeat(100);
		for (int bytes = 0; bytes < 200; bytes++) {
			String shortened = ConversationContextService.excerpt(text, bytes);
			assertTrue(ConversationContextService.budgetSize(shortened) <= bytes);
			assertFalse(shortened.contains("\uFFFD"));
			assertFalse(!shortened.isEmpty() && Character.isHighSurrogate(shortened.charAt(shortened.length() - 1)));
		}
	}

	@Test
	void toolResultsAreReferencedByIdInsteadOfInjectedInFull() {
		UUID topic = UUID.randomUUID();
		UUID session = UUID.randomUUID();
		jdbc.update("INSERT INTO rp_topics (id, name) VALUES (?, 'tool-context-test')", topic);
		try {
			jdbc.update("INSERT INTO rp_chat_sessions (id, topic_id) VALUES (?, ?)", session, topic);
			jdbc.update(
					"INSERT INTO rp_chat_messages (session_id, role, content, metadata) VALUES (?, 'assistant', ?, ?::jsonb)",
					session, "TOOL_PAYLOAD_".repeat(1000), "{\"type\":\"tool_result\"}");
			String context = service.assemble(topic, session, "继续");
			assertTrue(context.contains("工具结果已外置"));
			assertTrue(context.contains("读取原始结果"));
			assertFalse(context.contains("TOOL_PAYLOAD_".repeat(500)));
		}
		finally {
			jdbc.update("DELETE FROM rp_topics WHERE id = ?", topic);
		}
	}

	@Test
	void excludedMessagesInvalidateSummaryAndAreNotAssembled() {
		UUID topic = UUID.randomUUID();
		UUID session = UUID.randomUUID();
		jdbc.update("INSERT INTO rp_topics (id, name) VALUES (?, 'context-exclusion-test')", topic);
		try {
			jdbc.update("INSERT INTO rp_chat_sessions (id, topic_id) VALUES (?, ?)", session, topic);
			message(session, 0, "user", "保留的研究目标");
			message(session, 1, "assistant", "应该被排除的历史内容");
			UUID excluded = jdbc.queryForObject(
					"SELECT id FROM rp_chat_messages WHERE session_id = ? AND content = '应该被排除的历史内容'", UUID.class,
					session);
			jdbc.update(
					"UPDATE rp_chat_sessions SET context_exclusions = ?::jsonb, context_summary = 'stale', context_compacted_message_id = ? WHERE id = ?",
					"{\"message_ids\":[\"" + excluded + "\"]}", excluded, session);
			String context = service.assemble(topic, session, "继续研究");
			assertTrue(context.contains("保留的研究目标"));
			assertFalse(context.contains("应该被排除的历史内容"));
		}
		finally {
			jdbc.update("DELETE FROM rp_topics WHERE id = ?", topic);
		}
	}

	@Test
	void excludedMemoryIsNotAssembled() {
		UUID topic = UUID.randomUUID();
		UUID session = UUID.randomUUID();
		jdbc.update("INSERT INTO rp_topics (id, name) VALUES (?, 'memory-exclusion-test')", topic);
		try {
			jdbc.update("INSERT INTO rp_chat_sessions (id, topic_id) VALUES (?, ?)", session, topic);
			UUID memory = jdbc.queryForObject(
					"INSERT INTO rp_topic_memory (topic_id, kind, content) VALUES (?, 'CONFIRMED_FACT', '不应进入本次上下文') RETURNING id",
					UUID.class, topic);
			jdbc.update("UPDATE rp_chat_sessions SET context_exclusions = ?::jsonb WHERE id = ?",
					"{\"memory_ids\":[\"" + memory + "\"]}", session);
			String context = service.assemble(topic, session, "继续");
			assertFalse(context.contains("不应进入本次上下文"));
		}
		finally {
			jdbc.update("DELETE FROM rp_topics WHERE id = ?", topic);
		}
	}

	@Test
	void harnessInjectsActiveUserMemoryWithoutTreatingItAsSourceEvidence() {
		UUID topic = UUID.randomUUID();
		UUID session = UUID.randomUUID();
		jdbc.update("INSERT INTO rp_topics (id, name) VALUES (?, 'user-memory-context-test')", topic);
		try {
			jdbc.update("INSERT INTO rp_chat_sessions (id, topic_id) VALUES (?, ?)", session, topic);
			message(session, 0, "user", "初始研究问题");
			jdbc.update("INSERT INTO rp_user_memory (kind, content) VALUES ('PREFERENCE', '默认用中文回答，保留关键英文术语')");
			String context = service.assemble(topic, session, "解释这个研究结果");
			assertTrue(context.contains("User preferences and constraints"));
			assertTrue(context.contains("默认用中文回答，保留关键英文术语"));
			assertTrue(context.contains("do not treat as source evidence"));
			jdbc.update("UPDATE rp_user_memory SET status = 'DISMISSED' WHERE content = '默认用中文回答，保留关键英文术语'");
			assertFalse(service.assemble(topic, session, "再次解释").contains("默认用中文回答，保留关键英文术语"));
		}
		finally {
			jdbc.update("DELETE FROM rp_topics WHERE id = ?", topic);
			jdbc.update("DELETE FROM rp_user_memory WHERE content = '默认用中文回答，保留关键英文术语'");
		}
	}

	private void message(UUID session, int index, String role, String content) {
		jdbc.update("""
				INSERT INTO rp_chat_messages (session_id, role, content, created_at)
				VALUES (?, ?, ?, '2026-01-01'::timestamptz + (? * interval '1 second'))
				""", session, role, content, index);
	}

}
