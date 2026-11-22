# Data Model: ReAct Agent Loop

## 1. Session

`Session` 是一次对话的内存领域对象；第 17 节不落 SQLite，第 18 节再由 SessionManager 持久化。

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `sessionId` | String | 是 | 会话标识；第 18 节由 channel + user + profile 生成 |
| `profileName` | String | 是 | 所属 AgentProfile 名称，用于注册表查找 |
| `channel` | String | 是 | 接入渠道，如 cli/web/scheduler |
| `userId` | String | 是 | 用户标识 |
| `messages` | List&lt;AgentMessage&gt; | 是 | 按时间顺序累积的对话消息，构造时为空列表 |

行为：

- `appendUser(content)`：追加 USER 消息。
- `appendAssistant(AgentResponse)`：追加 ASSISTANT 消息，包含文本与工具调用列表。
- `appendToolResult(AgentToolCall, ToolResult)`：追加 TOOL 消息，携带工具名、工具调用 ID 与结果文本。
- `recentMessages(maxTurns)`：返回最近 N 个完整轮次；每轮从 USER 消息开始，包含其后的 ASSISTANT/TOOL 消息。`maxTurns <= 0` 返回空列表。
- `messages()`：返回不可变快照，避免外部修改内部历史。

## 2. AgentMessage

| 字段 | 类型 | 说明 |
|---|---|---|
| `role` | AgentRole | `SYSTEM` / `USER` / `ASSISTANT` / `TOOL` |
| `content` | String | 消息文本；TOOL 消息为工具结果文本 |
| `toolName` | String | TOOL 消息的工具名，可空 |
| `toolCallId` | String | TOOL 消息关联的模型工具调用 ID，可空 |
| `toolCalls` | List&lt;AgentToolCall&gt; | ASSISTANT 消息携带的工具调用，默认空列表 |

约束：

- USER/SYSTEM 消息的 `toolCalls` 为空列表。
- TOOL 消息必须带 `toolName`；`toolCallId` 由模型响应提供，缺失时使用空字符串。
- 列表字段做防御性复制。

## 3. AgentPrompt

一轮模型请求的 core 领域输入。

| 字段 | 类型 | 说明 |
|---|---|---|
| `systemPrompt` | String | 已完成身份、Bootstrap、Skill 元数据与当前时间拼接的 system 文本 |
| `messages` | List&lt;AgentMessage&gt; | 最近 N 轮会话历史 |
| `tools` | List&lt;LytgTool&gt; | 当前 AgentProfile 允许的工具，默认空列表 |

## 4. AgentResponse

模型一轮响应的 core 领域表示。

| 字段 | 类型 | 说明 |
|---|---|---|
| `text` | String | 模型文本；没有文本时为空字符串 |
| `toolCalls` | List&lt;AgentToolCall&gt; | 模型请求的工具调用，默认空列表 |

行为：`hasToolCalls()` 返回 `toolCalls` 是否非空。

## 5. AgentToolCall

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | String | 模型提供的调用 ID，用于关联工具结果 |
| `name` | String | 工具名称 |
| `arguments` | String | 工具输入 JSON；模型返回空时使用 `{}` |

## 6. AgentProfile（第 16 节已有，补充相关字段）

本节消费的字段：

| 字段 | 说明 |
|---|---|
| `identity.prompt` | Agent 指令，进入 system prompt |
| `tools` | 允许的工具名称列表；PromptBuilder 按此过滤工具集合 |
| `bootstrap` | 工作区根目录下的 Bootstrap 文件名列表 |
| `skills` | `.lytgos/skills/<name>/SKILL.md` 的 Skill 名称列表 |
| `settings.maxIterations` | ReAct 最大轮数；缺省/非正数使用 10 |
| `settings.maxHistoryTurns` | 历史最近轮数；缺省/非正数使用 20 |

## 7. ToolInvocation（`tool_invocations` 表）

一次工具调用尝试对应一行；成功和失败都写；不记录模型原文。

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | INTEGER | PK AUTOINCREMENT | 主键 |
| `session_id` | TEXT | NULL | 会话标识；无状态调用时为空 |
| `tool_name` | TEXT | NOT NULL | 工具名称 |
| `input_json` | TEXT | NULL | 工具输入 JSON |
| `success` | INTEGER | NOT NULL | 0/1 |
| `error_message` | TEXT | NULL | 失败原因；成功时为空 |
| `duration_ms` | INTEGER | NOT NULL | 执行耗时，毫秒 |
| `created_at` | TEXT | NOT NULL | ISO-8601 时间戳 |

索引：

- `(session_id)`
- `(created_at)`

行为：

- 只插入，不更新、不删除。
- 工具不存在：写 `success=0` 与错误原因，不执行任何工具。
- 工具抛异常：包装为失败结果并写 `success=0`，异常不穿透到调用方。
- 工具返回 `ToolResult.success=false`：写失败审计，结果仍回填给模型。

## 8. ToolInvocationRecord（core 审计端口数据载体）

| 字段 | 类型 | 说明 |
|---|---|---|
| `sessionId` | String | 会话标识，可空 |
| `toolName` | String | 工具名称 |
| `inputJson` | String | 工具输入，可空 |
| `success` | boolean | 是否成功 |
| `errorMessage` | String | 失败原因，可空 |
| `durationMs` | long | 毫秒 |

`JpaToolInvocationAuditor` 负责补 `created_at` 并写入 `ToolInvocation` 实体。

## 9. AgentProfileContext

- ThreadLocal 保存当前请求的 `AgentProfile`。
- `set(profile)`、`current()`（未设置返回 null）、`clear()`。
- `AgentService.process` 在入口 set，`finally` clear；不得跨请求保留。
