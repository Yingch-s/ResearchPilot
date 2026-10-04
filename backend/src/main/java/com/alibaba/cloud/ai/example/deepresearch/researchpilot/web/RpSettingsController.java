/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.alibaba.cloud.ai.example.deepresearch.researchpilot.web;

import com.alibaba.cloud.ai.example.deepresearch.researchpilot.config.RuntimeSettingsService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Local settings API used by the packaged Settings page. */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/rp/settings")
public class RpSettingsController {

	private final RuntimeSettingsService settings;

	public RpSettingsController(RuntimeSettingsService settings) {
		this.settings = settings;
	}

	@GetMapping
	public RuntimeSettingsService.SettingsView get() {
		return settings.view();
	}

	@PutMapping
	public RuntimeSettingsService.SettingsView update(@RequestBody RuntimeSettingsService.SettingsUpdate request) {
		return settings.update(request);
	}

}
