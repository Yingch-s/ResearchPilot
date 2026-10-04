# 过夜施工验收清单（2026-10-02）

> 昨夜自主施工范围：B2.3 检索挂接 + Phase C Evidence Store。
> fork 提交链：`d2e66fd`(B2.3) → `32da6f6`(Phase C) → `1ac2bf5`(修复)。
> 每项验收：跑命令 → 对照预期 → 在结果处打勾或记录问题。

## 0. 环境预检

```bash
# Docker 三件套应为 Up 状态
docker ps  # 期望: deep-research-redis / deep-research-es / researchpilot-postgres / rp-embedding
# sidecar（若未跑）
cd /d/Workspace/Agent/deepresearch/sidecar && uv run uvicorn main:app --port 8100
# 后端（若未跑）
cd /d/Workspace/Agent/deepresearch
export LLM_API_KEY="$DEEPSEEK_API_KEY"
mvn spring-boot:run
```

## 1. B2.3 验收：论文库驱动研究（核心里程碑）

```bash
# 用你上传过 DPR 论文的专题 id 替换 <TOPIC_ID>（或重新上传一篇再取）
curl -s -N -X POST http://localhost:8080/chat/stream \
  -H "Content-Type: application/json; charset=utf-8" \
  --data-binary @req_kb3.json   # 内容含 rp_topic_id
```

**预期**：
- 后端日志出现 `planner 注入论文库上下文 N 条`（≥1）
- 出现 ≥2 次 `researcher_x 注入论文库片段`（研究步骤从论文库取证）
- 任务正常 END，产出报告

**对照原理**：planner 拿到 KB 采样后会规划 2-4 个取证步骤；Researcher 每步做 Hybrid 检索注入 Top6 原文片段。

## 2. Phase C 验收：证据登记 + 矩阵

```bash
# 用上一步任务日志里的 thread_id（形如 __default__-N）
curl -s http://localhost:8080/api/rp/research/<THREAD_ID>/matrix | python -m json.tool --no-ensure-ascii
```

**预期**：
- `fields` ≥2（研究步骤）、`papers` ≥1、`cells` = fields×papers
- cell 状态只会是 SUPPORTED / NEEDS_REVIEW / NOT_FOUND 三种
- 每条证据带 `source_chunk_id`（可回溯 ES chunk）与 `verification`（n-gram 校验 JSON）

```bash
curl -s http://localhost:8080/api/rp/research/<THREAD_ID>/evidence | python -m json.tool --no-ensure-ascii
```

**预期**：证据分两类 origin：`SUMMARIZED`（模型归纳，NEEDS_REVIEW）与 `EXTRACTED`（原文，经校验）。

## 3. 拷问题（验收后请回答，这是协议）

1. **为什么 planner 注入 KB 后反而先走了"零步骤直通"？** 这个 bug 暴露了"向 LLM 注入上下文"的什么普遍风险？（提示：采样 vs 全量）
2. **确定性引用校验（n-gram）对"中文归纳+英文原文"系统性失效**——如果你明天跑评测发现 NEEDS_REVIEW 占比 90%，你会怎么改进？（提示：嵌入相似度 / 翻译对齐 / 混合阈值）
3. **rp_topic_id 从请求到 state 到 ResearcherNode 一路传递**——如果多用户并发各自带不同 topic_id，现在的实现哪里会串数据？（检查 MemorySaver 的 thread 隔离）

## 4. 已知未决（如实记录）

| 项 | 状态 | 归属 |
|---|---|---|
| HITL-resume | **✅ 机制修复完成（99e0649）**：中断→审批→恢复→论文库取证实测通过（StateUtil.getPlan 快照兜底）。遗留：研究循环 25 分钟未收敛——独立的长任务耗时问题（非 HITL 路径同样存在） | 评测期调参 |
| 前端 undefined 渲染 / 开始研究按钮误发 | 已定位 | Phase E（你指定风格后） |
| n-gram 校验跨语言失效 | 已记录，改进方向明确 | Phase E 评测驱动 |
| 模型配比（全 flash vs 混配 v4-pro） | 未决策 | 待评测数据 |

## 5. 今天剩余人工工作

- [ ] 按清单跑验收，记录异常
- [ ] 回答 3 道拷问题
- [ ] **开始标注 gold set**（Phase E 唯一必须人工的部分：20-30 条问题+gold span，格式随评测 harness 模板）
