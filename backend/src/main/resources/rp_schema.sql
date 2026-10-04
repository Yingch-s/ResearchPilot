-- ResearchPilot Phase B：论文知识库业务真值 Schema（幂等，可重复执行）
-- 设计来源：ResearchPilot Phase 1 Python Schema 平移；FTS/向量列不在此处（检索副本在 ES）

CREATE TABLE IF NOT EXISTS rp_topics (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          TEXT NOT NULL,
    research_goal TEXT,
    archived_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE rp_topics ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS rp_papers (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic_id   UUID NOT NULL REFERENCES rp_topics (id) ON DELETE CASCADE,
    title      TEXT NOT NULL,
    metadata   JSONB NOT NULL DEFAULT '{}'::jsonb,
    status     TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REMOVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE rp_papers ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}'::jsonb;
CREATE INDEX IF NOT EXISTS idx_rp_papers_topic ON rp_papers (topic_id);

CREATE TABLE IF NOT EXISTS rp_document_versions (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    paper_id              UUID NOT NULL REFERENCES rp_papers (id) ON DELETE CASCADE,
    version               INT NOT NULL DEFAULT 1,
    file_sha256           TEXT NOT NULL,
    object_key            TEXT NOT NULL,
    file_size             BIGINT NOT NULL,
    parse_status          TEXT NOT NULL DEFAULT 'UPLOADED'
        CHECK (parse_status IN ('UPLOADED', 'PARSING', 'READY', 'PARTIAL', 'FAILED')),
    parser_version        TEXT,
    normalized_object_key TEXT,
    quality_flags         TEXT[] NOT NULL DEFAULT '{}',
    chunk_count           INT NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (paper_id, file_sha256)
);
CREATE INDEX IF NOT EXISTS idx_rp_versions_sha ON rp_document_versions (file_sha256);
CREATE TABLE IF NOT EXISTS rp_section_contexts (
    parent_id TEXT PRIMARY KEY,
    document_version_id UUID NOT NULL REFERENCES rp_document_versions(id) ON DELETE CASCADE,
    section_path TEXT NOT NULL,
    content TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_rp_section_context_version ON rp_section_contexts(document_version_id);
ALTER TABLE rp_document_versions ADD COLUMN IF NOT EXISTS section_context_ready BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE rp_document_versions ADD COLUMN IF NOT EXISTS section_context_checked_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS rp_ingestion_jobs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_version_id UUID NOT NULL REFERENCES rp_document_versions (id) ON DELETE CASCADE,
    status              TEXT NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    stage               TEXT NOT NULL DEFAULT 'UPLOADED',
    attempts            INT NOT NULL DEFAULT 0,
    max_attempts        INT NOT NULL DEFAULT 3,
    lease_until         TIMESTAMPTZ,
    worker_id           TEXT,
    last_error          TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_rp_jobs_claim ON rp_ingestion_jobs (status, created_at);

-- 可恢复分片上传会话；分片按 (session, index) 幂等保存，完成后校验整体 SHA-256。
CREATE TABLE IF NOT EXISTS rp_upload_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic_id UUID NOT NULL REFERENCES rp_topics(id) ON DELETE CASCADE,
    filename TEXT NOT NULL,
    content_type TEXT,
    total_size BIGINT NOT NULL,
    chunk_size INT NOT NULL,
    expected_sha256 TEXT,
    object_key TEXT,
    status TEXT NOT NULL DEFAULT 'UPLOADING' CHECK (status IN ('UPLOADING', 'COMPLETED', 'CANCELLED', 'EXPIRED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS rp_upload_chunks (
    session_id UUID NOT NULL REFERENCES rp_upload_sessions(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    content BYTEA NOT NULL,
    size INT NOT NULL,
    sha256 TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (session_id, chunk_index)
);
ALTER TABLE rp_upload_sessions ADD COLUMN IF NOT EXISTS result JSONB;

-- ===== Phase C：Evidence Store（证据状态机 + 可追溯引用链）=====
CREATE TABLE IF NOT EXISTS rp_evidence (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id             TEXT NOT NULL,
    topic_id            UUID NOT NULL,
    paper_id            UUID,
    document_version_id UUID,
    schema_field_id     TEXT NOT NULL,
    field_title         TEXT,
    value               TEXT NOT NULL,
    supporting_quote    TEXT,
    source_chunk_id     TEXT,
    section_path        TEXT,
    page                INT,
    status              TEXT NOT NULL
        CHECK (status IN ('SUPPORTED', 'CONTRADICTED', 'NOT_FOUND', 'NOT_REPORTED', 'PARSE_UNCERTAIN', 'NEEDS_REVIEW')),
    origin              TEXT NOT NULL DEFAULT 'EXTRACTED'
        CHECK (origin IN ('EXTRACTED', 'SUMMARIZED', 'USER_EDIT')),
    verification        TEXT,
    -- Structured claim binding: one claim can be checked against its own sources.
    claim_id            TEXT,
    claim_text          TEXT,
    claim_index         INT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE rp_evidence ADD COLUMN IF NOT EXISTS claim_id TEXT;
ALTER TABLE rp_evidence ADD COLUMN IF NOT EXISTS claim_text TEXT;
ALTER TABLE rp_evidence ADD COLUMN IF NOT EXISTS claim_index INT;
CREATE INDEX IF NOT EXISTS idx_rp_evidence_task ON rp_evidence (task_id);
CREATE INDEX IF NOT EXISTS idx_rp_evidence_paper ON rp_evidence (topic_id, paper_id);

-- ===== Phase D：HITL 恢复的 plan 快照（graph-core checkpoint 时序问题的确定性补丁）=====
CREATE TABLE IF NOT EXISTS rp_plan_snapshots (
    thread_id TEXT PRIMARY KEY,
    plan      JSONB NOT NULL,
    saved_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ===== V0.4 H1.1：ResearchRun 持久化状态机（业务事实唯一来源）=====
-- 设计来源：文档/V0.4_架构决策冻结_2026-10-02.md（决策 1/3）
-- Graph State 与 MemorySaver checkpoint 降级为执行引擎内部缓存，Run 生命周期以本表事务提交为准。

CREATE TABLE IF NOT EXISTS rp_runs (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic_id         UUID,
    thread_id        TEXT NOT NULL,
    session_id       TEXT,
    query            TEXT NOT NULL,
    status           TEXT NOT NULL DEFAULT 'CREATED'
        CHECK (status IN ('CREATED', 'PLANNING', 'WAITING_APPROVAL', 'RESEARCHING', 'VERIFYING',
                          'FINALIZING', 'COMPLETED', 'PARTIAL_COMPLETED', 'FAILED', 'CANCELLED')),
    plan_id          UUID,
    plan_snapshot    JSONB,
    execution_cursor INT NOT NULL DEFAULT 0,     -- 最后持久化完成的步骤序号（0 = 尚无完成步骤）
    attempt          INT NOT NULL DEFAULT 0,     -- 执行段序号（每次恢复/重试 +1）
    global_deadline  TIMESTAMPTZ,                -- 决策 3：创建/进入 RESEARCHING 时确定
    research_deadline TIMESTAMPTZ,
    finalization_deadline TIMESTAMPTZ,
    budget_reason    TEXT,                       -- 终态原因：timeout/loop_cap/no_progress/cancelled/rejected
    pause_requested  BOOLEAN NOT NULL DEFAULT FALSE,
    allow_web        BOOLEAN NOT NULL DEFAULT FALSE,
    lease_until      TIMESTAMPTZ,
    worker_id        TEXT,
    heartbeat_at     TIMESTAMPTZ,
    started_at       TIMESTAMPTZ,
    finished_at      TIMESTAMPTZ,
    client_request_id TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS rp_run_plan_versions (
    run_id UUID NOT NULL REFERENCES rp_runs(id) ON DELETE CASCADE,
    version INT NOT NULL,
    snapshot JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (run_id, version)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_rp_runs_thread ON rp_runs (thread_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_rp_runs_client_request ON rp_runs (client_request_id)
    WHERE client_request_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_rp_runs_status ON rp_runs (status);

-- V1 会话持久化：快速提问与深度研究共享会话身份，浏览器本地缓存只作为离线兜底。
CREATE TABLE IF NOT EXISTS rp_chat_sessions (
    id UUID PRIMARY KEY,
    topic_id UUID NOT NULL REFERENCES rp_topics (id) ON DELETE CASCADE,
    title TEXT NOT NULL DEFAULT '新对话',
    parent_session_id UUID REFERENCES rp_chat_sessions(id) ON DELETE SET NULL,
    branch_message_id UUID,
    draft JSONB,
    context_summary TEXT,
    context_summary_version INT NOT NULL DEFAULT 0,
    context_compacted_through TIMESTAMPTZ,
    context_exclusions JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (topic_id, id)
);
CREATE INDEX IF NOT EXISTS idx_rp_chat_sessions_topic_updated ON rp_chat_sessions (topic_id, updated_at DESC);
CREATE TABLE IF NOT EXISTS rp_chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES rp_chat_sessions (id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK (role IN ('user', 'assistant', 'system')),
    content TEXT NOT NULL,
    citations JSONB,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_rp_chat_messages_session_created ON rp_chat_messages (session_id, created_at);
ALTER TABLE rp_chat_messages ADD COLUMN IF NOT EXISTS metadata JSONB;
ALTER TABLE rp_chat_messages ADD COLUMN IF NOT EXISTS message_id UUID;
CREATE UNIQUE INDEX IF NOT EXISTS uq_rp_chat_message_request ON rp_chat_messages(session_id, message_id) WHERE message_id IS NOT NULL;
ALTER TABLE rp_chat_sessions ADD COLUMN IF NOT EXISTS context_summary TEXT;
ALTER TABLE rp_chat_sessions ADD COLUMN IF NOT EXISTS draft JSONB;
ALTER TABLE rp_chat_sessions ADD COLUMN IF NOT EXISTS context_summary_version INT NOT NULL DEFAULT 0;
ALTER TABLE rp_chat_sessions ADD COLUMN IF NOT EXISTS context_compacted_through TIMESTAMPTZ;
ALTER TABLE rp_chat_sessions ADD COLUMN IF NOT EXISTS context_compacted_message_id UUID;
ALTER TABLE rp_chat_sessions ADD COLUMN IF NOT EXISTS context_exclusions JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE rp_chat_sessions ADD COLUMN IF NOT EXISTS parent_session_id UUID REFERENCES rp_chat_sessions(id) ON DELETE SET NULL;
ALTER TABLE rp_chat_sessions ADD COLUMN IF NOT EXISTS branch_message_id UUID;

CREATE TABLE IF NOT EXISTS rp_run_steps (
    run_id            UUID NOT NULL,
    step_index        INT NOT NULL,
    step_id           TEXT NOT NULL,             -- plan 步骤业务标识（标题或生成 id）
    status            TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'SKIPPED')),
    active_attempt_id UUID,                      -- 当前有效尝试（决策 2：仅此 attempt 可提交结果）
    attempt_count     INT NOT NULL DEFAULT 0,
    result            TEXT,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (run_id, step_index)
);

-- Research-stage checkpoints. Raw retrieval and summaries are reusable work
-- products; evidence acceptance remains owned by the step commit transaction.
CREATE TABLE IF NOT EXISTS rp_run_step_artifacts (
    run_id UUID NOT NULL REFERENCES rp_runs(id) ON DELETE CASCADE,
    step_index INT NOT NULL,
    attempt_id UUID,
    kind TEXT NOT NULL CHECK (kind IN ('RETRIEVAL', 'SUMMARY')),
    query_fingerprint TEXT NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (run_id, step_index, kind, query_fingerprint)
);
CREATE INDEX IF NOT EXISTS idx_rp_step_artifacts_lookup
    ON rp_run_step_artifacts(run_id, step_index, kind, created_at DESC);

CREATE TABLE IF NOT EXISTS rp_research_artifact_cache (
    topic_id UUID NOT NULL REFERENCES rp_topics(id) ON DELETE CASCADE,
    kind TEXT NOT NULL CHECK (kind IN ('RETRIEVAL')),
    query_fingerprint TEXT NOT NULL,
    scope_fingerprint TEXT NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (topic_id, kind, query_fingerprint, scope_fingerprint)
);
CREATE INDEX IF NOT EXISTS idx_rp_research_artifact_cache_lookup
    ON rp_research_artifact_cache(topic_id, query_fingerprint, created_at DESC);

-- 决策 2：研究取证记录归属某次执行；原文来源（rp_source_blocks 概念）可跨 Run 复用不带 run_id。
-- 迁移原则：先加可空列 + 回填，验证后再设非空/外键——禁止直接强制变更。
ALTER TABLE rp_evidence ADD COLUMN IF NOT EXISTS run_id UUID;
ALTER TABLE rp_evidence ADD COLUMN IF NOT EXISTS step_id TEXT;
ALTER TABLE rp_evidence ADD COLUMN IF NOT EXISTS attempt_id UUID;
ALTER TABLE rp_evidence ADD COLUMN IF NOT EXISTS evidence_status TEXT
    CHECK (evidence_status IN ('CANDIDATE', 'ACCEPTED', 'REJECTED', 'SUPERSEDED'));
CREATE INDEX IF NOT EXISTS idx_rp_evidence_run ON rp_evidence (run_id);
CREATE INDEX IF NOT EXISTS idx_rp_evidence_claim ON rp_evidence (run_id, claim_id);

-- ===== V0.4 H1.3：研究报告持久化（终态收敛的确定性产物，降级链第三级保底）=====
CREATE TABLE IF NOT EXISTS rp_reports (
    run_id          UUID PRIMARY KEY,
    report          TEXT NOT NULL,
    completed_steps INT NOT NULL DEFAULT 0,
    total_steps     INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- User-pinned outputs shown in the Notebook's成果 panel. Runs remain in history;
-- only explicitly saved report/matrix entries appear as durable artifacts.
CREATE TABLE IF NOT EXISTS rp_saved_artifacts (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic_id   UUID NOT NULL REFERENCES rp_topics(id) ON DELETE CASCADE,
    run_id     UUID NOT NULL REFERENCES rp_runs(id) ON DELETE CASCADE,
    kind       TEXT NOT NULL CHECK (kind IN ('REPORT', 'MATRIX')),
    title      TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (topic_id, run_id, kind)
);
CREATE INDEX IF NOT EXISTS idx_rp_saved_artifacts_topic ON rp_saved_artifacts(topic_id, created_at DESC);
CREATE TABLE IF NOT EXISTS rp_report_versions (
    run_id UUID NOT NULL REFERENCES rp_runs(id) ON DELETE CASCADE,
    version INT NOT NULL,
    report TEXT NOT NULL,
    completed_steps INT NOT NULL DEFAULT 0,
    total_steps INT NOT NULL DEFAULT 0,
    source_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (run_id, version)
);
ALTER TABLE rp_report_versions ADD COLUMN IF NOT EXISTS source_ids JSONB NOT NULL DEFAULT '[]'::jsonb;

-- Revocable, read-only report shares. Only a hash is stored; the raw token is returned once.
CREATE TABLE IF NOT EXISTS rp_report_shares (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL REFERENCES rp_runs(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_rp_report_shares_run ON rp_report_shares(run_id);

-- ===== V0.4 H3：引用真实性与结论支持性拆分（治理 §四.1，硬规则 3）=====
-- quote_status：这段被引用文字是否真实出现在指定论文指定位置（确定性 Layer1 判定）
-- claim_status：论文中的证据是否足以支持报告结论（语义判定，校准前仅为待复核候选）
-- 旧 status 列保留为兼容聚合（矩阵 API），不再承载双重语义；校准前 claim_status 的
-- SUPPORTED/CONTRADICTED 不得作为最终已验证结论展示。
ALTER TABLE rp_evidence ADD COLUMN IF NOT EXISTS quote_status TEXT;
ALTER TABLE rp_evidence ADD COLUMN IF NOT EXISTS claim_status TEXT;

-- ===== V1 UX：资料范围快照（勾选仅影响下一次请求；Run 开始后冻结）=====
ALTER TABLE rp_runs ADD COLUMN IF NOT EXISTS paper_scope JSONB;
ALTER TABLE rp_runs ADD COLUMN IF NOT EXISTS plan_snapshot JSONB;
ALTER TABLE rp_runs ADD COLUMN IF NOT EXISTS pause_requested BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE rp_runs ADD COLUMN IF NOT EXISTS client_request_id TEXT;
ALTER TABLE rp_runs ADD COLUMN IF NOT EXISTS allow_web BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE rp_runs ADD COLUMN IF NOT EXISTS parent_run_id UUID REFERENCES rp_runs(id) ON DELETE SET NULL;
ALTER TABLE rp_runs ADD COLUMN IF NOT EXISTS continuation_reason TEXT;
ALTER TABLE rp_runs ADD COLUMN IF NOT EXISTS lease_until TIMESTAMPTZ;
ALTER TABLE rp_runs ADD COLUMN IF NOT EXISTS worker_id TEXT;
ALTER TABLE rp_runs ADD COLUMN IF NOT EXISTS heartbeat_at TIMESTAMPTZ;
ALTER TABLE rp_runs ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ;
ALTER TABLE rp_runs ADD COLUMN IF NOT EXISTS finished_at TIMESTAMPTZ;
CREATE UNIQUE INDEX IF NOT EXISTS uq_rp_runs_client_request ON rp_runs (client_request_id)
    WHERE client_request_id IS NOT NULL;

-- ===== V1 UX：异构来源 —— 网页补充证据可溯源（source_type 区分 PAPER/WEB）=====
ALTER TABLE rp_evidence ADD COLUMN IF NOT EXISTS source_type TEXT NOT NULL DEFAULT 'PAPER';
ALTER TABLE rp_evidence ADD COLUMN IF NOT EXISTS source_url TEXT;

-- 用户研究笔记：可选绑定专题、Run 或证据，原文和用户意见分开保存。
CREATE TABLE IF NOT EXISTS rp_notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic_id UUID NOT NULL REFERENCES rp_topics(id) ON DELETE CASCADE,
    run_id UUID REFERENCES rp_runs(id) ON DELETE SET NULL,
    evidence_id TEXT,
    title TEXT NOT NULL DEFAULT '未命名笔记',
    body TEXT NOT NULL,
    source_quote TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_rp_notes_topic_updated ON rp_notes(topic_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_rp_notes_evidence ON rp_notes(evidence_id);
ALTER TABLE rp_notes ADD COLUMN IF NOT EXISTS favorite BOOLEAN NOT NULL DEFAULT FALSE;

-- Stable, user-confirmed project memory is separate from chat summaries and run findings.
CREATE TABLE IF NOT EXISTS rp_topic_memory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic_id UUID NOT NULL REFERENCES rp_topics(id) ON DELETE CASCADE,
    kind TEXT NOT NULL CHECK (kind IN ('STABLE_PREFERENCE', 'CONFIRMED_FACT')),
    content TEXT NOT NULL,
    source TEXT NOT NULL DEFAULT 'USER_CONFIRMED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_rp_topic_memory_topic ON rp_topic_memory(topic_id, updated_at DESC);

-- Harness memory shared across notebooks. The local profile key is explicit so a
-- future authenticated user identity can replace it without changing the contract.
CREATE TABLE IF NOT EXISTS rp_user_memory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_key TEXT NOT NULL DEFAULT 'local',
    kind TEXT NOT NULL CHECK (kind IN ('PREFERENCE', 'CONSTRAINT')),
    content TEXT NOT NULL,
    source TEXT NOT NULL DEFAULT 'USER_CONFIRMED',
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUPERSEDED', 'DISMISSED')),
    source_message_id UUID,
    supersedes_id UUID REFERENCES rp_user_memory(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_rp_user_memory_owner_status ON rp_user_memory(owner_key, status, updated_at DESC);

-- 运行中补充要求：按时间记录，不覆盖原始问题和已执行计划步骤。
CREATE TABLE IF NOT EXISTS rp_run_requirements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL REFERENCES rp_runs(id) ON DELETE CASCADE,
    requirement TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPLIED', 'REJECTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_rp_run_requirements_run ON rp_run_requirements(run_id, created_at);

-- 可选保存的网络来源快照：引用指向抓取时的内容，不随网页后续变化。
CREATE TABLE IF NOT EXISTS rp_web_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic_id UUID NOT NULL REFERENCES rp_topics(id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    final_url TEXT NOT NULL,
    title TEXT,
    content_type TEXT,
    content TEXT NOT NULL,
    content_sha256 TEXT NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (topic_id, url, content_sha256)
);
CREATE INDEX IF NOT EXISTS idx_rp_web_snapshots_topic ON rp_web_snapshots(topic_id, fetched_at DESC);
ALTER TABLE rp_web_snapshots ADD COLUMN IF NOT EXISTS extracted_text TEXT;
ALTER TABLE rp_web_snapshots ADD COLUMN IF NOT EXISTS paper_id UUID REFERENCES rp_papers(id) ON DELETE SET NULL;
