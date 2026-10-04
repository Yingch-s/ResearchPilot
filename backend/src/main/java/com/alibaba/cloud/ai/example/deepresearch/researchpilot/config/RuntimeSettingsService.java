/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.alibaba.cloud.ai.example.deepresearch.researchpilot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Properties;

/**
 * Local, single-user runtime configuration for a packaged ResearchPilot instance.
 *
 * <p>
 * Secrets are never returned from the API. Values are written to a separate properties
 * file so a release package can be configured from the web UI without editing source or
 * committing an .env file. Model clients are created during application startup, so a
 * saved change takes effect after a backend restart.
 * </p>
 */
@Service
public class RuntimeSettingsService {

	private static final String LLM_BASE_URL = "researchpilot.model.base-url";

	private static final String LLM_MODEL = "researchpilot.model.model-name";

	private static final String LLM_API_KEY = "researchpilot.model.api-key";

	private static final String TAVILY_API_KEY = "spring.ai.alibaba.toolcalling.tavilysearch.api-key";

	private static final String SIDECAR_BASE_URL = "researchpilot.kb.sidecar-base-url";

	private final Path configPath;

	public RuntimeSettingsService(@Value("${RP_RUNTIME_CONFIG:./data/runtime.properties}") String configPath) {
		this.configPath = Path.of(configPath).toAbsolutePath().normalize();
	}

	public synchronized SettingsView view() {
		Properties properties = load();
		String llmKey = secretValue(properties, LLM_API_KEY, "LLM_API_KEY");
		String tavilyKey = secretValue(properties, TAVILY_API_KEY, "TAVILY_API_KEY");
		return new SettingsView(value(properties, LLM_BASE_URL, "LLM_BASE_URL", "https://api.deepseek.com"),
				value(properties, LLM_MODEL, "LLM_MODEL", "deepseek-flash"), mask(llmKey), configured(llmKey),
				mask(tavilyKey), configured(tavilyKey),
				value(properties, SIDECAR_BASE_URL, "RP_SIDECAR_URL", "http://localhost:8100"), true);
	}

	public synchronized SettingsView update(SettingsUpdate update) {
		Objects.requireNonNull(update, "settings");
		Properties properties = load();
		if (update.llm_base_url() != null) {
			String baseUrl = update.llm_base_url().trim();
			if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://"))
				throw new IllegalArgumentException("模型 API 地址必须以 http:// 或 https:// 开头");
			if (baseUrl.length() > 500)
				throw new IllegalArgumentException("模型 API 地址过长");
			properties.setProperty(LLM_BASE_URL, baseUrl);
		}
		if (update.llm_model() != null) {
			String model = update.llm_model().trim();
			if (model.isBlank() || model.length() > 160)
				throw new IllegalArgumentException("模型名称不能为空且不能超过 160 个字符");
			properties.setProperty(LLM_MODEL, model);
		}
		setSecret(properties, LLM_API_KEY, update.llm_api_key(), update.clear_llm_api_key());
		setSecret(properties, TAVILY_API_KEY, update.tavily_api_key(), update.clear_tavily_api_key());
		if (update.sidecar_base_url() != null) {
			String sidecar = update.sidecar_base_url().trim();
			if (!sidecar.startsWith("http://") && !sidecar.startsWith("https://"))
				throw new IllegalArgumentException("解析服务地址必须以 http:// 或 https:// 开头");
			properties.setProperty(SIDECAR_BASE_URL, sidecar);
		}
		store(properties);
		return view();
	}

	private void setSecret(Properties properties, String key, String value, Boolean clear) {
		if (Boolean.TRUE.equals(clear)) {
			properties.remove(key);
		}
		else if (value != null && !value.isBlank()) {
			if (value.length() > 1000)
				throw new IllegalArgumentException("密钥过长");
			properties.setProperty(key, value.trim());
		}
	}

	private Properties load() {
		Properties properties = new Properties();
		if (!Files.isRegularFile(configPath))
			return properties;
		try (InputStream input = Files.newInputStream(configPath)) {
			properties.load(input);
			return properties;
		}
		catch (IOException e) {
			throw new IllegalStateException("无法读取运行配置", e);
		}
	}

	private void store(Properties properties) {
		try {
			Path parent = configPath.getParent();
			if (parent != null)
				Files.createDirectories(parent);
			Path temporary = configPath.resolveSibling(configPath.getFileName() + ".tmp");
			try (OutputStream output = Files.newOutputStream(temporary)) {
				properties.store(output, "ResearchPilot web settings; keep this file private");
			}
			try {
				Files.move(temporary, configPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			}
			catch (java.nio.file.AtomicMoveNotSupportedException e) {
				Files.move(temporary, configPath, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException e) {
			throw new IllegalStateException("无法保存运行配置", e);
		}
	}

	private String value(Properties properties, String key, String environmentKey, String fallback) {
		String value = properties.getProperty(key);
		if (value != null && !value.isBlank())
			return value;
		value = System.getenv(environmentKey);
		return value == null || value.isBlank() ? fallback : value;
	}

	private String secretValue(Properties properties, String key, String environmentKey) {
		String value = properties.getProperty(key);
		if (configured(value))
			return value;
		return System.getenv(environmentKey);
	}

	private boolean configured(String value) {
		return value != null && !value.isBlank();
	}

	private String mask(String value) {
		if (!configured(value))
			return "";
		if (value.length() <= 4)
			return "••••";
		return "••••••••" + value.substring(value.length() - 4);
	}

	public record SettingsUpdate(String llm_base_url, String llm_model, String llm_api_key, Boolean clear_llm_api_key,
			String tavily_api_key, Boolean clear_tavily_api_key, String sidecar_base_url) {
	}

	public record SettingsView(String llm_base_url, String llm_model, String llm_api_key_masked,
			boolean llm_api_key_configured, String tavily_api_key_masked, boolean tavily_api_key_configured,
			String sidecar_base_url, boolean restart_required) {
	}

}
