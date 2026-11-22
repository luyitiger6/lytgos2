# Architecture

LytgOS2 is a Java 21 + Spring Boot 3.5.x single-binary Agent OS. The runtime kernel — LLM providers, the ReAct loop, memory, tools, the REST service, and the CLI — ships as one fat JAR. State lives in SQLite and in a workspace on the local filesystem (`.lytgos/` by default).

LytgOS2 1.0 is a **single-node runtime kernel**. The design leaves room for a distributed foundation and cross-node Agent collaboration, but those are roadmap phases, not 1.0 features.

![LytgOS2 system architecture](/images/architecture.svg)

## Four layers

```text
Access      CLI Channel · Web Service · AgentScheduler
                 ↓
Engine      AgentService → ReActLoop → PromptBuilder
                              ↓
                         ToolExecutor
                 ↓
Capability  ProviderService · MemoryService · ToolRegistry
                 ↓
Foundation  AgentLoader · ContextLoader · Session store
            SQLite · ConfigLoader
```

All three trigger sources call the same `AgentService.process(...)`. The engine does not know whether a message came from the CLI, HTTP, or cron.

![LytgOS2 layered architecture](/images/layer-diagram.svg)

## Agent model

One directory = one Agent:

```text
.lytgos/agents/<name>/
├── AGENT.md          # frontmatter = Profile, body = task instructions
├── skills/           # relative symlinks into .lytgos/skills/
├── scripts/          # optional, read on demand
└── REFERENCE.md      # optional, read on demand
```

- `AGENT.md` frontmatter is the runtime Profile: `name`, `description`, `identity`, `provider`, `tools`, `mcp_servers`, `channels`, `bootstrap`, `schedules`, and `settings`.
- The Markdown body is injected into the system prompt as this Agent's task instructions.
- Skills are bound with relative symlinks under the Agent's `skills/`; there is no `skills:` frontmatter field. Each turn only injects the Skill name, description, and local path. The Skill body, scripts, and references are read on demand through `read_file` / `shell`.
- `ContextLoader` does not cache. Editing `AGENT.md` or re-binding a Skill takes effect on the next turn.
- An Agent directory is a context source, not a Tool; it is not registered in `ToolRegistry`.

## Runtime flow

```text
message
  → append to Session
  → PromptBuilder: system prompt + date/time + memory + history + tool list
  → ProviderService.call()
  → no tool call? → final response
  → tool call? → ToolExecutor → Sandbox → execute
      → write tool_invocations → append result → loop
  → max_iterations reached → stop
```

The loop is self-implemented. Spring AI is used for provider protocol conversion and `@Tool` JSON Schema generation only; its automatic tool execution is disabled.

## Modules

| Module | Responsibility |
| --- | --- |
| `lytgos-core` | `LytgTool`, `Session`, `Profile`, `AgentLoader`, `ContextLoader`, `ReActLoop`, `PromptBuilder`, `ToolExecutor`, `AgentService`, `AgentScheduler` |
| `lytgos-provider` | `ProviderService`, function-calling adaptation, explicit provider name → `ChatModel` mapping |
| `lytgos-memory` | `MemoryService`, `LongTermMemoryStore`, `MarkdownMemoryStore`, memory tools |
| `lytgos-tool` | Nine built-in Tools, MCP client, `ToolRegistry`, `Sandbox` / `WhitelistSandbox`, notify adapters |
| `lytgos-channel-cli` | `CliChannel`, `lytgos chat` |
| `lytgos-web` | `WebServer`, six controllers, unified `ApiResponse`, OpenAPI, notify and scheduler support endpoints |
| `lytgos-storage` | SQLite, JPA repositories, Session/audit/scheduler/notify persistence, Flyway migrations |
| `lytgos-cli` | Picocli entry point, 12 commands, `ConfigLoader` |
| `lytgos-boot` | Spring Boot main class, auto-configuration, fat JAR packaging |

## Storage and audit

Six tables in `.lytgos/lytgos.db`:

| Table | Purpose |
| --- | --- |
| `sessions` | Session metadata and JSON conversation history |
| `tool_invocations` | One row per Tool call |
| `llm_calls` | One row per LLM call |
| `scheduled_tasks` | Schedule registration and runtime state |
| `task_executions` | Execution history |
| `notify_channels` | Global notify channel registry |

`tool_invocations` and `llm_calls` are written from the first usable version. Audit query endpoints are an extension; the rows themselves are recorded in 1.0.

## What 1.0 is not

LytgOS2 1.0 does not include authentication/RBAC, SSO, inbound IM channels, persona/knowledge modules, vector memory, tool policy, provider fallback, SSE/WebSocket streaming, a web dashboard, multi-tenancy, or distributed scheduling. It is a single-node runtime kernel for a trusted private deployment. See the [Roadmap](/docs/roadmap) for the phases.
