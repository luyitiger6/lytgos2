# Research: Agent Provider（第 16 节）

## 决策 1：本节 Profile 来源

- **Decision**: 使用 `.lytgos/profiles/*.yaml` 与 `ProfileLoader`/`ProfileRegistry`，放在 `lytgos-core`。
- **Rationale**: 课件与 `lytgos-dev` 的模块落位表都以 ProfileLoader 为本节交付物；Provider 是第一个消费 Profile 的模块。后续 Agent 课程再迁移到 `.lytgos/agents/*/AGENT.md` 与 `AgentLoader.deriveProfile`。
- **Alternatives considered**: 直接按 `TechnicalSolution` §8.2 使用 `AGENT.md`/AgentLoader——与本节课件 harness 的 YAML 全字段解析不一致；同时支持两套 Profile 来源——引入超出本节交付物的概念。

## 决策 2：Profile 字段集

- **Decision**: 保留课件完整字段集：`name`、`description`、`identity`、`provider`（name/model/temperature）、`tools`、`skills`、`mcp_servers`、`channels`、`notify_channels`、`schedules`、`bootstrap`、`settings`。其中 `notify_channels` 是兼容字段，本节不消费，第 19 节迁移到全局通知渠道注册表。
- **Rationale**: `ProfileLoaderTest` 要求合法 YAML 全字段解析；用户已确认按课件保留完整字段。
- **Alternatives considered**: 只保留 `TechnicalSolution` §8.2 的最小字段集——会让本节 harness 的全字段断言失败；删除 `notify_channels`——需要同时修改课件验收点，超出本节范围。

## 决策 3：Spring AI 1.1.2 实际 API 适配

- **Decision**: 使用本地依赖中已验证的 API：
  - `ChatModel.call(Prompt)`；
  - `ToolCallingChatOptions` / `DefaultToolCallingChatOptions.Builder` 的 `toolCallbacks(...)` 与 `internalToolExecutionEnabled(false)`；
  - `ChatResponse`、`ChatResponse.getMetadata().getUsage()`、`Usage.getPromptTokens()/getCompletionTokens()/getTotalTokens()`。
- **Rationale**: 课件里的 `Request.autoExecuteTools(false)` 是示意写法，Spring AI 1.1.2 中不存在；必须按真实 API 落地，否则编译与测试无法通过。
- **Alternatives considered**: 自造 `Request` 包装类——增加无用抽象；继续使用课件伪代码——不可编译。

## 决策 4：Provider 映射与 OpenAI 兼容 connector

- **Decision**: 给 `lytgos-provider` 增加 BOM 管理的 `spring-ai-openai` 1.1.2；按 `lytgos.providers` 逐条手工构造 `OpenAiChatModel`，保存到显式 `Map<String, ChatModel>`。每个 provider 使用自己的 `base-url` 与 `api-key`；不按类型扫描容器中的 ChatModel。
- **Rationale**: DeepSeek/Kimi 走 OpenAI 兼容协议；Spring AI BOM 1.1.2 已包含 `spring-ai-openai`；当前 provider 模块只有 DashScope starter，不能覆盖用户自己的 DeepSeek/Kimi 账号。
- **Alternatives considered**: 使用单个 OpenAI starter 自动配置——只能配置一套 base URL，无法同时支持 DeepSeek 与 Kimi；使用 DashScope 调 DeepSeek——需要阿里云百炼账号，不符合本项目用户自带账号的约束。
- **Missing key behavior**: api-key 为空的 provider 不构造 model，启动不失败；Profile 引用该 provider 时在调用前得到清晰错误，而不是静默使用其他模型。

## 决策 5：工具 schema 只翻译、不执行

- **Decision**: `ToolSchemaAdapter` 把 `LytgTool` 映射为 Spring AI `ToolCallback`，只填 `ToolDefinition`（name/description/inputSchema）；`ToolCallback.call(...)` 直接抛 `UnsupportedOperationException`。`ProviderService` 始终把 `internalToolExecutionEnabled` 设为 `false`。
- **Rationale**: 工具执行权属于后续 `ReActLoop` + `ToolExecutor`，必须经过 Sandbox；Spring AI 自动执行会造成双重执行并绕过沙箱。
- **Alternatives considered**: 在适配器里调用 `LytgTool.execute`——越过执行边界；启用 Spring AI 自动执行——违反项目宪章。

## 决策 6：审计端口与存储

- **Decision**: 在 `lytgos-core` 增加 `LlmCallAuditor` 端口（含 `LlmCallRecord`），在 `lytgos-storage` 实现 `LlmCall` 实体、`LlmCallRepository` 和 `JpaLlmCallAuditor`。ProviderService 只依赖 core 端口，不依赖 storage。
- **Rationale**: 保持模块依赖方向；审计从 day one 落库；失败调用也必须留痕，不能只靠日志。
- **Alternatives considered**: ProviderService 直接依赖 `LlmCallRepository`——违反 provider → storage 的模块方向；只写日志——违反“审计 day one”原则。

## 决策 7：Flyway 管理建表

- **Decision**: `llm_calls` 由 `lytgos-storage/src/main/resources/db/migration/sqlite/V1__init.sql` 创建；使用 Spring Boot 管理的 Flyway 11.7.2；SQLite 支持内置于 `flyway-core`，不引入不存在的 `flyway-database-sqlite`；`spring.jpa.hibernate.ddl-auto=none`；`LlmCallRepositoryTest` 执行同一份 Flyway 迁移。
- **Rationale**: 项目宪章要求 Flyway 按 vendor 分轨；课件的“手工建表脚本、不依赖 ddl-auto”意图由迁移脚本满足；避免测试与生产 schema 漂移。
- **Alternatives considered**: 仅在测试里放一份 `schema.sql`——生产与测试两份 schema 容易漂移；`ddl-auto=update`——SQLite 演进能力弱，违反项目工程约束。
- **Version note**: Spring Boot 3.5.16 管理 Flyway 11.7.2、`sqlite-jdbc` 3.49.1.0 与 `hibernate-community-dialects` 6.6.53.Final；Flyway 11.7.2 的 SQLite 支持已内置在 `flyway-core`，Maven Central 没有 `flyway-database-sqlite` 制品。

## 决策 8：集成冒烟与 CI 跳过

- **Decision**: `ProviderSmokeIT` 使用 `@Tag("integration")`；Surefire 默认排除 `integration` 组；根 POM 增加 `integration` profile，手动运行时包含该组。`ProviderSmokeIT` 在缺少真实 key 时通过 JUnit `Assumptions` 跳过，不失败。
- **Rationale**: 持续集成（Continuous Integration，CI）不能依赖外部模型可用性；真实连通性由人工在本地验证。
- **Alternatives considered**: 默认运行集成测试——无 key 时流水线必红；使用 Failsafe 命名 `*IT`——需要额外插件配置，且本课程约定使用 `@Tag("integration")`。

## 决策 9：编码与注释约定

- **Decision**: 本节新增或修改的代码全部使用中文注释；每个字段、方法参数和局部变量都要有注释；类 Javadoc 保留 `@author LytgOS2 Team` 以满足阿里巴巴 Java 开发规约（P3C）的 `ClassMustHaveAuthorRule`。
- **Rationale**: 用户明确要求；同时满足 P3C 阻断门禁。
- **Alternatives considered**: 只写“为什么”的少量注释——与用户本次要求冲突，不采用。

## 决策 10：Spring AI Alibaba（SAA）的定位

- **Decision**: SAA 保留在 `lytgos-provider` classpath，用于 DashScope/Qwen connector 与未来 AgentScope/A2A 分布式 Agent 基座；ProviderService 的核心抽象是 Spring AI `ChatModel`，SAA 的 DashScope `ChatModel` 与 Spring AI OpenAI `ChatModel` 可以放进同一个显式映射。
- **Rationale**: 本地 SAA 1.1.2.4 BOM 包含 `spring-ai-alibaba-starter-dashscope`、`spring-ai-alibaba-starter-agentscope`、`spring-ai-alibaba-starter-a2a-nacos`、`spring-ai-alibaba-starter-config-nacos` 等 artifact；其中 AgentScope/A2A/Nacos 正是未来分布式 Agent 的接入点。但本地 BOM 没有 DeepSeek/OpenAI connector；DeepSeek/Kimi 使用 Spring AI 官方 OpenAI 兼容 connector，才能使用用户自己的账号。SAA 的 DashScope 路径需要阿里云百炼账号，`specs/001-lytgos-runtime-kernel/research.md` 已明确拒绝这条路径。
- **Alternatives considered**: 全部走 SAA DashScope——改变“用户自带 DeepSeek/Kimi 账号”的前提；现在引入 AgentScope/A2A starter——超出 1.0 单机范围，属于分布式阶段，不能在第 16 节静默扩张。
- **Consequence**: 第 16 节不新增 AgentScope/A2A 依赖；未来分布式阶段再按 ADR/ReleaseBaseline 引入。SAA 的 connector 仍可通过同一 `Map<String, ChatModel>` 扩展。
