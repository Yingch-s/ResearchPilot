package com.alibaba.cloud.ai.example.deepresearch.researchpilot.ingest;

import com.alibaba.cloud.ai.example.deepresearch.researchpilot.ResearchPilotConfig;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.store.RpObjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddingBatchCheckpointTest {

	@TempDir
	Path directory;

	@Test
	void completedBatchIsReusedButChangedModelOrTextIsNot() throws Exception {
		var cache = cache();
		String version = UUID.randomUUID().toString();
		var calls = new AtomicInteger();
		java.util.function.Supplier<List<double[]>> compute = () -> {
			calls.incrementAndGet();
			return List.of(new double[] { 1, 2 });
		};
		cache.getOrCompute(version, "model:revision-1", 2, List.of("第一批"), compute);
		assertArrayEquals(new double[] { 1, 2 },
				cache.getOrCompute(version, "model:revision-1", 2, List.of("第一批"), compute).get(0));
		assertEquals(1, calls.get());
		cache.getOrCompute(version, "model:revision-2", 2, List.of("第一批"), compute);
		cache.getOrCompute(version, "model:revision-1", 2, List.of("不同文本"), compute);
		assertEquals(3, calls.get());
	}

	@Test
	void unavailableModelIdentityDisablesReuseAndFailedBatchIsNotSaved() throws Exception {
		var cache = cache();
		String version = UUID.randomUUID().toString();
		var calls = new AtomicInteger();
		for (int i = 0; i < 2; i++)
			cache.getOrCompute(version, null, 2, List.of("text"), () -> {
				calls.incrementAndGet();
				return List.of(new double[] { 1, 2 });
			});
		assertEquals(2, calls.get());
		assertThrows(IllegalStateException.class, () -> cache.getOrCompute(version, "model:rev", 2, List.of("text"),
				() -> List.of(new double[] { Double.NaN, 2 })));
		assertThrows(IllegalStateException.class,
				() -> cache.getOrCompute(version, "model:rev", 2, List.of("text"), () -> List.of(new double[] { 1 })));
		assertArrayEquals(new double[] { 1, 2 },
				cache.getOrCompute(version, "model:rev", 2, List.of("text"), () -> List.of(new double[] { 1, 2 }))
					.get(0));
	}

	@Test
	void corruptFileIsRecomputed() throws Exception {
		var cache = cache();
		String version = UUID.randomUUID().toString();
		cache.getOrCompute(version, "model:rev", 2, List.of("text"), () -> List.of(new double[] { 1, 2 }));
		try (var paths = java.nio.file.Files.walk(directory)) {
			java.nio.file.Files.writeString(paths.filter(p -> p.toString().endsWith(".json")).findFirst().orElseThrow(),
					"broken");
		}
		assertArrayEquals(new double[] { 3, 4 },
				cache.getOrCompute(version, "model:rev", 2, List.of("text"), () -> List.of(new double[] { 3, 4 }))
					.get(0));
	}

	private EmbeddingBatchCheckpoint cache() {
		return new EmbeddingBatchCheckpoint(
				new RpObjectStore(new ResearchPilotConfig.KbProperties(directory.toString(), null, null, 2, 32)));
	}

}
