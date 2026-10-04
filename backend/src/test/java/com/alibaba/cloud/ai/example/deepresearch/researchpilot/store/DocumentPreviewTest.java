package com.alibaba.cloud.ai.example.deepresearch.researchpilot.store;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentPreviewTest {

	@Test
	void returnsBoundedPreviewPagesAndEmptyFinalPage() throws Exception {
		var jdbc = mock(JdbcTemplate.class);
		var store = mock(RpObjectStore.class);
		UUID version = UUID.randomUUID();
		when(jdbc.queryForList(anyString(), eq(version)))
			.thenReturn(List.of(Map.of("normalized_object_key", "normalized/test.json")));
		var root = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
		var blocks = root.putArray("blocks");
		for (int i = 0; i < 205; i++)
			blocks.addObject().put("text", "block-" + i).put("page", i / 10);
		when(store.get("normalized/test.json"))
			.thenReturn(root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
		var service = new DocumentPreviewService(jdbc, store);
		var first = service.preview(version, 0);
		assertEquals(100, ((List<?>) first.get("blocks")).size());
		assertEquals(true, first.get("has_more"));
		var last = service.preview(version, 200);
		assertEquals(5, ((List<?>) last.get("blocks")).size());
		assertEquals(false, last.get("has_more"));
		assertEquals(List.of(), service.preview(version, Integer.MAX_VALUE).get("blocks"));
	}

	@Test
	void missingDocumentAndMissingParseHaveDistinctErrors() {
		var jdbc = mock(JdbcTemplate.class);
		var service = new DocumentPreviewService(jdbc, mock(RpObjectStore.class));
		UUID version = UUID.randomUUID();
		when(jdbc.queryForList(anyString(), eq(version))).thenReturn(List.of());
		assertEquals(404,
				assertThrows(ResponseStatusException.class, () -> service.preview(version, 0)).getStatusCode().value());
		when(jdbc.queryForList(anyString(), eq(version))).thenReturn(List.of(new java.util.HashMap<>()));
		assertEquals(409,
				assertThrows(ResponseStatusException.class, () -> service.preview(version, 0)).getStatusCode().value());
		assertEquals(400,
				assertThrows(ResponseStatusException.class, () -> service.preview(version, -1)).getStatusCode()
					.value());
	}

	@Test
	void originalDownloadUsesFormatMimeAndDoesNotServeActiveHtml() {
		assertEquals("application/pdf", DocumentPreviewService.mime("test.PDF"));
		assertEquals("image/jpeg", DocumentPreviewService.mime("test.jpeg"));
		assertEquals("application/octet-stream", DocumentPreviewService.mime("test.html"));
		assertTrue(DocumentPreviewService.mime("test.docx").contains("wordprocessingml"));
		assertTrue(DocumentPreviewService.mime("test.pptx").contains("presentationml"));
		assertEquals("text/plain; charset=utf-8", DocumentPreviewService.mime("test.md"));
	}

}
