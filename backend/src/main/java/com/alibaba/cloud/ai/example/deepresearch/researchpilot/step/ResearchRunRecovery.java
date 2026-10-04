package com.alibaba.cloud.ai.example.deepresearch.researchpilot.step;

import com.alibaba.cloud.ai.example.deepresearch.researchpilot.run.RpRunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Reclaims durable ResearchPilot runs after a process restart. */
@Component
public class ResearchRunRecovery {

	private static final Logger logger = LoggerFactory.getLogger(ResearchRunRecovery.class);

	private final RpRunService runService;

	private final ResearchScheduler scheduler;

	private final ExecutorService executor = Executors.newFixedThreadPool(2);

	public ResearchRunRecovery(RpRunService runService, ResearchScheduler scheduler) {
		this.runService = runService;
		this.scheduler = scheduler;
	}

	@Scheduled(initialDelayString = "${researchpilot.recovery.initial-delay-ms:5000}",
			fixedDelayString = "${researchpilot.recovery.interval-ms:10000}")
	public void recover() {
		for (var row : runService.recoveryScan()) {
			Object value = row.get("id");
			if (value == null) {
				continue;
			}
			UUID runId = value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
			executor.submit(() -> {
				try {
					scheduler.startOrResumeRun(runId);
				}
				catch (Exception e) {
					logger.error("恢复 ResearchPilot Run 失败: {}", runId, e);
				}
			});
		}
	}

}
