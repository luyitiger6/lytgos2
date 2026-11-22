# 工具体系

Agent 能调用的每一个能力都是一个 `LytgTool`——无论是内置文件读取器、HTTP 客户端、记忆操作，还是远程 MCP server。`ToolExecutor` 和 `ToolRegistry` 只和接口打交道，不关心 Tool 的来源。

## LytgTool 接口

```java
interface LytgTool {
  String getName();
  String getDescription();
  String getInputSchema();      // JSON Schema
  ToolResult execute(JsonNode input);
}
```

`ToolResult` 包含成功标识、结果内容、错误信息，以及是否可重试。

## 内置 Tool（9 个）

| 分组 | Tool | 说明 |
| --- | --- | --- |
| 文件 | `read_file` | 读取文件，受路径白名单限制 |
| 文件 | `write_file` | 写入文件，受路径白名单限制 |
| 文件 | `list_dir` | 列目录，受路径白名单限制 |
| Shell | `shell` | 执行白名单内的可执行文件与参数数组，带超时；不经 Shell 解释 |
| HTTP | `http_get` | HTTP GET，受域名白名单限制 |
| HTTP | `http_post` | HTTP POST，受域名白名单限制 |
| Memory | `save_memory` | 按 `scope` 写入长期记忆 |
| Memory | `recall_memory` | 按关键词检索归档记忆 |
| Notify | `notify` | 按渠道名推送消息 |

所有 Tool 都包装为 `LytgTool` 并注册到 `ToolRegistry`。ReAct 循环不感知它是内置 Tool、`@Tool` 注解 Tool，还是 MCP 代理 Tool。

## 注册与过滤

启动时，LytgOS2 扫描 `@Tool` Java Bean 和内置 Tool，连接配置的 MCP server，并把每个 Tool 包装成 `LytgTool`。Profile 的 `tools` 列表过滤出传给模型的子集；未列出的 Tool 不会进入 LLM。

```yaml
tools:
  - read_file
  - http_get
  - save_memory
  - recall_memory
  - notify
```

使用 `lytgos tool list` 查看已注册的名称。

## 三档扩展

| 档位 | 门槛 | 方式 |
| --- | --- | --- |
| 零代码 | 最低 | 写 `AGENT.md`，复用现成 MCP server |
| 轻代码 | 中等 | 自写 MCP server |
| 重代码 | 最高 | 实现原生 `@Tool` Java Bean |

MCP 是不写 Java 代码接入外部系统的主路径。1.0 **只支持 stdio transport**；SSE transport 属于扩展。MCP server 定义放在 `mcp_servers.yaml`。

## Sandbox

1.0 只有一个实现：`WhitelistSandbox`。

| 策略 | 示例 |
| --- | --- |
| `file.allowed_paths` | 工作区与配置的可读根目录 |
| `shell.allowed_commands` | 精确的可执行文件名 |
| `http.allowed_domains` | `*.github.com` 这类 host 通配符 |

Sandbox 处理 `../` 路径穿越，精确匹配 Shell 可执行文件，并在通配符匹配前解析 HTTP host。校验失败抛出 `SandboxViolationException`，并记录为失败的 Tool 调用。

LytgOS2 **不使用** Java `SecurityManager`；它在受支持的 JDK 上不可用。容器/microVM 隔离属于扩展。

## 审计

每次 Tool 调用写入 `tool_invocations`：Tool 名、输入、结果、成功、错误和耗时。无论 Sandbox 放行还是拒绝，都会写审计行。

## 1.0 边界

Tool Policy、Tool LRU 加载、异步 Tool 调用、MCP Server 暴露、SSE transport 和容器 Sandbox 都不属于 1.0。
