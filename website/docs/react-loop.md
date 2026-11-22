# ReAct Loop

The reasoning loop is self-implemented. LytgOS2 does not use Spring AI's Agent abstraction, and Spring AI's automatic tool execution is **disabled**: tool scheduling belongs to `ReActLoop` + `ToolExecutor`.

![ReAct Loop](/images/react-loop.svg)

## Inputs

The loop is triggered by any of the three entry points:

- a CLI message from `lytgos chat`
- an HTTP request to `POST /api/v1/agents/{name}/invoke` or `POST /api/v1/sessions/{id}/messages`
- a cron message from `AgentScheduler`

All three end at `AgentService.process(...)`.

## One iteration

```text
1. Append the user message to the Session
2. PromptBuilder assembles the prompt:
   system prompt (identity + AGENT.md body + bootstrap)
   + current date/time
   + core memory + recalled archive memory
   + recent conversation history
   + available Tool list
3. ProviderService calls the configured ChatModel
4. No tool call → return the final response
5. Tool call → ToolExecutor executes each call in order
   → Sandbox check
   → execute
   → write tool_invocations
   → append the Tool result to history
6. Repeat until a final response or max_iterations
```

## PromptBuilder

The prompt is assembled on every iteration; nothing is cached:

| Part | Content |
| --- | --- |
| System prompt | `identity` + `AGENT.md` body + bootstrap files (`AGENTS.md`, `SOUL.md`, `USER.md`) |
| Date/time | Current date and time, so scheduled tasks know what "today" means |
| Memory | Core memory (never truncated) and archive memory |
| History | Recent turns, truncated at `max_history_turns` (default 20) |
| Tools | The Tool subset enabled for this Agent |

## ToolExecutor

For each tool call, `ToolExecutor`:

1. looks up the `LytgTool` in `ToolRegistry`
2. enforces the `WhitelistSandbox` policy
3. executes the Tool
4. writes a `tool_invocations` row
5. returns the result to the loop

Multiple tool calls in one model response are executed sequentially, not in parallel.

## Limits

| Setting | Default | Meaning |
| --- | --- | --- |
| `settings.max_iterations` | `10` | Maximum loop iterations per message. At the limit, the Agent returns its partial result. |
| `settings.max_history_turns` | `20` | Recent conversation turns included in the prompt. |

1.0 does not summarize or compress old history; older turns are dropped. Summarization is an extension.

## Audit

Every LLM call writes to `llm_calls`, and every Tool call writes to `tool_invocations`. The audit rows are written from the first usable version, even though audit query endpoints are an extension.

## Boundaries

- Synchronous execution only; no SSE/WebSocket streaming.
- No async or parallel Tool execution.
- No multi-Agent orchestration inside the loop.
- No Spring AI automatic Tool execution.
