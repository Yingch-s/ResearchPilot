# 负载测试报告（P1-D）

> 2026-10-18 ｜ 环境：真实 HTTP + 真实调度器/PG/ES，**mock LLM**（OpenAI 边界假服务，150ms 延迟）
> 方法：12 个研究任务并发提交（准入上限 4），轮询至终态；Prometheus 抓取 `/actuator/prometheus`

## 结果

| 指标 | 数值 |
|---|---|
| 提交接口延迟 | P50 199ms / P95 303ms |
| 任务完成时间 | P50 3s / P95 4s（mock 下全管道：planning→4 步 research→finalize） |
| 任务成功率 | 12/12 = 100% |
| 排队等待 | 未触发（完成速度快于积压积累；准入机制由 P0-B IT 单独验证） |
| 指标端点 | `rp_active_runs` / `rp_queued_runs` / `rp_admission_rejected_total` / `rp_llm_tokens_total` 全部在线 |

## 复现

```bash
# 1. mock LLM（可控延迟）
python docs/perf/fake_llm_server.py 18081 150
# 2. 后端指向 mock（环境变量）
set RESEARCHPILOT_MODEL_BASE_URL=http://localhost:18081
# 3. 压测
python docs/perf/load_test.py 12 <topic_id>
```

## 可信度边界（明示）

- mock 只代表**调度器 / PG / 队列 / API 层**路径；不含真实模型延迟分布、限流与重试行为
- 完成时间数字无绝对意义（由 mock 延迟决定）；有效结论是**结构性的**：无死锁、无任务丢失、
  提交接口不随并发退化（P95<提交即返回的 202 语义）、指标管线端到端工作
- 真实模型端到端由 D3 评测覆盖（现网配置）

## 发现并顺带修复

- `cp_d3.argfile` 为 10-03 快照，缺 security/prometheus 依赖 → 启动即 ClassNotFound；
  已用 `mvn dependency:build-classpath` 重建（`cp_d3.argfile` 现与 pom 同步）
