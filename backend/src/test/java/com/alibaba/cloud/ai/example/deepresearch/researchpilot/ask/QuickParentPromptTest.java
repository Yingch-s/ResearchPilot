package com.alibaba.cloud.ai.example.deepresearch.researchpilot.ask;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.retrieve.RpRetrievalService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuickParentPromptTest {

	@Test
	void quickAnswerReceivesParentLimitationsWithoutChangingCitationExcerpt() throws Exception {
		var retrieval = mock(RpRetrievalService.class);
		var client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
		var spec = client.prompt();
		when(spec.user(anyString()).call().content()).thenReturn("{\"intent\":\"material\"}", "answer [1]");
		var child = new RpRetrievalService.Candidate("child", "paper", "parent", "Original quote", "Results", 2, "text",
				1, List.of());
		when(retrieval.hybridRetrieve(eq("topic"), eq(List.of("paper")), anyString(), anyInt(), anyBoolean()))
			.thenReturn(List.of(child));
		when(retrieval.expandParents(List.of("parent"), "topic", List.of("paper")))
			.thenReturn(Map.of("parent", "Only valid for English benchmarks"));
		var service = new RpAskService(retrieval, client, mock(RpWebSearchService.class));
		var result = service.ask("topic", List.of("paper"), "question", false, false);
		var captured = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(spec, atLeastOnce()).user(captured.capture());
		String prompt = captured.getAllValues()
			.stream()
			.filter(value -> value != null && value.contains("Original quote"))
			.findFirst()
			.orElseThrow();
		assertTrue(prompt.contains("Only valid for English benchmarks"));
		assertTrue(prompt.contains("引用仍使用上面的原文编号"));
		assertEquals("Original quote", result.citations().get(0).text());
		assertEquals("child", result.citations().get(0).chunkId());
	}

}
