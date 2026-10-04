package com.alibaba.cloud.ai.example.deepresearch.researchpilot.run;

import com.alibaba.cloud.ai.example.deepresearch.TestDatabase;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class RunRequirementsIT {

	private static JdbcTemplate jdbc;

	private static TransactionTemplate transaction;

	private static RpRunService service;

	@BeforeAll
	static void setup() {
		var ds = new DriverManagerDataSource(TestDatabase.url(), "researchpilot", "researchpilot");
		new ResourceDatabasePopulator(new ClassPathResource("rp_schema.sql")).execute(ds);
		jdbc = new JdbcTemplate(ds);
		var manager = new DataSourceTransactionManager(ds);
		transaction = new TransactionTemplate(manager);
		var proxy = new ProxyFactory(new RpRunService(jdbc, new RpEventLogger("./target/rp-requirements-test")));
		proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
		service = (RpRunService) proxy.getProxy();
	}

	private UUID run() {
		UUID id = service.createRun("requirements-" + UUID.randomUUID(), "test", null, "original question");
		service.transition(id, RunStatus.CREATED, RunStatus.PLANNING, null);
		service.transition(id, RunStatus.PLANNING, RunStatus.RESEARCHING, null);
		service.initSteps(id, List.of("first", "second", "third"));
		return id;
	}

	@Test
	void appliedRequirementsSurviveRecoveryAndAreNotRepeatedInContext() {
		transaction.executeWithoutResult(tx -> {
			tx.setRollbackOnly();
			UUID id = run();
			UUID requirement = service.addRequirement(id, "Only training-free methods");
			String first = service.claimStepWithRequirements(id, 0, UUID.randomUUID(), null, "original question")
				.orElseThrow();
			assertEquals("APPLIED", jdbc.queryForObject("SELECT status FROM rp_run_requirements WHERE id = ?",
					String.class, requirement));
			String recovered = service.claimStepWithRequirements(id, 1, UUID.randomUUID(), null, "original question")
				.orElseThrow();
			assertEquals(first, recovered);
			service.addRequirement(id, "Include Chinese sources");
			String next = service.claimStepWithRequirements(id, 2, UUID.randomUUID(), null, "original question")
				.orElseThrow();
			assertTrue(next.contains("Only training-free methods"));
			assertTrue(next.contains("Include Chinese sources"));
			assertEquals("original question", service.loadRun(id).get("query"));
		});
	}

	@Test
	void pausedOrUnclaimedStepsDoNotConsumeRequirements() {
		transaction.executeWithoutResult(tx -> {
			tx.setRollbackOnly();
			UUID id = run();
			UUID requirement = service.addRequirement(id, "Preserve scope");
			service.requestPause(id);
			assertTrue(service.claimStepWithRequirements(id, 0, UUID.randomUUID(), null, "question").isEmpty());
			assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM rp_run_requirements WHERE id = ?",
					String.class, requirement));
			service.clearPause(id);
			assertTrue(service.claimStepWithRequirements(id, 99, UUID.randomUUID(), null, "question").isEmpty());
			assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM rp_run_requirements WHERE id = ?",
					String.class, requirement));
		});
	}

	@Test
	void stoppedAndMissingRunsRejectNewRequirements() {
		transaction.executeWithoutResult(tx -> {
			tx.setRollbackOnly();
			UUID id = run();
			jdbc.update("UPDATE rp_runs SET budget_reason = 'stop_requested' WHERE id = ?", id);
			assertEquals(409,
					assertThrows(ResponseStatusException.class, () -> service.addRequirement(id, "late"))
						.getStatusCode()
						.value());
			assertEquals(404, assertThrows(ResponseStatusException.class,
					() -> service.addRequirement(UUID.randomUUID(), "missing"))
				.getStatusCode()
				.value());
			assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM rp_run_requirements WHERE run_id = ?",
					Integer.class, id));
		});
	}

}
