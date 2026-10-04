package com.alibaba.cloud.ai.example.deepresearch.researchpilot.web;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract-level checks for the JSON envelope consumed by the frontend. */
class RpErrorHandlerTest {

	private final RpErrorHandler handler = new RpErrorHandler();

	@Test
	void invalidRequestsHaveStableNonRetryableEnvelope() {
		var response = handler.invalid(new IllegalArgumentException("bad input"));
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertEquals("INVALID_REQUEST", response.getBody().get("code"));
		assertEquals("bad input", response.getBody().get("message"));
		assertFalse((Boolean) response.getBody().get("retryable"));
	}

	@Test
	void statusAndDatabaseFailuresPreserveRetrySemantics() {
		var rejected = handler.status(new ResponseStatusException(HttpStatus.CONFLICT, "already exists"));
		assertEquals(HttpStatus.CONFLICT, rejected.getStatusCode());
		assertEquals("REQUEST_REJECTED", rejected.getBody().get("code"));
		assertFalse((Boolean) rejected.getBody().get("retryable"));

		var unavailable = handler.database(new DataAccessResourceFailureException("connection lost"));
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, unavailable.getStatusCode());
		assertEquals("DATABASE_UNAVAILABLE", unavailable.getBody().get("code"));
		assertTrue((Boolean) unavailable.getBody().get("retryable"));
	}

	@Test
	void unexpectedFailuresDoNotExposeInternalDetails() {
		var response = handler.unexpected(new IllegalStateException("secret stack detail"));
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
		assertEquals("INTERNAL_ERROR", response.getBody().get("code"));
		assertEquals("服务暂时不可用，请稍后重试", response.getBody().get("message"));
		assertTrue((Boolean) response.getBody().get("retryable"));
	}

}
