package com.alibaba.cloud.ai.example.deepresearch.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportServiceContractTest {

	@Test
	void markdownAndPdfAreSupportedWithStablePaths() {
		var service = new ExportService("target/export-contract");
		assertTrue(service.isSupportedFormat("markdown"));
		assertTrue(service.isSupportedFormat("md"));
		assertTrue(service.isSupportedFormat("pdf"));
		assertFalse(service.isSupportedFormat("docx"));
		assertEquals(service.getReportFilePath("thread-1", "md"), service.getReportFilePath("thread-1", "markdown"));
		assertTrue(service.getReportFilePath("thread-1", "pdf").endsWith(".pdf"));
	}

}
