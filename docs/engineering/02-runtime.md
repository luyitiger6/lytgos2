# 运行时基线

## 1. HTTP Server

- Spring MVC + Java 21 虚拟线程：`spring.threads.virtual.enabled=true`。
- 优雅停机：`server.shutdown=graceful`，超时 30s。
- 请求头上限 16KB，表单/请求体上限 32KB，和消息契约的 32KB 上限一致。
- 1.0 不做 SSE/WebSocket、异步 Tool、认证/RBAC；不要静默加入。

## 2. 日志

- 统一 SLF4J，禁止 `System.out` / `System.err`（CLI 用户输出通过 `PrintWriter` 注入）。
- 开发环境：可读的 console pattern。
- 生产环境（`prod` profile）：ECS JSON，console + 滚动文件。
- 文件位置：`.lytgos/logs/lytgos.log`，按天/大小滚动，保留 30 天，总上限 2GB。
- 不打印 API key、Authorization、webhook URL 里的 `key=`；审计信息写数据库，不靠日志反解析。
- 1.0 不做分布式 trace ID 串联；需要请求关联时用局部 `requestId`，先改基线。

## 3. 健康检查

契约端点是：

- `GET /api/v1/health`
- `GET /api/v1/info`

Actuator 只暴露 `health` / `info`，不引入 Prometheus registry，不配置 Grafana。

1.0 的“监控”定义是：健康检查 + 结构化日志 + `tool_invocations` / `llm_calls` 审计表。指标面板属于扩展阶段。

## 4. 配置与密钥

- 配置前缀 `lytgos.*`。
- 密钥只允许 `${ENV_VAR}` 占位符，禁止明文写入 `AGENT.md` 或提交的配置。
- 工作区默认 `.lytgos/`，运行数据、数据库、日志都不提交。
- 新增配置项要有默认值或明确失败信息，不能静默吞掉。

## 5. Spring AI Alibaba

- SAA starter 在 `lytgos-provider` 的 classpath 上，用于 connector 兼容性。
- 默认关闭：`spring.ai.dashscope.enabled=false`，避免没有 DashScope key 时启动失败。
- 启用 DashScope/Qwen 时设置 `spring.ai.dashscope.enabled=true` 和 `DASHSCOPE_API_KEY`。
- DeepSeek/Kimi 走 Spring AI 的 OpenAI 兼容 connector，不依赖 DashScope key。
- SAA 的 `ChatModel` 仍必须通过 `ProviderRegistry` 的显式 name → `ChatModel` 映射使用，禁止按类型扫描 Bean。

## 6. 线程与 SQLite

- 虚拟线程适合 I/O 等待，不解决 CPU 密集问题。
- JDK 21 注意 `synchronized` 导致的 carrier-thread pinning；热点路径优先用 `ReentrantLock`。
- SQLite 是单写者；US-4 接入时必须设置 WAL、`busy_timeout`，并避免长事务。
