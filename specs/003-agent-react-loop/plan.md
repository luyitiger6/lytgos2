# Implementation Plan: ReAct Agent Loop

**Branch**: `003-lesson17-react` | **Date**: 2026-11-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-agent-react-loop/spec.md`

## Summary

第 17 节交付自实现的 ReAct 循环：`ReActLoop` 只做调度，`PromptBuilder` 负责组装上下文，`ToolExecutor` 负责唯一的工具执行与 `tool_invocations` 审计，`AgentService` 负责 AgentProfile 查找、请求级上下文和会话保存钩子，`AgentProfileContext` 负责 ThreadLocal 隔离，`ContextLoader` 负责无缓存的 Bootstrap/Skill 元数据加载。为保证 `lytgos-core` 不依赖 Spring AI，core 新增 `LlmGateway` 端口和 `AgentPrompt`/`AgentResponse`/`AgentToolCall`/`AgentMessage` 领域类型；`lytgos-provider` 的 `ProviderService` 实现该端口并做 Spring AI 转换。第 16 节的 Profile 已按业务命名统一为 `AgentProfile`，第 16、17 节合并为一个以 ReAct 为主的 PR。

## Technical Context

**Language/Version**: Java 21；Spring Boot 3.5.16；Spring AI 1.1.2；Spring AI Alibaba 1.1.2.4-security-fix（保留但不用于本节）

**Primary Dependencies**: 不新增第三方依赖；复用 `lytgos-core` 的 SnakeYAML/SLF4J、`lytgos-provider` 的 Spring AI OpenAI 兼容 connector、`lytgos-storage` 的 Spring Data JPA/SQLite/Flyway、JUnit 5/Mockito/AssertJ

**Storage**: SQLite `.lytgos/lytgos.db`；Flyway 迁移 `db/migration/sqlite/V2__init.sql` 新增 `tool_invocations`；V1 的 `llm_calls` 保持不变；本节不创建 `sessions` 表

**Testing**: 课件 harness 五个测试类 `ReActLoopTest`、`PromptBuilderTest`、`ToolExecutorTest`、`AgentServiceTest`、`ContextLoaderTest`；补充 `ToolInvocationRepositoryTest` 验证 Flyway V2；全部单测默认运行，不依赖真实网络；`ProviderSmokeIT` 仍是可选真实模型冒烟

**Target Platform**: JVM 21，Windows/Linux；单二进制 fat JAR

**Project Type**: Maven 多模块 Java 服务/库

**Performance Goals**: 单次 ReAct 处理最多 `max_iterations` 次模型调用（默认 10）；工具顺序执行，不并行；每次工具调用只写一条审计；PromptBuilder 每次读取文件，不做缓存

**Constraints**: core 不依赖 Spring AI；不使用 Spring AI Agent 抽象；不自动执行工具；不引入 Reactor、`CompletableFuture`、自建线程池；不实现 Sandbox、Memory、Session SQLite、CLI/REST；所有新增/修改代码中文注释，字段/参数/局部变量均有注释

**Scale/Scope**: 第 17 节单节范围；单实例、单请求顺序执行；工具数量与历史轮数由 AgentProfile 配置；`tool_invocations` 每次工具调用一行

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 门禁结果 | 证据 |
|---|---|---|
| I. 单二进制 9 模块 | PASS | 只改 `lytgos-core`、`lytgos-provider`、`lytgos-storage`、`lytgos-boot`；不新增模块 |
| II. 五大核心能力优先 | PASS | 只实现 ReAct；不引入并行工具、Agent 委托、流式输出、Memory、Sandbox 实现 |
| III. 自实现 ReAct 循环 | PASS | `ReActLoop` 是唯一循环；默认 10 轮、历史 20 轮、工具顺序执行；不使用 Spring AI Agent |
| IV. Spring AI 受限使用与 Provider 显式映射 | PASS | core 通过 `LlmGateway` 端口调用；ProviderService 仍使用显式 provider 映射并关闭自动工具执行 |
| V. Tool 统一注册、上下文分离与 Sandbox 先行 | PASS | ToolExecutor 是唯一执行入口；工具通过注入的 `Map<String, LytgTool>` 查找；Sandbox 接入位明确留到第 24 节 |
| VI. Memory 单门面、Flyway 与审计 day one | PASS | `tool_invocations` 走 Flyway V2；工具成功/失败都写审计；Memory 长期记忆留扩展点；Session 持久化留第 18 节 |
| VII. 每个 User Story 可演示 | PASS | 五个课程 harness 单测 + repository 测试；真实模型 Demo 依赖第 20 节 `http_get`，在报告中列为剩余人工项 |

**Post-design re-check**: PASS。新增 core 端口是依赖倒置所需，不是为了扩展范围；没有引入新的外部能力或数据库表。

### 课程阶段偏差（用户已确认）

- 本节使用 `AgentProfile` YAML 配置，`AGENT.md`/`AgentLoader` 迁移留到后续课程；Agent 指令取 `identity.prompt`。
- Session 只实现内存领域对象与保存钩子，SQLite `sessions` 表与 SessionManager 留到第 18 节。
- `tool_invocations` 只新增 V2 迁移，不改 V1；不实现审计查询接口。
- 真实 Demo 一的 `http_get` 工具属于后续 Tool 课程；本节用工具替身完成 harness。
- 注释要求比默认规范更严格（中文、每个变量），以用户本次指令为准。

## Project Structure

### Documentation (this feature)

```text
specs/003-agent-react-loop/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── react-engine.md
│   └── tool-invocation.md
├── checklists/
│   └── requirements.md
└── tasks.md              # 由 /speckit-tasks 生成
```

### Source Code (repository root)

```text
lytgos-core/
├── src/main/java/io/lytgos/core/
│   ├── Lytgos.java
│   ├── agent/
│   │   ├── AgentProfile.java
│   │   ├── AgentProfileLoader.java
│   │   ├── AgentProfileRegistry.java
│   │   ├── AgentProfileContext.java
│   │   ├── AgentService.java
│   │   ├── ReActLoop.java
│   │   ├── PromptBuilder.java
│   │   ├── ContextLoader.java
│   │   ├── LlmGateway.java
│   │   └── package-info.java
│   ├── llm/
│   │   ├── AgentPrompt.java
│   │   ├── AgentResponse.java
│   │   ├── AgentToolCall.java
│   │   ├── AgentMessage.java
│   │   └── package-info.java
│   ├── session/
│   │   ├── Session.java
│   │   └── package-info.java
│   ├── tool/
│   │   ├── LytgTool.java
│   │   ├── ToolResult.java
│   │   ├── ToolExecutor.java
│   │   └── package-info.java
│   └── audit/
│       ├── LlmCallAuditor.java
│       ├── ToolInvocationAuditor.java
│       ├── ToolInvocationRecord.java
│       └── package-info.java
└── src/test/java/io/lytgos/core/
    ├── agent/
    │   ├── ReActLoopTest.java
    │   ├── PromptBuilderTest.java
    │   ├── AgentServiceTest.java
    │   ├── ContextLoaderTest.java
    │   ├── AgentProfileLoaderTest.java
    │   └── AgentProfileRegistryTest.java
    └── tool/
        └── ToolExecutorTest.java

lytgos-provider/
├── src/main/java/io/lytgos/provider/
│   ├── ProviderService.java         # 实现 LlmGateway，保留原有 chat 兼容入口
│   ├── ProviderProperties.java
│   ├── ProviderModelFactory.java
│   └── tool/ToolSchemaAdapter.java
└── src/test/java/io/lytgos/provider/
    ├── ProviderServiceTest.java
    ├── ProviderModelFactoryTest.java
    ├── ProviderSmokeIT.java
    └── tool/ToolSchemaAdapterTest.java

lytgos-storage/
├── src/main/java/io/lytgos/storage/
│   ├── StorageModule.java
│   ├── llm/                         # 第 16 节已有
│   └── tool/
│       ├── ToolInvocation.java
│       ├── ToolInvocationRepository.java
│       ├── JpaToolInvocationAuditor.java
│       └── package-info.java
├── src/main/resources/db/migration/sqlite/
│   ├── V1__init.sql                 # llm_calls，保持不变
│   └── V2__init.sql                 # tool_invocations
└── src/test/java/io/lytgos/storage/
    ├── StorageTestApplication.java
    ├── llm/                         # 第 16 节已有
    └── tool/
        └── ToolInvocationRepositoryTest.java

lytgos-boot/
├── src/main/java/io/lytgos/boot/
│   ├── LytgosApplication.java
│   ├── agent/AgentRuntimeConfiguration.java  # ReAct 引擎与 AgentService 装配
│   ├── agent/AgentProfileBootstrapConfiguration.java
│   ├── provider/ProviderBootstrapConfiguration.java
│   ├── persistence/PersistenceConfiguration.java
│   └── workspace/WorkspaceInitialization.java
└── src/main/resources/application.yaml
```

**Structure Decision**: 保持 9 模块；core 按 `agent`、`llm`、`session`、`tool`、`audit` 领域分包；storage 新增 `tool` 领域；boot 新增 `agent/AgentRuntimeConfiguration` 装配 ReAct 引擎；provider 只做 Spring AI 适配，不反向依赖引擎。

## Complexity Tracking

> 本节没有违反宪章的复杂度；新增 core 端口是本计划的关键架构决策。

| 决策 | 为什么需要 | 被拒绝的更简单方案 |
|---|---|---|
| core 新增 `LlmGateway`/`AgentPrompt`/`AgentResponse` 端口 | core 必须自实现 ReAct 且不能依赖 Spring AI | 在 provider 实现 ReActLoop（违反模块落位）；core 直接依赖 Spring AI（违反依赖方向） |
| Session 保存钩子而非 SessionManager | 第 18 节才交付 SessionManager；本节只验证“处理完成后保存” | 本节提前建 sessions 表与仓库（超出本节交付物） |
| 工具集合用 `Map<String, LytgTool>` 注入 | 避免 core 依赖 `lytgos-tool` 的 ToolRegistry | 在 core 新建重复的注册表接口（与第 20 节交付物冲突） |
