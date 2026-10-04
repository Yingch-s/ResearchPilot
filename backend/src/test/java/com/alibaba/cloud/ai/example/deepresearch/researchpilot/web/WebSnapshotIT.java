package com.alibaba.cloud.ai.example.deepresearch.researchpilot.web;

import com.alibaba.cloud.ai.example.deepresearch.TestDatabase;

import java.util.UUID;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.store.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSnapshotIT {

	private static JdbcTemplate jdbc;

	private static TransactionTemplate transaction;

	private static RpController controller;

	private static WebSourceFetcher fetcher;

	private static RpObjectStore objects;

	@BeforeAll
	static void setup() {
		var ds = new DriverManagerDataSource(TestDatabase.url(), "researchpilot", "researchpilot");
		new ResourceDatabasePopulator(new ClassPathResource("rp_schema.sql")).execute(ds);
		jdbc = new JdbcTemplate(ds);
		var manager = new DataSourceTransactionManager(ds);
		transaction = new TransactionTemplate(manager);
		objects = mock(RpObjectStore.class);
		fetcher = mock(WebSourceFetcher.class);
		var target = new RpController(new RpRepository(jdbc), objects, null, null, jdbc, null);
		ReflectionTestUtils.setField(target, "webFetcher", fetcher);
		var proxy = new ProxyFactory(target);
		proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
		controller = (RpController) proxy.getProxy();
	}

	@Test
	void snapshotCreatesSelectableTextDocumentJobAndRepeatReturnsSamePaper() {
		transaction.executeWithoutResult(tx -> {
			tx.setRollbackOnly();
			try {
				UUID topic = jdbc.queryForObject("INSERT INTO rp_topics(name) VALUES ('web test') RETURNING id",
						UUID.class);
				String url = "https://example.com/";
				when(fetcher.fetch(url))
					.thenReturn(new WebSourceFetcher.Snapshot(url, "中文研究", "text/html", "<p>正文</p>", "正文"));
				var request = new RpController.WebSnapshotRequest(url);
				var first = controller.saveWebSnapshot(topic, request);
				var second = controller.saveWebSnapshot(topic, request);
				assertEquals(201, first.getStatusCode().value());
				assertEquals(200, second.getStatusCode().value());
				assertEquals(1,
						jdbc.queryForObject("SELECT count(*) FROM rp_papers WHERE topic_id = ?", Integer.class, topic));
				var paper = jdbc.queryForMap(
						"SELECT p.metadata::text, dv.object_key, j.status FROM rp_papers p JOIN rp_document_versions dv ON dv.paper_id = p.id JOIN rp_ingestion_jobs j ON j.document_version_id = dv.id WHERE p.topic_id = ?",
						topic);
				assertTrue(paper.get("metadata").toString().contains("WEB_SNAPSHOT"));
				assertEquals("QUEUED", paper.get("status"));
				verify(objects).put(paper.get("object_key").toString(),
						"# 中文研究\n\n来源：https://example.com/\n\n正文".getBytes(StandardCharsets.UTF_8));
				var snapshot = controller.webSnapshots(topic).get(0);
				assertEquals("中文研究", snapshot.get("title"));
				assertNotNull(snapshot.get("paper_id"));
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}

	@Test
	void changedPageCreatesNewImmutableSnapshotWhileOldContentRemainsReadable() {
		transaction.executeWithoutResult(tx -> {
			tx.setRollbackOnly();
			try {
				UUID topic = jdbc.queryForObject("INSERT INTO rp_topics(name) VALUES ('web revisions') RETURNING id",
						UUID.class);
				String url = "https://example.com/revision";
				when(fetcher.fetch(url)).thenReturn(
						new WebSourceFetcher.Snapshot(url, "old", "text/html", "old html", "old text"),
						new WebSourceFetcher.Snapshot(url, "new", "text/html", "new html", "new text"));
				controller.saveWebSnapshot(topic, new RpController.WebSnapshotRequest(url));
				controller.saveWebSnapshot(topic, new RpController.WebSnapshotRequest(url));
				assertEquals(2, controller.webSnapshots(topic).size());
				assertEquals(1, jdbc.queryForObject(
						"SELECT count(*) FROM rp_web_snapshots WHERE topic_id = ? AND content = 'old html' AND extracted_text = 'old text'",
						Integer.class, topic));
				assertEquals(2,
						jdbc.queryForObject("SELECT count(*) FROM rp_papers WHERE topic_id = ?", Integer.class, topic));
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}

}
