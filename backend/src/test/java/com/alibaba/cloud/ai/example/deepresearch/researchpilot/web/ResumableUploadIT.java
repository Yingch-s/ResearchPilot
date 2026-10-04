package com.alibaba.cloud.ai.example.deepresearch.researchpilot.web;

import com.alibaba.cloud.ai.example.deepresearch.TestDatabase;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.store.RpRepository;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.store.RpObjectStore;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ResumableUploadIT {

	private static JdbcTemplate jdbc;

	private static TransactionTemplate transaction;

	private static MockMvc mvc;

	private static RpObjectStore objects;

	private static RpController controller;

	private static final ObjectMapper JSON = new ObjectMapper();

	@BeforeAll
	static void setup() {
		var ds = new DriverManagerDataSource(TestDatabase.url(), "researchpilot", "researchpilot");
		new ResourceDatabasePopulator(new ClassPathResource("rp_schema.sql")).execute(ds);
		jdbc = new JdbcTemplate(ds);
		var manager = new DataSourceTransactionManager(ds);
		transaction = new TransactionTemplate(manager);
		objects = mock(RpObjectStore.class);
		controller = new RpController(new RpRepository(jdbc), objects, null, null, jdbc, null);
		var proxy = new ProxyFactory(controller);
		proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
		mvc = MockMvcBuilders.standaloneSetup(proxy.getProxy()).build();
	}

	@Test
	void rawBinaryBindingValidatesIndicesAndReplaysCompletionWithoutDuplicatePaper() {
		transaction.executeWithoutResult(tx -> {
			tx.setRollbackOnly();
			try {
				UUID topic = topic();
				byte[] bytes = "unique upload content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
				String upload = create(topic, bytes.length, hash(bytes));
				mvc.perform(
						put("/api/rp/upload-sessions/" + upload + "/chunks/1").contentType("application/octet-stream")
							.content(bytes))
					.andExpect(status().isBadRequest());
				mvc.perform(
						put("/api/rp/upload-sessions/" + upload + "/chunks/0").contentType("application/octet-stream")
							.content("short"))
					.andExpect(status().isBadRequest());
				putChunk(upload, bytes);
				putChunk(upload, bytes);
				byte[] different = bytes.clone();
				different[0]++;
				mvc.perform(
						put("/api/rp/upload-sessions/" + upload + "/chunks/0").contentType("application/octet-stream")
							.content(different))
					.andExpect(status().isConflict());
				mvc.perform(get("/api/rp/upload-sessions/" + upload))
					.andExpect(jsonPath("$.received.length()").value(1));
				String first = mvc.perform(post("/api/rp/upload-sessions/" + upload + "/complete"))
					.andExpect(status().isAccepted())
					.andReturn()
					.getResponse()
					.getContentAsString();
				String second = mvc.perform(post("/api/rp/upload-sessions/" + upload + "/complete"))
					.andExpect(status().isOk())
					.andReturn()
					.getResponse()
					.getContentAsString();
				assertEquals(JSON.readTree(first).get("paper_id"), JSON.readTree(second).get("paper_id"));
				assertEquals(1,
						jdbc.queryForObject("SELECT count(*) FROM rp_papers WHERE topic_id = ?", Integer.class, topic));
				assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM rp_upload_chunks WHERE session_id = ?::uuid",
						Integer.class, upload));
				verify(objects).put(anyString(), eq(bytes));
				clearInvocations(objects);
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}

	@Test
	void incompleteHashFailureCancellationAndExpiryDoNotCreatePapers() {
		transaction.executeWithoutResult(tx -> {
			tx.setRollbackOnly();
			try {
				UUID topic = topic();
				byte[] bytes = "bad hash content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
				String upload = create(topic, bytes.length, "0".repeat(64));
				mvc.perform(post("/api/rp/upload-sessions/" + upload + "/complete")).andExpect(status().isConflict());
				putChunk(upload, bytes);
				mvc.perform(post("/api/rp/upload-sessions/" + upload + "/complete"))
					.andExpect(status().isUnprocessableEntity());
				mvc.perform(delete("/api/rp/upload-sessions/" + upload)).andExpect(status().isOk());
				assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM rp_upload_chunks WHERE session_id = ?::uuid",
						Integer.class, upload));
				mvc.perform(
						put("/api/rp/upload-sessions/" + upload + "/chunks/0").contentType("application/octet-stream")
							.content(bytes))
					.andExpect(status().isConflict());
				assertEquals(0,
						jdbc.queryForObject("SELECT count(*) FROM rp_papers WHERE topic_id = ?", Integer.class, topic));
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}

	@Test
	void multiChunkResumeAcceptsOutOfOrderPartsAndCleansExpiredPayloads() {
		transaction.executeWithoutResult(tx -> {
			tx.setRollbackOnly();
			try {
				UUID topic = topic();
				byte[] bytes = new byte[4 * 1024 * 1024 + 5];
				java.util.Arrays.fill(bytes, (byte) 'a');
				String upload = create(topic, bytes.length, hash(bytes));
				mvc.perform(
						put("/api/rp/upload-sessions/" + upload + "/chunks/1").contentType("application/octet-stream")
							.content(java.util.Arrays.copyOfRange(bytes, 4 * 1024 * 1024, bytes.length)))
					.andExpect(status().isOk());
				mvc.perform(post("/api/rp/upload-sessions/" + upload + "/complete")).andExpect(status().isConflict());
				mvc.perform(get("/api/rp/upload-sessions/" + upload))
					.andExpect(jsonPath("$.received[0].chunk_index").value(1));
				putChunk(upload, java.util.Arrays.copyOf(bytes, 4 * 1024 * 1024));
				mvc.perform(post("/api/rp/upload-sessions/" + upload + "/complete")).andExpect(status().isAccepted());
				verify(objects).put(anyString(), eq(bytes));
				clearInvocations(objects);
				String expired = create(topic, 5, hash("hello".getBytes()));
				putChunk(expired, "hello".getBytes());
				jdbc.update("UPDATE rp_upload_sessions SET updated_at = now() - interval '25 hours' WHERE id = ?::uuid",
						expired);
				controller.cleanupExpiredUploads();
				mvc.perform(get("/api/rp/upload-sessions/" + expired)).andExpect(status().isNotFound());
				assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM rp_upload_chunks WHERE session_id = ?::uuid",
						Integer.class, expired));
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}

	private UUID topic() {
		UUID topic = UUID.randomUUID();
		jdbc.update("INSERT INTO rp_topics (id, name) VALUES (?, 'upload integration test')", topic);
		return topic;
	}

	private String create(UUID topic, int size, String hash) throws Exception {
		String body = JSON
			.writeValueAsString(java.util.Map.of("filename", "test.TXT", "total_size", size, "sha256", hash));
		return JSON.readTree(mvc
			.perform(post("/api/rp/topics/" + topic + "/upload-sessions").contentType("application/json").content(body))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString()).get("upload_id").asText();
	}

	private String hash(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

	private void putChunk(String upload, byte[] bytes) throws Exception {
		mvc.perform(put("/api/rp/upload-sessions/" + upload + "/chunks/0").contentType("application/octet-stream")
			.content(bytes)).andExpect(status().isOk());
	}

}
