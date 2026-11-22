# REST API

`lytgos serve` 启动一个使用虚拟线程的 Spring MVC 服务，默认端口 `8080`。所有端点位于 `/api/v1` 下（调度 v2 支撑端点在 `/api/v2` 下）。

## 响应信封

成功和错误共用同一个信封：

```json
{
  "code": 0,
  "message": "ok",
  "data": {},
  "timestamp": "2026-11-21T12:00:00Z"
}
```

`code` 为 `0` 表示成功；非 0 表示错误。`data` 是端点相关数据，`message` 是可读摘要。

## Controller

| Controller | 资源 |
| --- | --- |
| `SessionApiController` | 创建 Session、发消息、查历史、归档 |
| `AgentApiController` | 无状态 Agent 调用 |
| `ProfileApiController` | 列出已加载 Profile |
| `MemoryApiController` | 查看长期记忆 |
| `ToolApiController` | 列出已注册 Tool |
| `SystemApiController` | Health 与运行时信息 |

## 核心端点（10 个）

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `POST` | `/api/v1/sessions` | 创建 Session |
| `POST` | `/api/v1/sessions/{id}/messages` | 发消息并运行 ReAct 循环 |
| `GET` | `/api/v1/sessions/{id}` | 查 Session 历史 |
| `DELETE` | `/api/v1/sessions/{id}` | 归档 Session |
| `POST` | `/api/v1/agents/{name}/invoke` | 无持久 Session 的 Agent 调用 |
| `GET` | `/api/v1/profiles` | 列出已加载 Profile |
| `GET` | `/api/v1/memory` | 查看长期记忆 |
| `GET` | `/api/v1/tools` | 列出已注册 Tool |
| `GET` | `/api/v1/health` | Health 检查 |
| `GET` | `/api/v1/info` | 运行时与版本信息 |

请求体以 `/swagger-ui` 暴露的 OpenAPI schema 为准。典型调用：

```bash
# 创建 Session
curl -X POST http://localhost:8080/api/v1/sessions \
  -H 'Content-Type: application/json' \
  -d '{"profile": "weather-daily"}'

# 给 Session 发消息
curl -X POST http://localhost:8080/api/v1/sessions/<id>/messages \
  -H 'Content-Type: application/json' \
  -d '{"content": "今天北京天气怎么样？"}'

# 无状态调用 Agent
curl -X POST http://localhost:8080/api/v1/agents/weather-daily/invoke \
  -H 'Content-Type: application/json' \
  -d '{"message": "查一下今天的天气。"}'

# Health
curl http://localhost:8080/api/v1/health
```

## 1.0 支撑端点

通知渠道：

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/api/v1/notify-channels` | 列出渠道 |
| `POST` | `/api/v1/notify-channels` | 注册渠道 |
| `PUT` | `/api/v1/notify-channels/{id}` | 更新渠道 |
| `DELETE` | `/api/v1/notify-channels/{id}` | 删除渠道 |

调度 v2：

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/api/v2/schedules` | 列出已注册任务 |
| `GET` | `/api/v2/schedules/{scheduleId}/executions` | 查看执行历史 |
| `POST` | `/api/v2/schedules/{scheduleId}/run` | 立即执行 |
| `PUT` | `/api/v2/schedules/{scheduleId}` | 启用/停用或更新运行状态 |

调度定义仍来自 `AGENT.md` frontmatter；v2 API 管理运行状态和执行，不管理 cron 定义。

## 限制

| 限制 | 值 |
| --- | --- |
| 单条消息大小 | 32 KB |
| Session 历史返回 | 最近 100 条 |
| Agent 调用超时 | 60 秒，超时返回 HTTP 504 |

## 安全边界

1.0 没有认证、RBAC、API Key 或 CORS 白名单。请部署在可信内网；如果要从更大网络访问，在前面加反向代理或 VPN。见[认证](/zh/docs/auth)。

## 1.0 不包含

Agent 目录上传/生命周期 API、SSE/WebSocket 流式响应、Webhook 触发端点、审计查询端点、Metrics/Prometheus 和 Web 仪表板都属于扩展阶段。
