# MCP Configuration Contract: LytgOS2 1.0

**Feature**: `001-lytgos-runtime-kernel` | **File**: `.lytgos/mcp_servers.yaml` | **Transport**: stdio only

1.0 只支持 MCP **stdio** transport；SSE 属扩展阶段。LytgOS2 作为 MCP Client，在启动时连接配置的 server，调用 `tools/list`，把工具包装为 `LytgTool` 注册到 `ToolRegistry`。

## 1. 配置结构

```yaml
servers:
  tech-news:
    transport: stdio
    command: python3
    args: ["examples/mcp/tech-news-server/server.py"]
    env:
      NEWS_API_KEY: ${NEWS_API_KEY}
    timeout_seconds: 30
    enabled: true
```

| 字段 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `transport` | 是 | — | 1.0 只接受 `stdio`；其他值报配置错误 |
| `command` | 是 | — | 可执行文件；由 LytgOS2 直接启动，不经 shell |
| `args` | 否 | `[]` | 参数数组 |
| `env` | 否 | `{}` | 环境变量；值支持 `${ENV_VAR}` 占位 |
| `timeout_seconds` | 否 | 30 | 连接/工具调用超时；1–300 |
| `enabled` | 否 | `true` | `false` 时不启动/不注册工具 |

- Server 名（`tech-news`）必须匹配 `^[a-z0-9][a-z0-9-]{0,62}$`。
- `env` 中的 `${ENV_VAR}` 在启动时解析；缺失时记录告警，server 仍尝试启动（由 server 自己决定是否失败）。
- 配置文件不存在时视为没有 MCP server，不影响启动。

## 2. 生命周期

```text
启动
  → 读取 mcp_servers.yaml
  → 对每个 enabled server 启动 stdio 进程
  → initialize（协议版本/能力协商）
  → tools/list
  → 每个工具包装为 LytgTool，source = "mcp:<server>"
  → 注册到 ToolRegistry
运行
  → 模型请求 MCP Tool
  → ToolExecutor 调用 McpToolAdapter
  → tools/call
  → 结果文本回填；审计写 tool_invocations
失败
  → 传输超时/断连：尝试一次重连
  → 仍失败：返回 retryable=true 的结构化错误
  → 启动失败：记录日志，server 的工具不可用；不影响其他 Agent
```

- server 进程的 stdout 是 MCP 协议通道；日志必须写 stderr。
- LytgOS2 退出时关闭所有 MCP 进程。
- 1.0 不做工具热更新；`tools/list` 在启动时拉取一次，server 重启后需重启 LytgOS2 或触发重新加载（扩展阶段再补）。

## 3. Agent 引用

Agent 的 `AGENT.md`：

```yaml
mcp_servers:
  - tech-news
tools:
  - tech-news_search
  - tech-news_fetch
  - notify
```

- `mcp_servers` 中的名称必须存在于 `.lytgos/mcp_servers.yaml`。
- 实际可调用的 MCP Tool 由 `tools` 列表过滤；未列出的 Tool 不可用。
- 若某 Tool 的来源是 `mcp:<server>`，但 `mcp_servers` 未声明该 server，Agent 校验失败并记录错误。
- Agent 目录与 Skill 不会因为 MCP 配置而进入 ToolRegistry。

## 4. 工具适配与输出

- 工具名冲突：内置 Tool 优先；MCP Tool 与内置同名时，MCP Tool 注册失败并记录错误（不静默覆盖）。
- MCP Tool 的 `inputSchema` 直接使用 server 返回的 JSON Schema。
- `tools/call` 返回的文本内容作为 `ToolResult.content`；非文本内容序列化为 JSON 文本。
- 结果截断到 `lytgos.tools.max-result-chars`。
- MCP 协议错误：`retryable=false`；传输错误/超时：`retryable=true`。
- 每次逻辑调用写一行 `tool_invocations`，`tool_source="mcp:<server>"`。

## 5. 安全边界

- MCP server 是由管理员配置的可信进程，LytgOS2 不沙箱化 server 自身的文件/网络访问；server 负责自己的鉴权和外部访问。
- LytgOS2 只校验：配置文件语法、server 名、`transport=stdio`、`command` 非空、超时范围。
- `env` 中的密钥通过环境变量注入；不得把明文密钥写入 `mcp_servers.yaml` 或 `AGENT.md`。
- MCP Tool 的输出进入模型上下文前截断；不得包含未脱敏的密钥。
- 1.0 不暴露 MCP Server（只做 Client），不实现 SSE/Streamable HTTP，不做 Nacos MCP registry。

## 6. 示例（科技日报）

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

`examples/agents/daily-tech-news/AGENT.md`：

```yaml
mcp_servers: [tech-news]
tools: [tech-news_search, tech-news_fetch, recall_memory, save_memory, notify, read_file]
```

启动后 `lytgos tool list` 应显示 `tech-news_search` / `tech-news_fetch`，来源 `mcp:tech-news`。
