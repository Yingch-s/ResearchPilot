# 故障演练汇总（Phase 5）

> 2026-10-19 ｜ 三项演练：worker 死亡（自动化 IT）、LLM 风暴（自动化 IT）、DB 瞬断（手动脚本）

| 演练 | 形式 | 触发 | 预期 | 结果 |
|---|---|---|---|---|
| 1. worker 死亡 | `ChaosWorkerDeathIT` | 租约过期 5 分钟 + 孤儿 RUNNING 步骤 | recoveryScan 识别 → 新 worker 拿租约开新 attempt → 旧 attempt 迟到提交被条件更新拒绝 | ✅ PASS |
| 2. DB 瞬断 | `db_outage_drill.sh`（手动） | docker pause PG 60s | 暂停期连接异常不 panic；恢复后任务续跑无重复执行 | 手动执行项 |
| 3. LLM 风暴 | `ChaosLlmStormIT` | 假服务对全部 LLM 请求回 500 | create 快速 5xx（retryable 标记，幂等键重试重驱动）；系统持续响应无死锁 | ✅ PASS（<60s 快速失败） |

## 演练确立的行为语义（面试可讲）

1. **诚实失败**：planner LLM 全挂 = create 快速 5xx 而非长时间悬挂；客户端按 `client_request_id` 重试即重驱动（幂等）
2. **attempt 幂等键是跨 worker 一致性的基石**：孤儿步骤的迟到提交被 `active_attempt_id` 条件更新拒绝
3. **租约 = 执行权**：新接管者必须先 acquireLease（租约过期的 run 可被任意健康 worker 认领）

## 复现

```bash
mvn test -Dtest=ChaosWorkerDeathIT -Drp.skipTests=false
mvn test -Dtest=ChaosLlmStormIT -Drp.skipTests=false
bash docs/chaos/db_outage_drill.sh
```
