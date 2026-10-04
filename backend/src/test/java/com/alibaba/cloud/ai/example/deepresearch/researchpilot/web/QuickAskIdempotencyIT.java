package com.alibaba.cloud.ai.example.deepresearch.researchpilot.web;

import com.alibaba.cloud.ai.example.deepresearch.TestDatabase;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.ask.RpAskService;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.store.RpRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuickAskIdempotencyIT {

	private static JdbcTemplate jdbc;

	private static RpController controller;

	private static RpAskService ask;

	private static org.springframework.transaction.support.TransactionTemplate transaction;

	@BeforeAll
	static void setup() {
		var ds = new DriverManagerDataSource(TestDatabase.url(), "researchpilot", "researchpilot");
		new ResourceDatabasePopulator(new ClassPathResource("rp_schema.sql")).execute(ds);
		jdbc = new JdbcTemplate(ds);
		var manager = new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds);
		transaction = new org.springframework.transaction.support.TransactionTemplate(manager);
		ask = mock(RpAskService.class);
		when(ask.ask(anyString(), any(), anyString(), anyBoolean(), anyBoolean(), anyString()))
			.thenReturn(new RpAskService.AskResult("稳定回答", List.of(), 1));
		controller = new RpController(new RpRepository(jdbc), null, null, null, jdbc, null);
		ReflectionTestUtils.setField(controller, "askService", ask);
		var proxy = new org.springframework.aop.framework.ProxyFactory(controller);
		proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(manager,
				new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
		controller = (RpController) proxy.getProxy();
	}

	@Test
	void retryWithSameMessageIdReturnsSavedAnswerWithoutCallingModelAgain() {
		clearInvocations(ask);
		transaction.executeWithoutResult(tx -> {
			tx.setRollbackOnly();
			UUID topic = jdbc.queryForObject("INSERT INTO rp_topics(name) VALUES ('quick idempotency') RETURNING id",
					UUID.class);
			String session = UUID.randomUUID().toString();
			String message = UUID.randomUUID().toString();
			var request = new RpController.AskRequest("问题", List.of(), false, false, session, message);
			var first = controller.ask(topic.toString(), request);
			var second = controller.ask(topic.toString(), request);
			assertEquals("稳定回答", first.get("answer"));
			assertEquals(first.get("answer"), second.get("answer"));
			verify(ask, times(1)).ask(anyString(), any(), anyString(), anyBoolean(), anyBoolean(), anyString());
			assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM rp_chat_messages WHERE session_id = ?::uuid",
					Integer.class, session));
			assertTrue(second.get("citations") instanceof com.fasterxml.jackson.databind.JsonNode);
		});
	}

	@Test
	void concurrentRetriesCallModelOnceAndWriteOneMessagePair() throws Exception {
		clearInvocations(ask);
		UUID topic = jdbc.queryForObject("INSERT INTO rp_topics(name) VALUES ('quick concurrency') RETURNING id",
				UUID.class);
		String session = UUID.randomUUID().toString();
		var request = new RpController.AskRequest("concurrent question", List.of(), false, false, session,
				UUID.randomUUID().toString());
		var entered = new java.util.concurrent.CountDownLatch(1);
		var release = new java.util.concurrent.CountDownLatch(1);
		when(ask.ask(anyString(), any(), anyString(), anyBoolean(), anyBoolean(), anyString()))
			.thenAnswer(invocation -> {
				entered.countDown();
				assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS));
				return new RpAskService.AskResult("稳定回答", List.of(), 1);
			});
		var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
		try {
			var first = workers.submit(() -> controller.ask(topic.toString(), request));
			assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
			var second = workers.submit(() -> controller.ask(topic.toString(), request));
			release.countDown();
			assertEquals(first.get(5, java.util.concurrent.TimeUnit.SECONDS).get("answer"),
					second.get(5, java.util.concurrent.TimeUnit.SECONDS).get("answer"));
			verify(ask, times(1)).ask(anyString(), any(), anyString(), anyBoolean(), anyBoolean(), anyString());
			assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM rp_chat_messages WHERE session_id = ?::uuid",
					Integer.class, session));
		}
		finally {
			release.countDown();
			workers.shutdownNow();
			workers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
			jdbc.update("DELETE FROM rp_topics WHERE id = ?", topic);
			when(ask.ask(anyString(), any(), anyString(), anyBoolean(), anyBoolean(), anyString()))
				.thenReturn(new RpAskService.AskResult("稳定回答", List.of(), 1));
		}
	}

}
