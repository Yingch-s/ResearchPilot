package com.alibaba.cloud.ai.example.deepresearch.researchpilot.web;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** Stable error envelope consumed by the ResearchPilot frontend. */
@RestControllerAdvice(basePackages = "com.alibaba.cloud.ai.example.deepresearch.researchpilot.web")
public class RpErrorHandler {

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<Map<String, Object>> status(ResponseStatusException e) {
		HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
		return response(status == null ? HttpStatus.BAD_REQUEST : status, "REQUEST_REJECTED", e.getReason(), false);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, Object>> invalid(IllegalArgumentException e) {
		return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e.getMessage(), false);
	}

	@ExceptionHandler(DataAccessException.class)
	public ResponseEntity<Map<String, Object>> database(DataAccessException e) {
		return response(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE", "数据服务暂时不可用，请稍后重试", true);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> unexpected(Exception e) {
		// 未预期异常必须留栈（此前静默吞掉，500 无从定位）
		org.slf4j.LoggerFactory.getLogger(RpErrorHandler.class).error("未预期异常: {}", String.valueOf(e), e);
		return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务暂时不可用，请稍后重试", true);
	}

	private ResponseEntity<Map<String, Object>> response(HttpStatus status, String code, String message,
			boolean retryable) {
		return ResponseEntity.status(status)
			.body(Map.of("code", code, "message",
					message == null || message.isBlank() ? status.getReasonPhrase() : message, "retryable", retryable));
	}

}
