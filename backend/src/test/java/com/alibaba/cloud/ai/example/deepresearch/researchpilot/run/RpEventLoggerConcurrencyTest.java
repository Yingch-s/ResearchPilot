package com.alibaba.cloud.ai.example.deepresearch.researchpilot.run;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class RpEventLoggerConcurrencyTest {

	@Test
	void concurrentWritersProduceUniqueOrderedSequences() throws Exception {
		Path directory = Files.createTempDirectory("rp-events-");
		RpEventLogger logger = new RpEventLogger(directory.toString());
		UUID runId = UUID.randomUUID();
		int writers = 12;
		var pool = Executors.newFixedThreadPool(writers);
		var ready = new CountDownLatch(writers);
		var start = new CountDownLatch(1);
		for (int i = 0; i < writers; i++) {
			pool.submit(() -> {
				ready.countDown();
				start.await();
				logger.log(runId, null, null, RpEventType.STEP_STARTED, "test", null, java.util.Map.of());
				return null;
			});
		}
		ready.await();
		start.countDown();
		pool.shutdown();
		while (!pool.isTerminated())
			Thread.sleep(10);

		var events = logger.after(runId, 0, 100);
		assertThat(events).hasSize(writers);
		assertThat(events).extracting(event -> ((Number) event.get("sequence")).longValue())
			.containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L);
	}

}
