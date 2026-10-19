#!/usr/bin/env bash
# Phase 5 演练 2：DB 瞬断 —— 手动演练脚本（与运行库共库，不能在自动化测试中执行）
# 步骤：
#   1. 提交 2 个研究任务（正常路径）
#   2. docker pause researchpilot-postgres   # PG 停响应（进程存活，连接挂起）
#   3. 观察：调度器心跳失败日志（RpRunService.heartbeat / acquireLease 异常）
#   4. sleep 60 && docker unpause researchpilot-postgres
#   5. 验证恢复：任务在下一轮补位/恢复扫描后继续；终态达成；无重复执行（attempt 幂等）
# 判定标准：
#   - 暂停期间：无 panic / 无数据损坏；日志出现连接异常
#   - 恢复后：所有任务到达终态（COMPLETED/PARTIAL/FAILED），无两个 worker 同时持有同一 run
echo "此脚本为操作手册——按上述步骤人工执行并记录观察结果到 chaos-drill-log.md"
