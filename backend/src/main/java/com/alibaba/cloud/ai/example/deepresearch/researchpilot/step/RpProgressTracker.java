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

package com.alibaba.cloud.ai.example.deepresearch.researchpilot.step;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * V0.4 H2.1：有效进展检测器（治理 §三.3，替代步骤粒度进展指纹）。
 *
 * <p>
 * 判定规则（项目所有者定义）：
 * <ul>
 * <li>新增一条有效且非重复证据 / 命中未见原文段落 = 有效进展；</li>
 * <li>重复检索相同段落（chunk 全部已见）= 无进展；</li>
 * <li>LLM 换句话重复提出同一问题 = 无进展 —— 字面签名去重 + 未见段落过滤双重防线： 字面重复被签名拦截；语义换说法若命中的仍是已见
 * chunk，同样判无进展而停止；</li>
 * <li>新增未经验证的结论不算进展（证据接纳由调度器事务决定，本检测器只看检索新颖性）。</li>
 * </ul>
 * 纯逻辑类，无 I/O；粒度对齐单步骤的步内反思循环（跨步骤推进由调度器与状态机负责）。
 */
public class RpProgressTracker {

	private final Set<String> attemptedQuerySignatures = new HashSet<>();

	private final Set<String> seenChunkIds = new HashSet<>();

	/**
	 * 查询签名：小写 + 去标点 + 空白折叠。字面相同的重复问题（含换标点/大小写）签名一致。
	 */
	public static String signature(String query) {
		if (query == null) {
			return "";
		}
		// 小写 + 去标点/空白。对去重目的"过度合并"是保守方向（只会提前停，不会无限循环）；
		// 中文无空格语言下空白插入不应视为新查询，故完全移除空白而非折叠。
		return query.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "").trim();
	}

	/**
	 * 该查询是否从未提出过（首次调用恒为 true）。
	 */
	public boolean isNewQuery(String query) {
		return !attemptedQuerySignatures.contains(signature(query));
	}

	public void recordQuery(String query) {
		attemptedQuerySignatures.add(signature(query));
	}

	/**
	 * 过滤出未见过的 chunk（重复检索相同段落 → 返回空 = 无进展信号），并把返回的 chunk 记为已见。
	 */
	public <C> List<C> filterNovel(List<C> candidates, java.util.function.Function<C, String> chunkIdOf) {
		List<C> novel = new ArrayList<>();
		for (C c : candidates) {
			if (chunkIdOf.apply(c) != null && seenChunkIds.add(chunkIdOf.apply(c))) {
				novel.add(c);
			}
		}
		return novel;
	}

	public int seenChunkCount() {
		return seenChunkIds.size();
	}

}
