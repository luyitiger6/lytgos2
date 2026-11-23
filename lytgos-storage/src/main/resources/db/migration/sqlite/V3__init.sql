-- 第 18 节：会话持久化表。
-- 一条会话对应一行；消息历史整体序列化为 JSON 存入 messages_json。
CREATE TABLE IF NOT EXISTS sessions (
    session_id TEXT PRIMARY KEY,
    profile_name TEXT NOT NULL,
    channel TEXT NOT NULL,
    user_id TEXT NOT NULL,
    messages_json TEXT NOT NULL DEFAULT '[]',
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at TEXT NOT NULL,
    last_active_at TEXT NOT NULL,
    archived_at TEXT
);

-- 按最近活跃时间列出会话。
CREATE INDEX IF NOT EXISTS idx_sessions_last_active_at ON sessions (last_active_at);
