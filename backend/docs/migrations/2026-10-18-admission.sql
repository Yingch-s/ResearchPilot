-- P0-B 准入与背压：QUEUED 状态 + 补位索引
-- 对存量库执行（运行库与测试库 5433 各一次）
ALTER TABLE rp_runs DROP CONSTRAINT IF EXISTS rp_runs_status_check;
ALTER TABLE rp_runs ADD CONSTRAINT rp_runs_status_check
    CHECK (status IN ('CREATED', 'PLANNING', 'WAITING_APPROVAL', 'RESEARCHING', 'QUEUED', 'VERIFYING',
                      'FINALIZING', 'COMPLETED', 'PARTIAL_COMPLETED', 'FAILED', 'CANCELLED'));
CREATE INDEX IF NOT EXISTS idx_rp_runs_queued ON rp_runs (created_at) WHERE status = 'QUEUED';
