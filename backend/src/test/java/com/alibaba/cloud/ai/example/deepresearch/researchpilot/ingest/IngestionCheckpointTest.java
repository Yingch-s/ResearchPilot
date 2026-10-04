package com.alibaba.cloud.ai.example.deepresearch.researchpilot.ingest;

import com.alibaba.cloud.ai.example.deepresearch.researchpilot.ResearchPilotConfig;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.sidecar.SidecarClient;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.store.RpObjectStore;
import com.alibaba.cloud.ai.example.deepresearch.researchpilot.store.RpRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IngestionCheckpointTest {

	@TempDir
	Path directory;

	@Test
	void embeddingFailureRetryReusesPublishedParse() throws Exception {
		runRetry(false);
	}

	@Test
	void indexFailureRetryReusesPublishedParse() throws Exception {
		runRetry(true);
	}

	@Test
	void newWorkerReusesCompletedBatchAfterLaterBatchFails() throws Exception {
		var repo = mock(RpRepository.class);
		var sidecar = mock(SidecarClient.class);
		var indexer = mock(RpEsIndexer.class);
		var props = new ResearchPilotConfig.KbProperties(directory.toString(), null, null, 2, 1);
		var store = new RpObjectStore(props);
		String version = UUID.randomUUID().toString();
		String job = UUID.randomUUID().toString();
		store.put("paper/test.pdf", new byte[] { 1, 2 });
		when(repo.claimQueuedJob(anyString(), eq(600))).thenReturn(
				Map.of("id", job, "document_version_id", version, "attempts", 1, "max_attempts", 3),
				Map.of("id", job, "document_version_id", version, "attempts", 2, "max_attempts", 3));
		when(repo.getVersionObjectKey(version))
			.thenReturn(Optional.of(new RpRepository.VersionRef(UUID.fromString(version), "paper/test.pdf")));
		when(repo.getScope(version)).thenReturn(new RpRepository.ChunkScope("topic", "paper"));
		var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		var parsed = mapper.createObjectNode();
		parsed.put("parser_version", "test-1");
		parsed.putArray("quality_flags");
		var blocks = parsed.putArray("blocks");
		for (int i = 1; i <= 3; i++) {
			var block = blocks.addObject();
			block.put("text", "Finding " + i);
			block.put("section_path", "Section " + i);
			block.put("content_type", "text");
			block.put("page", i);
		}
		when(sidecar.parse(any(), eq(version), anyString())).thenReturn(parsed);
		AtomicReference<String> checkpoint = new AtomicReference<>();
		when(repo.parseCheckpoint(version)).thenAnswer(invocation -> checkpoint.get());
		doAnswer(invocation -> {
			checkpoint.set(invocation.getArgument(1));
			return null;
		}).when(repo).saveParseCheckpoint(eq(version), anyString(), eq("test-1"), any());
		when(sidecar.embeddingIdentity()).thenReturn("model:revision-1");
		when(sidecar.embed(List.of("Finding 1\n"))).thenReturn(List.of(new double[] { 1, 2 }));
		when(sidecar.embed(List.of("Finding 2\n"))).thenThrow(new IllegalStateException("embedding unavailable"))
			.thenReturn(List.of(new double[] { 3, 4 }));
		when(sidecar.embed(List.of("Finding 3\n"))).thenReturn(List.of(new double[] { 5, 6 }));
		when(indexer.countByVersion(version)).thenReturn(3L);
		new RpIngestionWorker(repo, store, sidecar, new RpChunker(), indexer, props).poll();
		verify(indexer, never()).indexChunks(anyString(), anyString(), anyString(), anyList());
		// Reconstruct both worker and store to exercise durable files, not an in-memory
		// cache.
		new RpIngestionWorker(repo, new RpObjectStore(props), sidecar, new RpChunker(), indexer, props).poll();
		verify(sidecar, times(1)).parse(any(), eq(version), anyString());
		verify(sidecar, times(1)).embed(List.of("Finding 1\n"));
		verify(sidecar, times(2)).embed(List.of("Finding 2\n"));
		verify(sidecar, times(1)).embed(List.of("Finding 3\n"));
		verify(repo).requeue(eq(job), eq(version), contains("unavailable"));
		verify(repo).finishJob(eq(job), eq(version), eq(true), eq("READY"), eq("READY"), isNull(), eq("test-1"),
				eq("normalized/" + version + ".json"), any(), eq(3));
	}

	private void runRetry(boolean failIndex) throws Exception {
		var repo = mock(RpRepository.class);
		var sidecar = mock(SidecarClient.class);
		var indexer = mock(RpEsIndexer.class);
		var props = new ResearchPilotConfig.KbProperties(directory.toString(), null, null, 2, 32);
		var store = new RpObjectStore(props);
		String version = UUID.randomUUID().toString();
		String job = UUID.randomUUID().toString();
		store.put("paper/test.pdf", new byte[] { 1, 2 });
		when(repo.claimQueuedJob(anyString(), eq(600))).thenReturn(
				Map.of("id", job, "document_version_id", version, "attempts", 1, "max_attempts", 3),
				Map.of("id", job, "document_version_id", version, "attempts", 2, "max_attempts", 3));
		when(repo.getVersionObjectKey(version))
			.thenReturn(Optional.of(new RpRepository.VersionRef(UUID.fromString(version), "paper/test.pdf")));
		when(repo.getScope(version)).thenReturn(new RpRepository.ChunkScope("topic", "paper"));
		var parsed = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
				"{\"parser_version\":\"test-1\",\"quality_flags\":[],\"blocks\":[{\"text\":\"A research finding\",\"content_type\":\"text\",\"page\":1}]}");
		when(sidecar.parse(any(), eq(version), anyString())).thenReturn(parsed);
		AtomicReference<String> checkpoint = new AtomicReference<>();
		when(repo.parseCheckpoint(version)).thenAnswer(invocation -> checkpoint.get());
		doAnswer(invocation -> {
			checkpoint.set(invocation.getArgument(1));
			return null;
		}).when(repo).saveParseCheckpoint(eq(version), anyString(), eq("test-1"), any());
		if (failIndex) {
			when(sidecar.embed(anyList())).thenReturn(List.of(new double[] { 1, 2 }));
			doThrow(new IllegalStateException("index unavailable")).doNothing()
				.when(indexer)
				.indexChunks(anyString(), anyString(), eq(version), anyList());
		}
		else {
			when(sidecar.embed(anyList())).thenThrow(new IllegalStateException("embedding unavailable"))
				.thenReturn(List.of(new double[] { 1, 2 }));
		}
		when(indexer.countByVersion(version)).thenReturn(1L);
		var worker = new RpIngestionWorker(repo, store, sidecar, new RpChunker(), indexer, props);
		worker.poll();
		worker.poll();
		verify(sidecar, times(1)).parse(any(), eq(version), anyString());
		verify(repo).requeue(eq(job), eq(version), contains("unavailable"));
		verify(repo).finishJob(eq(job), eq(version), eq(true), eq("READY"), eq("READY"), isNull(), eq("test-1"),
				eq("normalized/" + version + ".json"), any(), eq(1));
	}

}
