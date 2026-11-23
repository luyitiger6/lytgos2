# Data Model: CLI and Session Persistence

## 1. 领域模型

### 1.1 Session（`lytgos-core/io.lytgos.core.session.Session`）

| 字段 | Java 类型 | 约束 | 说明 |
|---|---|---|---|
| `sessionIdentifier` | `String` | 非空；只由 `SessionManager` 生成 | 会话唯一标识，格式 `channel:user:profileName` |
| `agentProfileName` | `String` | 非空 | 所属 AgentProfile 名称 |
| `channelName` | `String` | 非空（构造器把 null 归一为空字符串，`SessionManager` 拒绝空值） | 接入渠道，CLI 固定为 `cli` |
| `userIdentifier` | `String` | 非空（同上） | 用户标识，CLI 默认取当前系统用户名 |
| `history` | `List<AgentMessage>` | 有序、可追加、不可外部修改 | 会话消息历史；通过 `messages()` 获取快照 |

现有方法 `append`、`appendUser`、`appendAssistant`、`appendToolResult`、`messages()`、`recentMessages(maxTurns)`、`messageCount()`、`sessionId()`、`profileName()`、`channel()`、`userId()` 保持不变。第 18 节不新增 Session 字段。

### 1.2 AgentMessage（`lytgos-core/io.lytgos.core.llm.AgentMessage`）

| 字段 | Java 类型 | 约束 | 说明 |
|---|---|---|---|
| `role` | `AgentRole` | 非空；`SYSTEM`/`USER`/`ASSISTANT`/`TOOL` | 消息角色 |
| `content` | `String` | null 归一为空字符串 | 文本内容；工具消息为工具结果 |
| `toolName` | `String` | 可空 | 工具消息的工具名 |
| `toolCallId` | `String` | 可空 | 工具结果关联的模型调用 ID |
| `toolCalls` | `List<AgentToolCall>` | 不可变复制，缺省空列表 | Agent 回复携带的工具调用 |

### 1.3 AgentToolCall（`lytgos-core/io.lytgos.core.llm.AgentToolCall`）

| 字段 | Java 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `String` | null 归一为空字符串 | 模型提供的调用 ID |
| `name` | `String` | 非空 | 工具名 |
| `arguments` | `String` | 空白归一为 `{}` | 工具输入 JSON |

## 2. 会话门面

### 2.1 SessionManager（`lytgos-core/io.lytgos.core.session.SessionManager`）

| 方法 | 签名 | 契约 |
|---|---|---|
| 获取或创建 | `Session getOrCreate(String channel, String user, String profileName)` | 三个参数非空白且不含 `:`；同一三元组重复调用返回同一会话；不存在时创建并保存空历史会话 |
| 按标识查询 | `Optional<Session> get(String sessionId)` | 标识为空或不存在时返回 `Optional.empty()`；存在时还原完整历史 |
| 保存 | `void save(Session session)` | 新增或更新 `messages_json`、`last_active_at`；保存失败抛出运行时异常 |

`session_id` 生成规则（仅 `JpaSessionManager` 内部）：

```text
session_id = channel + ":" + user + ":" + profileName
```

示例：`cli:luyi:default`。

## 3. 持久化模型

### 3.1 SessionEntity（`lytgos-storage/io.lytgos.storage.session.SessionEntity`）

| 字段 | Java 类型 | 数据库列 | 约束 | 说明 |
|---|---|---|---|---|
| `sessionId` | `String` | `session_id` | 主键，非空 | 会话标识 |
| `profileName` | `String` | `profile_name` | 非空 | AgentProfile 名称 |
| `channel` | `String` | `channel` | 非空 | 接入渠道 |
| `userId` | `String` | `user_id` | 非空 | 用户标识 |
| `messagesJson` | `String` | `messages_json` | 非空，默认 `[]` | 消息历史 JSON 数组 |
| `status` | `String` | `status` | 非空，默认 `ACTIVE`；取值 `ACTIVE`/`ARCHIVED` | 会话状态 |
| `createdAt` | `String` | `created_at` | 非空；ISO-8601 UTC 文本 | 创建时间 |
| `lastActiveAt` | `String` | `last_active_at` | 非空；每次 `save` 更新；ISO-8601 UTC 文本 | 最后活跃时间 |
| `archivedAt` | `String` | `archived_at` | 可空；`ARCHIVED` 时非空；ISO-8601 UTC 文本 | 归档时间 |

第 18 节只写入 `ACTIVE`，不提供归档命令；`ARCHIVED` 留给后续扩展。

### 3.2 表结构（Flyway `V3__init.sql`）

```sql
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

CREATE INDEX IF NOT EXISTS idx_sessions_last_active_at ON sessions (last_active_at);
```

时间以 ISO-8601 字符串（`Instant.toString()`）存储，与 V1/V2 的文本时间列保持一致。

### 3.3 SessionJsonCodec（`lytgos-storage/io.lytgos.storage.session.SessionJsonCodec`）

| 方法 | 签名 | 契约 |
|---|---|---|
| 编码 | `String encode(List<AgentMessage> messages)` | `null` 或空列表编码为 `[]`；输出合法 JSON 数组 |
| 解码 | `List<AgentMessage> decode(String json)` | `null`/空白返回空列表；非法 JSON 抛出 `IllegalStateException`，不静默丢历史 |

JSON 结构直接复用 record 字段名：

```json
[
  {"role":"USER","content":"你好","toolName":null,"toolCallId":null,"toolCalls":[]},
  {"role":"ASSISTANT","content":"你好，有什么可以帮你？","toolName":null,"toolCallId":null,"toolCalls":[]}
]
```

## 4. CLI 视图模型

### 4.1 WorkspacePaths（`lytgos-cli/io.lytgos.cli.workspace.WorkspacePaths`）

| 字段/方法 | 类型 | 说明 |
|---|---|---|
| `root` | `Path` | 工作区根，默认 `.lytgos` |
| `agents` | `Path` | `.lytgos/agents` |
| `skills` | `Path` | `.lytgos/skills` |
| `memory` | `Path` | `.lytgos/memory` |
| `sessions` | `Path` | `.lytgos/sessions` |
| `logs` | `Path` | `.lytgos/logs` |
| `profiles` | `Path` | `.lytgos/profiles` |
| `database` | `Path` | `.lytgos/lytgos.db` |

### 4.2 ProviderView（`lytgos-cli/io.lytgos.cli.workspace.ProviderView`）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `name` | `String` | 非空 | provider 名称 |
| `baseUrl` | `String` | 可空 | 连接地址 |

`ProviderView` 不包含 api-key 字段，从类型层面阻止凭证输出。

### 4.3 ProfileSummary（`lytgos-cli/io.lytgos.cli.profile`）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `name` | `String` | 非空 | AgentProfile 名称（文件名去扩展名） |
| `provider` | `String` | 可空 | YAML 中的 `provider.name` |
| `model` | `String` | 可空 | YAML 中的 `provider.model` |
| `path` | `Path` | 非空 | 对应 YAML 文件路径 |

### 4.4 SessionSummary（`lytgos-cli/io.lytgos.cli.session`）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `sessionId` | `String` | 非空 | 会话标识 |
| `profileName` | `String` | 非空 | AgentProfile 名称 |
| `channel` | `String` | 非空 | 接入渠道 |
| `userId` | `String` | 非空 | 用户标识 |
| `status` | `String` | 非空 | `ACTIVE`/`ARCHIVED` |
| `lastActiveAt` | `String` | 非空 | 最后活跃时间文本 |

## 5. 默认工作区与 Profile 模板

### 5.1 工作区布局

```text
.lytgos/
├── agents/
├── skills/
├── memory/
├── sessions/
├── logs/
├── profiles/
│   └── default.yaml
├── AGENTS.md
├── SOUL.md
├── USER.md
├── MEMORY.md
├── mcp_servers.yaml
└── lytgos.db            # 首次启动运行时后由 Flyway 创建
```

`AGENTS.md`、`SOUL.md`、`USER.md`、`MEMORY.md`、`mcp_servers.yaml` 为空或带一行说明的占位文件；已存在时跳过，不覆盖。

### 5.2 默认 Profile 模板（`profiles/default.yaml`）

```yaml
name: default
description: 默认 AgentProfile，由 lytgos init 创建
provider:
  name: deepseek
  model: deepseek-chat
  temperature: 0.7
settings:
  max_iterations: 10
  max_history_turns: 20
```

`profile create <name>` 生成同样的模板，只把 `name` 替换为目标名称；名称只允许 `[A-Za-z0-9_-]+`。

## 6. 关系与生命周期

- 一个 `Session` 对应 `sessions` 表一行；`Session.history` 与 `messages_json` 一一对应。
- 一个 channel/user/profile 三元组对应一个 `Session`；三元组变化即新行。
- 创建：`getOrCreate` 在内存构造 `Session`，`save` 时首次插入。
- 更新：每次 `AgentService.process` 成功后 `save`，覆盖 `messages_json` 并刷新 `last_active_at`。
- 删除：第 18 节不提供删除；归档字段为后续扩展预留。
- CLI 轻命令只读 `sessions` 表，不写；写路径只经过 `JpaSessionManager`。
