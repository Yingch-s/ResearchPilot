-- P0-A 多用户：rp_users + 归属列（存量数据归 admin）
CREATE TABLE IF NOT EXISTS rp_users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username      TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role          TEXT NOT NULL DEFAULT 'user' CHECK (role IN ('admin', 'user')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- admin 种子（密码占位：不可登录，首次需要时由 DBA 重置为 BCrypt 哈希）
INSERT INTO rp_users (username, password_hash, role)
VALUES ('admin', '$2a$10$DISABLEDADMINACCOUNTDISABLEDADMINACCO', 'admin')
ON CONFLICT (username) DO NOTHING;

ALTER TABLE rp_topics ADD COLUMN IF NOT EXISTS owner_user_id UUID REFERENCES rp_users(id);
ALTER TABLE rp_runs   ADD COLUMN IF NOT EXISTS owner_user_id UUID REFERENCES rp_users(id);
UPDATE rp_topics SET owner_user_id = (SELECT id FROM rp_users WHERE username = 'admin') WHERE owner_user_id IS NULL;
UPDATE rp_runs   SET owner_user_id = (SELECT id FROM rp_users WHERE username = 'admin') WHERE owner_user_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_rp_runs_owner ON rp_runs (owner_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_rp_topics_owner ON rp_topics (owner_user_id);
