package com.alibaba.cloud.ai.example.deepresearch;

/** Test database endpoint can be changed when tests run inside a container. */
public final class TestDatabase {

	private TestDatabase() {
	}

	public static String url() {
		return System.getProperty("rp.test.db.url",
				System.getenv().getOrDefault("RP_TEST_DB_URL", "jdbc:postgresql://localhost:5433/researchpilot"));
	}

	public static String esHost() {
		return System.getProperty("rp.es.host", System.getenv().getOrDefault("RP_ES_HOST", "localhost"));
	}

}
