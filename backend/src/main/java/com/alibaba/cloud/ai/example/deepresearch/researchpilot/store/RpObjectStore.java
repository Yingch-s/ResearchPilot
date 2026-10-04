/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.example.deepresearch.researchpilot.store;

import com.alibaba.cloud.ai.example.deepresearch.researchpilot.ResearchPilotConfig;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ResearchPilot Phase B：本地文件对象存储（接口形态与 MinIO/S3 兼容，未来可换实现）。
 */
@Component
public class RpObjectStore {

	private final Path root;

	public RpObjectStore(ResearchPilotConfig.KbProperties props) {
		this.root = Path.of(props.objectStorePath());
		try {
			Files.createDirectories(root);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private Path resolve(String key) {
		if (Path.of(key).isAbsolute() || key.contains("..")) {
			throw new IllegalArgumentException("非法 object_key: " + key);
		}
		return root.resolve(key);
	}

	public void put(String key, byte[] data) {
		try {
			Path path = resolve(key);
			Files.createDirectories(path.getParent());
			Files.write(path, data);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public byte[] get(String key) {
		try {
			return Files.readAllBytes(resolve(key));
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public boolean exists(String key) {
		return Files.exists(resolve(key));
	}

}
