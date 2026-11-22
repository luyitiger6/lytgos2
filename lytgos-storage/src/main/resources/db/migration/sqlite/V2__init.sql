-- 第 17 节课件：工具调用审计表。
-- 只新增 tool_invocations；V1 的 llm_calls 保持不变。
CREATE TABLE IF NOT EXISTS tool_invocations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT,
    tool_name TEXT NOT NULL,
    input_json TEXT,
    success INTEGER NOT NULL,
    error_message TEXT,
    duration_ms INTEGER NOT NULL,
    created_at TEXT NOT NULL
);

-- 按会话查询工具调用。
CREATE INDEX IF NOT EXISTS idx_tool_invocations_session_id
    ON tool_invocations (session_id);

-- 按时间查询审计记录。
CREATE INDEX IF NOT EXISTS idx_tool_invocations_created_at
    ON tool_invocations (created_at);
