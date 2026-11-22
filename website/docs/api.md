# REST API

`lytgos serve` starts a Spring MVC server with virtual threads. The default port is `8080`; every endpoint is under `/api/v1` (plus the scheduler v2 support endpoints under `/api/v2`).

## Response envelope

Success and error responses share one envelope:

```json
{
  "code": 0,
  "message": "ok",
  "data": {},
  "timestamp": "2026-11-21T12:00:00Z"
}
```

`code` is `0` on success; non-zero values indicate an error. `data` is the endpoint-specific payload. `message` is a human-readable summary.

## Controllers

| Controller | Resources |
| --- | --- |
| `SessionApiController` | Create a Session, send a message, read history, archive |
| `AgentApiController` | Stateless Agent invocation |
| `ProfileApiController` | List loaded Profiles |
| `MemoryApiController` | Inspect long-term memory |
| `ToolApiController` | List registered Tools |
| `SystemApiController` | Health and runtime info |

## Core endpoints (10)

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/sessions` | Create a Session |
| `POST` | `/api/v1/sessions/{id}/messages` | Send a message and run the ReAct loop |
| `GET` | `/api/v1/sessions/{id}` | Read Session history |
| `DELETE` | `/api/v1/sessions/{id}` | Archive a Session |
| `POST` | `/api/v1/agents/{name}/invoke` | Invoke an Agent without a persistent Session |
| `GET` | `/api/v1/profiles` | List loaded Profiles |
| `GET` | `/api/v1/memory` | Inspect long-term memory |
| `GET` | `/api/v1/tools` | List registered Tools |
| `GET` | `/api/v1/health` | Health check |
| `GET` | `/api/v1/info` | Runtime and version info |

Request bodies follow the OpenAPI schema exposed at `/swagger-ui`. Typical calls:

```bash
# create a session
curl -X POST http://localhost:8080/api/v1/sessions \
  -H 'Content-Type: application/json' \
  -d '{"profile": "weather-daily"}'

# send a message to the session
curl -X POST http://localhost:8080/api/v1/sessions/<id>/messages \
  -H 'Content-Type: application/json' \
  -d '{"content": "What is the weather in Beijing today?"}'

# stateless Agent invocation
curl -X POST http://localhost:8080/api/v1/agents/weather-daily/invoke \
  -H 'Content-Type: application/json' \
  -d '{"message": "Check today’s weather."}'

# health
curl http://localhost:8080/api/v1/health
```

## Support endpoints (1.0)

Notify channels:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/notify-channels` | List channels |
| `POST` | `/api/v1/notify-channels` | Register a channel |
| `PUT` | `/api/v1/notify-channels/{id}` | Update a channel |
| `DELETE` | `/api/v1/notify-channels/{id}` | Delete a channel |

Scheduler v2:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v2/schedules` | List registered schedules |
| `GET` | `/api/v2/schedules/{scheduleId}/executions` | List execution history |
| `POST` | `/api/v2/schedules/{scheduleId}/run` | Run a schedule now |
| `PUT` | `/api/v2/schedules/{scheduleId}` | Enable/disable or update runtime state |

Schedule definitions still come from `AGENT.md` frontmatter; the v2 API manages runtime state and execution, not cron definitions.

## Limits

| Limit | Value |
| --- | --- |
| Message size | 32 KB |
| Session history returned | Latest 100 messages |
| Agent invocation timeout | 60 seconds, then HTTP 504 |

## Security boundary

1.0 has no authentication, RBAC, API keys, or CORS whitelist. Run it on a trusted private network and put a reverse proxy or VPN in front if it is reachable from a broader network. See [Auth](/docs/auth).

## Not in 1.0

Agent directory upload/lifecycle APIs, SSE/WebSocket streaming, webhook trigger endpoints, audit query endpoints, Metrics/Prometheus, and a web dashboard are extension-phase features.
