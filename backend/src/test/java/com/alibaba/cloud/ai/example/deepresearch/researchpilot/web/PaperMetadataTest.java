package com.alibaba.cloud.ai.example.deepresearch.researchpilot.web;

import org.junit.jupiter.api.Test;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.store.RpRepository;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaperMetadataTest {

	@Test
	void rejectsNonObjectMetadataAndInvalidTagsOrYear() {
		var repository = mock(RpRepository.class);
		var controller = new RpController(repository, null, null, null, null, null);
		for (String json : java.util.List.of("[]", "null", "42", "{\"tags\":\"tag\"}", "{\"year\":1499}",
				"{\"year\":\"2026\"}", "{\"authors\":[]}")) {
			assertEquals(400,
					controller.updatePaper("paper", new RpController.PaperUpdate("Title", json))
						.getStatusCode()
						.value());
		}
		verifyNoInteractions(repository);
	}

	@Test
	void manualMetadataIsStampedAndUnknownFieldsAreRetained() throws Exception {
		var repository = mock(RpRepository.class);
		when(repository.updatePaper(eq("paper"), eq("Title"), anyString())).thenReturn(true);
		var controller = new RpController(repository, null, null, null, null, null);
		assertEquals(200, controller.updatePaper("paper", new RpController.PaperUpdate(" Title ",
				"{\"tags\":[\"跨语言\"],\"year\":2026,\"automatic\":{\"title\":\"Detected\"},\"provenance\":\"AUTOMATIC\"}"))
			.getStatusCode()
			.value());
		var payload = ArgumentCaptor.forClass(String.class);
		verify(repository).updatePaper(eq("paper"), eq("Title"), payload.capture());
		var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload.getValue());
		assertEquals("USER_CONFIRMED", json.get("provenance").asText());
		assertNotNull(json.get("confirmed_at"));
		assertEquals("Detected", json.get("automatic").get("title").asText());
	}

}
