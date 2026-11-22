# Data Model: Agent Provider（第 16 节）

## 1. Profile

Profile 是 Agent 的运行时配置，本节从 `.lytgos/profiles/*.yaml` 加载，放入 `ProfileRegistry` 的内存索引。

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | String | 是 | Profile 名称，内存索引的唯一键 |
| `description` | String | 否 | 面向人的描述 |
| `identity` | Identity | 否 | Agent 身份信息 |
| `provider` | ProviderRef | 是 | 本次使用的 provider、model、temperature |
| `tools` | List&lt;String&gt; | 否 | 可用 Tool 名称；默认空列表 |
| `skills` | List&lt;String&gt; | 否 | 课件兼容字段；默认空列表 |
| `mcp_servers` | List&lt;String&gt; | 否 | MCP server 名称；默认空列表 |
| `channels` | List&lt;String&gt; | 否 | 可用渠道；默认空列表 |
| `notify_channels` | List&lt;String&gt; | 否 | 兼容字段，本节不消费；默认空列表 |
| `schedules` | List&lt;ScheduleSpec&gt; | 否 | 定时任务定义；默认空列表 |
| `bootstrap` | List&lt;String&gt; | 否 | Bootstrap 文件列表；默认空列表 |
| `settings` | Settings | 否 | 运行时设置 |

### 1.1 嵌套类型

| 类型 | 字段 | 类型 | 说明 |
|---|---|---|---|
| `Identity` | `agent_name` | String | Agent 展示名称 |
| `Identity` | `prompt` | String | Agent 身份提示 |
| `ProviderRef` | `name` | String | provider 名称，必须在全局 provider 配置中存在 |
| `ProviderRef` | `model` | String | 本次调用的模型名称 |
| `ProviderRef` | `temperature` | Double | 采样温度；缺省使用 0.7 |
| `ScheduleSpec` | `cron` | String | cron 表达式 |
| `ScheduleSpec` | `zone` | String | 时区 |
| `ScheduleSpec` | `message` | String | 触发时发送给 Agent 的消息 |
| `Settings` | `max_iterations` | Integer | ReAct 最大轮数；缺省 10 |
| `Settings` | `max_history_turns` | Integer | 历史轮数上限；缺省 20 |

### 1.2 校验规则

- `name` 非空；重复名称保留首个并记录错误，不静默覆盖。
- `provider`、`provider.name`、`provider.model` 非空。
- `provider.name` 必须能在全局 `lytgos.providers` 配置中找到；否则该 Profile 加载失败并记录清晰错误。
- 单个 Profile 文件损坏或校验失败时，只影响该文件，不阻断其他 Profile。
- 字符串值中的 `${ENV_VAR}` 占位符按注入的环境变量解析器替换；测试可注入受控解析器。
- `temperature` 为空时使用 0.7；`settings` 为空时使用默认 `max_iterations=10`、`max_history_turns=20`。

## 2. Provider Connection（全局配置）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | String | 是 | provider 名称，Profile 通过它引用 |
| `base_url` | String | 是 | OpenAI 兼容 API 的 base URL |
| `api_key` | String | 否 | 凭证占位符；为空时不构造该 provider 的模型 |

配置来源：`lytgos.providers`。本节至少配置 `deepseek` 与 `kimi` 两个示例；缺少某个 key 不阻断启动，只影响使用该 provider 的调用。

## 3. LlmCall（`llm_calls` 表）

一次模型调用尝试对应一行；成功和失败都写；不记录 prompt/completion 原文，避免敏感内容进入审计表。

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | INTEGER | PK AUTOINCREMENT | 主键 |
| `session_id` | TEXT | NULL | 会话标识；无状态调用时为 NULL |
| `profile_name` | TEXT | NOT NULL | Profile 名称 |
| `provider` | TEXT | NOT NULL | provider 名称 |
| `model` | TEXT | NOT NULL | 实际模型 |
| `prompt_tokens` | INTEGER | NULL | 输入 token；未返回时 NULL |
| `completion_tokens` | INTEGER | NULL | 输出 token；未返回时 NULL |
| `total_tokens` | INTEGER | NULL | 总 token；未返回时 NULL |
| `duration_ms` | INTEGER | NOT NULL | 调用耗时，毫秒 |
| `success` | INTEGER | NOT NULL | 0/1，是否成功 |
| `error_message` | TEXT | NULL | 失败原因；成功时为 NULL |
| `created_at` | TEXT | NOT NULL | ISO-8601 时间戳 |

索引：

- `(session_id)`
- `(created_at)`
- `(provider, model, created_at)`

约束与行为：

- 只插入，不更新、不删除；每次模型调用尝试一行。
- 未知 provider 在模型调用前失败，不产生 `llm_calls` 行。
- 模型调用失败时先写 `success=0` 与 `error_message`，再把异常上抛。
- token 字段使用可空整数，兼容不同 provider 的返回。

## 4. LlmCallRecord（core 审计端口的数据载体）

`LlmCallAuditor` 端口接收一条与 `llm_calls` 对应的记录，字段如下：

| 字段 | 类型 | 说明 |
|---|---|---|
| `sessionId` | String | 会话标识，可空 |
| `profileName` | String | Profile 名称 |
| `provider` | String | provider 名称 |
| `model` | String | 模型名称 |
| `promptTokens` | Integer | 可空 |
| `completionTokens` | Integer | 可空 |
| `totalTokens` | Integer | 可空 |
| `durationMs` | long | 毫秒 |
| `success` | boolean | 是否成功 |
| `errorMessage` | String | 可空 |

`JpaLlmCallAuditor` 负责补 `created_at` 并写入 `LlmCall` 实体。

## 5. Tool Descriptor

工具说明只包含模型需要看到的元数据，不包含执行逻辑：

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` | String | 工具名称 |
| `description` | String | 工具描述 |
| `inputSchema` | String | 输入 JSON Schema |

`ToolSchemaAdapter` 把 `LytgTool` 的这三个字段映射为 Spring AI `ToolDefinition`，包装成 `ToolCallback`；`ToolCallback.call(...)` 直接拒绝执行。

## 6. 关系与规模

- `Profile.provider.name` 引用全局 `Provider Connection.name`，不建立数据库外键。
- `LlmCall.profile_name` 记录调用时使用的 Profile 名称；不建立外键，避免 Profile 文件重命名影响历史审计。
- `LlmCall.session_id` 可为空，支持后续无状态调用；本节不实现 Session 生命周期。
- 数据规模：每次模型调用新增一行；按 `created_at` 与 `session_id` 建索引，满足后续审计查询扩展。
- 本节只创建 `llm_calls` 一张表；其余核心表在对应课程/迭代中通过后续 Flyway 迁移增加。
