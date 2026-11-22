# Quick Start

Get LytgOS2 built, configured, and running your first Agent.

## Prerequisites

- **Java 21+** — required runtime baseline
- **Maven 3.9+** — to build from source
- At least one LLM provider API key (DeepSeek or Kimi in 1.0)

## Build

LytgOS2 is a Maven multi-module project:

```bash
mvn clean package
```

The runnable JAR lands in `lytgos-boot/target/`. The examples below use `lytgos <command>` as shorthand for:

```bash
java -jar lytgos-boot/target/lytgos-boot-*.jar <command>
```

## Configure a provider

Create an external `config/application.yml` and keep it out of version control. Use environment placeholders for credentials:

```yaml
lytgos:
  root: .lytgos
  providers:
    - name: deepseek
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com
    - name: kimi
      api-key: ${MOONSHOT_API_KEY}
      base-url: https://api.moonshot.cn/v1
```

```bash
export DEEPSEEK_API_KEY=sk-...
```

Never commit real keys. 1.0 has no encrypted secret store; environment variables and an external config file are the boundary.

## Initialize the workspace

```bash
lytgos init
```

This creates `.lytgos/` and the default bootstrap files. The command is idempotent: existing files are never overwritten. Check the result with:

```bash
lytgos status
```

## Create your first Agent

An Agent is one directory with an `AGENT.md`:

```bash
lytgos profile create weather-daily
```

Edit `.lytgos/agents/weather-daily/AGENT.md`:

```markdown
---
name: weather-daily
description: Daily weather reminder
identity:
  agent_name: Weather
  prompt: |
    You are a weather assistant. Check today’s weather and suggest clothing.
provider:
  name: deepseek
  model: deepseek-chat
tools:
  - http_get
  - notify
  - save_memory
  - recall_memory
channels:
  - name: cli
bootstrap:
  - AGENTS.md
  - SOUL.md
  - USER.md
schedules:
  - key: daily
    name: Daily weather
    cron: "0 0 8 * * *"
    zone: Asia/Shanghai
    message: Check today’s Beijing weather and send a dressing tip.
settings:
  max_iterations: 10
  max_history_turns: 20
---

You are a weather assistant. In the morning, check the Beijing weather and
send a short dressing suggestion to the `team-lark` notify channel.
```

Register a notify channel (webhook) if the Agent uses `notify`:

```bash
curl -X POST http://localhost:8080/api/v1/notify-channels \
  -H 'Content-Type: application/json' \
  -d '{"name":"team-lark","type":"webhook","url":"https://open.larksuite.com/open-apis/bot/v2/hook/xxxx","description":"Team Lark webhook"}'
```

The domain must be allowed by `http.allowed_domains` in the sandbox policy.

## Chat with the Agent

```bash
lytgos chat --profile weather-daily
```

## Start the REST service

```bash
lytgos serve
```

The server listens on port `8080` by default:

```bash
curl http://localhost:8080/api/v1/health
```

For daemon mode, use `lytgos gateway`.

## 1.0 demo path

The 1.0 acceptance demos follow this flow:

| Demo | What it validates | Required |
| --- | --- | --- |
| Daily weather | LLM + ReAct + HTTP + notify + Scheduler + Session query | Yes |
| Daily tech digest | Agent directory + global Skill symlink + MCP + Memory + notify + Scheduler | Yes |
| Daily GitHub digest | Agent directory + `scripts/` + dedicated Tool/MCP | Optional |

All three reuse the same `AgentService.process(...)` path for CLI, HTTP, and cron triggers.

## Next steps

- [Profile reference](/docs/profile) — every `AGENT.md` field
- [Tool system](/docs/tool) — the nine built-in Tools and the sandbox
- [REST API](/docs/api) — the 1.0 endpoint surface
- [CLI](/docs/cli) — the 12 commands
