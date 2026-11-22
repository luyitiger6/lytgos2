# REST API Contract: LytgOS2 1.0

**Feature**: `001-lytgos-runtime-kernel` | **Base URL**: `http://<host>:8080/api`

所有端点由 `lytgos serve` 暴露。1.0 无认证、无 RBAC，默认按可信内网部署；CORS 全开。`Content-Type: application/json`。

## 1. 统一信封

```json
{
  "code": 0,
  "message": "OK",
  "data": { },
  "timestamp": "2026-11-22T08:00:00Z"
}
```

- 成功：`code=0`，`message="OK"`，`data` 为业务数据；HTTP 200/201。
- 失败：`code` 等于 HTTP 状态码（400/404/409/500/503/504），`message` 为可操作的中文错误，`data=null`；HTTP status 同步设置。
- 错误不得包含堆栈、内部绝对路径、API key 或完整请求头。

## 2. 核心 10 端点

### 2.1 创建 Session

`POST /api/v1/sessions`

```json
{ "profile": "daily-weather", "user": "alice" }
```

- `profile` 必填且必须存在；`user` 必填，1–64 字符。
- channel 固定为 `api`；每次调用都新建 Session，不复用 active。
- 响应 `201`：

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "sessionId": "api:alice:daily-weather:01JB8Z5X4K9Q2M3N4P5R6S7T8V",
    "profile": "daily-weather",
    "userId": "alice",
    "status": "active",
    "createdAt": "2026-11-22T08:00:00Z"
  },
  "timestamp": "2026-11-22T08:00:00Z"
}
```

### 2.2 发送消息

`POST /api/v1/sessions/{sessionId}/messages`

```json
{ "message": "北京今天天气如何？" }
```

- `message` 必填，≤ 32KB。
- 同步执行 ReAct 循环，最长 60 秒；超时返回 `504`。
- Session 不存在 → `404`；已归档 → `409`；Agent/Provider 错误 → `503`/`500`。
- 响应 `200`：

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "sessionId": "api:alice:daily-weather:01JB8Z5X4K9Q2M3N4P5R6S7T8V",
    "reply": "北京今天 12°C，建议穿薄外套。",
    "terminationReason": "stop",
    "toolCalls": [
      { "name": "http_get", "success": true, "durationMs": 182 }
    ]
  },
  "timestamp": "2026-11-22T08:00:03Z"
}
```

`terminationReason`：`stop`（正常结束）或 `max_iterations`（达到最大轮次）。`toolCalls` 只含本次请求内执行的逻辑 Tool 调用摘要。

### 2.3 查询 Session

`GET /api/v1/sessions/{sessionId}`

- 归档 Session 仍可查询历史。
- 消息最多返回最近 100 条；`truncated=true` 表示更早消息未返回。
- 响应 `200`：

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "sessionId": "api:alice:daily-weather:01JB8Z5X4K9Q2M3N4P5R6S7T8V",
    "profile": "daily-weather",
    "channel": "api",
    "userId": "alice",
    "status": "active",
    "createdAt": "2026-11-22T08:00:00Z",
    "lastActiveAt": "2026-11-22T08:00:03Z",
    "archivedAt": null,
    "messages": [
      { "role": "user", "content": "北京今天天气如何？", "createdAt": "2026-11-22T08:00:00Z" },
      { "role": "assistant", "content": "北京今天 12°C，建议穿薄外套。", "terminationReason": "stop", "createdAt": "2026-11-22T08:00:03Z" }
    ],
    "messageCount": 2,
    "truncated": false
  },
  "timestamp": "2026-11-22T08:00:04Z"
}
```

### 2.4 归档 Session

`DELETE /api/v1/sessions/{sessionId}`

- 将 `status` 置为 `archived`，写 `archived_at`；不物理删除。
- 已归档时幂等返回 200；不存在 → `404`。
- 归档后 `POST .../messages` → `409`。
- 响应 `200`：`data = { "sessionId": "...", "status": "archived", "archivedAt": "..." }`

### 2.5 无状态 Agent 调用

`POST /api/v1/agents/{name}/invoke`

```json
{ "message": "用一句话解释 ReAct。" }
```

- 不创建、不持久化 Session；不写入会话历史。
- `llm_calls`/`tool_invocations` 照常写入，`session_id` 为 `null`。
- 同步执行，最长 60 秒；超时 `504`；Agent 不存在 `404`；Provider 故障 `503`。
- 响应 `200`：

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "reply": "ReAct 是……",
    "terminationReason": "stop",
    "toolCalls": []
  },
  "timestamp": "2026-11-22T08:00:03Z"
}
```

### 2.6 列出 Profile

`GET /api/v1/profiles`

```json
{
  "code": 0,
  "message": "OK",
  "data": [
    {
      "name": "daily-weather",
      "description": "每日天气与穿搭建议",
      "provider": "deepseek",
      "model": "deepseek-chat",
      "tools": ["http_get", "notify"],
      "channels": ["cli"],
      "schedules": [
        { "key": "daily", "cron": "0 0 8 * * *", "zone": "Asia/Shanghai", "enabled": true }
      ]
    }
  ],
  "timestamp": "2026-11-22T08:00:00Z"
}
```

### 2.7 查询长期记忆

`GET /api/v1/memory`

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "core": "- 用户偏好：回答使用中文\n- 项目：部署在 K8s",
    "archival": "- 2026-11-20：讨论过 AI 芯片方向",
    "archivalTruncated": false
  },
  "timestamp": "2026-11-22T08:00:00Z"
}
```

- `archival` 为按 `archival-max-chars` 截断后的内容；`core` 永不截断。
- 文件不存在时返回空字符串，不报错。

### 2.8 列出 Tool

`GET /api/v1/tools`

```json
{
  "code": 0,
  "message": "OK",
  "data": [
    { "name": "read_file", "description": "读取白名单内文件", "source": "builtin" },
    { "name": "github_search", "description": "搜索 GitHub", "source": "mcp:github" }
  ],
  "timestamp": "2026-11-22T08:00:00Z"
}
```

- 只列可被 Agent 调用的 Tool；Agent 目录与 Skill **不出现**。
- `source`：`builtin` / `mcp:<server>` / `java`。

### 2.9 健康检查

`GET /api/v1/health`

```json
{
  "code": 0,
  "message": "OK",
  "data": { "status": "UP", "name": "LytgOS2", "version": "1.0.0", "timestamp": "2026-11-22T08:00:00Z" },
  "timestamp": "2026-11-22T08:00:00Z"
}
```

### 2.10 运行信息

`GET /api/v1/info`

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "name": "LytgOS2",
    "version": "1.0.0",
    "modules": 9,
    "runtimeMode": "serve",
    "providers": ["deepseek", "kimi"],
    "profiles": 2,
    "tools": 9
  },
  "timestamp": "2026-11-22T08:00:00Z"
}
```

- 不返回 API key、工作区绝对路径或审计内容。

## 3. 1.0 支撑端点

### 3.1 通知渠道 CRUD

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/notify-channels` | 列出渠道 |
| POST | `/api/v1/notify-channels` | 创建 |
| PUT | `/api/v1/notify-channels/{id}` | 更新 |
| DELETE | `/api/v1/notify-channels/{id}` | 删除 |

创建请求：

```json
{
  "name": "team-webhook",
  "type": "wecom",
  "url": "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=...",
  "description": "每日天气推送",
  "config": {}
}
```

- `name` 唯一，匹配 `^[a-z0-9][a-z0-9-]{0,62}$`。
- `type`：`wecom` / `feishu` / `dingtalk` / `generic`。
- URL 必须 `http`/`https`，host 命中 `lytgos.sandbox.http.allowed-domains`，否则 `400`。
- 响应 `data` 含 `id`、`name`、`type`、`url`、`description`、`config`、`createdAt`、`updatedAt`。

### 3.2 定时任务 v2

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v2/schedules` | 列出任务（含 retired） |
| GET | `/api/v2/schedules/{scheduleId}/executions` | 执行历史，按 `startedAt` 倒序 |
| POST | `/api/v2/schedules/{scheduleId}/run` | 手动触发，异步返回 `202` |
| PUT | `/api/v2/schedules/{scheduleId}` | 只切换 `enabled` |

任务对象：

```json
{
  "scheduleId": 12,
  "profileName": "daily-weather",
  "scheduleKey": "daily",
  "displayName": "每日天气",
  "cron": "0 0 8 * * *",
  "zone": "Asia/Shanghai",
  "enabled": true,
  "retired": false,
  "nextRunAt": "2026-11-23T00:00:00Z",
  "lastRunAt": "2026-11-22T00:00:00Z",
  "lastStatus": "success",
  "runCount": 3
}
```

手动触发响应 `202`：

```json
{ "code": 0, "message": "OK", "data": { "scheduleId": 12, "status": "triggered" }, "timestamp": "..." }
```

- 1.0 不允许通过 API 修改 `cron`、`message`、`key`；`PUT` 仅接受 `{ "enabled": true|false }`。
- `run` 与自动触发走同一 `AgentScheduler` 执行路径；重叠时按进程内锁跳过。
- 执行历史对象：`{ id, scheduleId, sessionId, startedAt, success, errorMessage, durationMs }`。

## 4. 错误码

| HTTP | 场景 |
|---|---|
| 400 | 缺字段、非法 JSON、消息超 32KB、URL 不在白名单、非法 cron |
| 404 | Session / Agent / Profile / 渠道 / 任务不存在 |
| 409 | 向已归档 Session 发消息 |
| 500 | 未预期内部错误（不返回堆栈） |
| 503 | Provider 不可用/未配置、MCP 不可用导致 Agent 无法完成 |
| 504 | Agent 调用超过 60 秒 |

```json
{
  "code": 409,
  "message": "Session 已归档，不能追加消息；请创建新 Session。",
  "data": null,
  "timestamp": "2026-11-22T08:00:00Z"
}
```

## 5. OpenAPI

- `GET /v3/api-docs`：OpenAPI 3 JSON。
- `GET /swagger-ui/index.html`：Swagger UI。
- 契约以本文件与生成的 OpenAPI 为准；字段变更需同步更新本文件。
