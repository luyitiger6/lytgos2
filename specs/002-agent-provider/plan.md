# Implementation Plan: Agent Provider

**Branch**: `002-lesson16-provider` | **Date**: 2026-11-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-agent-provider/spec.md`

## Summary

第 16 节交付 Provider 薄封装：按 Profile 显式选择模型，完成一次 Spring AI `ChatModel.call(Prompt)`，成功和失败都写 `llm_calls` 审计，并把可用工具的 schema 翻译给模型但不执行。本节同时交付 Profile YAML 加载、内存索引、全局 provider 配置、OpenAI 兼容 connector 的 per-provider 模型映射，以及 Flyway 管理的 `llm_calls` 表。实现遵循项目宪章：显式映射、关闭 Spring AI 自动工具执行、密钥走环境变量、无 fallback/hedge/circuit breaker。

## Technical Context

**Language/Version**: Java 21；Spring Boot 3.5.16；Spring AI 1.1.2；Spring AI Alibaba 1.1.2.4-security-fix（DashScope starter 保留但默认禁用）；Spring AI OpenAI 兼容 connector 1.1.2

**Primary Dependencies**: `spring-ai-openai`（BOM 管理）、Spring AI `spring-ai-model`/`spring-ai-client-chat`（SAA 传递依赖）、SnakeYAML、SLF4J、Spring Data JPA、SQLite JDBC、Flyway 11.7.2（SQLite 支持内置于 `flyway-core`）、Hibernate Community Dialects、JUnit 5、Mockito、AssertJ

**SAA 定位**: 保留 Spring AI Alibaba（SAA）在 `lytgos-provider` classpath，用于 DashScope/Qwen connector 与未来 AgentScope/A2A 分布式 Agent 基座；本节 Provider 的核心抽象是 Spring AI `ChatModel`，DeepSeek/Kimi 使用官方 OpenAI 兼容 connector，SAA 的 DashScope 模型仍可进入同一个显式映射。第 16 节不引入 AgentScope/A2A starter，避免把 1.0 单机 Provider 与未验证的分布式 API 绑定。

**Storage**: SQLite `.lytgos/lytgos.db`；Flyway 迁移位于 `lytgos-storage/src/main/resources/db/migration/sqlite/`；本节 `V1__init.sql` 只创建 `llm_calls`；`spring.jpa.hibernate.ddl-auto=none`

**Testing**: JUnit 5 + Mockito/AssertJ；单元测试默认运行；`ProviderSmokeIT` 使用 `@Tag("integration")`，Surefire 默认排除，手动 `-Pintegration` 运行；实现完成定义为 `./mvnw -B -ntp clean verify` 全绿

**Target Platform**: JVM 21，Windows/Linux；单二进制 fat JAR

**Project Type**: Maven 多模块 Java 服务/库

**Performance Goals**: 每次 `chat` 只发起一次模型调用；不增加 fallback、hedge racing 或额外重试；Provider 自身开销相对于模型延迟可忽略；每次调用尝试只写一条审计记录

**Constraints**: 显式 provider 名称到 `ChatModel` 映射，禁止按类型扫描；必须关闭 Spring AI 自动工具执行；工具执行不属于本节；密钥只通过 `${ENV_VAR}`；不引入 Reactor、`CompletableFuture` 或自建线程池；新增/修改代码使用中文注释，每个字段、方法参数和局部变量都要有注释

**Scale/Scope**: 第 16 节单节范围；provider 与 Profile 数量很小；`llm_calls` 每次模型调用一行，按 `session_id`、`created_at`、`(provider, model, created_at)` 建索引

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 门禁结果 | 证据 |
|---|---|---|
| I. 单二进制 9 模块 | PASS | 只改 `lytgos-core`、`lytgos-provider`、`lytgos-storage`、`lytgos-boot`；根 POM 仍为聚合器 |
| II. 五大核心能力优先 | PASS | 只实现 Provider；未引入 fallback、向量记忆、Web 仪表板等扩展能力 |
| III. 自实现 ReAct 循环 | N/A | 本节不实现 ReAct；代码不引入 Spring AI Agent 抽象 |
| IV. Spring AI 受限使用与 Provider 显式映射 | PASS | 只用 ChatModel/协议转换/Tool schema；显式 Map；`internalToolExecutionEnabled(false)`；无 fallback/hedge；审计落 `llm_calls` |
| V. Tool 统一注册、上下文分离与 Sandbox 先行 | N/A | 本节不执行工具，只做 schema 翻译；Sandbox 接线在后续 Tool 课程 |
| VI. Memory 单门面、Flyway 与审计 day one | PASS | Flyway `V1__init.sql` 建 `llm_calls`；成功/失败都写；不依赖 `ddl-auto=update` |
| VII. 每个 User Story 可演示 | PASS | 单测 harness + 人工 `ProviderSmokeIT`；报告区分已判卷与人工项 |

**Post-design re-check**: PASS。设计产物（research/data-model/contracts/quickstart）未引入新的宪章冲突。

### 课程阶段偏差（用户已确认）

- 本节使用 `.lytgos/profiles/*.yaml` + `ProfileLoader`；`AGENT.md`/`AgentLoader.deriveProfile` 迁移留到后续 Agent 课程。
- Profile 保留课件的 `skills`、`notify_channels` 兼容字段；`notify_channels` 本节不消费，第 19 节迁移到全局注册表。
- 本节 `V1__init.sql` 只创建 `llm_calls`；其余核心表在对应课程/迭代用后续迁移增加。
- 注释要求比默认规范更严格（中文、每个变量），以用户本次指令为准。

## Project Structure

### Documentation (this feature)

```text
specs/002-agent-provider/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── provider.md
│   └── profile-config.md
├── checklists/
│   └── requirements.md
└── tasks.md              # 由 /speckit-tasks 生成，不在本阶段创建
```

### Source Code (repository root)

```text
lytgos-core/
├── src/main/java/io/lytgos/core/
│   ├── Lytgos.java                  # 产品常量
│   ├── agent/                       # Agent Profile 域
│   │   ├── AgentProfile.java
│   │   ├── AgentProfileLoader.java
│   │   └── AgentProfileRegistry.java
│   ├── audit/                       # 审计端口域
│   │   └── LlmCallAuditor.java
│   ├── tool/                        # 工具契约域
│   │   ├── LytgTool.java
│   │   └── ToolResult.java
│   └── session/                     # 会话契约域
│       └── Session.java
└── src/test/java/io/lytgos/core/
    └── agent/
        ├── AgentProfileLoaderTest.java
        └── AgentProfileRegistryTest.java

lytgos-provider/
├── pom.xml                          # 增加 spring-ai-openai
├── src/main/java/io/lytgos/provider/
│   ├── ProviderService.java         # 替换现有接口为薄封装类
│   ├── ProviderProperties.java      # lytgos.providers 配置绑定
│   ├── ProviderModelFactory.java    # 按 provider 构造 OpenAiChatModel
│   └── tool/
│       └── ToolSchemaAdapter.java   # LytgTool → schema-only ToolCallback
└── src/test/java/io/lytgos/provider/
    ├── ProviderServiceTest.java
    ├── ProviderModelFactoryTest.java
    ├── ProviderSmokeIT.java         # @Tag("integration")
    └── tool/
        └── ToolSchemaAdapterTest.java

lytgos-storage/
├── pom.xml                          # JPA、SQLite、Flyway、方言
├── src/main/java/io/lytgos/storage/
│   ├── StorageModule.java
│   └── llm/                         # LLM 审计持久化域
│       ├── LlmCall.java             # JPA 实体
│       ├── LlmCallRepository.java   # Spring Data 仓库
│       └── JpaLlmCallAuditor.java   # 实现 core 审计端口
├── src/main/resources/db/migration/sqlite/
│   └── V1__init.sql                 # llm_calls 表与索引
└── src/test/java/io/lytgos/storage/
    ├── StorageTestApplication.java
    └── llm/
        ├── LlmCallRepositoryTest.java
        └── JpaLlmCallAuditorTest.java

lytgos-boot/
├── src/main/java/io/lytgos/boot/
│   ├── LytgosApplication.java
│   ├── persistence/PersistenceConfiguration.java    # @EntityScan / @EnableJpaRepositories
│   ├── provider/ProviderBootstrapConfiguration.java # provider 映射与 ProviderService
│   ├── agent/AgentProfileBootstrapConfiguration.java # AgentProfileLoader / Registry 与启动注册
│   └── workspace/WorkspaceInitialization.java       # 数据源初始化前确保 .lytgos 目录
├── src/main/resources/application.yaml              # lytgos.providers、datasource、JPA、Flyway
└── src/test/resources/application-test.yaml         # 测试用 SQLite 路径与空 provider 列表

lytgos-web/
└── src/main/java/io/lytgos/web/
    ├── api/ApiResponse.java, ErrorCode.java, GlobalExceptionHandler.java
    ├── openapi/OpenApiConfig.java
    └── system/SystemApiController.java

pom.xml                              # Surefire 默认排除 integration；integration profile
```

**Structure Decision**: 保持现有九模块 Maven 多模块结构；契约与纯领域类型放 `lytgos-core`，Provider 适配放 `lytgos-provider`，JPA 与 Flyway 放 `lytgos-storage`，Spring 装配与运行配置放 `lytgos-boot`。不新增模块。

## Implementation Notes

### A. Profile 与配置

- `Profile` 使用完整字段 record；嵌套 `Identity`、`ProviderRef`、`ScheduleSpec`、`Settings`；列表字段在紧凑构造器里做 null → 空列表与防御性复制。
- `ProfileLoader` 使用 SnakeYAML 解析为 Map 后手工映射，避免 SnakeYAML 对 record 的构造器假设；逐文件捕获异常并记录日志。
- `ProfileRegistry` 使用不可变或并发安全的 Map；重复名称保留首个并记录错误。
- 全局 provider 配置绑定到 `ProviderProperties`；Profile 校验使用全局配置中的 provider 名称集合。

### B. Provider 与工具 schema

- `ProviderModelFactory` 为每个有非空 `api-key` 的 provider 构造 `OpenAiChatModel`：
  - `OpenAiApi.builder().baseUrl(...).apiKey(...)`；
  - `OpenAiChatOptions` 默认项 + 每次调用的 profile model/temperature；
  - `ToolCallingManager.builder().build()`；
  - `NeverRetryPolicy` 的 `RetryTemplate`，保证一次调用语义；
  - `ObservationRegistry.NOOP`。
- `ProviderService.chat` 从 `Prompt.getOptions()` 提取 `ToolCallingChatOptions` 的工具回调，构造本次 `OpenAiChatOptions`，强制 `internalToolExecutionEnabled(false)`。
- `ToolSchemaAdapter` 生成只含 `ToolDefinition` 的 `ToolCallback`，`call` 直接抛 `UnsupportedOperationException`。

### C. 审计与持久化

- `LlmCallAuditor` 只依赖 core；`JpaLlmCallAuditor` 在 storage 实现，补齐 `created_at` 后保存。
- `ProviderService` 成功/失败各写一条审计；失败先审计再上抛；审计异常不被静默吞掉。
- `llm_calls` 只插入；无更新/删除；token 字段可空。

### D. 启动与测试

- `WorkspaceInitialization` 在数据源初始化前确保 `.lytgos` 目录存在；Profile 目录不存在时加载器返回空列表。
- `PersistenceConfiguration` 扫描 `io.lytgos` 下的实体与仓库。
- 根 POM 增加 Surefire 默认排除 `integration`；`integration` profile 打开该组。
- 测试按课件 harness 落地：`ProfileLoaderTest`、`ProviderServiceTest`、`ToolSchemaAdapterTest`、`LlmCallRepositoryTest`、`ProviderSmokeIT`；关键回归测试在实现前或同时落地。

## Complexity Tracking

No constitution violations. No complexity-tracking entries required; the confirmed course-stage deviations are recorded in Research and Constitution Check.
