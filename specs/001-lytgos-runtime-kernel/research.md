# Phase 0 Research: LytgOS2 1.0 运行时内核

**Feature**: `001-lytgos-runtime-kernel` | **Date**: 2026-11-22 | **Spec**: [spec.md](./spec.md)

本文件把规格中的技术未知项收敛为可执行决策。所有决策都以 `docs/ReleaseBaseline.md`（权威范围）、`docs/TechnicalSolution.md`（机制）和 `.specify/memory/constitution.md`（非协商原则）为前提。

> **2026-11-22 基线确认**：按设计文档保持 Spring Boot 3.5.16 + Spring AI 1.1.2 + Spring AI Alibaba 1.1.2.4-security-fix。Boot 4 方案因 SAA 兼容性放弃，详见 ADR 0004；实施以 `plan.md`、`tasks.md` 和 `docs/engineering/00-versions.md` 为准。

## 0. 待解决项与结论

| 未知项 | 结论 |
|---|---|
| Spring Boot / Spring AI 版本组合 | Spring Boot 3.5.16 + Spring AI 1.1.2 + Spring AI Alibaba 1.1.2.4-security-fix（ADR 0004） |
| DeepSeek / Kimi 连接器 | 两者均走 Spring AI 的 OpenAI 兼容 `OpenAiChatModel`，手工 per-provider 映射 |
| 避免 Spring AI 自动执行 Tool | 只调用 `ChatModel.call(Prompt)`，不使用 `ChatClient` 的自动工具执行 |
| SQLite + JPA + Flyway | `sqlite-jdbc` + Hibernate community SQLite dialect + `flyway-core`（SQLite 内置在 core）+ WAL |
| MCP Java SDK | 2.2.0；与 Jackson 2 对齐，US-4 前验证，仅 stdio |
| CLI 与 fat JAR 的关系 | 单一 fat JAR，`LytgosApplication` 按参数分派到 Picocli 命令树；不产出第二个可执行 JAR |
| 结构化日志 | Spring Boot 3.5 structured logging（ECS）到控制台与 `.lytgos/logs/lytgos.log` |
| OpenAPI | `springdoc-openapi-starter-webmvc-ui` 2.8.x |
| 旧命名 `OryxTool` | 重命名为 `LytgTool`，同步 README/readme.zh.md |

---

## 1. 运行时与依赖基线

**Decision**: 将父 POM 的 Spring Boot 从 3.3.5 升级到 **3.5.16**；Java 21 不变；使用 Spring AI **1.1.2** 的 `spring-ai-bom` 与 `spring-ai-starter-model-openai`；导入 Spring AI Alibaba **1.1.2.4-security-fix** 的 `spring-ai-alibaba-bom`，保留 SAA 的 connector 兼容性。

**Rationale**:
- SAA 1.1.2.4-security-fix 的 autoconfigure 依赖 **Spring AI 1.1.2** 与 **Spring Boot 3.5.10**；Boot 3.5.16 是同一线的安全补丁版本。
- 设计文档明确以 Spring AI Alibaba 作为 1.0 的 LLM connector 方案；不能中途改成“无 SAA”的 Boot 4 基线（ADR 0004）。
- 一次性把 Boot、Spring AI、SAA、MCP SDK 的版本对齐，避免后续 US-1 再返工。

**Alternatives considered**:
- 保持 Boot 3.3.5 + 旧版 Spring AI：缺少稳定 API，且与 SAA 1.1.2.x 不匹配；拒绝。
- Boot 4.1.3 + Spring AI 2.0.3、不引入 SAA：偏离设计文档，且 SAA 的 Boot 4 兼容版本仍是 milestone；拒绝。
- 直接使用 HTTP 客户端调用 LLM：违反目标技术栈，且重复实现协议转换；拒绝。

**Verification**: `mvn dependency:tree` 中 Spring AI / Spring AI Alibaba / Spring Boot 三个版本族一致；`mvn clean verify` 通过。

---

## 2. Provider 连接器策略

**Decision**: DeepSeek 与 Kimi 都通过 Spring AI 1.1.2 的 **OpenAI 兼容** `OpenAiChatModel` 接入，使用各自的 base URL；`ProviderRegistry` 手工维护 `provider name → ChatModel` 的不可变 Map。Spring AI Alibaba 1.1.2.4-security-fix 的 BOM 保留，SAA 的 connector 可用于 DashScope/Qwen 等扩展 Provider。

**Rationale**:
- DeepSeek 与 Kimi 都提供 OpenAI 兼容的 `/v1/chat/completions` 协议；官方 Spring AI Alibaba 文档对 DeepSeek 的推荐也是 OpenAI 兼容 starter。
- Spring AI Alibaba 1.1.2.4 的官方 starter 列表中没有确认的 Kimi/Moonshot 原生 starter；依赖未经验证的第三方 adapter 会把 1.0 的 Provider 可用性建立在不确定依赖上。
- 用一个连接器覆盖两个必需 Provider，减少协议差异；`ProviderService` 仍然只依赖 Spring AI 的 `ChatModel` 抽象，满足宪章原则 IV。
- BOM 保留使扩展阶段可以增加 `type: dashscope` 的 Provider，而不改上层契约。

**Alternatives considered**:
- Spring AI Alibaba DashScope 调 DeepSeek：需要阿里云百炼账号，不是规格要求的用户自己的 DeepSeek 账号；拒绝。
- 第三方 Moonshot/Kimi adapter：无官方确认；拒绝。
- 直接手写 OpenAI HTTP 客户端：重复协议转换，违反“Spring AI 只做 Provider 抽象/协议转换”的目标；拒绝。

**Implementation notes**:
- 每个 Provider 配置包含 `type: openai-compatible`、`base-url`、`api-key`（`${ENV_VAR}`）、`default-model`、`temperature`。
- `OpenAiChatModel` 使用 `OpenAiApi` + `OpenAiChatOptions` 手工构建；Agent 的 `provider.model` 覆盖默认模型。
- 不注入 `List<ChatModel>`，不按类型扫描 Bean；未知 provider name 在调用前报错。
- 缺少某个 Provider 的 key 不阻断启动；只有使用该 Provider 的 Agent 在调用前得到清晰错误。这样只配置 DeepSeek 或 Kimi 之一也能完成 US-1。

**Deviation note**: `docs/TechnicalSolution.md` 5.1 的“基于 Spring AI Alibaba 的 ChatModel”在本计划中落地为“基于 Spring AI `ChatModel`，使用 Spring AI Alibaba BOM/生态”。这是连接器实现细节，不改变 1.0 范围、端点或验收数字；若后续要求严格使用 DashScope，新增一个 Provider 类型即可。

**Sources**: [Spring AI Alibaba Releases](https://github.com/alibaba/spring-ai-alibaba/releases), [Spring AI Alibaba Documentation](https://java2ai.com/)

---

## 3. Spring AI 使用边界与 Tool 调度

**Decision**: `ProviderService` 只调用 `ChatModel.call(Prompt)`；Tool 定义通过 Spring AI 的 `ToolCallback` / `ToolCallingChatOptions` 交给模型；模型返回 `ToolCall` 后，由 `ReActLoop` + `ToolExecutor` 执行。**不使用 `ChatClient` 的自动工具执行，不使用 Spring AI Agent 抽象。**

**Rationale**:
- 宪章原则 III/IV 明确要求自实现 ReAct、禁用自动 Tool 执行；`ChatModel` 层不会替我们执行 Tool，`ChatClient`/`ToolCallingManager` 才会。
- Tool 的重试、沙箱、审计、顺序执行都必须经过 `ToolExecutor`；自动执行会绕过这些控制。
- `@Tool` 的 JSON Schema 生成仍由 Spring AI 完成：用 `MethodToolCallback`/`ToolCallbacks.from(...)` 取得定义，再包装为 `LytgTool`；执行时由 `ToolExecutor` 显式调用。

**Alternatives considered**:
- `ChatClient.prompt().tools(...).call()`：可能自动执行 Tool，导致重复调用；拒绝。
- Spring AI Agent 抽象：宪章禁止；拒绝。
- 手写 JSON Schema 与协议适配：重复 Spring AI 已有能力；拒绝。

**Implementation notes**:
- `LytgTool` 与 Spring AI `ToolCallback` 之间做双向薄适配：`toToolCallback(LytgTool)` 用于发给模型，`fromMethodToolCallback(...)` 用于 `@Tool` Bean。
- Tool 的 `inputSchema` 必须是 JSON Schema 字符串；内置 Tool 用 `FunctionToolCallback` 或等价的 schema 构造器生成。
-  Provider 调用路径中不能出现 `ToolCallingManager` 的自动循环；只有 `ReActLoop` 可以推进轮次。

---

## 4. ReAct 循环、Prompt 与会话策略

**Decision**:
- `ReActLoop` 默认 `max_iterations=10`；同一轮多个 Tool 调用**顺序执行**；达到上限时返回可识别的 `terminationReason=MAX_ITERATIONS`。
- `max_history_turns=20`，一轮 = 一条用户消息及其最终模型回复；同轮 Tool 往返不额外计轮。截断按用户消息边界进行，保留完整轮次。
- 每个 Session 使用公平 `ReentrantLock` 串行化处理；锁按 session id 维度，进程内有效。
- 每步持久化：先写用户消息，再写 LLM 调用审计，再写 assistant/tool 消息；进程重启后从最近一次持久化状态恢复。
- 无状态 invoke 使用 transient 会话模式：不创建 Session 行，审计 `session_id` 为空。

**Session resolution**:
| 入口 | channel | user | 行为 |
|---|---|---|---|
| CLI `chat` | `cli` | 当前 OS 用户名，取不到用 `local` | 自动恢复最近 active；`--new` 新建 |
| REST Session | `api` | 调用方提供 | `POST /sessions` 总是新建 |
| Scheduler | `scheduler` | `scheduler` | 复用该 Profile 最近 active；没有则新建 |
| REST invoke | — | — | transient，不落 Session |

- `session_id` 采用可读前缀 + 唯一后缀：`<channel>:<user>:<profile>:<ULID>`；active 查找按 `(channel, user_id, profile_name, status)` + `last_active_at` 索引。
- 归档 Session 只读：`POST /sessions/{id}/messages` 返回 409；历史仍可 `GET`；`session list` 默认不显示。

**Prompt 组装**:
1. Bootstrap 三文件（按 Profile 的 `bootstrap` 列表，默认 `AGENTS.md`/`SOUL.md`/`USER.md`）+ `identity.prompt` + `AGENT.md` 正文。
2. 运行上下文：日期时间、工作区路径、Profile 名称。
3. 长期记忆：核心区全量 + 归档区截断内容。
4. 已绑定 Skill 的 name/description/本地绝对路径（不注入正文）。
5. 可用 Tool 的 name/description（schema 通过 ToolCallback 发送）。
6. 最近 N 轮对话 + 当前用户消息。

**Rationale**: 规格与基线明确要求；顺序执行、可恢复、可审计、归档只读都是验收项。公平锁保证“按接收顺序处理”。

**Alternatives considered**: 并行 Tool（规格明确顺序执行）；全局单锁（吞吐不足）；纯内存会话（不能跨重启）；归档后自动复活（已澄清为拒绝）。

---

## 5. Tool 重试与审计粒度

**Decision**:
- 一次“逻辑 Tool 调用” = 模型的一次 Tool 请求。
- 仅当 `ToolResult.retryable=true` 时重试；首次执行后最多再重试 **3 次**（共 4 次尝试），指数退避默认 200/400/800ms（可配置）。
- 每次逻辑调用只写 **一行** `tool_invocations`：`retry_count`（0–3）、`attempt_errors_json`（每次失败尝试的错误摘要）、最终 `success/result/error`、总耗时。
- SC-003 的“一次调用”按模型请求计数；同一次调用的重试不产生额外审计行。
- 未知 Tool、沙箱违规、MCP 失联等也写失败审计行，并把结构化错误回填给模型。

**Rationale**: 澄清 Q3 的结论；审计与对话/模型请求一一对应，同时保留重试诊断信息；避免 SC-003 与实现口径冲突。

**Alternatives considered**: 每次尝试一行（需要 correlation id，SC-003 需去重；1.0 复杂度过高）；只记最终结果（丢失重试诊断，违背可审计定位）。

---

## 6. Memory 后端

**Decision**: `MemoryService` 是唯一门面；会话记忆委托 `SessionStore`，长期记忆委托 `MarkdownMemoryStore`。`MEMORY.md` 必须含 `## 核心记忆` 和 `## 归档记忆`。核心区全量注入、永不截断、不参与检索；归档区默认最多 6000 字符（`lytgos.memory.archival-max-chars`），按最旧条目截断；`recall_memory(query)` 在归档区做不区分大小写的行匹配，无命中返回空列表。文件/分区缺失时按空记忆处理并在保存时补齐分区。

**Rationale**: 基线/宪法要求；人类可读、git 可跟踪、零额外依赖；1.0 不做自动抽取/向量检索。

**Alternatives considered**: SQLite/Mem0 后端（扩展阶段）；向量检索（扩展）；自动事实抽取（超出 1.0）。

---

## 7. SQLite + JPA + Flyway

**Decision**:
- 依赖：`spring-boot-starter-data-jpa`、`org.xerial:sqlite-jdbc:3.47.1.0`、`hibernate-community-dialects`（版本由 Boot 管理的 Hibernate 6.6.x）、`flyway-core`。Flyway 10 的 SQLite 支持内置于 core，不引入 `flyway-database-sqlite`（该模块不存在）。
- 方言：`org.hibernate.community.dialect.SQLiteDialect`；`spring.jpa.hibernate.ddl-auto=validate`；Flyway 迁移在 `classpath:db/migration/sqlite/`，`V1__init.sql` 建 6 张表 + 索引。
- 连接：`jdbc:sqlite:${lytgos.workspace}/lytgos.db?journal_mode=WAL&busy_timeout=5000&foreign_keys=on`；一个写者，短事务。
- 时间戳以 ISO-8601 TEXT 存储，使用 `AttributeConverter<Instant, String>`；JSON 字段用 Jackson 写 TEXT。
- `session_id` 在 `tool_invocations`/`llm_calls` 中可空，支持无状态 invoke；`task_executions.session_id` 非空。
- 索引：`sessions(channel,user_id,profile_name,status,last_active_at)`、`sessions(status,last_active_at)`、审计表 `session_id`/`created_at`、`task_executions(schedule_id,started_at)`、`notify_channels(name)` 唯一。

**Rationale**: 基线固定 6 表；SQLite 零外部依赖；Flyway 保证表结构可演进；SQLite ALTER 能力弱，不能依赖 `ddl-auto=update`。WAL + busy_timeout 是在单节点 100 并发 Session 下可用的最小配置。

**Alternatives considered**: H2（不符合基线）、PostgreSQL（扩展）、`ddl-auto=update`（SQLite 迁移不可靠）、每消息一张表（基线要求 `messages_json`，且 6 表固定）。

**Sources**: [Flyway modular V10 issue #3780](https://github.com/flyway/flyway/issues/3780), [Spring Boot Flyway dependency hint PR #41574](https://github.com/spring-projects/spring-boot/pull/41574)

---

## 8. MCP Client

**Decision**: 使用官方 MCP Java SDK **2.2.0** 的 `mcp-core` + `mcp-json-jackson2`，仅实现 **stdio** transport。启动时读取 `.lytgos/mcp_servers.yaml`，逐个连接、调用 `tools/list`、把工具包装为 `LytgTool`（source=`mcp:<server>`）注册到 `ToolRegistry`。Tool 调用超时 30 秒；传输失败时尝试一次重连，仍失败则返回可重试错误并写失败审计。Server 启动失败只记录日志，其工具不可用；引用它的 Agent 记录校验错误但不阻断其他 Agent。

**Rationale**: 基线要求 MCP stdio；官方 SDK 2.2.0 支持 stdio；选择 `mcp-json-jackson2` 与 Spring Boot 的 Jackson 2 对齐，避免 Jackson 3 冲突；SSE 属扩展阶段。

**Alternatives considered**: SSE transport（扩展）；Spring AI MCP client（耦合更深，且基线指定 MCP Java SDK）；自写 JSON-RPC（重复实现）。

**Sources**: [MCP Java SDK quickstart](https://java.sdk.modelcontextprotocol.io/latest/), [MCP Java SDK GitHub](https://github.com/modelcontextprotocol/java-sdk)

---

## 9. Sandbox

**Decision**: `WhitelistSandbox` 覆盖三类白名单：
- 文件：`lytgos.sandbox.file.allowed-paths`；使用 `toRealPath()`/父目录 real path 校验，拒绝 `..`、绝对路径逃逸与指向白名单外的软链接。
- Shell：`shell.allowed-commands` 精确匹配可执行文件名；`ProcessBuilder` 参数数组执行，不经 shell 解释；默认超时 30 秒；输出截断到 `lytgos.tools.max-result-chars`。
- HTTP/notify：`http.allowed-domains` 解析 host 后做通配符匹配（如 `*.example.com`）；默认 5s 连接/15s 读取超时；`notify` 复用同一校验。

违规统一抛 `SandboxViolationException`，由 `ToolExecutor` 转为 `ToolResult` 失败并写 `tool_invocations`。默认白名单是安全的最小值：文件仅 `.lytgos`，shell 空，http 空；Demo 在 `application.yaml` 中显式加入所需域名。

**Rationale**: 宪法原则 V；1.0 唯一可审计的最小安全边界；`SecurityManager` 在受支持 JDK 上不可用。

**Alternatives considered**: `SecurityManager`（不可用）、容器隔离（扩展）、仅字符串前缀检查（可被 `../`/软链接绕过）。

---

## 10. AgentScheduler

**Decision**:
- `ThreadPoolTaskScheduler` + `CronTrigger`，从 `AGENT.md` 的 `schedules` 动态注册；cron 为 Spring 6 字段格式（秒 分 时 日 月 周），带 IANA zone。
- `scheduled_tasks` 由 `reconcile(profileName, scheduleKey, ...)` 维护稳定 `schedule_id`；配置删除/改 key 时旧记录标记 `retired`，历史保留。
- 每个任务一把进程内 `ReentrantLock`；上一次未结束则**跳过**本次触发（不排队、不并行）。
- `serve` 与 `gateway` 启动调度器；`chat` 不启动。手动 run 通过 `POST /api/v2/schedules/{id}/run` 异步触发（202），写 `task_executions`；执行历史可查。
- `PUT /api/v2/schedules/{id}` 只允许切换 `enabled`；1.0 不支持运行时增删改 cron。
- 启动时 reconcile 所有 Profile；失败只记日志，不影响调度器或其他任务。

**Rationale**: 基线 5.6；单节点锁不是分布式锁；Demo 需要自动触发与手动补跑。

**Alternatives considered**: `@Scheduled` 静态注解（不能按 Profile 动态生成）；排队/并行（规格要求跳过）；分布式锁（扩展）。

---

## 11. CLI 与运行时入口

**Decision**: **单一 Spring Boot fat JAR**，main 为 `io.lytgos.boot.LytgosApplication`；`lytgos-cli` 保留 Picocli 命令树，被 fat JAR 包含。`bin/lytgos` 只是启动脚本。分派规则：

| 参数 | Web | Scheduler | 行为 |
|---|---|---|---|
| 无参数 / `serve` | SERVLET | 开 | 启动 REST + 调度器 |
| `gateway` | NONE | 开 | 无 HTTP 的常驻守护进程 |
| `chat` | NONE | 关 | 启动运行时后进入交互式 CLI |
| `init` / `profile ...` | 不启动完整 Spring | 关 | 纯文件操作，快速执行 |
| `status` / `provider list` | NONE（按需） | 关 | 读取工作区/配置 |
| `tool list` / `session list` | NONE | 关 | 需要注册表/存储，启动运行时 |

- `lytgos --help` / `--version` 不启动 Spring。
- `chat --profile <name>` 自动恢复最近 active Session，`--new` 显式新建；OS 用户名取不到时用 `local`。
- `serve`/`gateway` 要求已初始化工作区；未初始化时给出“先运行 lytgos init”的清晰错误。
- `LytgosCli` 仍是命令树与 `main`（IDE/测试可直接运行非 Spring 命令）；生产入口是 fat JAR。

**Rationale**: 宪法原则 I 要求单一可执行 fat JAR；如果单独打 CLI JAR，就会产生第二个交付物。当前骨架已有 `lytgos-boot → lytgos-cli` 依赖，统一入口不需要新增模块或反转依赖。该决策解决了 2026-11-21 记忆中“两个入口点”的遗留问题：1.0 采用统一启动器，而不是两个 jar。

**Alternatives considered**:
- 单独 CLI 可执行 jar（两个交付物，违反原则 I；拒绝）。
- 两个 jar + shell 分派（仍是两个可执行体；拒绝）。
- 所有命令都启动 Web（`gateway` 会暴露 HTTP，与澄清 Q1 冲突；拒绝）。

---

## 12. Web Service 契约

**Decision**:
- 10 个核心端点 + notify-channels CRUD + scheduler v2 端点；`serve` 是唯一 HTTP 进程。
- 统一信封 `ApiResponse {code, message, data, timestamp}`；成功 `code=0`，错误使用 HTTP 状态码作为 `code`，并设置对应 HTTP status。
- 归档 Session 写入返回 409；不存在返回 404；Provider 故障 503；Agent 调用超时 504；校验失败 400。
- `POST /sessions` 总是新建；`invoke` 不落 Session；`GET /sessions/{id}` 最多返回最近 100 条消息；单条消息最大 32KB；invoke 最长 60s。
- Spring MVC + virtual threads（`spring.threads.virtual.enabled=true`）；CORS 全开（可信内网）；无认证。
- OpenAPI 通过 springdoc 2.8.x 暴露 `/v3/api-docs` 与 `/swagger-ui`。

**Rationale**: 基线 5.7/6.3；规格 FR-027…FR-032；无状态语义已澄清。

**Alternatives considered**: WebFlux（不需要响应式）、SSE（扩展）、原始 DTO 返回（不一致）。

**Sources**: [springdoc-openapi v2](https://springdoc.org/v2/)

---

## 13. 配置、密钥与日志

**Decision**:
- `application.yaml` 使用 `lytgos.*` 前缀；工作区默认 `./.lytgos`，可被 `LYTGOS_WORKSPACE`/`lytgos.workspace` 覆盖。
- Provider 配置含 `base-url`、`api-key: ${ENV_VAR}`、`default-model`、`temperature`；启动时不因某个 Provider 缺 key 而失败，调用该 Provider 前给出清晰错误；`provider list` 永不显示 key。
- Sandbox 默认值最小化：file=`[.lytgos]`，shell=`[]`，http=`[]`。
- 日志：Spring Boot 3.5 structured logging（ECS）到控制台；滚动文件 `.lytgos/logs/lytgos.log`；对 `Authorization`/`api-key`/`token` 等字段做脱敏。
- MCP 配置 `.lytgos/mcp_servers.yaml`；notify channels 存 SQLite，通过 REST 管理。
- 错误响应与日志都不输出堆栈到客户端、不输出密钥或内部绝对路径（日志可含工作区相对路径）。

**Rationale**: 规格 FR-008/FR-031/FR-034；快速开始只要求配置一个 Provider；安全默认值避免 Demo 前误开放。

**Alternatives considered**: KMS/Vault（扩展）、明文密钥（禁止）、`application.yaml` 中直接写 key（禁止）。

---

## 14. 测试与验证策略

**Decision**:
- 单元测试：`ReActLoop`、`PromptBuilder`、`ToolExecutor`、`MarkdownMemoryStore`、`WhitelistSandbox`、Session 解析、Provider 映射、CLI 命令。
- 集成测试：Spring Boot Test + 临时工作区/临时 SQLite + Flyway；WireMock 模拟 OpenAI 兼容 API、天气/新闻 HTTP；MCP SDK test server 或 stdio 测试进程。
- Web：MockMvc 覆盖 10 核心端点 + 支撑端点、统一信封、归档 409、无状态 invoke、错误码、OpenAPI 生成。
- CLI：Picocli `CommandLine` 直接执行；`init` 幂等；`profile` CRUD；`session list` 过滤；`chat` 用 fake AgentService/WireMock。
- E2E：天气与科技日报 Demo（`@Tag("demo")`）；断言 `llm_calls`/`tool_invocations` 行、Session 历史、调度执行历史。
- 性能：人工 4 小时 soak（10 Agent/100 Session），提供脚本与记录模板；不伪装成 CI 必过测试。
- 每个 user story 结束后 `/speckit-analyze`；未实际运行的测试不得声称通过。

**Rationale**: 宪法原则 VII；规格 FR-036；Demo 验收。

**Alternatives considered**: Testcontainers（SQLite 不需要）、真实外部 API（不稳定、需密钥）、只做单元测试（无法证明端到端）。

---

## 15. Demo 策略

**Decision**:
- `examples/agents/daily-weather/AGENT.md`：`http_get` 调 Open-Meteo，`notify` 推送到 webhook；每日 cron；手动补跑用 `lytgos chat --profile daily-weather` 或 invoke。
- `examples/agents/daily-tech-news/AGENT.md`：绑定 `examples/skills/tech-digest/SKILL.md` 的软连接，配置本地示例 MCP server；`save_memory`/`recall_memory` 体现偏好；`notify` 推送。
- `examples/mcp/tech-news-server/`：最小 stdio MCP server（示例，不进入 Maven 模块）；测试用 mock 版本。
- `examples/notify/README.md`：说明如何创建 webhook channel 与配置沙箱域名。
- 可选 Demo 三（GitHub 日报）：提供 `examples/agents/daily-github/` 的目录示例与 `scripts/`，不纳入 1.0 发布阻塞。
- Demo 测试用 WireMock + 本地 MCP，不依赖公网；现场演示使用真实天气 API 与用户 webhook。

**Rationale**: 基线 7.3；零代码路径；避免把不可控公网服务放进 CI。

**Alternatives considered**: 真实 IM 渠道（1.0 不做）；纯硬编码新闻（不能验证 MCP/Skill）。

---

## 16. 命名清理与文档

**Decision**: 在 Pre-US-1 阶段把 `OryxTool` 重命名为 `LytgTool`，同步 `ToolResult` javadoc、`ToolRegistry` 引用、`README.md`、`readme.zh.md`；检查代码块与命令示例，不做未经检查的批量重命名。其余旧占位名不引入。

**Rationale**: `CLAUDE.md` 命名红线；旧占位名会污染 1.0 契约与文档。

**Alternatives considered**: 保留 `OryxTool`（违反命名规范）；全仓批量替换（可能误伤历史文档，拒绝）。

---

## 17. 主页与文档

**Decision**: 复用现有 VitePress 站点；补充/校对 1.0 快速开始、CLI/fat JAR 用法、Provider 与沙箱配置、部署与无认证说明、Demo 步骤；验证 SC-013 的 10 分钟首次运行路径。不引入新的站点框架。

**Rationale**: FR-037/SC-013；现有站点已与基线和 Agent Harness OS 定位对齐（本地验证状态）。

**Alternatives considered**: 新建站点（不必要）；跳过（不满足验收）。

---

## 18. 关键风险与缓解

| 风险 | 缓解 |
|---|---|
| Spring Boot 3.5 升级引发依赖冲突 | Pre-US-1 先只升级父 POM 并跑 `mvn dependency:tree`/空测试；再引入 Spring AI |
| Spring AI OpenAI starter 的 Tool 定义 API 变化 | 用 `ToolCallback` 适配层隔离；Provider 测试覆盖 ToolCall 解析 |
| SQLite 写锁导致并发超时 | WAL + busy_timeout + 短事务 + per-session 锁；压测验证 |
| MCP SDK 2.2.0 与 Jackson 2 的兼容性 | 使用 `mcp-core` + `mcp-json-jackson2`；集成测试覆盖 stdio 握手/tools/list/call |
| 单一 fat JAR 内 Picocli 与 Spring 上下文初始化顺序 | `LytgosApplication` 先判断命令类型；`--help` 不启动 Spring；为文件命令保留轻量路径 |
| 归档/无状态 invoke 的审计外键 | `session_id` 可空；Flyway 不设不可空外键；测试覆盖 null |
| 沙箱误放行软链接 | `toRealPath()` 校验 + 符号链接测试；默认白名单最小化 |
| Demo 依赖公网 | CI 用 WireMock/本地 MCP；现场 Demo 单独验证 |
