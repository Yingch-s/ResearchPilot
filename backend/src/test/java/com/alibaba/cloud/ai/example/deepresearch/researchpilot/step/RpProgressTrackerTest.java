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

package com.alibaba.cloud.ai.example.deepresearch.researchpilot.step;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V0.4 H2.1：有效进展检测器纯逻辑测试（治理 §三.3 判定规则）。
 */
class RpProgressTrackerTest {

	private record Chunk(String chunkId) {
	}

	@Test
	void identicalQueriesShareSignature() {
		assertEquals(RpProgressTracker.signature("DPR 训练目标是什么？"), RpProgressTracker.signature("dpr  训练目标是什么"));
	}

	@Test
	void repeatedQueryIsNotNew() {
		RpProgressTracker t = new RpProgressTracker();
		assertTrue(t.isNewQuery("训练目标"));
		t.recordQuery("训练目标");
		assertFalse(t.isNewQuery("训练目标"), "重复查询无进展");
		assertFalse(t.isNewQuery("训练 目标."), "仅标点/空白差异视为同一查询");
	}

	@Test
	void novelChunkFilteringReturnsOnlyUnseen() {
		RpProgressTracker t = new RpProgressTracker();
		var first = t.filterNovel(List.of(new Chunk("c1"), new Chunk("c2")), Chunk::chunkId);
		assertEquals(2, first.size());
		// 重复检索相同段落 → 全部已见 → 空列表 = 无进展信号
		var second = t.filterNovel(List.of(new Chunk("c1"), new Chunk("c2")), Chunk::chunkId);
		assertTrue(second.isEmpty());
		// 部分新颖：只保留未见
		var third = t.filterNovel(List.of(new Chunk("c2"), new Chunk("c3")), Chunk::chunkId);
		assertEquals(1, third.size());
		assertEquals("c3", third.get(0).chunkId());
	}

	@Test
	void nullChunkIdIsIgnored() {
		RpProgressTracker t = new RpProgressTracker();
		var result = t.filterNovel(List.of(new Chunk(null), new Chunk("c1")), Chunk::chunkId);
		assertEquals(1, result.size());
	}

}
