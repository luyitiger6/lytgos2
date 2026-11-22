# Profile 配置（AGENT.md frontmatter）

在 LytgOS2 中，**一个目录 = 一个 Agent**。Agent 是 `.lytgos/agents/<name>/` 下的一个目录，由其中的 `AGENT.md` 定义：

- **YAML frontmatter**——Agent 的 Profile：身份、provider/model、Tool、定时任务和运行时限制。
- **Markdown 正文**——Agent 的任务指令，注入 system prompt。

**不存在 `.lytgos/profiles/` 目录**。Profile 就是 frontmatter 本身。可选的 `skills/` 软连接、`scripts/` 和 `REFERENCE.md` 放在同一个目录里，通过 `read_file` / `shell` 按需读取；它们不写在 frontmatter 中。

## 完整示例

`.lytgos/agents/ops-agent/AGENT.md`：

```markdown
---
name: ops-agent
description: 负责部署和监控的运维助手

identity:
  agent_name: 运维小欧
  prompt: |
    You are a professional operations assistant.
    Be concise. Prefer shell commands over prose explanations.

provider:
  name: deepseek
  model: deepseek-chat
  temperature: 0.7

tools:
  - read_file
  - shell
  - http_get
  - save_memory
  - recall_memory
  - notify

mcp_servers:
  - github-mcp

channels:
  - name: cli

bootstrap:
  - AGENTS.md
  - SOUL.md
  - USER.md

schedules:
  - key: morning-check
    name: Morning check
    cron: "0 0 8 * * *"
    zone: Asia/Shanghai
    message: Run the morning health check.

settings:
  max_iterations: 10
  max_history_turns: 20
---

You are a professional operations assistant. When triggered, check disk and
memory usage, summarize anything abnormal, and send the summary to the
`ops-lark` notify channel.
```

闭合 `---` 下面的文本是正文：这个 Agent 的任务指令，和 Bootstrap 文件一起注入 system prompt。

## 顶层字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `name` | string | 是 | 唯一 Agent 标识。与目录名一致，用于 `lytgos chat --profile <name>` 和 `/api/v1/agents/{name}/invoke`。 |
| `description` | string | 否 | 人类可读描述，`lytgos profile list` 显示。 |
| `identity` | object | 是 | Agent 显示名和核心 prompt。 |
| `provider` | object | 是 | LLM Provider 和模型选择。 |
| `tools` | string 列表 | 否 | 这个 Agent 启用的 Tool 名称。必须存在于 `ToolRegistry`；未列出的 Tool 不会进入 LLM。 |
| `mcp_servers` | string 列表 | 否 | `mcp_servers.yaml` 中的 MCP server 名称。它们提供的 Tool 对该 Agent 可用。 |
| `channels` | object 列表 | 否 | 这个 Agent 启用的渠道。1.0 支持 `cli`。 |
| `bootstrap` | string 列表 | 否 | 按顺序注入 system prompt 的 Bootstrap 文件。 |
| `schedules` | object 列表 | 否 | 这个 Agent 的 cron 触发任务。 |
| `settings` | object | 否 | 运行时参数。 |

## identity

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `identity.agent_name` | string | 是 | CLI prompt 和审计记录中的显示名。 |
| `identity.prompt` | string | 是 | 核心 system prompt，在 Bootstrap、正文和记忆之前注入。 |

## provider

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `provider.name` | string | 是 | Provider 名称，通过显式 name → `ChatModel` 映射解析。 |
| `provider.model` | string | 是 | 传给 Provider API 的模型标识。 |
| `provider.temperature` | float | 否 | 采样温度。默认使用 Provider 配置。 |

API key 配置在 Provider 配置中，不写在 `AGENT.md`。不要硬编码明文密钥。

## tools

`tools` 列表控制 ReAct 循环期间可用的内置 Tool 和 MCP Tool。1.0 内置名称：

`read_file`、`write_file`、`list_dir`、`shell`、`http_get`、`http_post`、`save_memory`、`recall_memory`、`notify`。

使用 `lytgos tool list` 查看全部已注册名称。

## mcp_servers

MCP server 名称引用 `.lytgos/mcp_servers.yaml` 中的条目。1.0 支持 stdio transport。启动时注册 server 提供的 Tool，列出的 Agent 可以使用。

## channels

1.0 支持 CLI 渠道（`cli`）。飞书、企业微信、钉钉、Slack 等入站 IM 渠道属于扩展阶段。

## bootstrap

Bootstrap 文件按顺序注入 system prompt，提供跨 Agent 的工作区级上下文。

| 文件 | 谁写 | 用途 |
| --- | --- | --- |
| `AGENTS.md` | 人 | 项目级行为说明 |
| `SOUL.md` | 人 | Agent 人格、语气和风格 |
| `USER.md` | 仅人 | 用户偏好；Agent 只读，不写 |

从 LytgOS2 的视角看，`USER.md` 是只读的。Agent 可写的对应文件是 `MEMORY.md`，通过 `save_memory` 更新。

## schedules

`schedules` 声明 cron 触发任务。**定义**只来自 frontmatter；1.0 不通过 API 增删改 cron 定义。

```yaml
schedules:
  - key: morning-check
    name: Morning check
    cron: "0 0 8 * * *"     # Spring cron: 秒 分 时 日 月 周
    zone: Asia/Shanghai
    message: Run the morning health check.
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `key` | string | Agent 内的任务 key。LytgOS2 为运行操作分配稳定的全局 `scheduleId`。 |
| `name` | string | 显示名称。 |
| `cron` | string | Spring 6 段 cron 表达式。 |
| `zone` | string | 可选 IANA 时区。省略时使用 UTC。 |
| `message` | string | 任务触发时交给 Agent 的消息。 |

v2 API 可以列出任务、查看执行历史、立即执行和启停。修改 cron 表达式需要编辑 `AGENT.md` 并重新加载或重启。

## settings

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `settings.max_iterations` | int | `10` | 每条消息的最大 ReAct 循环次数。 |
| `settings.max_history_turns` | int | `20` | 注入 prompt 的最近对话轮次。 |

## Skill

Skill 是全局库，不是 frontmatter 字段：

```text
.lytgos/skills/<name>/          # 全局 Skill 实体
.lytgos/agents/<agent>/skills/  # 指向全局库的相对软连接
```

每轮只注入已绑定 Skill 的 name、description 和本地路径；正文、脚本和参考按需读取。软连接集合是绑定的唯一真相源。

## 1.0 边界

没有 `.lytgos/profiles/` 目录，没有动态 Agent 上传/生命周期 API，没有 Web 管理台，也没有 persona 或知识库模块。这些属于扩展阶段。
