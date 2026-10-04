package com.alibaba.cloud.ai.example.deepresearch.researchpilot.step;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.retrieve.RpRetrievalService;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.ExecutionContext;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpEventLogger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResearchParentPromptTest {

	@Test
	void realResearchCallIncludesScopedParentBackgroundAndKeepsChildAnchor() throws Exception {
		var retrieval = mock(RpRetrievalService.class);
		var client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
		var promptSpec = client.prompt();
		when(promptSpec.user(anyString()).call().chatResponse()).thenReturn(null);
		var executor = new ResearchStepExecutor(retrieval, null, client, mock(RpEventLogger.class), null, 5);
		UUID topic = UUID.randomUUID();
		List<String> papers = List.of(UUID.randomUUID().toString());
		var ctx = new ExecutionContext(UUID.randomUUID(), topic, "step", UUID.randomUUID(), null, null, papers, false);
		var child = new RpRetrievalService.Candidate("child", papers.get(0), "parent", "Original child quote",
				"Methods", 3, "text", 1, List.of());
		when(retrieval.expandParents(List.of("parent"), topic.toString(), papers))
			.thenReturn(Map.of("parent", "Important exclusion condition"));
		var method = ResearchStepExecutor.class.getDeclaredMethod("research", ExecutionContext.class, String.class,
				String.class, String.class, List.class);
		method.setAccessible(true);
		method.invoke(executor, ctx, "question", "step", "description", List.of(child));
		var captured = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(promptSpec, atLeastOnce()).user(captured.capture());
		String prompt = captured.getAllValues()
			.stream()
			.filter(value -> value != null && value.contains("Original child quote"))
			.findFirst()
			.orElseThrow();
		assertTrue(prompt.contains("[KB-1]"));
		assertTrue(prompt.contains("Important exclusion condition"));
		assertTrue(prompt.contains("不得将背景当作已校验结论"));
		verify(retrieval).expandParents(List.of("parent"), topic.toString(), papers);
	}

}
