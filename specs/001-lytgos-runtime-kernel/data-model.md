# Data Model: LytgOS2 1.0 运行时内核

**Feature**: `001-lytgos-runtime-kernel` | **Date**: 2026-11-22 | **Spec**: [spec.md](./spec.md)

本文件定义 1.0 的持久化实体、运行时对象、校验规则与状态迁移。数据分两处：

- **文件系统**：用户维护的 Agent 目录、Skill、Bootstrap、`MEMORY.md`、MCP 配置、日志、产出物。
- **SQLite**：`sessions`、`tool_invocations`、`llm_calls`、`scheduled_tasks`、`task_executions`、`notify_channels` 六张表，由 Flyway 管理。

用户数据（Agent/Skill/Bootstrap/Memory/MCP）不以数据库为源；SQLite 只存运行时状态与审计。

---

## 1. 文件系统模型

### 1.1 Workspace

```text
.lytgos/
├── agents/<name>/AGENT.md          # 一个目录 = 一个 Agent
├── agents/<name>/skills/           # 指向 ../../skills/<skill> 的相对软连接
├── agents/<name>/scripts/          # 可选脚本（经 shell Tool 白名单执行）
├── agents/<name>/REFERENCE.md      # 可选参考资料（经 read_file 读取）
├── skills/<name>/SKILL.md          # 全局 Skill 实体库
├── memory/MEMORY.md                # 长期记忆
├── mcp_servers.yaml                # MCP stdio server 配置
├── sessions/                       # 兼容目录；Session 真源是 SQLite
├── logs/                           # 结构化日志
├── output/                         # Agent 产出物
├── AGENTS.md / SOUL.md / USER.md   # Bootstrap 三文件
└── lytgos.db                       # SQLite（首次运行时由 Flyway 建表）
```

- 工作区根默认 `./.lytgos`，可由 `lytgos.workspace` 或 `LYTGOS_WORKSPACE` 覆盖。
- `lytgos init` 幂等：只创建缺失的目录/文件，不覆盖已有内容。

### 1.2 AgentDefinition（从 `AGENT.md` 派生）

`AGENT.md` 的 frontmatter 是运行时 Profile；正文是任务指令。`AgentLoader.deriveProfile()` 做解析与校验。

| 字段 | 类型 | 必填 | 默认 | 校验/说明 |
|---|---|---|---|---|
| `name` | string | 是 | — | 必须等于父目录名；`^[a-z0-9][a-z0-9-]{0,62}$` |
| `description` | string | 是 | — | 非空，用于 `profile list` |
| `identity.agent_name` | string | 否 | `name` | 展示名 |
| `identity.prompt` | string | 否 | 空 | 附加人格/系统提示 |
| `provider.name` | string | 是 | — | 必须存在于 ProviderRegistry |
| `provider.model` | string | 是 | — | 非空；覆盖 Provider 默认模型 |
| `provider.temperature` | double | 否 | Provider 默认 | `0.0 ≤ t ≤ 2.0` |
| `tools` | list<string> | 否 | `[]` | 每个名称必须已注册；未列出的 Tool 不可用 |
| `mcp_servers` | list<string> | 否 | `[]` | 每个名称必须存在于 `mcp_servers.yaml`；引用 MCP Tool 时必须声明对应 server |
| `channels` | list<object> | 否 | `[{name: cli}]` | 1.0 仅支持 `cli`；未知 Channel 记校验错误 |
| `bootstrap` | list<string> | 否 | `[AGENTS.md, SOUL.md, USER.md]` | 文件相对工作区根；缺失时告警并跳过 |
| `schedules` | list<object> | 否 | `[]` | 见 1.3 |
| `settings.max_iterations` | int | 否 | `10` | `1 ≤ n ≤ 50` |
| `settings.max_history_turns` | int | 否 | `20` | `1 ≤ n ≤ 100`；一轮 = 一条用户消息 + 最终回复 |
| 未知字段 | — | — | — | 记录告警并忽略；必填缺失则 Agent 校验失败 |

**Profile 运行时对象**（`io.lytgos.core.agent.AgentProfile`）：`name`、`description`、`identity`、`provider`、`tools`、`mcpServers`、`channels`、`bootstrap`、`schedules`、`settings`、`directory`、`body`、`skills`。`directory` 与 `body` 不来自 frontmatter，由加载器填充。

### 1.3 ScheduleDefinition

| 字段 | 类型 | 必填 | 默认 | 校验/说明 |
|---|---|---|---|---|
| `key` | string | 是 | — | 同一 Profile 内唯一；`^[a-z0-9][a-z0-9-]{0,62}$` |
| `cron` | string | 是 | — | Spring 6 字段：秒 分 时 日 月 周 |
| `zone` | string | 否 | 系统时区 | IANA zone，如 `Asia/Shanghai` |
| `message` | string | 是 | — | 非空；到点发给 Agent 的用户消息 |
| `display_name` | string | 否 | `key` | 展示名，不参与定位 |
| `enabled` | boolean | 否 | `true` | 管理端可切换 |

- `schedule_id` 由存储层 `reconcile(profileName, key)` 生成并保持稳定；定义删除/改 key 时旧记录 `retired`，历史保留。
- 1.0 不从 API 修改 `cron`/`message`/`key`；只允许切换 `enabled`。

### 1.4 Skill

```text
.lytgos/skills/<name>/SKILL.md
---
name: tech-digest
description: 汇总当日科技新闻
---
（正文按需读取）
```

- `SKILL.md` frontmatter 至少含 `name`、`description`；`name` 必须等于目录名。
- Agent 绑定：`agents/<agent>/skills/<name>` 是指向 `../../skills/<name>` 的**相对软连接**。
- 校验：软连接必须是相对路径，`toRealPath()` 必须位于 `.lytgos/skills/` 内；拒绝绝对路径、`..` 逃逸、指向普通文件的链接。
- 每轮只注入 `name`、`description`、Agent 本地绝对路径；正文/脚本/参考经 `read_file`/`shell` 按需读取。

### 1.5 Bootstrap

- 默认三个文件：`AGENTS.md`（项目级行为）、`SOUL.md`（默认人格）、`USER.md`（用户偏好）。
- `bootstrap` 列表可覆盖；文件缺失时记录告警，不阻断 Agent。
- 内容全量进入 system prompt；不缓存，修改后下一轮生效。

### 1.6 MEMORY.md

```markdown
# 长期记忆

## 核心记忆

- 用户偏好：回答使用中文
- 项目：部署在 K8s

## 归档记忆

- 2026-11-20：讨论过 AI 芯片方向
- 2026-11-21：关注科技新闻
```

| 字段 | 类型 | 约束 |
|---|---|---|
| `MemoryEntry.content` | string | 非空；`save_memory` 单条 ≤ 4KB |
| `MemoryEntry.scope` | enum | `core` / `archival` |
| `MemoryEntry.section` | string | `## 核心记忆` / `## 归档记忆` |

- 两个二级标题必须存在；缺失时 `load` 按空分区处理，`save` 时补齐。
- 条目为 Markdown 列表行；`save_memory(content, scope)` 追加到对应分区末尾。
- 核心区：全量注入 system prompt，永不截断，不参与 `recall_memory`。
- 归档区：默认最多 6000 字符（`lytgos.memory.archival-max-chars`）；超限时从最旧条目开始截断。
- `recall_memory(query)`：在归档区做不区分大小写的行匹配，返回匹配行；无命中返回空列表。
- 1.0 不做自动抽取、向量检索、语义召回、Memory Wiki。

### 1.7 MCP Server 配置

`.lytgos/mcp_servers.yaml`：

```yaml
servers:
  tech-news:
    transport: stdio
    command: python3
    args: ["examples/mcp/tech-news-server/server.py"]
    env:
      NEWS_API_KEY: ${NEWS_API_KEY}
    timeout_seconds: 30
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `transport` | 是 | 1.0 仅 `stdio` |
| `command` | 是 | 可执行文件；不受 Shell 白名单约束，但只由 LytgOS2 启动 |
| `args` | 否 | 参数数组，不经 shell 解释 |
| `env` | 否 | 环境变量；支持 `${ENV_VAR}` 占位 |
| `timeout_seconds` | 否 | 默认 30 |

---

## 2. SQLite 表

命名与字段以 `docs/ReleaseBaseline.md` 6.1/TechnicalSolution 9.2 为准；SQLite 类型使用 `TEXT`/`INTEGER`/`REAL`。所有时间戳以 ISO-8601 UTC `TEXT` 存储，JPA 通过 `AttributeConverter<Instant,String>` 转换。

### 2.1 `sessions`

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `session_id` | TEXT | PK | `<channel>:<user>:<profile>:<ULID>`，≤128 字符 |
| `profile_name` | TEXT | NOT NULL | Agent 名 |
| `channel` | TEXT | NOT NULL | `cli` / `api` / `scheduler` |
| `user_id` | TEXT | NOT NULL | CLI=OS 用户名或 `local`；REST=调用方；scheduler=`scheduler` |
| `messages_json` | TEXT | NOT NULL | JSON 数组，见 2.7 |
| `status` | TEXT | NOT NULL, CHECK IN (`active`,`archived`) | 归档后只读 |
| `created_at` | TEXT | NOT NULL | ISO-8601 |
| `last_active_at` | TEXT | NOT NULL | 每次追加消息更新 |
| `archived_at` | TEXT | NULL | 归档时间 |

索引：

```sql
CREATE INDEX idx_sessions_active_lookup
  ON sessions(channel, user_id, profile_name, status, last_active_at);
CREATE INDEX idx_sessions_status_active
  ON sessions(status, last_active_at);
```

- `session_id` 唯一；同一个 `(channel,user,profile)` 可有多条 active（`chat --new`、REST 新建）。
- CLI/Scheduler 的“恢复最近 active”按 `last_active_at DESC`。
- 归档不物理删除；1.0 没有 unarchive。

### 2.2 `tool_invocations`

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | INTEGER | PK AUTOINCREMENT | |
| `session_id` | TEXT | NULL | 无状态 invoke 时为 NULL；不设不可空外键 |
| `profile_name` | TEXT | NOT NULL | |
| `tool_name` | TEXT | NOT NULL | |
| `tool_source` | TEXT | NULL | `builtin` / `mcp:<server>` / `java` |
| `input_json` | TEXT | NOT NULL | 模型给出的参数 JSON |
| `result_json` | TEXT | NULL | 最终成功结果 |
| `success` | INTEGER | NOT NULL | 0/1 |
| `error_message` | TEXT | NULL | 最终错误（可诊断、无密钥） |
| `retry_count` | INTEGER | NOT NULL DEFAULT 0 | 0–3 |
| `attempt_errors_json` | TEXT | NULL | 每次失败尝试的错误摘要数组 |
| `duration_ms` | INTEGER | NOT NULL | 包含重试的总耗时 |
| `created_at` | TEXT | NOT NULL | |

索引：`(session_id)`、`(created_at)`、`(tool_name, created_at)`。

- **一次模型请求 = 一行**；重试不新增行。
- 未知 Tool、沙箱违规、MCP 失联也写失败行。
- `retry_count` 最大 3；“最多重试三次”= 首次 + 3 次重试（共 4 次尝试）。

### 2.3 `llm_calls`

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | INTEGER | PK AUTOINCREMENT | |
| `session_id` | TEXT | NULL | 无状态 invoke 时为 NULL |
| `profile_name` | TEXT | NOT NULL | |
| `provider` | TEXT | NOT NULL | provider name |
| `model` | TEXT | NOT NULL | 实际模型 |
| `prompt_tokens` | INTEGER | NULL | Provider 未返回时 NULL |
| `completion_tokens` | INTEGER | NULL | |
| `total_tokens` | INTEGER | NULL | |
| `duration_ms` | INTEGER | NOT NULL | |
| `success` | INTEGER | NOT NULL | 0/1 |
| `error_message` | TEXT | NULL | |
| `created_at` | TEXT | NOT NULL | |

索引：`(session_id)`、`(created_at)`、`(provider, model, created_at)`。

- 每次 `ChatModel.call` 一行，成功/失败都写；失败时 token 字段可为 NULL。
- 不记录 prompt/completion 原文（避免密钥/敏感内容进入审计表）。

### 2.4 `scheduled_tasks`

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `schedule_id` | INTEGER | PK AUTOINCREMENT | 稳定运行态 ID |
| `profile_name` | TEXT | NOT NULL | |
| `schedule_key` | TEXT | NOT NULL | Agent 内 key |
| `display_name` | TEXT | NULL | |
| `cron` | TEXT | NOT NULL | 6 字段 |
| `zone` | TEXT | NOT NULL | IANA |
| `message` | TEXT | NOT NULL | |
| `enabled` | INTEGER | NOT NULL DEFAULT 1 | 管理端开关 |
| `retired` | INTEGER | NOT NULL DEFAULT 0 | 定义删除/改 key 后退役 |
| `next_run_at` | TEXT | NULL | |
| `last_run_at` | TEXT | NULL | |
| `last_status` | TEXT | NULL | `success` / `failed` |
| `run_count` | INTEGER | NOT NULL DEFAULT 0 | |
| `updated_at` | TEXT | NOT NULL | |

唯一约束：`UNIQUE(profile_name, schedule_key)`。索引：`(retired, enabled)`。

- 表只存状态/历史，不是定义源；启动时从 `AGENT.md` reconcile。
- 定义删除或 key 变化：旧行 `retired=1`，不参与调度，历史保留。

### 2.5 `task_executions`

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | INTEGER | PK AUTOINCREMENT | |
| `schedule_id` | INTEGER | NOT NULL, FK → scheduled_tasks | |
| `session_id` | TEXT | NOT NULL | 钟推 Session |
| `started_at` | TEXT | NOT NULL | |
| `success` | INTEGER | NOT NULL | 0/1 |
| `error_message` | TEXT | NULL | |
| `duration_ms` | INTEGER | NOT NULL | |

索引：`(schedule_id, started_at)`、`(started_at)`。

- 每次触发（自动或手动）一行；开始时插入，结束时更新 success/duration。
- 重叠触发被跳过时不写新行（日志记录跳过原因）；如需审计跳过，后续版本再加。

### 2.6 `notify_channels`

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | INTEGER | PK AUTOINCREMENT | |
| `name` | TEXT | NOT NULL UNIQUE | `notify(content, channel)` 引用的全局名 |
| `type` | TEXT | NOT NULL | `wecom` / `feishu` / `dingtalk` / `generic` |
| `url` | TEXT | NOT NULL | webhook URL；必须过 HTTP 域名白名单 |
| `description` | TEXT | NULL | |
| `config_json` | TEXT | NULL | 适配器附加配置 |
| `created_at` | TEXT | NOT NULL | |
| `updated_at` | TEXT | NOT NULL | |

- URL 必须是 `http`/`https`，且 host 命中 `lytgos.sandbox.http.allowed-domains`；否则 CRUD 拒绝。
- `DELETE` 物理删除；后续 `notify` 返回“渠道不存在”并写失败审计。
- 不允许在配置中存明文密钥；如 webhook 需要密钥，使用 `${ENV_VAR}` 并由适配器在发送时解析（1.0 的 webhook 适配器不要求密钥）。

### 2.7 `messages_json` 结构

```json
[
  {
    "role": "user",
    "content": "查一下北京天气并给出穿搭建议",
    "createdAt": "2026-11-22T08:00:00Z"
  },
  {
    "role": "assistant",
    "content": null,
    "toolCalls": [
      {
        "id": "call_01",
        "name": "http_get",
        "arguments": "{\"url\":\"https://api.open-meteo.com/...\"}"
      }
    ],
    "createdAt": "2026-11-22T08:00:01Z"
  },
  {
    "role": "tool",
    "toolCallId": "call_01",
    "name": "http_get",
    "content": "{\"temperature\": 12, ...}",
    "createdAt": "2026-11-22T08:00:02Z"
  },
  {
    "role": "assistant",
    "content": "北京今天 12°C，建议……",
    "terminationReason": "stop",
    "createdAt": "2026-11-22T08:00:03Z"
  }
]
```

| 字段 | 适用角色 | 说明 |
|---|---|---|
| `role` | 全部 | `user` / `assistant` / `tool` |
| `content` | user/assistant/tool | assistant 带 ToolCall 时可为 null |
| `toolCalls` | assistant | ToolCall 数组 |
| `toolCallId` | tool | 对应 assistant 的 `toolCalls[].id` |
| `name` | tool | Tool 名称 |
| `terminationReason` | assistant | `stop` / `max_iterations`；普通回复可省略 |
| `createdAt` | 全部 | ISO-8601 UTC |

- 截断发生在 `max_history_turns` 边界：保留最近 N 个完整“用户消息 → 最终 assistant 回复”轮次；不保留孤立的 tool 消息。
- `GET /sessions/{id}` 最多返回最近 100 条消息（从尾部取），并在响应中标记 `truncated`。

---

## 3. 运行时对象

| 对象 | 说明 |
|---|---|
| `LytgTool` | `name()` / `description()` / `inputSchema()` / `execute(String inputJson)` → `ToolResult` |
| `ToolResult` | `success`、`content`、`error`、`retryable`；失败时包含可诊断错误 |
| `Profile` | 从 `AGENT.md` 派生的运行时配置；见 1.2 |
| `Session` | `sessionId`、`profileName`、`channel`、`userId`、`messages`、`status`、时间戳 |
| `Message` | 见 2.7 |
| `ToolDefinition` | `name`、`description`、`inputSchema`、`source`；用于 `GET /tools` 与模型请求 |
| `ToolInvocation` | 一次逻辑 Tool 调用；映射 `tool_invocations` 一行 |
| `LlmCall` | 一次模型调用；映射 `llm_calls` 一行 |
| `ScheduledTask` | 一次定时任务状态；映射 `scheduled_tasks` |
| `TaskExecution` | 一次触发历史；映射 `task_executions` |
| `NotifyChannel` | 通知渠道；映射 `notify_channels` |
| `ApiResponse<T>` | `code`、`message`、`data`、`timestamp` |

---

## 4. 状态迁移

### 4.1 Session

```text
(创建) → active ──DELETE /sessions/{id}──→ archived
                  │
                  └── 追加消息（正常对话）
archived：GET 可读；POST 消息 → 409；session list 默认不显示；1.0 不可恢复为 active
```

### 4.2 ScheduledTask

```text
(AGENT.md 定义) → reconcile → enabled=1, retired=0
enabled=1 ⇄ enabled=0（PUT /schedules/{id}）
定义删除 / key 变化 → retired=1（不再调度，历史保留）
```

### 4.3 Tool 调用

```text
模型请求 → 执行（可能重试 0–3 次） → success / failed（一行审计）
未知 Tool / 沙箱违规 / MCP 失联 → failed（一行审计）
```

### 4.4 LLM 调用

```text
请求 → success / failed（一行审计，失败也写）
```

---

## 5. 校验规则汇总

- Agent 名、schedule key、notify channel name：`^[a-z0-9][a-z0-9-]{0,62}$`；Agent 名 = 目录名。
- Provider 名必须存在于 ProviderRegistry；模型名非空；temperature ∈ [0,2]。
- `tools` 中每个名称必须已注册；`mcp_servers` 中每个名称必须存在；MCP Tool 必须声明对应 server。
- Skill 软连接必须相对、位于 `.lytgos/skills/` 内；拒绝绝对路径与逃逸。
- Schedule key 同 Profile 唯一；cron 必须是合法 6 字段；zone 必须是合法 IANA。
- Notify channel URL 必须 http/https、host 命中 HTTP 白名单、不能包含明文密钥。
- Session status 只能是 `active`/`archived`；归档写入拒绝。
- 单条消息 ≤ 32KB；历史最多返回 100 条；`max_iterations` 1–50；`max_history_turns` 1–100。
- Tool 重试仅对 `retryable=true`；最多 3 次重试；审计 `retry_count` 0–3。
- 无状态 invoke 的审计 `session_id` 为 NULL；不得创建 Session。
- 日志/错误不得包含密钥、堆栈或内部绝对路径。

---

## 6. 并发、一致性与保留

- **Session 并发**：按 `session_id` 的公平 `ReentrantLock` 串行化；同一 Session 的请求按到达顺序处理。
- **SQLite**：`journal_mode=WAL`、`busy_timeout=5000`、`foreign_keys=ON`；短事务；单写者。
- **Scheduler**：按 `schedule_id` 的 `ReentrantLock`；重叠触发跳过，不排队。
- **审计**：与调用同步写入；写失败返回内部错误并记录日志，不静默丢弃。
- **保留**：Session/审计/任务历史不物理删除；归档只改状态；notify channel 可删除；日志按滚动策略保留。
- **ID/时间**：Session 后缀用 ULID；`schedule_id` 为稳定自增整数；所有时间 ISO-8601 UTC；cron 使用配置的 zone。
