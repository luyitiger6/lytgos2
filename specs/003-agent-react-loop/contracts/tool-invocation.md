# Contract: Tool Invocation Audit

## 1. 表结构

Flyway 迁移：`lytgos-storage/src/main/resources/db/migration/sqlite/V2__init.sql`。

```sql
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

CREATE INDEX IF NOT EXISTS idx_tool_invocations_session_id
    ON tool_invocations (session_id);

CREATE INDEX IF NOT EXISTS idx_tool_invocations_created_at
    ON tool_invocations (created_at);
```

规则：

- 只新增 V2，不修改 V1 的 `llm_calls`。
- 只插入，不更新、不删除。
- 工具名必填；输入、错误原因、会话标识可空。
- 每次工具调用尝试一行；成功和失败都写。

## 2. Core 审计端口

```java
public interface ToolInvocationAuditor {
  void record(ToolInvocationRecord record);
}

public record ToolInvocationRecord(
    String sessionId,
    String toolName,
    String inputJson,
    boolean success,
    String errorMessage,
    long durationMs) {}
```

规则：

- `ToolExecutor` 依赖该端口，不依赖 storage。
- 审计写入异常向上抛出，不静默吞掉。
- `durationMs` 是工具查找与执行的总耗时，最小为 0。

## 3. Storage 实现

- `ToolInvocation`：JPA 实体，映射上述列；`created_at` 由 `JpaToolInvocationAuditor` 用 `Instant.now().toString()` 补齐。
- `ToolInvocationRepository`：`JpaRepository<ToolInvocation, Long>`。
- `JpaToolInvocationAuditor`：`@Component`，实现 core 端口，映射 record 后保存。

## 4. 行为契约

| 场景 | ToolResult | 审计 |
|---|---|---|
| 工具存在且执行成功 | `success=true`、content 非空 | `success=1`，error_message 为空 |
| 工具存在但返回 `success=false` | 原样返回 | `success=0`，error_message 非空 |
| 工具不存在 | `success=false`、error 含工具名 | `success=0`，error_message 含工具名 |
| 工具执行抛异常 | `success=false`、error 含异常消息 | `success=0`，error_message 含异常消息 |
| 工具返回 null | `success=false` | `success=0`，error_message 说明空结果 |

工具失败不终止 ReAct 循环：失败结果回填 Session，由模型决定下一轮是否重试或换方案。

## 5. 测试契约

- `ToolExecutorTest`：成功、失败、工具不存在、异常四条路径都断言 auditor 记录；异常不穿透。
- `ToolInvocationRepositoryTest`：用临时 SQLite + Flyway 验证 V2 表结构，保存成功/失败记录并读回；断言 `success` 与 `error_message` 列真实存在且可空。
