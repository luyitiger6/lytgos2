# Tasks: LytgOS2 1.0 运行时内核

**Input**: Design documents from `specs/001-lytgos-runtime-kernel/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: 本规格 `FR-036` 明确要求“每个功能模块至少有一个端到端测试”，因此测试任务已包含。未实际运行的测试不得声称通过。

**Organization**: 任务按 user story 分组，每个 story 可独立实现与验证；优先级遵循 spec：US1 (P1) → US2 (P2) → US3 ∥ US4 (P3) → US5 (P4)。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行执行（不同文件、无未完成依赖）
- **[Story]**: 所属 user story（`US1`…`US5`）；Setup/Foundational/Polish 无故事标签
- 每个任务都包含可执行动作与具体文件路径
- 数据模型约束以 `data-model.md` 的 `Entity.field` 引用为准，不在任务中重复约束全文

## Path Conventions

本仓库是九模块 Java Maven 单体：`lytgos-core/`、`lytgos-provider/`、`lytgos-memory/`、`lytgos-tool/`、`lytgos-channel-cli/`、`lytgos-web/`、`lytgos-storage/`、`lytgos-cli/`、`lytgos-boot/`。Java 源码在 `<module>/src/main/java/io/lytgos/...`，测试在 `<module>/src/test/java/io/lytgos/...`。

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 依赖基线、命名清理、构建/启动骨架、共享测试设施。

- [x] T001a Upgrade `pom.xml` to Spring Boot 3.5.16 and add dependencyManagement for Spring AI 1.1.2 BOM, Spring AI Alibaba 1.1.2.4-security-fix BOM, MCP BOM 2.2.0, and springdoc 2.8.17. Completed in the engineering foundation.
- [ ] T001b Lock `sqlite-jdbc`/Flyway versions and finalize Surefire/Failsafe settings in `pom.xml`; do this together with the US-4 storage implementation.
- [x] T002 [P] Rename `OryxTool` to `LytgTool` and update references in `lytgos-core/src/main/java/io/lytgos/core/LytgTool.java`, `lytgos-core/src/main/java/io/lytgos/core/ToolResult.java`, `lytgos-tool/src/main/java/io/lytgos/tool/ToolRegistry.java`, `README.md`, and `readme.zh.md`.
- [ ] T003 [P] Add Spring AI OpenAI and SLF4J dependencies to `lytgos-provider/pom.xml`; add SLF4J and Spring context support to `lytgos-memory/pom.xml`.
- [ ] T004 [P] Add MCP SDK (`mcp-core`, `mcp-json-jackson2`), Jackson, Spring AI model, JPA, SQLite JDBC, Hibernate community dialects, and Flyway dependencies to `lytgos-tool/pom.xml` and `lytgos-storage/pom.xml`.
- [ ] T005 [P] Add springdoc/validation to `lytgos-web/pom.xml`, channel-cli and SnakeYAML to `lytgos-cli/pom.xml`, boot/logback to `lytgos-boot/pom.xml`, and SnakeYAML/Spring context to `lytgos-core/pom.xml`.
- [ ] T006 [P] Create `bin/lytgos` and `bin/lytgos.cmd` launchers that execute the `lytgos-boot` fat JAR with all arguments.
- [ ] T007 Create base `lytgos-boot/src/main/resources/application.yaml` with `lytgos.workspace`, providers (deepseek/kimi), sandbox defaults, SQLite/JPA/Flyway, virtual threads, and request limits.
- [ ] T008 [P] Create `lytgos-boot/src/main/resources/logback-spring.xml` with ECS structured console logging and rolling file `.lytgos/logs/lytgos.log`.
- [ ] T009 [P] Create workspace templates under `lytgos-cli/src/main/resources/workspace/`: `AGENTS.md`, `SOUL.md`, `USER.md`, `MEMORY.md` (with `## 核心记忆` / `## 归档记忆`), and `mcp_servers.yaml`.
- [ ] T010 [P] Create shared test fixtures: `TempWorkspace.java` in `lytgos-core/src/test/java/io/lytgos/core/testsupport/`, `WireMockSupport.java` in `lytgos-provider/src/test/java/io/lytgos/provider/testsupport/`, `McpTestServer` in `lytgos-tool/src/test/java/io/lytgos/tool/testsupport/`, and `lytgos-boot/src/test/resources/application-test.yaml`.
- [ ] T011 [P] Update `.gitignore` to ignore `.lytgos/`, `*.db`, `*.db-*`, `logs/`, and generated build output.

**Checkpoint**: Dependencies resolve, fat JAR packaging is configured, and shared fixtures exist.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 核心契约、端口、Agent 加载、SQLite/Flyway 6 表、JPA stores 与测试替身。**所有 user story 都依赖本阶段。**

**⚠️ CRITICAL**: 本阶段完成前不要开始 US1–US5 的业务实现。

- [ ] T012 [P] Consolidate `LytgTool.java`/`ToolResult.java` (moved from `io.lytgos.core`) and create `ToolDefinition.java`/`ToolSource.java` in `lytgos-core/src/main/java/io/lytgos/core/model/`; cite `data-model.md` `ToolResult.success`, `ToolResult.content`, `ToolResult.error`, `ToolResult.retryable`, `ToolDefinition.name`, `ToolDefinition.description`, `ToolDefinition.inputSchema`, and `ToolDefinition.source`.
- [ ] T013 [P] Create/move profile/config models in `lytgos-core/src/main/java/io/lytgos/core/model/`: `Profile.java`, `Identity.java`, `ProviderConfig.java`, `Settings.java`, `ScheduleDefinition.java`; cite `data-model.md` `AgentDefinition.name`, `AgentDefinition.description`, `AgentDefinition.identity.agent_name`, `AgentDefinition.identity.prompt`, `AgentDefinition.provider.name`, `AgentDefinition.provider.model`, `AgentDefinition.provider.temperature`, `AgentDefinition.tools`, `AgentDefinition.mcp_servers`, `AgentDefinition.channels`, `AgentDefinition.bootstrap`, `AgentDefinition.schedules`, `AgentDefinition.settings.max_iterations`, `AgentDefinition.settings.max_history_turns`, `ScheduleDefinition.key`, `ScheduleDefinition.cron`, `ScheduleDefinition.zone`, `ScheduleDefinition.message`, `ScheduleDefinition.display_name`, and `ScheduleDefinition.enabled`.
- [ ] T014 [P] Create/move session/message models in `lytgos-core/src/main/java/io/lytgos/core/model/`: `Session.java`, `Message.java`, `ToolCall.java`, `SessionStatus.java`, `TerminationReason.java`; cite `data-model.md` `sessions.session_id`, `sessions.profile_name`, `sessions.channel`, `sessions.user_id`, `sessions.status`, `sessions.created_at`, `sessions.last_active_at`, `sessions.archived_at`, `Message.role`, `Message.content`, `Message.toolCalls`, `Message.toolCallId`, `Message.name`, `Message.terminationReason`, and `Message.createdAt`.
- [ ] T015 [P] Create core exceptions in `lytgos-core/src/main/java/io/lytgos/core/exception/`: `LytgosException.java`, `ProviderException.java`, `AgentNotFoundException.java`, `ArchivedSessionException.java`, `ToolExecutionException.java`.
- [ ] T016 [P] Create LLM ports in `lytgos-core/src/main/java/io/lytgos/core/port/`: `LlmGateway.java`, `LlmRequest.java`, `LlmResponse.java`.
- [ ] T017 [P] Create persistence/capability ports in `lytgos-core/src/main/java/io/lytgos/core/port/`: `MemoryGateway.java`, `ToolCatalog.java`, `SessionStore.java`, `AuditStore.java`, `ScheduledTaskStore.java`, `NotifyChannelStore.java`.
- [ ] T018 [P] Create `lytgos-core/src/main/java/io/lytgos/core/config/LytgosProperties.java` and `WorkspacePaths.java` with workspace/provider/sandbox/memory defaults.
- [ ] T019 Implement `AgentLoader.java`, `ContextLoader.java`, and `ProfileRegistry.java` in `lytgos-core/src/main/java/io/lytgos/core/engine/`; parse `AGENT.md` frontmatter/body, bootstrap files, skills metadata, and schedules; cite `data-model.md` `AgentDefinition.name`, `AgentDefinition.description`, `AgentDefinition.identity.agent_name`, `AgentDefinition.identity.prompt`, `AgentDefinition.provider.name`, `AgentDefinition.provider.model`, `AgentDefinition.provider.temperature`, `AgentDefinition.tools`, `AgentDefinition.mcp_servers`, `AgentDefinition.channels`, `AgentDefinition.bootstrap`, `AgentDefinition.schedules`, `AgentDefinition.settings.max_iterations`, `AgentDefinition.settings.max_history_turns`, `ScheduleDefinition.key`, `ScheduleDefinition.cron`, `ScheduleDefinition.zone`, `ScheduleDefinition.message`, `ScheduleDefinition.display_name`, `ScheduleDefinition.enabled`, `Skill.name`, and `Skill.description`.
- [ ] T020 Implement `SessionLockManager.java` and `SessionResolver.java` in `lytgos-core/src/main/java/io/lytgos/core/engine/`; fair per-session locking, CLI auto-resume, scheduler reuse, REST explicit, invoke transient; cite `data-model.md` `sessions.channel`, `sessions.user_id`, `sessions.status`, and `sessions.last_active_at`.
- [ ] T021 Create `ToolRegistry.java` in `lytgos-tool/src/main/java/io/lytgos/tool/` implementing `ToolCatalog`; support register/find/list/duplicate-name rejection.
- [ ] T022 [P] Create Flyway migration `lytgos-storage/src/main/resources/db/migration/sqlite/V1__init.sql` with the 6 tables (`sessions`, `tool_invocations`, `llm_calls`, `scheduled_tasks`, `task_executions`, `notify_channels`) and all indexes from `data-model.md` §2.
- [ ] T023 [P] Create JPA entities `SessionEntity.java`, `ToolInvocationEntity.java`, `LlmCallEntity.java` in `lytgos-storage/src/main/java/io/lytgos/storage/entity/`; cite every column in `data-model.md` `sessions.session_id`, `sessions.profile_name`, `sessions.channel`, `sessions.user_id`, `sessions.messages_json`, `sessions.status`, `sessions.created_at`, `sessions.last_active_at`, `sessions.archived_at`, `tool_invocations.id`, `tool_invocations.session_id`, `tool_invocations.profile_name`, `tool_invocations.tool_name`, `tool_invocations.tool_source`, `tool_invocations.input_json`, `tool_invocations.result_json`, `tool_invocations.success`, `tool_invocations.error_message`, `tool_invocations.retry_count`, `tool_invocations.attempt_errors_json`, `tool_invocations.duration_ms`, `tool_invocations.created_at`, `llm_calls.id`, `llm_calls.session_id`, `llm_calls.profile_name`, `llm_calls.provider`, `llm_calls.model`, `llm_calls.prompt_tokens`, `llm_calls.completion_tokens`, `llm_calls.total_tokens`, `llm_calls.duration_ms`, `llm_calls.success`, `llm_calls.error_message`, and `llm_calls.created_at`.
- [ ] T024 [P] Create JPA entities `ScheduledTaskEntity.java`, `TaskExecutionEntity.java`, `NotifyChannelEntity.java` in `lytgos-storage/src/main/java/io/lytgos/storage/entity/`; cite every column in `data-model.md` `scheduled_tasks.schedule_id`, `scheduled_tasks.profile_name`, `scheduled_tasks.schedule_key`, `scheduled_tasks.display_name`, `scheduled_tasks.cron`, `scheduled_tasks.zone`, `scheduled_tasks.message`, `scheduled_tasks.enabled`, `scheduled_tasks.retired`, `scheduled_tasks.next_run_at`, `scheduled_tasks.last_run_at`, `scheduled_tasks.last_status`, `scheduled_tasks.run_count`, `scheduled_tasks.updated_at`, `task_executions.id`, `task_executions.schedule_id`, `task_executions.session_id`, `task_executions.started_at`, `task_executions.success`, `task_executions.error_message`, `task_executions.duration_ms`, `notify_channels.id`, `notify_channels.name`, `notify_channels.type`, `notify_channels.url`, `notify_channels.description`, `notify_channels.config_json`, `notify_channels.created_at`, and `notify_channels.updated_at`.
- [ ] T025 Create Spring Data repositories in `lytgos-storage/src/main/java/io/lytgos/storage/repository/` for all 6 entities.
- [ ] T026 Create `StorageConfig.java`, `InstantStringConverter.java`, and `MessageJsonCodec.java` in `lytgos-storage/src/main/java/io/lytgos/storage/config/`; configure SQLite WAL/busy_timeout and JSON message serialization; cite `data-model.md` `Message.role`, `Message.content`, `Message.toolCalls`, `Message.toolCallId`, `Message.name`, `Message.terminationReason`, and `Message.createdAt`.
- [ ] T027 Implement `JpaSessionStore.java` and `JpaAuditStore.java` in `lytgos-storage/src/main/java/io/lytgos/storage/store/`; cite `data-model.md` `sessions.*` and `tool_invocations.*`/`llm_calls.*` field definitions.
- [ ] T028 Implement `JpaScheduledTaskStore.java` and `JpaNotifyChannelStore.java` in `lytgos-storage/src/main/java/io/lytgos/storage/store/`; cite `data-model.md` `scheduled_tasks.*`, `task_executions.*`, and `notify_channels.*`.
- [ ] T029 Create test doubles `InMemorySessionStore.java`, `InMemoryAuditStore.java`, `InMemoryToolCatalog.java` in `lytgos-core/src/test/java/io/lytgos/core/testsupport/`.
- [ ] T030 Add storage integration tests in `lytgos-storage/src/test/java/io/lytgos/storage/StorageIntegrationTest.java`: Flyway migration, session CRUD, audit rows with null `session_id`, JSON round-trip, WAL settings.
- [ ] T031 Add core tests in `lytgos-core/src/test/java/io/lytgos/core/engine/AgentLoaderTest.java` and `SessionResolverTest.java`: frontmatter validation, skills, schedules, session resolution.
- [ ] T032 [P] Create test Agent fixtures under `lytgos-core/src/test/resources/agents/hello/AGENT.md`, `.../agents/weather/AGENT.md`, and `lytgos-core/src/test/resources/skills/tech-digest/SKILL.md`.
- [ ] T033 Create `RuntimeMode.java` and `RuntimeConfiguration.java` in `lytgos-boot/src/main/java/io/lytgos/boot/` and wire core/storage beans; provider/memory/tool wiring is added in T050/T075/T094.
- [ ] T034 Add `lytgos-boot/src/test/java/io/lytgos/boot/ApplicationContextSmokeTest.java`: context loads, Flyway creates 6 tables, no scheduler in test profile.

**Checkpoint**: Foundation ready — US1 can begin; US3/US4 depend on the same ports/stores.

---

## Phase 3: User Story 1 - 配置 Provider 并完成多轮对话 (Priority: P1) 🎯 MVP

**Goal**: 用户通过 CLI 配置 Provider，与 Agent 多轮对话；Provider/模型可切换，每次 LLM 调用写 `llm_calls`。

**Independent Test**: 创建 workspace 与 Agent，配置一个 Provider，完成多轮对话；切换 Provider 后 `llm_calls` 的 provider/model 随之变化；不依赖 Tool、Memory 或 Web Service。

### Tests for User Story 1

- [ ] T035 [P] [US1] Contract test for explicit provider mapping with WireMock in `lytgos-provider/src/test/java/io/lytgos/provider/ProviderRegistryTest.java`: deepseek/kimi base URLs, model override, unknown provider error.
- [ ] T036 [P] [US1] Audit test in `lytgos-core/src/test/java/io/lytgos/core/engine/LlmAuditTest.java`: one `llm_calls` row per call with provider/model/token/duration; cite `data-model.md` `llm_calls.provider`, `llm_calls.model`, `llm_calls.prompt_tokens`, `llm_calls.completion_tokens`, `llm_calls.total_tokens`, `llm_calls.duration_ms`, `llm_calls.success`, and `llm_calls.error_message`.
- [ ] T037 [P] [US1] Session persistence/resume test in `lytgos-storage/src/test/java/io/lytgos/storage/SessionPersistenceTest.java`: append/load, active lookup, archived read-only; cite `data-model.md` `sessions.session_id`, `sessions.status`, `sessions.messages_json`, and `sessions.last_active_at`.
- [ ] T038 [P] [US1] CLI tests in `lytgos-cli/src/test/java/io/lytgos/cli/InitCommandTest.java`, `ProfileCommandTest.java`, and `ChatCommandTest.java`: idempotent init, profile create/list/show/delete, chat auto-resume and `--new`.
- [ ] T039 [P] [US1] E2E test in `lytgos-boot/src/test/java/io/lytgos/boot/Us1ChatE2ETest.java`: init workspace → create Agent → chat with WireMock → switch provider → assert `llm_calls` and session history.

### Implementation for User Story 1

- [ ] T040 [P] [US1] Implement `ProviderProperties.java`, `ProviderRegistry.java`, and `OpenAiCompatibleProviderFactory.java` in `lytgos-provider/src/main/java/io/lytgos/provider/`; explicit immutable name→`ChatModel` map, no type scanning; cite `data-model.md` `AgentDefinition.provider.name`, `AgentDefinition.provider.model`, and `AgentDefinition.provider.temperature`.
- [ ] T041 [US1] Implement `SpringAiLlmGateway.java` and basic `FunctionCallingAdapter.java` in `lytgos-provider/src/main/java/io/lytgos/provider/`; call `ChatModel.call(Prompt)` directly, not `ChatClient` auto tool execution.
- [ ] T042 [US1] Implement basic `PromptBuilder.java` and `AgentService.process` no-tool path in `lytgos-core/src/main/java/io/lytgos/core/engine/`; persist the user message and assistant reply; cite `data-model.md` `AgentDefinition.settings.max_iterations`, `AgentDefinition.settings.max_history_turns`, `sessions.messages_json`, and `Message.role`/`Message.content`/`Message.createdAt`.
- [ ] T043 [US1] Write `llm_calls` audit rows from `AgentService`/`JpaAuditStore`; success and failure paths; cite `data-model.md` `llm_calls.*` columns from T023.
- [ ] T044 [US1] Implement CLI session resolution and persistence in `lytgos-core/src/main/java/io/lytgos/core/engine/SessionResolver.java` + `AgentService.java`; cite `data-model.md` `sessions.session_id`, `sessions.channel`, `sessions.user_id`, `sessions.status`, and `sessions.last_active_at`.
- [ ] T045 [P] [US1] Implement `ConfigLoader.java` in `lytgos-cli/src/main/java/io/lytgos/cli/` for workspace paths, provider config, and `${ENV_VAR}` resolution.
- [ ] T046 [US1] Implement `InitCommand.java` in `lytgos-cli/src/main/java/io/lytgos/cli/command/` using `lytgos-cli/src/main/resources/workspace/`; idempotent, no overwrite.
- [ ] T047 [US1] Implement `ProfileListCommand.java`, `ProfileCreateCommand.java`, `ProfileShowCommand.java`, and `ProfileDeleteCommand.java` in `lytgos-cli/src/main/java/io/lytgos/cli/command/`; cite `data-model.md` `AgentDefinition.name`, `AgentDefinition.description`, `AgentDefinition.provider.name`, `AgentDefinition.provider.model`, `AgentDefinition.settings.max_iterations`, `AgentDefinition.settings.max_history_turns`, and `ScheduleDefinition.key`/`ScheduleDefinition.cron`/`ScheduleDefinition.zone`/`ScheduleDefinition.message`/`ScheduleDefinition.display_name`/`ScheduleDefinition.enabled` in the generated template.
- [ ] T048 [US1] Implement `ProviderListCommand.java` in `lytgos-cli/src/main/java/io/lytgos/cli/command/`; show name/type/default model/base URL/key presence, never key value.
- [ ] T049 [US1] Implement `ChatCommand.java` in `lytgos-cli/src/main/java/io/lytgos/cli/command/` and `CliChannel.java`/`ChatRepl.java` in `lytgos-channel-cli/src/main/java/io/lytgos/channel/cli/`; auto-resume, `--new`, `/quit`, errors stay actionable.
- [ ] T050 [US1] Implement `LytgosApplication` dispatch and `LytgosCli` wiring in `lytgos-boot/src/main/java/io/lytgos/boot/LytgosApplication.java` and `lytgos-cli/src/main/java/io/lytgos/cli/LytgosCli.java`; no-args/`serve` start servlet, `chat` starts NONE + no scheduler, file commands avoid full Spring; register commands implemented through US1 and extend in later stories.
- [ ] T051 [US1] Implement `StatusCommand.java` in `lytgos-cli/src/main/java/io/lytgos/cli/command/`; workspace/provider/profile/tool/session/task counts, no secrets.
- [ ] T052 [US1] Run `/speckit-analyze` for US1 and fix drift in `lytgos-core/src/main/java/io/lytgos/core/`, `lytgos-provider/src/main/java/io/lytgos/provider/`, and `lytgos-cli/src/main/java/io/lytgos/cli/`; record findings in `specs/001-lytgos-runtime-kernel/` notes.

**Checkpoint**: US1 works independently — multi-turn chat, provider switch, `llm_calls`, CLI init/profile/chat.

---

## Phase 4: User Story 2 - Agent 自主调用 Tool 完成任务 (Priority: P2)

**Goal**: 自实现 ReAct 循环，按顺序执行 Tool、回填结果、限制迭代次数；Tool 失败可重试且审计为一行。

**Independent Test**: 使用确定性测试 Tool，分别验证 0 次、1 次、连续 3 次 Tool 调用；验证最大迭代终止、重试与审计。

### Tests for User Story 2

- [ ] T053 [P] [US2] `ReActLoopTest.java` in `lytgos-core/src/test/java/io/lytgos/core/engine/`: 0/1/3 tool calls, sequential order, `max_iterations` termination, `terminationReason`; cite `data-model.md` `Message.toolCalls`, `Message.toolCallId`, and `Message.terminationReason`.
- [ ] T054 [P] [US2] `ToolExecutorTest.java` in `lytgos-core/src/test/java/io/lytgos/core/engine/`: retryable vs non-retryable, initial + 3 retries, one audit row, unknown tool; cite `data-model.md` `tool_invocations.retry_count`, `tool_invocations.attempt_errors_json`, `tool_invocations.success`, `tool_invocations.error_message`, and `tool_invocations.duration_ms`.
- [ ] T055 [P] [US2] `PromptBuilderTest.java` in `lytgos-core/src/test/java/io/lytgos/core/engine/`: system prompt order, bootstrap, skill metadata, history truncation at user-turn boundaries; cite `data-model.md` `AgentDefinition.settings.max_history_turns`.
- [ ] T056 [P] [US2] E2E test `Us2ReActE2ETest.java` in `lytgos-boot/src/test/java/io/lytgos/boot/`: fake provider returns tool calls; deterministic tools; assert final reply, `tool_invocations`, and session history.
- [ ] T057 [P] [US2] Create `DeterministicTool.java` test fixture in `lytgos-core/src/test/java/io/lytgos/core/testsupport/` with configurable success/failure/retryable behavior.

### Implementation for User Story 2

- [ ] T058 [P] [US2] Implement full `PromptBuilder.java` in `lytgos-core/src/main/java/io/lytgos/core/engine/`: bootstrap + agent body + date/time + skill metadata + tool definitions + history; inject long-term memory through `MemoryGateway` (empty stub until US3 T074); cite `data-model.md` `AgentDefinition.bootstrap`, `AgentDefinition.settings.max_history_turns`, and `Message.content`/`Message.role`/`Message.createdAt`.
- [ ] T059 [US2] Implement `ReActLoop.java` in `lytgos-core/src/main/java/io/lytgos/core/engine/`: loop, sequential tool calls, max iterations, final message, termination reason; cite `data-model.md` `Message.toolCalls`, `Message.toolCallId`, `Message.terminationReason`, `AgentDefinition.settings.max_iterations`, and `AgentDefinition.settings.max_history_turns`.
- [ ] T060 [US2] Implement `ToolExecutor.java` in `lytgos-core/src/main/java/io/lytgos/core/engine/`: find tool, enforce, execute, retry 0–3, write one audit row, return `ToolResult`; cite `data-model.md` `tool_invocations.tool_name`, `tool_invocations.tool_source`, `tool_invocations.input_json`, `tool_invocations.result_json`, `tool_invocations.success`, `tool_invocations.error_message`, `tool_invocations.retry_count`, `tool_invocations.attempt_errors_json`, and `tool_invocations.duration_ms`.
- [ ] T061 [US2] Integrate `ReActLoop` with `AgentService` in `lytgos-core/src/main/java/io/lytgos/core/engine/AgentService.java`: persist tool calls/results in order, save session after each step, return final/termination; cite `data-model.md` `sessions.messages_json` and `Message.role`/`Message.content`/`Message.toolCalls`/`Message.toolCallId`/`Message.name`/`Message.terminationReason`/`Message.createdAt`.
- [ ] T062 [US2] Extend `FunctionCallingAdapter.java` in `lytgos-provider/src/main/java/io/lytgos/provider/` to send tool definitions via `ToolCallingChatOptions` and parse `ToolCall` responses; cite `data-model.md` `ToolDefinition.name`, `ToolDefinition.description`, and `ToolDefinition.inputSchema`.
- [ ] T063 [US2] Write `llm_calls` rows for tool-call turns, including token usage and success/failure; cite `data-model.md` `llm_calls.session_id`, `llm_calls.provider`, `llm_calls.model`, `llm_calls.prompt_tokens`, `llm_calls.completion_tokens`, `llm_calls.total_tokens`, `llm_calls.duration_ms`, `llm_calls.success`, and `llm_calls.error_message`.
- [ ] T064 [US2] Update `ChatRepl.java` in `lytgos-channel-cli/src/main/java/io/lytgos/channel/cli/` to print tool summaries and max-iteration termination without exposing raw secrets.
- [ ] T065 [US2] Enforce `max_iterations` and `max_history_turns` validation in `AgentLoader.java`; cite `data-model.md` `AgentDefinition.settings.max_iterations` and `AgentDefinition.settings.max_history_turns`.
- [ ] T066 [US2] Run `/speckit-analyze` for US2 and fix drift in `lytgos-core/src/main/java/io/lytgos/core/engine/` and `lytgos-provider/src/main/java/io/lytgos/provider/`.

**Checkpoint**: US2 works independently — ReAct loop, sequential tools, retry/audit, max iterations.

---

## Phase 5: User Story 3 - 跨会话记忆与重启恢复 (Priority: P3)

**Goal**: 会话历史跨重启恢复；长期记忆写入 `MEMORY.md` 的核心/归档区；核心偏好永不截断；归档可关键词召回。

**Independent Test**: 对话后重启继续同一 Session；保存核心偏好后新 Session 自动包含；保存归档条目并关键词召回；无匹配返回空。

### Tests for User Story 3

- [ ] T067 [P] [US3] `MarkdownMemoryStoreTest.java` in `lytgos-memory/src/test/java/io/lytgos/memory/`: sections, missing file/section tolerance, archival truncation, case-insensitive recall, empty result.
- [ ] T068 [P] [US3] `MemoryServiceTest.java` and `MemoryToolsTest.java` in `lytgos-memory/src/test/java/io/lytgos/memory/`: facade delegation, save core/archival, recall only archival.
- [ ] T069 [P] [US3] Restart recovery E2E test `Us3MemoryRestartE2ETest.java` in `lytgos-boot/src/test/java/io/lytgos/boot/`: restart context, resume session, core preference visible in new session.
- [ ] T070 [P] [US3] Prompt injection test in `lytgos-core/src/test/java/io/lytgos/core/engine/PromptBuilderMemoryTest.java`: core full, archival truncated, no cross-contamination.

### Implementation for User Story 3

- [ ] T071 [US3] Implement `LongTermMemoryStore.java` and `MemoryService.java` in `lytgos-memory/src/main/java/io/lytgos/memory/`; single facade for session + long-term memory.
- [ ] T072 [P] [US3] Implement `MarkdownMemoryStore.java` in `lytgos-memory/src/main/java/io/lytgos/memory/`: `## 核心记忆`/`## 归档记忆`, append/save/recall/truncate, missing-file tolerance; cite `data-model.md` `MemoryEntry.content`, `MemoryEntry.scope`, `MemoryEntry.section`, and §1.6.
- [ ] T073 [P] [US3] Implement `MemoryTools.java` in `lytgos-memory/src/main/java/io/lytgos/memory/` with `save_memory`/`recall_memory`; cite `contracts/builtin-tools.md` §7–§8 and `data-model.md` `MemoryEntry.content`, `MemoryEntry.scope`, and `MemoryEntry.section`.
- [ ] T074 [US3] Integrate core/archival memory into `PromptBuilder.java`; core never truncated, archival per `lytgos.memory.archival-max-chars`; cite `data-model.md` `MemoryEntry.content`, `MemoryEntry.scope`, `MemoryEntry.section`, and §1.6.
- [ ] T075 [US3] Register `MemoryService`/`MemoryTools` in `lytgos-boot/src/main/java/io/lytgos/boot/RuntimeConfiguration.java` and `ToolRegistry`; cite `data-model.md` `ToolDefinition.name`, `ToolDefinition.description`, `ToolDefinition.inputSchema`, and `ToolDefinition.source`.
- [ ] T076 [P] [US3] Implement `MemoryProperties.java` in `lytgos-memory/src/main/java/io/lytgos/memory/` and add `lytgos.memory.archival-max-chars` to `lytgos-boot/src/main/resources/application.yaml`.
- [ ] T077 [US3] Run `/speckit-analyze` for US3 and fix drift in `lytgos-memory/src/main/java/io/lytgos/memory/` and `lytgos-core/src/main/java/io/lytgos/core/engine/PromptBuilder.java`.

**Checkpoint**: US3 works independently — memory save/recall, core never truncated, restart recovery.

---

## Phase 6: User Story 4 - 使用内置与插件 Tool，并受安全边界保护 (Priority: P3)

**Goal**: 9 个内置 Tool、MCP stdio、`@Tool` Java Bean 全部经 `ToolRegistry`/`ToolExecutor`；路径、Shell、域名白名单生效并审计。

**Independent Test**: 在受控工作区调用 9 个内置 Tool；配置 MCP server 与 Java Tool；构造路径穿越、非白名单命令和非白名单域名，确认全部拒绝并写失败审计；Agent/Skill 不出现在 Tool 列表。

### Tests for User Story 4

- [ ] T078 [P] [US4] `WhitelistSandboxTest.java` in `lytgos-tool/src/test/java/io/lytgos/tool/sandbox/`: `../`, symlink escape, exact executable match, domain wildcard, notify URL validation.
- [ ] T079 [P] [US4] `FileToolsTest.java` in `lytgos-tool/src/test/java/io/lytgos/tool/builtin/`: read/write/list success, allowlist denial, size limits.
- [ ] T080 [P] [US4] `ShellToolsTest.java` in `lytgos-tool/src/test/java/io/lytgos/tool/builtin/`: allowed command, non-allowlisted executable, timeout, no shell interpretation.
- [ ] T081 [P] [US4] `HttpToolsTest.java` in `lytgos-tool/src/test/java/io/lytgos/tool/builtin/` with WireMock: GET/POST success, 4xx/5xx, timeout, redirect revalidation, domain denial.
- [ ] T082 [P] [US4] `NotifyToolsTest.java` in `lytgos-tool/src/test/java/io/lytgos/tool/notify/`: channel lookup, webhook payload per type, missing channel, domain denial, network failure audit.
- [ ] T083 [P] [US4] `McpClientTest.java` in `lytgos-tool/src/test/java/io/lytgos/tool/mcp/`: stdio initialize, tools/list, tools/call, server exit, timeout/reconnect.
- [ ] T084 [P] [US4] `JavaToolAdapterTest.java` in `lytgos-tool/src/test/java/io/lytgos/tool/plugin/`: `@Tool` schema generation, explicit execution via `ToolExecutor`, no auto-execution.
- [ ] T085 [P] [US4] Tool list exclusion E2E test in `lytgos-boot/src/test/java/io/lytgos/boot/Us4ToolListE2ETest.java`: Agent directories and Skills are not registered as Tools; 9 built-ins + MCP/Java sources are visible.

### Implementation for User Story 4

- [ ] T086 [P] [US4] Implement `Sandbox.java`, `WhitelistSandbox.java`, and `SandboxViolationException.java` in `lytgos-tool/src/main/java/io/lytgos/tool/sandbox/`; path/shell/domain checks, no `SecurityManager`; cite `data-model.md` §1.1 workspace paths and `contracts/builtin-tools.md`.
- [ ] T087 [US4] Implement `FileTools.java` in `lytgos-tool/src/main/java/io/lytgos/tool/builtin/`: `read_file`, `write_file`, `list_dir`; enforce sandbox and result truncation.
- [ ] T088 [US4] Implement `ShellTools.java` in `lytgos-tool/src/main/java/io/lytgos/tool/builtin/`: exact executable allowlist, `ProcessBuilder`, no shell, timeout, cwd allowlist.
- [ ] T089 [US4] Implement `HttpTools.java` in `lytgos-tool/src/main/java/io/lytgos/tool/builtin/`: `http_get`, `http_post`, domain allowlist, redirect revalidation, size/timeout limits.
- [ ] T090 [US4] Implement `NotifyTools.java` in `lytgos-tool/src/main/java/io/lytgos/tool/builtin/` and `NotifyChannelAdapter.java`/`WebhookNotifyAdapter.java` in `lytgos-tool/src/main/java/io/lytgos/tool/notify/`; channel-name lookup, sandbox, WeCom/Feishu/DingTalk/generic payloads.
- [ ] T091 [US4] Implement `McpServerConfig.java`, `McpClientService.java`, and `McpToolAdapter.java` in `lytgos-tool/src/main/java/io/lytgos/tool/mcp/`; stdio only, startup connect, tools/list, one reconnect; cite `data-model.md` `McpServer.transport`, `McpServer.command`, `McpServer.args`, `McpServer.env`, and `McpServer.timeout_seconds`.
- [ ] T092 [US4] Implement `JavaToolAdapter.java` in `lytgos-tool/src/main/java/io/lytgos/tool/plugin/`; adapt Spring AI `@Tool` to `LytgTool`, schema only from Spring AI, execution through `ToolExecutor`.
- [ ] T093 [US4] Complete `ToolRegistry.java` in `lytgos-tool/src/main/java/io/lytgos/tool/`: register built-ins/MCP/Java with source, duplicate handling, list definitions; cite `data-model.md` `ToolDefinition.name`, `ToolDefinition.description`, `ToolDefinition.inputSchema`, and `ToolDefinition.source`.
- [ ] T094 [US4] Wire `ToolRegistry`, `WhitelistSandbox`, MCP client, and notify adapter in `lytgos-boot/src/main/java/io/lytgos/boot/RuntimeConfiguration.java`; add sandbox/MCP defaults to `lytgos-boot/src/main/resources/application.yaml`.
- [ ] T095 [US4] Implement `ToolListCommand.java` in `lytgos-cli/src/main/java/io/lytgos/cli/command/`; show name/source/description, exclude Agent/Skill; cite `data-model.md` `ToolDefinition.name`, `ToolDefinition.description`, and `ToolDefinition.source`.
- [ ] T096 [US4] Run `/speckit-analyze` for US4 and fix drift in `lytgos-tool/src/main/java/io/lytgos/tool/` and `lytgos-boot/src/main/java/io/lytgos/boot/RuntimeConfiguration.java`.

**Checkpoint**: US4 works independently — 9 tools, MCP, Java Tool, sandbox and failure audit.

---

## Phase 7: User Story 5 - 通过 REST API 集成 Agent (Priority: P4)

**Goal**: 10 个核心 REST 端点 + notify-channels/scheduler v2 支撑端点；统一信封、OpenAPI、Scheduler 自动/手动执行、CLI 补全。

**Independent Test**: 启动 `serve`，用 HTTP 完成创建 Session → 发消息 → 查历史 → 归档；验证无状态 invoke、列表、健康检查、错误；验证调度器状态/执行历史/手动 run。

### Tests for User Story 5

- [ ] T097 [P] [US5] Session API tests in `lytgos-web/src/test/java/io/lytgos/web/controller/SessionApiControllerTest.java`: create/send/get/delete, archived 409, 404, 32KB limit, history 100; cite `data-model.md` `sessions.status` and `sessions.archived_at`.
- [ ] T098 [P] [US5] Agent/Profile/Memory/Tool/System API tests in `lytgos-web/src/test/java/io/lytgos/web/controller/`: invoke no session, profiles/memory/tools/health/info envelope, 404/503/504.
- [ ] T099 [P] [US5] Notify channel API tests in `lytgos-web/src/test/java/io/lytgos/web/controller/NotifyChannelApiControllerTest.java`: CRUD, unique name, URL allowlist; cite `data-model.md` `notify_channels.name`, `notify_channels.type`, and `notify_channels.url`.
- [ ] T100 [P] [US5] Scheduler tests in `lytgos-core/src/test/java/io/lytgos/core/engine/AgentSchedulerTest.java` and `lytgos-web/src/test/java/io/lytgos/web/controller/ScheduleApiControllerTest.java`: reconcile, overlap skip, enabled toggle, manual run, executions; cite `data-model.md` `scheduled_tasks.schedule_id`, `scheduled_tasks.enabled`, `scheduled_tasks.retired`, and `task_executions.*`.
- [ ] T101 [P] [US5] OpenAPI test in `lytgos-web/src/test/java/io/lytgos/web/OpenApiTest.java`: `/v3/api-docs` includes 10 core + support endpoints.
- [ ] T102 [P] [US5] CLI tests in `lytgos-cli/src/test/java/io/lytgos/cli/SessionListCommandTest.java`, `ServeCommandTest.java`, and `GatewayCommandTest.java`: active/archived filters, no HTTP in gateway, serve mode.
- [ ] T103 [P] [US5] REST E2E test in `lytgos-boot/src/test/java/io/lytgos/boot/Us5RestE2ETest.java`: create → send → get → delete → 409; stateless invoke; envelope errors.
- [ ] T104 [P] [US5] Scheduler E2E test in `lytgos-boot/src/test/java/io/lytgos/boot/Us5SchedulerE2ETest.java`: manual run writes `task_executions`; enabled=false stops auto trigger.

### Implementation for User Story 5

- [ ] T105 [P] [US5] Implement `ApiResponse.java` and `GlobalExceptionHandler.java` in `lytgos-web/src/main/java/io/lytgos/web/`; envelope and error mapping (400/404/409/500/503/504), no stack traces.
- [ ] T106 [P] [US5] Create request/response DTO records in `lytgos-web/src/main/java/io/lytgos/web/dto/`; cite `data-model.md` `Message.role`, `Message.content`, `Message.toolCalls`, `Message.toolCallId`, `Message.name`, `Message.terminationReason`, and `Message.createdAt`.
- [ ] T107 [US5] Implement `SessionApiController.java` in `lytgos-web/src/main/java/io/lytgos/web/controller/`; cite `data-model.md` `sessions.session_id`, `sessions.profile_name`, `sessions.channel`, `sessions.user_id`, `sessions.messages_json`, `sessions.status`, `sessions.created_at`, `sessions.last_active_at`, and `sessions.archived_at`.
- [ ] T108 [US5] Implement `AgentApiController.java`, `ProfileApiController.java`, `MemoryApiController.java`, `ToolApiController.java`, and `SystemApiController.java` in `lytgos-web/src/main/java/io/lytgos/web/controller/`; invoke no session, lists, health/info; `ProfileApiController` cites `data-model.md` `AgentDefinition.name`, `AgentDefinition.description`, `AgentDefinition.provider.name`, `AgentDefinition.provider.model`, `AgentDefinition.tools`, `AgentDefinition.channels`, and `AgentDefinition.schedules`.
- [ ] T109 [US5] Implement `NotifyChannelApiController.java` in `lytgos-web/src/main/java/io/lytgos/web/controller/` and `NotifyChannelService` validation in `lytgos-core`/`lytgos-tool`; cite `data-model.md` `notify_channels.id`, `notify_channels.name`, `notify_channels.type`, `notify_channels.url`, `notify_channels.description`, `notify_channels.config_json`, `notify_channels.created_at`, and `notify_channels.updated_at`.
- [ ] T110 [US5] Implement `AgentScheduler.java` in `lytgos-core/src/main/java/io/lytgos/core/engine/`: reconcile, `ThreadPoolTaskScheduler`/`CronTrigger`, per-task lock/skip, `task_executions` start/finish, manual run; cite `data-model.md` `task_executions.id`, `task_executions.schedule_id`, `task_executions.session_id`, `task_executions.started_at`, `task_executions.success`, `task_executions.error_message`, `task_executions.duration_ms`, and `scheduled_tasks.enabled`/`scheduled_tasks.retired`.
- [ ] T111 [US5] Implement `ScheduleApiController.java` in `lytgos-web/src/main/java/io/lytgos/web/controller/`; cite `data-model.md` `scheduled_tasks.schedule_id`, `scheduled_tasks.profile_name`, `scheduled_tasks.schedule_key`, `scheduled_tasks.display_name`, `scheduled_tasks.cron`, `scheduled_tasks.zone`, `scheduled_tasks.message`, `scheduled_tasks.enabled`, `scheduled_tasks.retired`, `scheduled_tasks.next_run_at`, `scheduled_tasks.last_run_at`, `scheduled_tasks.last_status`, `scheduled_tasks.run_count`, and `scheduled_tasks.updated_at`.
- [ ] T112 [US5] Wire scheduler in `lytgos-boot/src/main/java/io/lytgos/boot/SchedulingConfiguration.java`; `serve`/`gateway` enable scheduler, `chat` does not; cite `data-model.md` `scheduled_tasks.enabled` and `scheduled_tasks.retired`.
- [ ] T113 [US5] Implement `OpenApiConfig.java` in `lytgos-web/src/main/java/io/lytgos/web/config/`; springdoc `/v3/api-docs` and `/swagger-ui`.
- [ ] T114 [US5] Implement `SessionListCommand.java`, `ServeCommand.java`, and `GatewayCommand.java` in `lytgos-cli/src/main/java/io/lytgos/cli/command/`; session status filter, serve/gateway runtime modes.
- [ ] T115 [US5] Configure virtual threads, request limits, and 60s invoke timeout in `lytgos-boot/src/main/resources/application.yaml` and web config; 32KB message, 100-message history.
- [ ] T116 [US5] Implement session ID generation, archived read-only, and stateless invoke null audit in `AgentService`/`SessionResolver`/controllers; cite `data-model.md` `sessions.session_id`, `sessions.status`, `llm_calls.session_id`, and `tool_invocations.session_id`.
- [ ] T117 [US5] Implement notify channel URL allowlist validation in `NotifyChannelService`/`WhitelistSandbox`; cite `data-model.md` `notify_channels.url`, `notify_channels.name`, and `notify_channels.type`.
- [ ] T118 [US5] Implement scheduler manual run (`202`) and enabled-only update semantics in `AgentScheduler`/`ScheduleApiController`; cite `data-model.md` `scheduled_tasks.enabled`, `scheduled_tasks.retired`, and `task_executions.*`.
- [ ] T119 [US5] Add structured logging/secret masking for web/provider/tool paths in `lytgos-boot/src/main/resources/logback-spring.xml` and `lytgos-web/src/main/java/io/lytgos/web/GlobalExceptionHandler.java`.
- [ ] T120 [US5] Run `/speckit-analyze` for US5 and fix drift in `lytgos-web/src/main/java/io/lytgos/web/`, `lytgos-core/src/main/java/io/lytgos/core/engine/AgentScheduler.java`, and `lytgos-cli/src/main/java/io/lytgos/cli/command/`.

**Checkpoint**: All user stories independently functional; REST + scheduler + CLI complete.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Demos、主页/文档、性能、最终验证与一致性检查。

- [ ] T121 [P] Create weather demo `examples/agents/daily-weather/AGENT.md` with schedule, `http_get`/`notify`, sandbox domains, and manual rerun steps.
- [ ] T122 [P] Create tech news demo `examples/agents/daily-tech-news/AGENT.md` and `examples/skills/tech-digest/SKILL.md`; include skill symlink instructions and memory preference flow.
- [ ] T123 [P] Create example stdio MCP server under `examples/mcp/tech-news-server/` with README and `server.py`; keep it outside the Maven reactor.
- [ ] T124 [P] Create `examples/notify/README.md` documenting webhook channel creation and sandbox domain configuration.
- [ ] T125 Add demo E2E tests `lytgos-boot/src/test/java/io/lytgos/boot/DemoE2ETest.java` with WireMock/local MCP; assert audit rows, skill metadata, memory preference.
- [ ] T126 [P] Update `README.md` and `readme.zh.md` for 1.0 CLI/fat JAR usage, quick start, no-auth warning, and remove all `OryxTool` references.
- [ ] T127 [P] Update VitePress `website/index.md`, `website/zh/index.md`, and `website/docs/` quick start/deployment pages; verify homepage and 10-minute first-run path.
- [ ] T128 [P] Create deployment guide `docs/Deployment.md` covering fat JAR, workspace, secrets, SQLite backup, sandbox, serve/gateway exclusivity, and no-auth warning.
- [ ] T129 [P] Create performance validation script `scripts/perf/session-load.js` and `scripts/perf/README.md` for the 4-hour, 10-Agent/100-Session soak.
- [ ] T130 Perform security/logging hardening review across `lytgos-provider/src/main/java/io/lytgos/provider/`, `lytgos-tool/src/main/java/io/lytgos/tool/sandbox/`, and `lytgos-web/src/main/java/io/lytgos/web/GlobalExceptionHandler.java`; fix secret/stack leakage and unsafe defaults.
- [ ] T131 Run `mvn clean package` from repo root and record the actual result in `specs/001-lytgos-runtime-kernel/` validation notes; do not claim success before running.
- [ ] T132 Run the validation steps in `specs/001-lytgos-runtime-kernel/quickstart.md` and record actual outcomes; fix any mismatch in code/docs.
- [ ] T133 Audit FR-036 module E2E coverage: ensure each of the 9 modules has at least one E2E test under its `src/test/java/`, add missing tests, and update `specs/001-lytgos-runtime-kernel/tasks.md` checkboxes.
- [ ] T134 [P] Create optional GitHub demo `examples/agents/daily-github/AGENT.md` and `examples/agents/daily-github/scripts/`; not a 1.0 release blocker.
- [ ] T135 Run final `/speckit-analyze` across `specs/001-lytgos-runtime-kernel/spec.md`, `plan.md`, `data-model.md`, `contracts/`, `tasks.md`, and the 9 modules; fix drift.
- [ ] T136 Finalize docs `docs/ReleaseBaseline.md`/`docs/TechnicalSolution.md` only where the implementation clarified existing wording (Provider connector, unified fat JAR); verify no scope/number changes; update `website/` links if needed.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies; start immediately.
- **Foundational (Phase 2)**: Depends on Phase 1; blocks all user stories.
- **US1 (Phase 3, P1)**: Depends on Phase 2.
- **US2 (Phase 4, P2)**: Depends on US1 (provider/session/AgentService).
- **US3 (Phase 5, P3)**: Depends on US2; can run in parallel with US4.
- **US4 (Phase 6, P3)**: Depends on US2; can run in parallel with US3.
- **US5 (Phase 7, P4)**: Depends on US1–US4 complete.
- **Polish (Phase 8)**: Depends on all desired user stories complete.

### Within Each User Story

- Tests first (FR-036); they should fail before implementation.
- Models before services; services before controllers/commands; core before integration.
- Each story ends with `/speckit-analyze`.
- US3 and US4 may integrate at the `ToolRegistry`/`PromptBuilder` boundary; keep their unit tests independent.

### Parallel Opportunities

- All `[P]` Setup tasks (T002–T006, T008–T011).
- All `[P]` Foundational tasks within Phase 2 (T012–T018, T022–T024, T032).
- US1 tests T035–T039; implementation T040 and T045; T046–T048 and T051 can follow T045 in parallel.
- US2 tests T053–T057; implementation T058; T059/T060 after their ports/interfaces.
- US3 tests T067–T070; implementation T072/T073 and T076; T071 after T072/T073.
- US4 tests T078–T085; implementation T086; T087–T090 after sandbox.
- US5 tests T097–T104; implementation T105/T106; T107–T110 after API envelope/DTOs.
- Polish demo/docs tasks T121–T124, T126–T129, T134.

---

## Parallel Example: User Story 1

```bash
# Launch US1 tests together:
Task: "ProviderRegistryTest in lytgos-provider/src/test/java/io/lytgos/provider/ProviderRegistryTest.java"
Task: "LlmAuditTest in lytgos-core/src/test/java/io/lytgos/core/engine/LlmAuditTest.java"
Task: "SessionPersistenceTest in lytgos-storage/src/test/java/io/lytgos/storage/SessionPersistenceTest.java"
Task: "CLI tests in lytgos-cli/src/test/java/io/lytgos/cli/"
Task: "Us1ChatE2ETest in lytgos-boot/src/test/java/io/lytgos/boot/Us1ChatE2ETest.java"

# Then parallel implementation:
Task: "ProviderRegistry/OpenAiCompatibleProviderFactory in lytgos-provider/src/main/java/io/lytgos/provider/"
Task: "ConfigLoader/InitCommand/Profile commands in lytgos-cli/src/main/java/io/lytgos/cli/"
```

## Parallel Example: User Story 2

```bash
# Launch US2 tests together:
Task: "ReActLoopTest in lytgos-core/src/test/java/io/lytgos/core/engine/ReActLoopTest.java"
Task: "ToolExecutorTest in lytgos-core/src/test/java/io/lytgos/core/engine/ToolExecutorTest.java"
Task: "PromptBuilderTest in lytgos-core/src/test/java/io/lytgos/core/engine/PromptBuilderTest.java"
Task: "Us2ReActE2ETest in lytgos-boot/src/test/java/io/lytgos/boot/Us2ReActE2ETest.java"
Task: "DeterministicTool in lytgos-core/src/test/java/io/lytgos/core/testsupport/DeterministicTool.java"
```

## Parallel Example: User Story 3 and User Story 4

```bash
# US3 and US4 can run in parallel after US2:
Task: "MarkdownMemoryStore + MemoryTools in lytgos-memory/src/main/java/io/lytgos/memory/"
Task: "WhitelistSandbox + FileTools/ShellTools/HttpTools in lytgos-tool/src/main/java/io/lytgos/tool/"
Task: "MCP client + JavaToolAdapter in lytgos-tool/src/main/java/io/lytgos/tool/"
```

## Parallel Example: User Story 5

```bash
# Launch US5 controller/contract tests together:
Task: "SessionApiControllerTest in lytgos-web/src/test/java/io/lytgos/web/controller/SessionApiControllerTest.java"
Task: "NotifyChannelApiControllerTest in lytgos-web/src/test/java/io/lytgos/web/controller/NotifyChannelApiControllerTest.java"
Task: "ScheduleApiControllerTest in lytgos-web/src/test/java/io/lytgos/web/controller/ScheduleApiControllerTest.java"
Task: "OpenApiTest in lytgos-web/src/test/java/io/lytgos/web/OpenApiTest.java"
Task: "Us5RestE2ETest in lytgos-boot/src/test/java/io/lytgos/boot/Us5RestE2ETest.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational.
3. Complete Phase 3: US1.
4. **STOP and VALIDATE**: multi-turn chat, provider switch, `llm_calls`, CLI init/profile/chat.
5. Demo US1; then proceed to US2.

### Incremental Delivery

1. Setup + Foundational → foundation ready.
2. US1 → validate → demo (MVP).
3. US2 → validate → demo (ReAct + tools).
4. US3 ∥ US4 → validate each independently → demo (memory, tools/MCP/sandbox).
5. US5 → validate → demo (REST + scheduler + full CLI).
6. Polish → demos, homepage, performance, final analyze.

### Parallel Team Strategy

- Team completes Setup + Foundational together.
- US1 first (provider/session/CLI).
- US2 next (ReAct/tool execution).
- Then split: Developer A = US3 Memory; Developer B = US4 Tools/MCP/Sandbox.
- Integrate for US5; then Polish in parallel.

---

## Data Model Field Coverage

Each constrained field from `data-model.md` is cited in at least one implementing task. Shared tasks may cite multiple fields.

| Entity | Constrained fields | Implementing tasks |
|---|---|---|
| `AgentDefinition` | `name`, `description`, `identity.agent_name`, `identity.prompt`, `provider.name`, `provider.model`, `provider.temperature`, `tools`, `mcp_servers`, `channels`, `bootstrap`, `schedules`, `settings.max_iterations`, `settings.max_history_turns` | T013, T019, T040, T047, T065, T108 |
| `ScheduleDefinition` | `key`, `cron`, `zone`, `message`, `display_name`, `enabled` | T013, T019, T047 |
| `Skill` | `name`, `description` | T019 |
| `McpServer` | `transport`, `command`, `args`, `env`, `timeout_seconds` | T091 |
| `sessions` | `session_id`, `profile_name`, `channel`, `user_id`, `messages_json`, `status`, `created_at`, `last_active_at`, `archived_at` | T023, T042, T044, T061, T107, T116 |
| `tool_invocations` | `id`, `session_id`, `profile_name`, `tool_name`, `tool_source`, `input_json`, `result_json`, `success`, `error_message`, `retry_count`, `attempt_errors_json`, `duration_ms`, `created_at` | T023, T060, T116 |
| `llm_calls` | `id`, `session_id`, `profile_name`, `provider`, `model`, `prompt_tokens`, `completion_tokens`, `total_tokens`, `duration_ms`, `success`, `error_message`, `created_at` | T023, T036, T043, T063, T116 |
| `scheduled_tasks` | `schedule_id`, `profile_name`, `schedule_key`, `display_name`, `cron`, `zone`, `message`, `enabled`, `retired`, `next_run_at`, `last_run_at`, `last_status`, `run_count`, `updated_at` | T024, T110, T111, T112, T118 |
| `task_executions` | `id`, `schedule_id`, `session_id`, `started_at`, `success`, `error_message`, `duration_ms` | T024, T110, T118 |
| `notify_channels` | `id`, `name`, `type`, `url`, `description`, `config_json`, `created_at`, `updated_at` | T024, T099, T109, T117 |
| `Message` | `role`, `content`, `toolCalls`, `toolCallId`, `name`, `terminationReason`, `createdAt` | T014, T026, T053, T061, T106 |
| `MEMORY.md` | `MemoryEntry.content`, `MemoryEntry.scope`, `MemoryEntry.section`, archival truncation | T072, T073, T074 |
| `ToolDefinition` / `ToolResult` | `ToolDefinition.name`, `ToolDefinition.description`, `ToolDefinition.inputSchema`, `ToolDefinition.source`; `ToolResult.success`, `ToolResult.content`, `ToolResult.error`, `ToolResult.retryable` | T012, T062, T075, T093, T095 |

---

## Notes

- `[P]` = different files, no incomplete dependency; do not run two tasks editing the same file in parallel.
- Tests are required by FR-036; run them and report actual results, never claim unexecuted tests passed.
- Each story must pass its independent test before moving to the next priority.
- After each story, run `/speckit-analyze`; fix drift before continuing.
- Keep the 9-module limit, 9 built-in Tools, 12 CLI commands, 10 core REST endpoints, 6 SQLite tables, and 2+1 Demos; no extension capabilities.
- Git commits (if made) use Chinese subject/body with the conventional prefix and `Co-Authored-By` trailer; do not commit secrets or `.lytgos/` runtime data.
