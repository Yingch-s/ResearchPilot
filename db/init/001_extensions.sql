-- ResearchPilot 数据库初始化：Phase 0 只需要 vector 扩展
-- 由 Docker Compose 首次启动时自动执行（挂载在 docker-entrypoint-initdb.d）
CREATE EXTENSION IF NOT EXISTS vector;
