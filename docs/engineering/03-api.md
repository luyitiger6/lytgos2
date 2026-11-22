# API 规范

## 1. 权威契约

REST 契约以 `specs/001-lytgos-runtime-kernel/contracts/rest-api.md` 为准。代码与契约冲突时，先改契约，再改代码。

- Base URL：`/api/v1`
- 内容类型：`application/json`
- 1.0 无认证、无 RBAC，默认可信内网；CORS 全开是基线决策，不要静默收紧。

## 2. 统一信封

```json
{
  "code": 0,
  "message": "OK",
  "data": {},
  "timestamp": "2026-11-22T08:00:00Z"
}
```

- 成功：`code=0`，HTTP 200/201。
- 失败：`code` 等于 HTTP 状态码，`data=null`，HTTP status 同步设置。
- 错误消息用中文、可操作；不得包含堆栈、绝对路径、API key 或完整请求头。

实现位置：

- `lytgos-web/src/main/java/io/lytgos/web/api/ApiResponse.java`
- `lytgos-web/src/main/java/io/lytgos/web/api/ErrorCode.java`
- `lytgos-web/src/main/java/io/lytgos/web/api/GlobalExceptionHandler.java`

## 3. 状态码

| HTTP | 场景 |
|---|---|
| 400 | 缺字段、非法 JSON、超长消息、非法 cron、URL 不在白名单 |
| 404 | Session / Agent / Profile / 渠道 / 任务不存在 |
| 409 | 向已归档 Session 发消息 |
| 500 | 未预期内部错误（不返回堆栈） |
| 503 | Provider 不可用/未配置、MCP 不可用 |
| 504 | Agent 调用超过 60 秒 |

## 4. OpenAPI

- `GET /v3/api-docs`：OpenAPI 3 JSON。
- `GET /swagger-ui/index.html`：Swagger UI。
- 控制器使用 `@Tag` / `@Operation` / `@Schema` 描述契约。
- 字段变更必须同步 `contracts/rest-api.md`，并跑契约相关测试。

## 5. 校验与异常

- 请求 DTO 使用 Jakarta Bean Validation。
- `GlobalExceptionHandler` 覆盖校验失败、非法 JSON、404、超时、`ResponseStatusException` 和未预期异常。
- 未预期异常只在服务端日志记录堆栈，客户端只收到通用错误消息。
- 新增业务异常时，优先继承项目异常并映射到既有状态码，不要新增无契约的状态码。
