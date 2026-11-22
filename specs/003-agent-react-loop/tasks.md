# Tasks: ReAct Agent Loop（第 17 节）

**Input**: Design documents from `/specs/003-agent-react-loop/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: 本课程要求验收 harness，测试任务必须先行或与实现同步落地。单元测试默认运行；真实模型冒烟 `ProviderSmokeIT` 仍打 `@Tag("integration")`，CI 默认跳过。

**Organization**: 任务按用户故事分组；每个用户故事有独立验收标准。第 16、17 节合并为一个以 ReAct 为主的 PR；第 16 节 `Profile` 已统一为 `AgentProfile`，本清单不再引入 `Profile` 类型。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行执行（不同文件，无未完成依赖）
- **[Story]**: 所属用户故事（US1/US2/US3/US4）
- 任务描述包含精确文件路径
- 数据模型约束以 `data-model.md` 的 `Entity.field` 引用为准

## Path Conventions

- 多模块 Maven 仓库根目录：`D:\lyagent\lytgos2`
- 源码路径以模块名开头，例如 `lytgos-core/src/main/java/...`
- 文档路径以 `specs/003-agent-react-loop/` 开头

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 包基线、依赖与领域目录就绪

- [X] T001 Verify the AgentProfile rename/package-by-domain baseline in `lytgos-core/src/main/java/io/lytgos/core/agent/`; create `package-info.java` for new `io.lytgos.core.llm`, `io.lytgos.storage.tool` domains; confirm no new third-party dependency is needed for the ReAct slice

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: core 领域类型、审计端口、Session、Prompt/Context 引擎、Provider 适配与 tool_invocations 持久化

**⚠️ CRITICAL**: 本阶段完成前不能进入用户故事实现。

- [X] T002 [P] Create `lytgos-core/src/main/java/io/lytgos/core/llm/AgentMessage.java` with `AgentRole` and data-model.md §2 `AgentMessage.role`, `AgentMessage.content`, `AgentMessage.toolName`, `AgentMessage.toolCallId`, `AgentMessage.toolCalls`; list fields defensively copied
- [X] T003 [P] Create `lytgos-core/src/main/java/io/lytgos/core/llm/AgentPrompt.java` with data-model.md §3 `AgentPrompt.systemPrompt`, `AgentPrompt.messages`, `AgentPrompt.tools`; null/empty defaults
- [X] T004 [P] Create `lytgos-core/src/main/java/io/lytgos/core/llm/AgentResponse.java` and `AgentToolCall.java` with data-model.md §4 `AgentResponse.text`, `AgentResponse.toolCalls` and §5 `AgentToolCall.id`, `AgentToolCall.name`, `AgentToolCall.arguments`; add `hasToolCalls()`
- [X] T005 [P] Create `lytgos-core/src/main/java/io/lytgos/core/session/Session.java` with data-model.md §1 `Session.sessionId`, `Session.profileName`, `Session.channel`, `Session.userId`, `Session.messages`; implement `appendUser`, `appendAssistant`, `appendToolResult`, `messages()`, `recentMessages(maxTurns)` and complete-turn history rule
- [X] T006 [P] Create `lytgos-core/src/main/java/io/lytgos/core/audit/ToolInvocationRecord.java` and `ToolInvocationAuditor.java` with data-model.md §8 `ToolInvocationRecord.sessionId`, `toolName`, `inputJson`, `success`, `errorMessage`, `durationMs`
- [X] T007 Create `lytgos-core/src/main/java/io/lytgos/core/agent/LlmGateway.java` per contracts/react-engine.md §2 (`AgentResponse chat(String sessionId, AgentProfile agentProfile, AgentPrompt prompt)`)
- [X] T008 Create `lytgos-core/src/main/java/io/lytgos/core/agent/PromptBuilder.java` per contracts/react-engine.md §6; consume data-model.md §6 `AgentProfile.identity.prompt`, `AgentProfile.tools`, `AgentProfile.settings.maxHistoryTurns`; append current date/time at end
- [X] T009 Create `lytgos-core/src/main/java/io/lytgos/core/agent/ContextLoader.java` per contracts/react-engine.md §5; read data-model.md §6 `AgentProfile.bootstrap` from workspace root and `AgentProfile.skills` from `.lytgos/skills/<name>/SKILL.md` with no cache
- [X] T010 Implement `LlmGateway` in `lytgos-provider/src/main/java/io/lytgos/provider/ProviderService.java`; convert `AgentPrompt`/`AgentResponse` to/from Spring AI `Prompt`/`ChatResponse`; keep the existing `chat(String, AgentProfile, Prompt)` entry point compatible
- [X] T011 [P] Create `lytgos-storage/src/main/java/io/lytgos/storage/tool/ToolInvocation.java` with data-model.md §7 `ToolInvocation.id`, `ToolInvocation.session_id`, `ToolInvocation.tool_name`, `ToolInvocation.input_json`, `ToolInvocation.success`, `ToolInvocation.error_message`, `ToolInvocation.duration_ms`, `ToolInvocation.created_at`
- [X] T012 [P] Create `lytgos-storage/src/main/resources/db/migration/sqlite/V2__init.sql` per contracts/tool-invocation.md §1; create `tool_invocations` and indexes; do not modify V1
- [X] T013 [P] Create `lytgos-storage/src/main/java/io/lytgos/storage/tool/ToolInvocationRepository.java` extending `JpaRepository<ToolInvocation, Long>`
- [X] T014 Create `lytgos-storage/src/main/java/io/lytgos/storage/tool/JpaToolInvocationAuditor.java` implementing `ToolInvocationAuditor`; map data-model.md §8 fields and fill `created_at`

**Checkpoint**: Foundation ready - 用户故事实现可以开始。

---

## Phase 3: User Story 1 - 多轮工具调用完成一次任务 (Priority: P1) 🎯 MVP

**Goal**: 模型返回工具调用时执行工具并回填，第二轮拿到结果后给出最终答复；多个工具按顺序执行。

**Independent Test**: 使用 mock `PromptBuilder`/`LlmGateway`/`ToolExecutor` 的 ReActLoopTest，断言工具调用后结果回填且最终文本返回，不需要真实网络。

### Tests for User Story 1

- [X] T015 [P] [US1] Write `lytgos-core/src/test/java/io/lytgos/core/tool/ToolExecutorTest.java` success path and missing-tool path; assert returned `ToolResult` content/error and one auditor record per attempt（测试先行/同步）
- [X] T016 [P] [US1] Write `lytgos-core/src/test/java/io/lytgos/core/agent/ReActLoopTest.java` for no-tool one-round finish、single tool call executed and backfilled、multiple tool calls executed sequentially、assistant/tool messages appended to `Session`（测试先行/同步）

### Implementation for User Story 1

- [X] T017 [US1] Implement `lytgos-core/src/main/java/io/lytgos/core/tool/ToolExecutor.java` per contracts/react-engine.md §7; inject `Map<String, LytgTool>` and `ToolInvocationAuditor`; add the Sandbox wiring placeholder comment for lesson 24（依赖 T006、T015）
- [X] T018 [US1] Implement `lytgos-core/src/main/java/io/lytgos/core/agent/ReActLoop.java` per contracts/react-engine.md §8; append user message, call `PromptBuilder`, call `LlmGateway`, append response, execute tool calls in order, return final text（依赖 T002–T010、T016、T017）

**Checkpoint**: US1 可独立验证；多轮工具调用闭环测试通过。

---

## Phase 4: User Story 2 - 循环边界与上下文控制 (Priority: P2)

**Goal**: 无工具调用立即收尾；达到最大轮数强制结束；历史按最近 N 个完整轮次截断；system prompt 末尾有当前时间。

**Independent Test**: PromptBuilderTest 与扩展后的 ReActLoopTest，使用固定 Clock 和 mock `LlmGateway`，断言截断、日期和恰好 10 次模型调用。

### Tests for User Story 2

- [X] T019 [P] [US2] Write `lytgos-core/src/test/java/io/lytgos/core/agent/PromptBuilderTest.java` for four-part system prompt order、data-model.md §6 `AgentProfile.settings.maxHistoryTurns` history truncation、system prompt ends with current date/time、tool filtering by `AgentProfile.tools`（测试先行/同步）
- [X] T020 [P] [US2] Extend `lytgos-core/src/test/java/io/lytgos/core/agent/ReActLoopTest.java` for exactly 10 provider calls and forced-stop text when the model always requests tools; assert each response/tool result is accumulated（测试先行/同步）

### Implementation for User Story 2

- [X] T021 [US2] Finalize `lytgos-core/src/main/java/io/lytgos/core/agent/PromptBuilder.java` for prompt order, data-model.md §6 `AgentProfile.settings.maxHistoryTurns` default 20, date/time suffix and `AgentProfile.tools` filtering（依赖 T008、T009、T019）
- [X] T022 [US2] Finalize `lytgos-core/src/main/java/io/lytgos/core/agent/ReActLoop.java` for data-model.md §6 `AgentProfile.settings.maxIterations` default 10 and the exact forced-stop message（依赖 T018、T020）

**Checkpoint**: US2 可独立验证；边界与截断回归测试通过。

---

## Phase 5: User Story 3 - 工具调用审计与请求级上下文隔离 (Priority: P2)

**Goal**: 工具成功/失败都写 `tool_invocations`；工具不存在/异常不吞；AgentProfileContext 在处理期间可读、异常时必清；保存钩子被调用。

**Independent Test**: ToolExecutorTest 失败/异常断言、AgentServiceTest 上下文清理断言、ToolInvocationRepositoryTest SQLite/Flyway 断言。

### Tests for User Story 3

- [X] T023 [P] [US3] Extend `lytgos-core/src/test/java/io/lytgos/core/tool/ToolExecutorTest.java` for `ToolResult.success=false`、tool exception、audit `success/errorMessage` fields and no exception leakage（测试先行/同步）
- [X] T024 [P] [US3] Write `lytgos-core/src/test/java/io/lytgos/core/agent/AgentServiceTest.java` for AgentProfileContext visible during processing、cleared on exception、save hook invoked after success（测试先行/同步）
- [X] T025 [P] [US3] Write `lytgos-storage/src/test/java/io/lytgos/storage/tool/ToolInvocationRepositoryTest.java` with temporary SQLite + Flyway; assert data-model.md §7 `ToolInvocation.success`, `ToolInvocation.error_message` columns exist and records round-trip（测试先行/同步）

### Implementation for User Story 3

- [X] T026 [US3] Create `lytgos-core/src/main/java/io/lytgos/core/agent/AgentProfileContext.java` with `set/current/clear` per contracts/react-engine.md §9
- [X] T027 [US3] Finalize `lytgos-core/src/main/java/io/lytgos/core/tool/ToolExecutor.java` audit failure semantics per contracts/tool-invocation.md §4; one `ToolInvocationRecord` per attempt（依赖 T017、T023）
- [X] T028 [US3] Implement `lytgos-core/src/main/java/io/lytgos/core/agent/AgentService.java` per contracts/react-engine.md §10; lookup by data-model.md §1 `Session.profileName`, set/clear context, call save hook, propagate save failure（依赖 T005、T018、T024、T026）
- [X] T029 [US3] Verify `JpaToolInvocationAuditor` integration in `lytgos-storage/src/test/java/io/lytgos/storage/tool/ToolInvocationRepositoryTest.java`; assert `created_at` non-empty and success/failure rows（依赖 T014、T025）

**Checkpoint**: US3 可独立验证；失败审计和上下文清理被回归测试钉死。

---

## Phase 6: User Story 4 - 上下文加载无缓存与错误可见 (Priority: P3)

**Goal**: Bootstrap/Skill 每次重新读取；Skill 缺失报错；Bootstrap 缺失 WARN；修改文件下一轮立即生效。

**Independent Test**: ContextLoaderTest 使用临时工作区，修改文件后再次加载，断言新内容；删除 Skill 断言异常；缺少 Bootstrap 断言警告且其余内容可用。

### Tests for User Story 4

- [X] T030 [P] [US4] Write `lytgos-core/src/test/java/io/lytgos/core/agent/ContextLoaderTest.java` for no-cache re-read、missing Skill error、missing Bootstrap WARN、Skill frontmatter name/description and absolute path injection（测试先行/同步）

### Implementation for User Story 4

- [X] T031 [US4] Finalize `lytgos-core/src/main/java/io/lytgos/core/agent/ContextLoader.java` per contracts/react-engine.md §5; use data-model.md §6 `AgentProfile.bootstrap` and `AgentProfile.skills`; reject path traversal; expose no cache（依赖 T009、T030）
- [X] T032 [US4] Extend `lytgos-core/src/test/java/io/lytgos/core/agent/PromptBuilderTest.java` to cover the ContextLoader integration and Skill metadata ordering（依赖 T019、T031）

**Checkpoint**: US4 可独立验证；无缓存与错误可见性回归测试通过。

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 启动装配、全量门禁、注释规范与 reviewer 导读

- [X] T033 [P] Create `lytgos-boot/src/main/java/io/lytgos/boot/agent/AgentRuntimeConfiguration.java` wiring `ContextLoader`、`PromptBuilder`、`ToolExecutor`、`ReActLoop`、`AgentService`; inject `LlmGateway`/`AgentProfileRegistry`/`ToolInvocationAuditor`; use an empty tool map and a no-op save hook until the Tool/Session lessons
- [X] T034 [P] Audit all new/modified files for Chinese comments on every field, method parameter, and local variable; ensure class Javadoc has `@author LytgOS2 Team`; confirm new packages have `package-info.java`
- [X] T035 [P] Update `specs/003-agent-react-loop/quickstart.md` with actual test commands, dependency versions, and the lesson-20 `http_get` deferral
- [X] T036 Run `.\mvnw.cmd -B -ntp clean verify` from repository root `D:\lyagent\lytgos2`; fix all failures; expect BUILD SUCCESS with `integration` group excluded
- [X] T037 Run `.\mvnw.cmd -B -ntp -Pp3c verify` from repository root `D:\lyagent\lytgos2`; fix all P3C violations; expect BUILD SUCCESS
- [X] T038 [P] Manual checks in `lytgos-*/src/main/java/` and `lytgos-*/src/test/java/`: grep no plaintext keys、no `Reactor`/`CompletableFuture`/self-built thread pools、no Spring AI Agent imports、no `io.lytgos.core.Profile` references; verify package-by-domain layout
- [X] T039 [P] Run `git status --short` / `git diff --stat` at repository root `D:\lyagent\lytgos2`; confirm no `.lytgos/`、secrets or build artifacts; prepare reviewer change summary and list the remaining manual Demo 1 item（needs lesson-20 `http_get`）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup; T002–T014 are the blocking core/provider/storage foundation.
- **US1 (Phase 3)**: Depends on T002–T010; T015/T016 tests first or同步 T017/T018.
- **US2 (Phase 4)**: Depends on US1 (T018) and T008/T009; T019/T020 tests before/sync T021/T022.
- **US3 (Phase 5)**: Depends on US1 (T017/T018) and T005/T006/T011–T014; T023–T025 tests before/sync T026–T029.
- **US4 (Phase 6)**: Depends on T009; T030 test before/sync T031/T032.
- **Polish (Phase 7)**: Depends on all desired stories; T036 after T033–T035 and code complete; T037 after T036.

### User Story Dependencies

- **US1 (P1)**: No other story dependency; MVP.
- **US2 (P2)**: Depends on US1 prompt/loop composition; independently testable.
- **US3 (P2)**: Depends on US1 ToolExecutor/ReActLoop and foundational storage; independently testable.
- **US4 (P3)**: Depends on foundational ContextLoader; independently testable.

### Within Each User Story

- Tests first or in the same change as implementation; key regression tests must be written before claiming completion.
- Models/ports before services; services before boot wiring; boot wiring before final smoke.
- Each task's DoD includes running the relevant module tests.

### Parallel Opportunities

- Foundational domain types T002–T006 can run in parallel; T011–T013 can run in parallel with core types.
- US1/US2/US3 test files are separate and can be drafted in parallel.
- T033/T034/T035/T038/T039 can run in parallel after code is stable; T036/T037 are sequential gates.

---

## Parallel Example: Foundational

```bash
./mvnw.cmd -B -ntp -pl lytgos-core -Dtest=AgentMessageTest test
./mvnw.cmd -B -ntp -pl lytgos-storage -Dtest=ToolInvocationRepositoryTest test
```

## Parallel Example: US1

```bash
# 先写测试，再实现：
./mvnw.cmd -B -ntp -pl lytgos-core -Dtest=ToolExecutorTest test
./mvnw.cmd -B -ntp -pl lytgos-core -Dtest=ReActLoopTest test
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: US1（ToolExecutor + ReActLoop + 多轮工具闭环）
4. **STOP and VALIDATE**: ReActLoopTest 通过；工具替身跑通“调用 → 回填 → 最终答复”
5. 继续 US2 → US3 → US4

### Incremental Delivery

1. Setup + Foundational → core 类型、Provider 适配、审计与 V2 表就绪
2. US1 → 多轮工具调用闭环（MVP）
3. US2 → 停止条件、历史截断、日期注入
4. US3 → 失败审计、AgentProfileContext、AgentService 保存钩子
5. US4 → ContextLoader 无缓存与错误可见
6. Polish → 启动装配、全量门禁、人工检查与 reviewer 导读

### Notes

- 本节所有新增/修改代码必须使用中文注释；字段、方法参数、局部变量都要有注释。
- 保持 `AgentProfile` 命名；不要重新引入 `Profile` 类型。
- 真实 Demo 一的 `http_get` 属于第 20 节；本节用工具替身完成 harness，报告中列为剩余人工项。
- 不自动 commit/push；第 16、17 节合并为以 ReAct 为主的提交，由用户审核后决定同步时机。
