# Implementation Plan: LytgOS2 1.0 运行时内核

**Branch**: `main`（feature directory: `specs/001-lytgos-runtime-kernel`） | **Date**: 2026-11-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-lytgos-runtime-kernel/spec.md`

## Summary

在现有九模块 Maven 骨架上交付 LytgOS2 1.0 运行时内核：Spring AI 薄封装的 Provider 抽象与显式 provider name → `ChatModel` 映射；自实现 `ReActLoop` + `PromptBuilder` + `ToolExecutor`；`MemoryService` 单一门面，SQLite 会话历史 + `MEMORY.md` 长期记忆；9 个内置 Tool、MCP stdio Client、`@Tool` Java Bean 适配与 `WhitelistSandbox`；12 个 CLI 命令、10 个核心 REST 端点与 notify-channels / scheduler v2 支撑端点；`AgentScheduler` 定时触发与状态持久化；审计表 day one 写入。

交付物是**一个可执行 Spring Boot fat JAR**，内含 Picocli 命令树，`bin/lytgos` 为启动脚本；桌面/服务器均可通过 `lytgos <command>` 或 `java -jar` 使用。按 `US-1 → US-2 → (US-3 ∥ US-4) → US-5` 推进，每个 user story 结束后执行 `/speckit-analyze`。1.0 的范围、数字与裁决以 `docs/ReleaseBaseline.md` 为准。

## Technical Context

**Language/Version**: Java 21（编译与运行基线，允许 JDK 21+）

**Primary Dependencies**:
- Spring Boot **3.5.16**（工程地基已从 3.3.5 升级；匹配 Spring AI 1.1.2 / SAA 1.1.2.x / springdoc 2.8.x；保持设计文档的 SAA 方案）
- Spring AI **1.1.2**：`spring-ai-bom` + `spring-ai-starter-model-openai`，用于 Provider 抽象、OpenAI 兼容协议转换、`@Tool` JSON Schema 生成
- Spring AI Alibaba **1.1.2.4-security-fix**：导入 `spring-ai-alibaba-bom`；autoconfigure 依赖 Spring AI 1.1.2；DeepSeek/Kimi 走 SAA/OpenAI 兼容 connector，DashScope/Qwen 作为扩展路径（ADR 0004）
- Spring Data JPA + Hibernate 6.6（Boot 3.5 管理版本）+ `hibernate-community-dialects`（SQLite，US-4 锁定）
- Flyway（Boot 3.5 BOM 管理，US-4 锁定）+ `org.xerial:sqlite-jdbc`（US-4 锁定）
- MCP Java SDK **2.2.0**：由 `mcp-bom` 管理；与 Jackson 2 对齐，US-4 前验证
- Picocli **4.7.6**、SnakeYAML、Logback/SLF4J（Boot 3.5 structured logging，ECS）
- `springdoc-openapi-starter-webmvc-ui` **2.8.17**（Boot 3.5.x 对应版本线）
- Jackson 2（Boot 3.5 默认）；不要引入依赖 Jackson 3 的库

**Storage**: SQLite `.lytgos/lytgos.db`（WAL + busy_timeout）；长期记忆 `.lytgos/memory/MEMORY.md`；Agent/Skill/Bootstrap/MCP 配置留在文件系统

**Testing**: JUnit 5、AssertJ、Spring Boot Test、MockMvc、WireMock 3.x、MCP SDK test server、临时工作区/临时 SQLite；4 小时压测为人工验收脚本，不伪装成 CI 测试

**Target Platform**: Linux 为主要部署目标；Windows 可作为开发环境；JDK 21+

**Project Type**: Java 多模块单体应用（Web Service + CLI + Scheduler，共用一个运行时）

**Performance Goals**: Session 创建 P99 ≤ 200ms；内部转发开销 ≤ 50ms（不含 LLM 耗时）；单节点 4 小时稳定运行 ≥ 10 Agent / 100 并发 Session

**Constraints**: 单节点；无认证/RBAC/SSO/API Key；无 SSE/WebSocket/异步 Tool；无 IM 入站渠道、Persona、Knowledge、向量记忆；无 Provider fallback/hedge/circuit breaker；固定 9 个 Maven 模块；密钥仅 `${ENV_VAR}`；审计写入 day one；1.0 不做运行时 cron 增删改

**Scale/Scope**: 9 个 Maven 模块、9 个内置 Tool、12 个 CLI 命令、10 个核心 REST 端点 + notify-channels / scheduler v2 支撑端点、6 张 SQLite 表、2 个硬性 Demo + 1 个可选 Demo

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 门禁 | 结果 | 计划证据 |
|---|---|---|---|
| I. 单二进制模块化内核 | 9 个模块、JDK 21、单一可执行 fat JAR、包名 `io.lytgos` | PASS | 保持 9 模块；Boot 3.5.16；单一 fat JAR 内含 Picocli 命令树；`bin/lytgos` 只是启动脚本，不是第二交付物；`OryxTool` 已重命名为 `LytgTool` |
| II. 五大核心能力优先 | 只交付 Provider / ReAct / Memory / Tool / Web Service；不引入扩展能力 | PASS | 规格已限定；notify-channels 与 scheduler v2 是基线内支撑端点；不新增 persona/knowledge/IM 模块 |
| III. 自实现 ReAct 循环 | 不使用 Spring AI Agent 抽象；默认 10 轮、历史 20 轮、Tool 顺序执行、审计 | PASS | `ReActLoop` 在 `lytgos-core`；直接调用 `ChatModel`；`ToolExecutor` 唯一调度入口；每轮与每次 Tool 调用写审计 |
| IV. Spring AI 受限使用与 Provider 显式映射 | 仅 Provider 抽象/协议转换/`@Tool` Schema；禁用自动 Tool 执行；显式映射；DeepSeek/Kimi；密钥占位符 | PASS | 手工构建 per-provider `ChatModel` Map；不注入 `List<ChatModel>`；不使用 `ChatClient` 自动工具执行；DeepSeek/Kimi 用 OpenAI 兼容 base URL；密钥 `${ENV_VAR}` |
| V. Tool 统一注册、上下文分离与 Sandbox 先行 | 单一 `lytgos-tool`；`LytgTool`/`ToolRegistry`；MCP stdio；Agent/Skill 不进 Registry；`WhitelistSandbox`；不用 `SecurityManager` | PASS | 9 个内置 Tool + MCP Adapter + `@Tool` Adapter；`WhitelistSandbox` 覆盖路径/可执行文件/域名；违规抛 `SandboxViolationException` 并写失败审计 |
| VI. Memory 单门面、文件持久化与审计 day one | `MemoryService`；SQLite 会话；`MEMORY.md` 双分区；6 张表；审计从第一版写入 | PASS | `MemoryService` 门面；`MarkdownMemoryStore`；Flyway 6 表；`llm_calls`/`tool_invocations` 随调用同步写入 |
| VII. 每个 User Story 必须可演示 | 每个 story 可演示；2 硬性 + 1 可选 Demo；每 story 后 `/speckit-analyze`；不虚构测试通过 | PASS | 计划按 story 产出可运行切片；Demo 与 quickstart 验证步骤；analyze 作为门禁 |

**Gate result: PASS.** 无不违反原则的结构选择。唯一需要说明的结构决策是“单一 fat JAR 内含 Picocli 命令树”：它满足原则 I 的单交付物要求，同时保留 `lytgos-cli` 的命令树职责，不新增模块。

**Post-Phase 1 design re-check: PASS.** `data-model.md`、`contracts/` 与 `quickstart.md` 没有新增模块、没有超出基线的端点/命令/Tool/表数量，也没有引入 Spring AI 自动 Tool 执行、`SecurityManager` 或扩展阶段能力。

## Project Structure

### Documentation (this feature)

```text
specs/001-lytgos-runtime-kernel/
├── plan.md              # 本文件
├── spec.md              # 已澄清的功能规格
├── research.md          # Phase 0：技术决策与取舍
├── data-model.md        # Phase 1：实体、表、状态与校验
├── quickstart.md        # Phase 1：端到端验证步骤
├── contracts/           # Phase 1：REST / CLI / Agent / Tool / MCP 契约
│   ├── rest-api.md
│   ├── cli.md
│   ├── agent-definition.md
│   ├── builtin-tools.md
│   └── mcp-config.md
└── checklists/
    └── requirements.md  # 已有规格质量清单
```

### Source Code (repository root)

```text
lytgos-core/
└── src/main/java/io/lytgos/core/
    ├── model/            # LytgTool, ToolResult, Profile, Session, Message, ToolCall, enums
    ├── engine/           # AgentLoader, ContextLoader, ProfileRegistry, PromptBuilder,
    │                     # ReActLoop, ToolExecutor, AgentService, AgentScheduler,
    │                     # ProfileContext, SessionLockManager, SessionResolver
    ├── port/             # LlmGateway, MemoryGateway, SessionStore, AuditStore,
    │                     # ScheduledTaskStore, NotifyChannelStore, ToolCatalog
    ├── exception/        # LytgosException, ProviderException, AgentNotFoundException
    └── config/           # LytgosProperties, WorkspacePaths

lytgos-provider/
└── src/main/java/io/lytgos/provider/
    ├── ProviderService.java, ProviderRegistry.java, ProviderProperties.java
    ├── OpenAiCompatibleProviderFactory.java, SpringAiLlmGateway.java
    └── FunctionCallingAdapter.java

lytgos-memory/
└── src/main/java/io/lytgos/memory/
    ├── MemoryService.java, LongTermMemoryStore.java
    ├── MarkdownMemoryStore.java, MemoryTools.java, MemoryProperties.java
    └── MemorySection.java

lytgos-tool/
└── src/main/java/io/lytgos/tool/
    ├── ToolRegistry.java
    ├── builtin/          # FileTools, ShellTools, HttpTools, NotifyTools
    ├── sandbox/          # Sandbox, WhitelistSandbox, SandboxViolationException
    ├── mcp/              # McpClientService, McpToolAdapter, McpServerConfig
    └── notify/           # NotifyChannelAdapter, WebhookNotifyAdapter

lytgos-storage/
└── src/main/
    ├── java/io/lytgos/storage/
    │   ├── entity/       # SessionEntity, ToolInvocationEntity, LlmCallEntity,
    │   │                 # ScheduledTaskEntity, TaskExecutionEntity, NotifyChannelEntity
    │   ├── repository/   # Spring Data JPA repositories
    │   ├── store/        # JpaSessionStore, JpaAuditStore, JpaScheduledTaskStore,
    │   │                 # JpaNotifyChannelStore
    │   └── config/       # SQLiteDataSourceConfig, InstantStringConverter
    └── resources/db/migration/sqlite/
        └── V1__init.sql  # 6 张表 + 索引

lytgos-web/
└── src/main/java/io/lytgos/web/
    ├── controller/       # Session, Agent, Profile, Memory, Tool, System, NotifyChannel, Schedule
    ├── dto/              # Request/Response records
    ├── ApiResponse.java, GlobalExceptionHandler.java
    └── config/OpenApiConfig.java

lytgos-channel-cli/
└── src/main/java/io/lytgos/channel/cli/
    ├── CliChannel.java
    └── ChatRepl.java

lytgos-cli/
└── src/main/java/io/lytgos/cli/
    ├── LytgosCli.java
    ├── command/          # Init, Status, Chat, Serve, Gateway, Profile*, ProviderList,
    │                     # ToolList, SessionList
    ├── ConfigLoader.java
    └── CliRuntime.java

lytgos-boot/
└── src/main/
    ├── java/io/lytgos/boot/
    │   ├── LytgosApplication.java   # fat JAR main；无参数默认 serve
    │   ├── RuntimeMode.java
    │   ├── RuntimeConfiguration.java
    │   └── SchedulingConfiguration.java
    └── resources/
        ├── application.yaml
        └── logback-spring.xml

examples/
├── agents/daily-weather/AGENT.md
├── agents/daily-tech-news/AGENT.md
├── skills/tech-digest/SKILL.md
├── mcp/tech-news-server/README.md (+ 最小 stdio MCP server)
└── notify/README.md

bin/
├── lytgos                 # Unix 启动脚本，执行 fat JAR
└── lytgos.cmd             # Windows 启动脚本

website/                   # 现有 VitePress 主页与文档；补充 1.0 快速开始/部署说明
```

**Structure Decision**:

- 保持 9 个核心 Maven 模块；不新增模块。契约（`port/`、`model/`、异常、配置）放 `lytgos-core`，实现放 provider/memory/tool/storage/web/channel-cli。
- 依赖方向：`core ← provider/memory/tool/storage/web/channel-cli ← boot → cli`。`lytgos-boot` 保留对 `lytgos-cli` 的依赖（现有骨架方向），fat JAR 的 main 是 `LytgosApplication`；它按参数分派到 Picocli 命令树，避免第二个可执行交付物。
- `lytgos-cli` 只依赖 core + channel-cli + Picocli；命令通过 core 端口/服务工作，Spring 实现由 boot 注入。`init`、`profile` 等文件命令不启动完整 Spring；`chat`/`serve`/`gateway`/`tool list`/`session list` 需要运行时，由 boot 启动上下文后执行。
- 单一 fat JAR 满足宪法原则 I；`bin/lytgos` 是启动脚本，不构成第二交付物。

## Implementation Sequence

| 阶段 | 范围 | 演示/门禁 |
|---|---|---|
| Pre-US-1 | 升级 Boot/依赖；`OryxTool` → `LytgTool`；README/readme.zh 同步；补测试基线；建立 core 端口、SQLite/Flyway 6 表与 JPA stores | `mvn clean package`；现有 health/info 仍可用 |
| US-1 | Provider 显式映射、DeepSeek/Kimi、`llm_calls`、Session 基础、CLI `init`/`chat`/`profile` | 多轮对话 + 切换 Provider + 审计；`/speckit-analyze` |
| US-2 | `PromptBuilder`、`ReActLoop`、`ToolExecutor`、Tool 重试、`tool_invocations`、`max_iterations`/`max_history_turns` | 0/1/3 次 Tool 调用与最大迭代终止；`/speckit-analyze` |
| US-3 ∥ US-4 | `MemoryService`/`MarkdownMemoryStore`/`MemoryTools`；9 内置 Tool、MCP stdio、`@Tool`、`WhitelistSandbox` | 记忆跨会话/重启；越权拒绝 + 审计；`/speckit-analyze` |
| US-5 | 10 REST + 支撑端点、OpenAPI、AgentScheduler v2、CLI 补全、结构化日志 | REST 全链路；调度器自动/手动；`/speckit-analyze` |
| 收尾 | 2 硬性 Demo + 1 可选 Demo、主页/快速开始/部署说明、4h 压测、完成定义检查 | `mvn clean package`；Demo 通过；无扩展能力半成品 |

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

无宪法违规，无需例外说明。单一 fat JAR 内含 Picocli 命令树是满足原则 I 的设计，不是复杂度豁免。
