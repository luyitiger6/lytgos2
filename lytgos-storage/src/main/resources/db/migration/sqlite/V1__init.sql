-- 第 16 节课件：LLM 调用审计表。
-- 只创建 llm_calls；其他核心表在后续课程/迭代中通过新的迁移增加。
CREATE TABLE IF NOT EXISTS llm_calls (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT,
    profile_name TEXT NOT NULL,
    provider TEXT NOT NULL,
    model TEXT NOT NULL,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    total_tokens INTEGER,
    duration_ms INTEGER NOT NULL,
    success INTEGER NOT NULL,
    error_message TEXT,
    created_at TEXT NOT NULL
);

-- 按会话查询最近调用。
CREATE INDEX IF NOT EXISTS idx_llm_calls_session_id ON llm_calls (session_id);

-- 按时间查询审计记录。
CREATE INDEX IF NOT EXISTS idx_llm_calls_created_at ON llm_calls (created_at);

-- 按 provider/model 做成本与稳定性分析。
CREATE INDEX IF NOT EXISTS idx_llm_calls_provider_model_created_at
    ON llm_calls (provider, model, created_at);
