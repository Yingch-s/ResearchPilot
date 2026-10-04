/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.alibaba.cloud.ai.example.deepresearch.researchpilot.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeSettingsServiceTest {

	@Test
	void savesNonSecretSettingsAndMasksSecrets() throws Exception {
		Path directory = Files.createTempDirectory("rp-settings-");
		Path file = directory.resolve("runtime.properties");
		RuntimeSettingsService service = new RuntimeSettingsService(file.toString());

		var saved = service.update(new RuntimeSettingsService.SettingsUpdate("https://api.example.test/v1", "model-x",
				"secret-key", false, "tavily-key", false, "http://sidecar:8100"));

		assertEquals("https://api.example.test/v1", saved.llm_base_url());
		assertEquals("model-x", saved.llm_model());
		assertTrue(saved.llm_api_key_configured());
		assertTrue(saved.tavily_api_key_configured());
		assertTrue(saved.llm_api_key_masked().endsWith("-key"));
		assertFalse(saved.llm_api_key_masked().contains("secret-key"));
		assertTrue(Files.exists(file));
	}

	@Test
	void blankSecretPreservesExistingAndClearRemovesIt() throws Exception {
		Path file = Files.createTempFile("rp-settings-", ".properties");
		RuntimeSettingsService service = new RuntimeSettingsService(file.toString());
		service.update(new RuntimeSettingsService.SettingsUpdate(null, null, "first", false, null, false, null));
		service.update(new RuntimeSettingsService.SettingsUpdate(null, null, "", false, null, false, null));
		assertTrue(service.view().llm_api_key_configured());
		service.update(new RuntimeSettingsService.SettingsUpdate(null, null, null, true, null, false, null));
		assertFalse(service.view().llm_api_key_configured());
	}

}
