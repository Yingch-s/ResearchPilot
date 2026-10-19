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

package com.alibaba.cloud.ai.example.deepresearch.researchpilot.run;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V0.4 H1.1：Run 生命周期转移表纯逻辑测试（决策 1）。
 */
class RunStatusTransitionTest {

	@Test
	void happyPathIsLegal() {
		assertTrue(RunStatus.CREATED.canTransitionTo(RunStatus.PLANNING));
		assertTrue(RunStatus.PLANNING.canTransitionTo(RunStatus.WAITING_APPROVAL));
		assertTrue(RunStatus.WAITING_APPROVAL.canTransitionTo(RunStatus.RESEARCHING));
		assertTrue(RunStatus.RESEARCHING.canTransitionTo(RunStatus.VERIFYING));
		assertTrue(RunStatus.VERIFYING.canTransitionTo(RunStatus.FINALIZING));
		assertTrue(RunStatus.FINALIZING.canTransitionTo(RunStatus.COMPLETED));
		assertTrue(RunStatus.FINALIZING.canTransitionTo(RunStatus.PARTIAL_COMPLETED));
	}

	@Test
	void autoAcceptSkipsApproval() {
		assertTrue(RunStatus.PLANNING.canTransitionTo(RunStatus.RESEARCHING));
	}

	@Test
	void cancelAllowedFromActiveStages() {
		assertTrue(RunStatus.PLANNING.canTransitionTo(RunStatus.CANCELLED));
		assertTrue(RunStatus.WAITING_APPROVAL.canTransitionTo(RunStatus.CANCELLED));
		assertTrue(RunStatus.RESEARCHING.canTransitionTo(RunStatus.CANCELLED));
		// 终态不可取消
		assertFalse(RunStatus.COMPLETED.canTransitionTo(RunStatus.CANCELLED));
	}

	@Test
	void terminalStatesAcceptNothing() {
		for (RunStatus terminal : new RunStatus[] { RunStatus.COMPLETED, RunStatus.PARTIAL_COMPLETED, RunStatus.FAILED,
				RunStatus.CANCELLED }) {
			assertTrue(terminal.isTerminal());
			for (RunStatus target : RunStatus.values()) {
				if (target != terminal) {
					assertFalse(terminal.canTransitionTo(target), terminal + " 不应转移到 " + target);
				}
			}
		}
	}

	@Test
	void backwardTransitionsAreIllegal() {
		assertFalse(RunStatus.RESEARCHING.canTransitionTo(RunStatus.PLANNING));
		assertFalse(RunStatus.VERIFYING.canTransitionTo(RunStatus.RESEARCHING));
		assertFalse(RunStatus.WAITING_APPROVAL.canTransitionTo(RunStatus.PLANNING));
		assertFalse(RunStatus.COMPLETED.canTransitionTo(RunStatus.RESEARCHING));
	}

	@Test
	void partialCompletedIsDistinctFromCompleted() {
		// 诚实停止语义：预算耗尽产出部分成果 ≠ 完整完成
		assertTrue(RunStatus.FINALIZING.canTransitionTo(RunStatus.PARTIAL_COMPLETED));
	}

	@Test
	void queuedTransitions() {
		// P0-B：排队入列与补位
		assertTrue(RunStatus.PLANNING.canTransitionTo(RunStatus.QUEUED));
		assertTrue(RunStatus.WAITING_APPROVAL.canTransitionTo(RunStatus.QUEUED));
		assertTrue(RunStatus.QUEUED.canTransitionTo(RunStatus.RESEARCHING));
		assertTrue(RunStatus.QUEUED.canTransitionTo(RunStatus.CANCELLED));
		// 排队中的 run 不允许跳到执行完成态或回退
		assertFalse(RunStatus.QUEUED.canTransitionTo(RunStatus.COMPLETED));
		assertFalse(RunStatus.QUEUED.canTransitionTo(RunStatus.PLANNING));
		// QUEUED 不是终态
		assertFalse(RunStatus.QUEUED.isTerminal());
	}

}
