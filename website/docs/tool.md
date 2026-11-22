# Tool System

Every capability an Agent can call is a `LytgTool` — a built-in file reader, an HTTP client, a memory operation, or a remote MCP server. `ToolExecutor` and `ToolRegistry` work through the interface and do not care where the Tool comes from.

## LytgTool interface

```java
interface LytgTool {
  String getName();
  String getDescription();
  String getInputSchema();      // JSON Schema
  ToolResult execute(JsonNode input);
}
```

`ToolResult` carries the success flag, result content, error message, and whether the call is retryable.

## Built-in Tools (9)

| Group | Tool | Description |
| --- | --- | --- |
| File | `read_file` | Read a file, path whitelist enforced |
| File | `write_file` | Write a file, path whitelist enforced |
| File | `list_dir` | List a directory, path whitelist enforced |
| Shell | `shell` | Execute a whitelisted executable and argument array, with a timeout; no shell interpretation |
| HTTP | `http_get` | HTTP GET, domain whitelist enforced |
| HTTP | `http_post` | HTTP POST, domain whitelist enforced |
| Memory | `save_memory` | Write long-term memory with a `scope` |
| Memory | `recall_memory` | Keyword search over archive memory |
| Notify | `notify` | Push a message to a notify channel by name |

All tools are wrapped as `LytgTool` and registered in `ToolRegistry`. The ReAct loop does not know whether a tool is built in, annotated with `@Tool`, or proxied from MCP.

## Registration and filtering

At startup, LytgOS2 scans `@Tool` Java Beans and built-in tools, connects configured MCP servers, and wraps every tool as a `LytgTool`. The Profile's `tools` list filters the subset passed to the model; unlisted tools never reach the LLM.

```yaml
tools:
  - read_file
  - http_get
  - save_memory
  - recall_memory
  - notify
```

Use `lytgos tool list` to inspect the registered names.

## Three extension tiers

| Tier | Effort | How |
| --- | --- | --- |
| Zero-code | Lowest | Write an `AGENT.md` and reuse an existing MCP server |
| Light-code | Medium | Write a custom MCP server |
| Heavy-code | Highest | Implement a native `@Tool` Java Bean |

MCP is the main path for connecting external systems without writing Java code. 1.0 supports **stdio transport only**; SSE transport is an extension. MCP server definitions live in `mcp_servers.yaml`.

## Sandbox

1.0 has one sandbox implementation: `WhitelistSandbox`.

| Policy | Example |
| --- | --- |
| `file.allowed_paths` | Workspace and configured readable roots |
| `shell.allowed_commands` | Exact executable names |
| `http.allowed_domains` | Host wildcards such as `*.github.com` |

The sandbox handles `../` path traversal, matches shell executables exactly, and parses the HTTP host before wildcard matching. A violation raises `SandboxViolationException` and is recorded as a failed tool call.

LytgOS2 does **not** use Java `SecurityManager`; it is unavailable on supported JDKs. Container/microVM isolation is an extension.

## Audit

Every tool call writes a `tool_invocations` row: tool name, input, result, success, error, and duration. The audit row is written whether the sandbox allowed or rejected the call.

## 1.0 boundaries

Tool Policy, Tool LRU loading, async tool calls, MCP server exposure, SSE transport, and container sandboxes are not part of 1.0.
