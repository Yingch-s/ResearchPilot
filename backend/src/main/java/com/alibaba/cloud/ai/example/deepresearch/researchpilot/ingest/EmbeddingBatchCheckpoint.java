package com.alibaba.cloud.ai.example.deepresearch.researchpilot.ingest;

import com.alibaba.cloud.ai.example.deepresearch.researchpilot.store.RpObjectStore;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

public class EmbeddingBatchCheckpoint {

	private final RpObjectStore store;

	private final ObjectMapper mapper = new ObjectMapper();

	public EmbeddingBatchCheckpoint(RpObjectStore store) {
		this.store = store;
	}

	public List<double[]> getOrCompute(String versionId, String identity, int dimension, List<String> texts,
			Supplier<List<double[]>> compute) throws Exception {
		String key = null;
		if (identity != null && !identity.isBlank()) {
			// Ordered fields keep the digest stable across JVM restarts.
			String input = mapper.writeValueAsString(List.of(identity, dimension, texts));
			String hash = HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
			key = "embedding-batches/" + java.util.UUID.fromString(versionId) + "/" + hash + ".json";
			if (store.exists(key)) {
				try {
					List<double[]> cached = mapper.readValue(store.get(key),
							mapper.getTypeFactory().constructCollectionType(List.class, double[].class));
					validate(cached, texts.size(), dimension);
					return cached;
				}
				catch (Exception ignored) {
					// Incomplete or corrupt batch files are recomputed, never treated as
					// valid vectors.
				}
			}
		}
		List<double[]> vectors = compute.get();
		validate(vectors, texts.size(), dimension);
		if (key != null) {
			store.put(key, mapper.writeValueAsBytes(vectors));
		}
		return vectors;
	}

	private void validate(List<double[]> vectors, int count, int dimension) {
		if (vectors == null || vectors.size() != count) {
			throw new IllegalStateException("Embedding batch count mismatch");
		}
		for (double[] vector : vectors) {
			if (vector == null || vector.length != dimension) {
				throw new IllegalStateException("Embedding dimension mismatch");
			}
			for (double number : vector) {
				if (!Double.isFinite(number)) {
					throw new IllegalStateException("Embedding contains non-finite value");
				}
			}
		}
	}

}
