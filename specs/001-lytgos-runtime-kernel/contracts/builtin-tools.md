# Built-in Tool Contract: LytgOS2 1.0

**Feature**: `001-lytgos-runtime-kernel` | **Registry**: `ToolRegistry` | **Abstraction**: `LytgTool`

1.0 固定 9 个内置 Tool。所有 Tool 统一包装为 `LytgTool`，注册到 `ToolRegistry`；ReAct 循环不感知来源。Tool 的输入是 JSON 对象字符串，输出是 `ToolResult`：

```java
record ToolResult(boolean success, String content, String error, boolean retryable) {}
```

通用规则：

- 每次**模型请求**的 Tool 调用写一行 `tool_invocations`；重试不新增行。
- 沙箱违规、未知 Tool、参数非法、外部失败都写失败审计，并把结构化错误回填给模型。
- Tool 返回给模型的内容默认截断到 `lytgos.tools.max-result-chars`（默认 8000）；审计 `result_json` 保存同样的截断内容并带 `truncated` 标记。
- 所有文件/Shell/HTTP 访问必须经过 `WhitelistSandbox`；默认白名单为空/仅工作区。
- 失败错误必须可诊断，不包含密钥、堆栈或工作区外的绝对路径。

---

## 1. `read_file`

**描述**：读取白名单路径内的文件。

```json
{
  "path": ".lytgos/agents/daily-weather/REFERENCE.md",
  "offset_bytes": 0,
  "max_bytes": 262144
}
```

| 字段 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `path` | 是 | — | 相对工作区或绝对路径；必须命中 `file.allowed-paths` |
| `offset_bytes` | 否 | 0 | 从文件偏移读取 |
| `max_bytes` | 否 | 262144 | 最大读取字节；上限 1MB |

- 成功：`{ "content": "...", "bytesRead": 123, "truncated": false }`
- 失败：路径不在白名单/不存在/是目录 → `retryable=false`。
- 沙箱：`Sandbox.enforce(FILE_READ, path)`；处理 `../`、软链接逃逸。

## 2. `write_file`

**描述**：写入白名单路径内的文件。

```json
{
  "path": ".lytgos/output/weather.md",
  "content": "# 今日天气\n..."
}
```

| 字段 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `path` | 是 | — | 必须命中 `file.allowed-paths` |
| `content` | 是 | — | ≤ 1MB |

- 成功：`{ "path": "...", "bytesWritten": 456 }`
- 父目录不存在时在允许范围内创建；不覆盖工作区外文件。
- 失败：沙箱违规/IO 错误 → `retryable=false`。
- 沙箱：`Sandbox.enforce(FILE_WRITE, path)`。

## 3. `list_dir`

**描述**：列出白名单路径内的目录条目。

```json
{ "path": ".lytgos/agents" }
```

- 成功：`{ "entries": [{"name":"daily-weather","type":"directory"}], "truncated": false }`
- 最多返回 200 条；超出时 `truncated=true`。
- 失败：路径不在白名单/不存在/不是目录 → `retryable=false`。

## 4. `shell`

**描述**：执行白名单内的可执行文件与参数数组，不经 Shell 解释。

```json
{
  "command": "python3",
  "args": ["scripts/collect.py", "--date", "2026-11-22"],
  "cwd": ".lytgos/agents/daily-tech-news",
  "timeout_seconds": 30
}
```

| 字段 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `command` | 是 | — | 可执行文件名/路径；精确匹配 `shell.allowed-commands` |
| `args` | 否 | `[]` | 参数数组，逐个传递，不经 shell |
| `cwd` | 否 | 工作区根 | 必须命中 `file.allowed-paths` |
| `timeout_seconds` | 否 | 30 | 1–300 |

- 成功：`{ "exitCode": 0, "stdout": "...", "stderr": "", "timedOut": false }`
- 超时：`retryable=true`；非零退出：`retryable=false`；沙箱违规：`retryable=false`。
- 输出截断到 `max-result-chars`；`truncated` 标记。
- 安全提示：白名单是管理员责任；只应加入不会读取环境变量或工作区外文件的命令。

## 5. `http_get`

**描述**：对白名单域名发起 HTTP GET。

```json
{
  "url": "https://api.open-meteo.com/v1/forecast?...",
  "headers": { "Accept": "application/json" },
  "timeout_seconds": 15
}
```

| 字段 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `url` | 是 | — | `http`/`https`；host 命中 `http.allowed-domains` |
| `headers` | 否 | `{}` | 不接受 `Host` 覆盖；不自动注入密钥 |
| `timeout_seconds` | 否 | 15 | 1–60 |

- 成功：`{ "status": 200, "headers": {"content-type": "..."}, "body": "...", "truncated": false }`
- 响应体上限 1MB；超出截断。
- 5xx/超时：`retryable=true`；4xx/沙箱违规：`retryable=false`。
- 沙箱：解析 URL host 后匹配；拒绝 IP 字面量以外的非白名单域名；重定向目标也必须重新校验。

## 6. `http_post`

**描述**：对白名单域名发起 HTTP POST。

```json
{
  "url": "https://api.example.com/v1/analyze",
  "body": "{\"text\":\"...\"}",
  "content_type": "application/json",
  "headers": {},
  "timeout_seconds": 15
}
```

- 请求体 ≤ 1MB；响应处理与 `http_get` 相同。
- 5xx/超时：`retryable=true`；4xx/沙箱违规：`retryable=false`。
- 不允许模型指定任意 `Host`；重定向重新校验。

## 7. `save_memory`

**描述**：向长期记忆的核心区或归档区追加一条内容。

```json
{ "content": "用户偏好：回答使用中文", "scope": "core" }
```

| 字段 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `content` | 是 | — | ≤ 4KB；不能包含未转义的分区标题 |
| `scope` | 是 | — | `core` / `archival` |

- 成功：`{ "scope": "core", "saved": true }`
- 核心区永不截断；归档区超限时从最旧条目截断。
- 文件/分区缺失时自动补齐；失败 `retryable=false`。

## 8. `recall_memory`

**描述**：按关键词检索归档记忆（核心区不参与检索）。

```json
{ "query": "芯片", "limit": 10 }
```

| 字段 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `query` | 是 | — | 非空；不区分大小写 |
| `limit` | 否 | 10 | 1–50 |

- 成功：`{ "matches": ["- 2026-11-20：讨论过 AI 芯片方向"], "count": 1 }`
- 无命中：`{ "matches": [], "count": 0 }`，不是错误。
- `retryable=false`。

## 9. `notify`

**描述**：通过全局注册的通知渠道发送消息。

```json
{ "content": "北京今天 12°C，建议穿薄外套。", "channel": "team-webhook" }
```

| 字段 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `content` | 是 | — | ≤ 4KB |
| `channel` | 是 | — | `notify_channels.name`，不是 URL |

- 成功：`{ "channel": "team-webhook", "sent": true }`
- 渠道不存在/URL 不在白名单：`retryable=false`。
- 网络失败/5xx：`retryable=true`（最多 3 次重试由 `ToolExecutor` 控制）。
- 发送前复用 `Sandbox.enforce(HTTP_REQUEST, url)`；适配器按 `type` 生成企业微信/飞书/钉钉/generic JSON。
- 发送失败写失败审计，不导致 Agent 崩溃。

---

## 10. Plugin Tool 契约

### 10.1 MCP Tool

- 启动时通过 `tools/list` 获取 name/description/inputSchema；包装为 `LytgTool`，`source="mcp:<server>"`。
- `execute` 转发 `tools/call`；结果取文本内容；MCP 错误转为 `ToolResult`。
- 传输超时/断连：尝试一次重连；仍失败 `retryable=true`。
- 输入/输出受 `max-result-chars` 截断。

### 10.2 Java `@Tool`

- 使用 Spring AI `@Tool` 注解的方法；启动时扫描并包装为 `LytgTool`，`source="java"`。
- JSON Schema 由 Spring AI 生成；不启用 Spring AI 的自动执行。
- 参数校验失败 → `retryable=false`；IO/临时故障可标记 `retryable=true`（由适配器约定）。
- 与内置 Tool 使用同一个 `ToolRegistry` / `ToolExecutor` 路径。

### 10.3 未知 Tool

模型请求了不存在的 Tool：

- 不执行任何外部动作；
- 写一行失败 `tool_invocations`（`tool_name` 为请求名，`error_message="Tool not found"`）；
- 向模型回填结构化错误，循环继续。

---

## 11. 重试口径

- 仅 `retryable=true` 的错误重试；首次执行 + 最多 3 次重试（共 4 次尝试）。
- 退避默认 200/400/800ms；可配置。
- 同一次逻辑调用只写一行审计：`retry_count`、`attempt_errors_json`、最终结果。
- 沙箱违规、参数错误、未知 Tool、4xx 不重试。
