# SQL 性能审查报告（P1-C）

> 2026-10-18 ｜ 方法：EXPLAIN (ANALYZE, BUFFERS) ｜ 数据集：真实库（~1k runs）+ 10 万行合成数据回放后清理
> 工具链：docker exec psql 直测生产 SQL（复制自 RunListingService/RpRunService/RpAdmissionService 现场代码）

## 结论先行

**既有 19 索引已覆盖全部查询形态；测量未发现值得引入的变更。**
关键分页（keyset `(created_at, id) <` 游标）在 10 万行下 0.157ms 不退化——Phase 0 设计决策的验证。

## 基线数字

| 查询 | 1k 行 | 100k 行 | 计划 |
|---|---|---|---|
| run 列表（owner 过滤 LIMIT 50） | 0.06ms | 0.07ms | Index Scan `idx_rp_runs_owner` |
| 分页深页（keyset 游标，owner 含 10 万行） | — | **0.16ms** | Index Scan + Incremental Sort，rows=20 无退化 |
| recoveryScan 轮询 | 0.02ms | 2.4ms | Index Scan `idx_rp_runs_status`，rows=5000 |
| 准入计数（三状态 count） | 0.07ms | 0.59ms | **Index Only Scan**（免回表） |
| NULL owner 幽灵行检查 | 0.03ms | 0.03ms | Index Scan |

## 尝试过并否决的优化

**partial index `WHERE status='RESEARCHING' AND NOT pause_requested`（recoveryScan 专用）**：
建索引后 planner 仍选全量 status 索引（lease 的 OR 条件使 partial 谓词不完全匹配）；
实测 3.4ms→2.4ms 差异来自缓存温度而非索引。**已 DROP**——写放大不值 0.2ms。

## 支撑决策的记录

1. 恢复扫描 2.4ms@100k、每 10s 一次 → 容量上限 ~400 万行才到 100ms 量级，远超本项目生命周期
2. 准入计数的 Index Only Scan 是 P0-B 选「status IN 三态 count」而非「NOT IN 终态」的直接收益（终态占 95%+ 行数）
3. 唯一的真退化风险是 OFFSET 深分页——现网已用 keyset（Phase 0 设计），本轮实测验证

## 复现命令

```bash
docker exec researchpilot-postgres psql -U researchpilot -d researchpilot -c "EXPLAIN (ANALYZE, BUFFERS) <查询>"
```
