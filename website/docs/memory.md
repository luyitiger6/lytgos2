# Memory

`MemoryService` is the only memory facade. It hides two layers:

- **Session memory** — conversation history for a Session, stored in SQLite.
- **Long-term memory** — durable notes stored in `MEMORY.md`.

## Session memory

Each Session has metadata and a JSON conversation history in the `sessions` table. `AgentService` appends messages and persists the Session after processing; a restart does not lose history. The prompt includes recent turns up to `max_history_turns` (default 20). The HTTP API returns at most the latest 100 messages per Session.

## Long-term memory

1.0 has one long-term backend: `MarkdownMemoryStore`.

```text
.lytgos/memory/MEMORY.md
```

The file has two top-level sections:

```md
## 核心记忆
- The weekly report goes out every Friday at 17:00.

## 归档记忆
- 2026-11: release window is Thursday evening.
```

- **Core memory** — injected into the system prompt in full, never truncated, and not searched.
- **Archive memory** — can be truncated and is searched by keyword.

An Agent writes memory explicitly through the `save_memory` Tool:

```json
{"content": "Weekly report goes out every Friday 17:00", "scope": "core"}
```

and recalls archive memory through `recall_memory`:

```json
{"query": "report schedule"}
```

There is no automatic fact extraction in 1.0. The Agent decides what to remember.

## USER.md vs MEMORY.md

| File | Who writes it | Purpose |
| --- | --- | --- |
| `USER.md` | Human | User preferences and background. Read-only from LytgOS2's perspective. |
| `MEMORY.md` | Agent via `save_memory` | The Agent's own growing memory. |

Both are injected into the system prompt. `USER.md` is authored by the user; `MEMORY.md` is written by the Agent.

## Boundaries

1.0 does not include vector retrieval, semantic recall, automatic memory extraction, episodic memory, Memory Wiki, or SQLite/Mem0 backends. `LongTermMemoryStore` is the extension point for those backends. The Markdown backend is the only implementation in 1.0.
