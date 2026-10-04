package com.alibaba.cloud.ai.example.deepresearch.researchpilot.run;

import com.alibaba.cloud.ai.example.deepresearch.TestDatabase;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RunListingIT {

	private static JdbcTemplate jdbc;

	private static RunListingService listing;

	@BeforeAll
	static void setup() {
		var ds = new DriverManagerDataSource(TestDatabase.url(), "researchpilot", "researchpilot");
		try (var ignored = ds.getConnection()) {
		}
		catch (Exception e) {
			Assumptions.assumeTrue(false, "PostgreSQL unavailable");
		}
		jdbc = new JdbcTemplate(ds);
		listing = new RunListingService(jdbc);
	}

	@Test
	void topicPageHasStableCursorAndDoesNotLeakOtherTopics() {
		UUID topic = UUID.randomUUID();
		UUID other = UUID.randomUUID();
		jdbc.update("INSERT INTO rp_topics (id, name) VALUES (?, 'list-test'), (?, 'other-list-test')", topic, other);
		try {
			for (int i = 0; i < 3; i++) {
				UUID id = UUID.randomUUID();
				jdbc.update(
						"INSERT INTO rp_runs (id, thread_id, topic_id, query, status, created_at) VALUES (?, ?, ?, ?, 'COMPLETED', ?::timestamptz)",
						id, "list-" + id, topic, "q" + i, "2026-01-01T00:00:0" + i + "Z");
			}
			UUID foreign = UUID.randomUUID();
			jdbc.update(
					"INSERT INTO rp_runs (id, thread_id, topic_id, query, status) VALUES (?, ?, ?, 'foreign', 'COMPLETED')",
					foreign, "list-" + foreign, other);
			var first = listing.page(topic, 2, null, null, false);
			assertEquals(2, ((java.util.List<?>) first.get("items")).size());
			assertTrue((Boolean) first.get("has_more"));
			@SuppressWarnings("unchecked")
			var cursor = (java.util.Map<String, Object>) first.get("next_cursor");
			var second = listing.page(topic, 2, java.time.OffsetDateTime.parse(cursor.get("before_time").toString()),
					UUID.fromString(cursor.get("before_id").toString()), false);
			assertEquals(1, ((java.util.List<?>) second.get("items")).size());
			assertFalse(((java.util.List<?>) second.get("items")).stream()
				.anyMatch(row -> ((java.util.Map<?, ?>) row).get("topic_id").toString().equals(other.toString())));
		}
		finally {
			jdbc.update("DELETE FROM rp_runs WHERE topic_id IN (?, ?)", topic, other);
			jdbc.update("DELETE FROM rp_topics WHERE id IN (?, ?)", topic, other);
		}
	}

	@Test
	void equalTimestampsUseIdTieBreakerAndNewInsertDoesNotShiftNextPage() {
		UUID topic = UUID.randomUUID();
		jdbc.update("INSERT INTO rp_topics (id, name) VALUES (?, 'ties-list-test')", topic);
		try {
			for (int i = 1; i <= 4; i++) {
				UUID id = UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", i));
				jdbc.update(
						"INSERT INTO rp_runs (id, thread_id, topic_id, query, status, created_at) VALUES (?, ?, ?, ?, 'COMPLETED', '2026-01-01'::timestamptz)",
						id, "ties-" + id, topic, "q" + i);
			}
			var first = listing.page(topic, 2, null, null, false);
			@SuppressWarnings("unchecked")
			var cursor = (java.util.Map<String, Object>) first.get("next_cursor");
			UUID newId = UUID.randomUUID();
			jdbc.update(
					"INSERT INTO rp_runs (id, thread_id, topic_id, query, status) VALUES (?, ?, ?, 'new', 'RESEARCHING')",
					newId, "list-new-" + newId, topic);
			var second = listing.page(topic, 2, java.time.OffsetDateTime.parse(cursor.get("before_time").toString()),
					UUID.fromString(cursor.get("before_id").toString()), false);
			@SuppressWarnings("unchecked")
			var rows = (java.util.List<java.util.Map<String, Object>>) second.get("items");
			assertEquals(java.util.List.of("q2", "q1"), rows.stream().map(r -> r.get("query")).toList());
			assertFalse((Boolean) second.get("has_more"));
			assertEquals(1, ((java.util.List<?>) listing.page(topic, 20, null, null, true).get("items")).size());
		}
		finally {
			jdbc.update("DELETE FROM rp_runs WHERE topic_id = ?", topic);
			jdbc.update("DELETE FROM rp_topics WHERE id = ?", topic);
		}
	}

	@Test
	void invalidLimitsAndIncompleteCursorsAreRejected() {
		assertThrows(org.springframework.web.server.ResponseStatusException.class,
				() -> listing.page(null, 0, null, null, false));
		assertThrows(org.springframework.web.server.ResponseStatusException.class,
				() -> listing.page(null, 101, null, null, false));
		assertThrows(org.springframework.web.server.ResponseStatusException.class,
				() -> listing.page(null, 20, java.time.OffsetDateTime.now(), null, false));
	}

}
