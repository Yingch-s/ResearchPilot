package com.alibaba.cloud.ai.example.deepresearch.researchpilot.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Runs independent workers with a hard concurrency, call, timeout and cancel boundary.
 */
public final class BoundedWorkerOrchestrator implements AutoCloseable {

	@FunctionalInterface
	public interface Worker {

		EvidencePacketMerger.EvidencePacket execute(BoundedWorkerContract contract) throws Exception;

	}

	private final ExecutorService executor;

	private final int maxCalls;

	public BoundedWorkerOrchestrator(int maxConcurrency, int maxCalls) {
		if (maxConcurrency < 1 || maxConcurrency > 16 || maxCalls < 1 || maxCalls > 100) {
			throw new IllegalArgumentException("并发数必须为 1-16，总调用预算必须为 1-100");
		}
		this.maxCalls = maxCalls;
		this.executor = Executors.newFixedThreadPool(maxConcurrency, runnable -> {
			Thread thread = new Thread(runnable, "rp-bounded-worker");
			thread.setDaemon(true);
			return thread;
		});
	}

	public Result execute(List<BoundedWorkerContract> contracts, Worker worker) {
		return execute(contracts, worker, () -> false);
	}

	/** Execute with a caller-owned stop signal (pause/stop/cancel). */
	public Result execute(List<BoundedWorkerContract> contracts, Worker worker, BooleanSupplier cancelled) {
		if (contracts == null || contracts.isEmpty()) {
			var merged = EvidencePacketMerger.merge(List.of());
			return new Result(List.of(), List.of(), 0, merged, List.of());
		}
		List<BoundedWorkerContract> accepted = new ArrayList<>();
		List<String> rejected = new ArrayList<>();
		int reserved = 0;
		for (BoundedWorkerContract contract : contracts) {
			if (contract == null || reserved + contract.maxCalls() > maxCalls) {
				if (contract != null) {
					rejected.add(contract.workerId());
				}
				continue;
			}
			accepted.add(contract);
			reserved += contract.maxCalls();
		}
		List<Future<EvidencePacketMerger.EvidencePacket>> futures = accepted.stream()
			.map(contract -> executor.submit(() -> worker.execute(contract)))
			.toList();
		long submittedAt = System.nanoTime();
		List<EvidencePacketMerger.EvidencePacket> packets = new ArrayList<>();
		for (int i = 0; i < futures.size(); i++) {
			BoundedWorkerContract contract = accepted.get(i);
			if (cancelled != null && cancelled.getAsBoolean()) {
				for (Future<?> future : futures) {
					future.cancel(true);
				}
				for (int remaining = i; remaining < accepted.size(); remaining++) {
					packets.add(failed(accepted.get(remaining)));
				}
				break;
			}
			try {
				EvidencePacketMerger.EvidencePacket packet = await(futures.get(i), contract, submittedAt, cancelled);
				packets.add(validPacket(contract, packet) ? packet : failed(contract));
			}
			catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				futures.forEach(future -> future.cancel(true));
				for (int remaining = i; remaining < accepted.size(); remaining++) {
					packets.add(failed(accepted.get(remaining)));
				}
				break;
			}
			catch (java.util.concurrent.CancellationException exception) {
				futures.forEach(future -> future.cancel(true));
				for (int remaining = i; remaining < accepted.size(); remaining++) {
					packets.add(failed(accepted.get(remaining)));
				}
				break;
			}
			catch (ExecutionException | java.util.concurrent.TimeoutException exception) {
				futures.get(i).cancel(true);
				packets.add(failed(contract));
			}
		}
		var merged = EvidencePacketMerger.merge(packets);
		return new Result(List.copyOf(packets), List.copyOf(rejected), reserved, merged,
				EvidencePacketReviewer.review(merged));
	}

	private EvidencePacketMerger.EvidencePacket await(Future<EvidencePacketMerger.EvidencePacket> future,
			BoundedWorkerContract contract, long submittedAt, BooleanSupplier cancelled)
			throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
		long deadline = submittedAt + contract.timeout().toNanos();
		while (true) {
			if (cancelled != null && cancelled.getAsBoolean()) {
				throw new java.util.concurrent.CancellationException("Worker execution cancelled");
			}
			long remaining = deadline - System.nanoTime();
			if (future.isDone()) {
				return future.get();
			}
			if (remaining <= 0) {
				throw new java.util.concurrent.TimeoutException("Worker budget expired");
			}
			try {
				return future.get(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(50)), TimeUnit.NANOSECONDS);
			}
			catch (java.util.concurrent.TimeoutException ignored) {
				// Poll cancellation without extending the shared batch deadline.
			}
		}
	}

	private boolean validPacket(BoundedWorkerContract contract, EvidencePacketMerger.EvidencePacket packet) {
		return packet != null && contract.workerId().equals(packet.workerId())
				&& packet.claims().stream().allMatch(claim -> contract.sourceIds().containsAll(claim.sourceIds()));
	}

	private static EvidencePacketMerger.EvidencePacket failed(BoundedWorkerContract contract) {
		return new EvidencePacketMerger.EvidencePacket(contract.workerId(), List.of(), List.of(), true);
	}

	@Override
	public void close() {
		executor.shutdownNow();
	}

	public record Result(List<EvidencePacketMerger.EvidencePacket> packets, List<String> rejectedWorkers,
			int reservedCalls, EvidencePacketMerger.MergeResult merged,
			List<EvidencePacketReviewer.ReviewFlag> reviewFlags) {
	}

}
