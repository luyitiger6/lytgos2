# Profile Reference (AGENT.md frontmatter)

In LytgOS2, **one directory = one Agent**. An Agent is a directory at `.lytgos/agents/<name>/`; its `AGENT.md` defines it:

- **YAML frontmatter** — the Agent's Profile: identity, provider/model, tools, schedules, and runtime limits.
- **Markdown body** — the Agent's task instructions, injected into the system prompt.

There is **no `.lytgos/profiles/` directory**. The Profile *is* the frontmatter. Optional `skills/` symlinks, `scripts/`, and `REFERENCE.md` live in the same directory and are read on demand through `read_file` / `shell`; they are not declared in frontmatter.

## Full example

`.lytgos/agents/ops-agent/AGENT.md`:

```markdown
---
name: ops-agent
description: Operations assistant for deployment and monitoring tasks

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

The text below the closing `---` is the body: this Agent's task instructions, injected into the system prompt alongside the bootstrap files.

## Top-level fields

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `name` | string | yes | Unique Agent identifier. Matches the directory name and is used by `lytgos chat --profile <name>` and `/api/v1/agents/{name}/invoke`. |
| `description` | string | no | Human-readable description shown by `lytgos profile list`. |
| `identity` | object | yes | Agent display name and core prompt. |
| `provider` | object | yes | LLM provider and model selection. |
| `tools` | list of strings | no | Tool names enabled for this Agent. Must exist in `ToolRegistry`. Unlisted tools never reach the LLM. |
| `mcp_servers` | list of strings | no | MCP server names from `mcp_servers.yaml`. Their tools become available to the Agent. |
| `channels` | list of objects | no | Channels this Agent is active on. `cli` is supported in 1.0. |
| `bootstrap` | list of strings | no | Bootstrap files prepended to the system prompt, in order. |
| `schedules` | list of objects | no | Cron-triggered runs of this Agent. |
| `settings` | object | no | Runtime tuning. |

## identity

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `identity.agent_name` | string | yes | Display name in the CLI prompt and audit records. |
| `identity.prompt` | string | yes | Core system prompt, injected before bootstrap files, the body, and memory. |

## provider

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `provider.name` | string | yes | Provider name, resolved through the explicit name → `ChatModel` map. |
| `provider.model` | string | yes | Model identifier passed to the provider API. |
| `provider.temperature` | float | no | Sampling temperature. Defaults to the provider configuration. |

The API key is configured in the provider config, not in `AGENT.md`. Never hardcode a raw key.

## tools

The `tools` list controls which built-in and MCP-sourced tools are available during the ReAct loop. 1.0 built-in names:

`read_file`, `write_file`, `list_dir`, `shell`, `http_get`, `http_post`, `save_memory`, `recall_memory`, `notify`.

Run `lytgos tool list` to see all registered names.

## mcp_servers

MCP server names refer to entries in `.lytgos/mcp_servers.yaml`. 1.0 supports stdio transport. The server's tools are registered at startup and become available to Agents that list the server.

## channels

1.0 supports the CLI channel (`cli`). Inbound IM channels (Feishu, WeCom, DingTalk, Slack) are extension-phase work.

## bootstrap

Bootstrap files are prepended to the system prompt in order. They apply workspace-wide context across Agents.

| File | Who writes it | Purpose |
| --- | --- | --- |
| `AGENTS.md` | Human | Project-level behavior notes |
| `SOUL.md` | Human | Agent personality, tone, and style |
| `USER.md` | Human only | User preferences; Agents read but never write it |

`USER.md` is read-only from LytgOS2's perspective. The Agent-writable equivalent is `MEMORY.md`, updated through `save_memory`.

## schedules

`schedules` declares cron-triggered runs. The **definition** comes only from frontmatter; 1.0 does not create or edit cron definitions through the API.

```yaml
schedules:
  - key: morning-check
    name: Morning check
    cron: "0 0 8 * * *"     # Spring cron: sec min hour day month weekday
    zone: Asia/Shanghai
    message: Run the morning health check.
```

| Field | Type | Description |
| --- | --- | --- |
| `key` | string | Agent-local schedule key. LytgOS2 assigns a stable global `scheduleId` for runtime operations. |
| `name` | string | Display name. |
| `cron` | string | Spring 6-field cron expression. |
| `zone` | string | Optional IANA time zone. Omitting it uses UTC. |
| `message` | string | Message handed to the Agent when the schedule fires. |

The v2 API can list schedules, read execution history, run one immediately, and enable/disable it. Changing the cron expression requires editing `AGENT.md` and reloading or restarting.

## settings

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `settings.max_iterations` | int | `10` | Maximum ReAct loop iterations per message. |
| `settings.max_history_turns` | int | `20` | Recent conversation turns included in the prompt. |

## Skills

Skills are a global library, not a frontmatter field:

```text
.lytgos/skills/<name>/          # global Skill entity
.lytgos/agents/<agent>/skills/  # relative symlinks into the global library
```

Each turn injects only the bound Skill's name, description, and local path. The body, scripts, and references are read on demand. The symlink set is the single source of truth for binding.

## 1.0 boundaries

There is no `.lytgos/profiles/` directory, no dynamic Agent upload/lifecycle API, no web admin console, and no persona or knowledge module. Those belong to extension phases.
