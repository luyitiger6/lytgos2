# Tasks: Agent Provider（第 16 节）

**Input**: Design documents from `/specs/002-agent-provider/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: 本课程要求验收 harness，测试任务必须先行或与实现同步落地。单元测试默认运行；`ProviderSmokeIT` 使用 `@Tag("integration")`，CI 默认跳过。

**Organization**: 任务按用户故事分组；每个用户故事有独立验收标准。

> 实现完成后已按领域重构包名；任务描述中的原始路径仅记录当时的执行计划，当前源码路径以 `plan.md` 的 Project Structure 为准。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行执行（不同文件，无未完成依赖）
- **[Story]**: 所属用户故事（US1/US2/US3/US4）
- 任务描述包含精确文件路径
- 数据模型约束以 `data-model.md` 的 `Entity.field` 引用为准

## Path Conventions

- 多模块 Maven 仓库根目录：`D:\lyagent\lytgos2`
- 源码路径以模块名开头，例如 `lytgos-provider/src/main/java/...`
- 文档路径以 `specs/002-agent-provider/` 开头

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 依赖、构建配置、运行时配置与启动基础设施

- [X] T001 Add SnakeYAML and SLF4J dependencies to `lytgos-core/pom.xml`（版本由 Spring Boot 管理）
- [X] T002 [P] Add BOM-managed `org.springframework.ai:spring-ai-openai` to `lytgos-provider/pom.xml`
- [X] T003 [P] Add `spring-boot-starter-data-jpa`、`org.xerial:sqlite-jdbc`、`org.flywaydb:flyway-core`（SQLite 支持内置）、`org.hibernate.orm:hibernate-community-dialects` to `lytgos-storage/pom.xml`；不引入不存在的 `flyway-database-sqlite`
- [X] T004 [P] Configure Surefire in `pom.xml`：默认 `excludedGroups=integration`，增加 `integration` profile（`groups=integration` 且清空 excludedGroups）
- [X] T005 [P] Update `lytgos-boot/src/main/resources/application.yaml`：`lytgos.workspace`、`lytgos.providers`（deepseek/kimi，base-url + `${ENV}` api-key）、SQLite datasource、`ddl-auto=none`、SQLite dialect、Flyway location
- [X] T006 [P] Update `lytgos-boot/src/test/resources/application-test.yaml`：测试 SQLite 路径、`ddl-auto=none`、Flyway、空 providers、测试 workspace
- [X] T007 Create `lytgos-boot/src/main/java/io/lytgos/boot/config/WorkspaceInitialization.java`：在数据源初始化前创建 `.lytgos/` 目录
- [X] T008 Create `lytgos-boot/src/main/java/io/lytgos/boot/config/PersistenceConfiguration.java`：`@EntityScan("io.lytgos")` 与 `@EnableJpaRepositories("io.lytgos")`
- [X] T009 [P] Verify dependency resolution with `./mvnw -B -ntp -pl lytgos-provider,lytgos-storage dependency:tree`：确认 `spring-ai-openai:1.1.2`、Flyway 11.7.2、SQLite JDBC、Hibernate dialect；版本不一致时记录到 `specs/002-agent-provider/quickstart.md`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 所有用户故事共享的模型、端口、持久化与 provider 工厂

**⚠️ CRITICAL**: 本阶段完成前不能进入用户故事实现。

- [X] T010 Expand `lytgos-core/src/main/java/io/lytgos/core/Profile.java` with full fields and nested `Identity`/`ProviderRef`/`ScheduleSpec`/`Settings` records; list fields defensively copied; per `data-model.md` §1 (`Profile.name`, `Profile.provider`, `ProviderRef.name`, `ProviderRef.model`, `ProviderRef.temperature`, `Profile.tools`, `Profile.skills`, `Profile.mcp_servers`, `Profile.channels`, `Profile.notify_channels`, `Profile.schedules`, `Profile.bootstrap`, `Profile.settings`, `Settings.max_iterations`, `Settings.max_history_turns`)
- [X] T011 [P] Create `lytgos-core/src/main/java/io/lytgos/core/LlmCallAuditor.java` with nested `LlmCallRecord`; fields per `data-model.md` §4 (`LlmCallRecord.sessionId`, `profileName`, `provider`, `model`, `promptTokens`, `completionTokens`, `totalTokens`, `durationMs`, `success`, `errorMessage`)
- [X] T012 [P] Create JPA entity `lytgos-storage/src/main/java/io/lytgos/storage/LlmCall.java`; columns per `data-model.md` §3 (`LlmCall.id`, `LlmCall.session_id`, `LlmCall.profile_name`, `LlmCall.provider`, `LlmCall.model`, `LlmCall.prompt_tokens`, `LlmCall.completion_tokens`, `LlmCall.total_tokens`, `LlmCall.duration_ms`, `LlmCall.success`, `LlmCall.error_message`, `LlmCall.created_at`)
- [X] T013 [P] Create `lytgos-storage/src/main/java/io/lytgos/storage/LlmCallRepository.java` extending `JpaRepository<LlmCall, Long>`
- [X] T014 [P] Create `lytgos-storage/src/main/resources/db/migration/sqlite/V1__init.sql`：按 `data-model.md` §3 创建 `llm_calls` 与三个索引；只插入表，不建其他表
- [X] T015 Implement `lytgos-storage/src/main/java/io/lytgos/storage/JpaLlmCallAuditor.java`：实现 core 端口，映射 `data-model.md` §4 字段，补齐 `LlmCall.created_at` 后保存
- [X] T016 [P] Create `lytgos-core/src/main/java/io/lytgos/core/ProfileRegistry.java`：按 `Profile.name` 索引，重复名称保留首个并记录错误；启动期注册
- [X] T017 Create `lytgos-provider/src/main/java/io/lytgos/provider/ProviderProperties.java`：绑定 `lytgos.providers`；字段按 `data-model.md` §2 (`ProviderConnection.name`, `ProviderConnection.base_url`, `ProviderConnection.api_key`)
- [X] T018 Create `lytgos-provider/src/main/java/io/lytgos/provider/ProviderModelFactory.java`：按 `ProviderConnection` 构造 per-provider `OpenAiChatModel`；空 api-key 跳过；`NeverRetryPolicy`；`ToolCallingManager`；`ObservationRegistry.NOOP`

**Checkpoint**: Foundation ready - 用户故事实现可以开始。

---

## Phase 3: User Story 1 - 按 Profile 选择模型并完成一次调用 (Priority: P1) 🎯 MVP

**Goal**: 按 Profile 的 provider/model 完成一次模型调用，未知名清晰报错，provider 间不串台。

**Independent Test**: Mock 两个 `ChatModel`，断言只调用目标 provider；这不需要 ProfileLoader 或真实网络。

### Tests for User Story 1

- [X] T019 [US1] Write routing tests in `lytgos-provider/src/test/java/io/lytgos/provider/ProviderServiceTest.java`：两个 provider 按名路由、目标调用一次、另一个从未调用；未知名抛清晰异常；使用英文方法名 + 中文 `@DisplayName`（测试先行/同步）
- [X] T020 [US1] Implement `lytgos-provider/src/main/java/io/lytgos/provider/ProviderService.java`：构造函数、显式 map 查找、按 `data-model.md` §1 `ProviderRef.name/model/temperature` 构造 `OpenAiChatOptions`、调用 `ChatModel.call(Prompt)`；成功路径预留审计调用
- [X] T021 [US1] Create `lytgos-boot/src/main/java/io/lytgos/boot/config/ProviderBootstrapConfiguration.java`：启用 `ProviderProperties`，用 `ProviderModelFactory` 构造 `Map<String, ChatModel>`，装配 `ProviderService` bean
- [X] T022 [US1] Write `lytgos-provider/src/test/java/io/lytgos/provider/ProviderSmokeIT.java`：`@Tag("integration")`、真实 `DEEPSEEK_API_KEY`、真实 DeepSeek 模型、recording auditor；断言非空响应与 `success=true`；无 key 时 `Assumptions` 跳过
- [X] T023 [US1] Add `ProviderModelFactory` unit test in `lytgos-provider/src/test/java/io/lytgos/provider/ProviderModelFactoryTest.java`：空 api-key 不构造模型，非空 name/base-url 构造；不访问网络

**Checkpoint**: US1 可独立验证；`ProviderServiceTest` 路由测试通过。

---

## Phase 4: User Story 2 - 加载 Profile 并建立内存索引 (Priority: P2)

**Goal**: 从 `.lytgos/profiles/*.yaml` 加载完整 Profile，校验 provider，坏文件不阻断其他文件，建立内存索引。

**Independent Test**: 使用临时目录中的合法、未知 provider、损坏 YAML 三个文件，断言合法项可查询、未知 provider 报错、损坏文件不阻断。

### Tests for User Story 2

- [X] T024 [US2] Write `lytgos-core/src/test/java/io/lytgos/core/ProfileLoaderTest.java`：全字段 YAML、未知 provider 清晰错误、坏文件不阻断其余加载、`${ENV}` 占位符解析、重复名称处理；断言 `data-model.md` §1 的 `Profile.name`、`ProviderRef.name/model/temperature`、`Settings.max_iterations/max_history_turns` 与列表字段
- [X] T025 [US2] Implement `lytgos-core/src/main/java/io/lytgos/core/ProfileLoader.java`：SnakeYAML → Map → Profile 手工映射；注入环境变量解析器；逐文件错误日志；校验 `ProviderRef.name` 在全局 provider 集合中；目录缺失返回空列表
- [X] T026 [US2] Create `lytgos-boot/src/main/java/io/lytgos/boot/config/ProfileBootstrapConfiguration.java`：装配 `ProfileLoader` 与 `ProfileRegistry`，用 `ApplicationRunner` 在启动时加载并注册；传入 workspace 的 `profiles/` 路径与全局 provider 名称
- [X] T027 [US2] Add `ProfileRegistry` duplicate-name test in `lytgos-core/src/test/java/io/lytgos/core/ProfileRegistryTest.java`：保留首个、记录错误、按名查找

**Checkpoint**: US2 可独立验证；`ProfileLoaderTest` 通过。

---

## Phase 5: User Story 3 - 成功和失败调用都留下审计记录 (Priority: P2)

**Goal**: 每次模型调用尝试写一条 `llm_calls`；失败记录 `success=false` + 错误原因并继续上抛。

**Independent Test**: Mock 成功与异常两种 `ChatModel`，断言 auditor 收到成功/失败记录；SQLite 仓库测试读取 `success` 与 `error_message`。

### Tests for User Story 3

- [X] T028 [US3] Extend `lytgos-provider/src/test/java/io/lytgos/provider/ProviderServiceTest.java` with audit tests：成功记录填写 `data-model.md` §4 的 `LlmCallRecord` 字段；失败记录 `success=false` 且 `errorMessage` 含原因；异常继续上抛；审计写入失败不被静默吞掉（测试先行/同步）
- [X] T029 [US3] Implement audit behavior in `lytgos-provider/src/main/java/io/lytgos/provider/ProviderService.java`：成功/失败各写一条；从 `ChatResponse.getMetadata().getUsage()` 取 token（可空）；`durationMs`；失败先审计再上抛；审计异常按 `contracts/provider.md` §3.3 处理
- [X] T030 [US3] Write `lytgos-storage/src/test/java/io/lytgos/storage/LlmCallRepositoryTest.java`：使用临时 SQLite + Flyway 迁移；保存/读取 `LlmCall`；断言 `data-model.md` §3 的 `LlmCall.success`、`LlmCall.error_message`、`LlmCall.prompt_tokens`、`LlmCall.completion_tokens`、`LlmCall.total_tokens` 列真实存在且可空
- [X] T031 [US3] Add failure-audit assertion to `lytgos-provider/src/test/java/io/lytgos/provider/ProviderServiceTest.java`：审计先落账再抛异常，避免只测“抛了异常”
- [X] T032 [US3] Verify `JpaLlmCallAuditor` integration with `LlmCallRepository` in `lytgos-storage/src/test/java/io/lytgos/storage/JpaLlmCallAuditorTest.java`：core 记录 → 实体 → 查询，`created_at` 非空

**Checkpoint**: US3 可独立验证；失败审计路径已被回归测试钉死。

---

## Phase 6: User Story 4 - 工具 schema 可见但不在本能力内执行 (Priority: P3)

**Goal**: 把 `LytgTool` 的 name/description/inputSchema 翻译成 Spring AI 工具定义；请求关闭自动执行；本能力不执行工具。

**Independent Test**: 用带工具定义的 Prompt 调用 mock model，捕获 options；断言工具回调非空、字段对齐、`internalToolExecutionEnabled=false`；适配器的 `call` 抛 `UnsupportedOperationException`。

### Tests for User Story 4

- [X] T033 [US4] Write `lytgos-provider/src/test/java/io/lytgos/provider/ToolSchemaAdapterTest.java`：`data-model.md` §5 的 `ToolDescriptor.name`、`description`、`inputSchema` 逐字段对齐；产物 `call` 被拒绝；空列表返回空；测试先行/同步
- [X] T034 [US4] Implement `lytgos-provider/src/main/java/io/lytgos/provider/ToolSchemaAdapter.java`：`LytgTool` → `ToolCallback`（只含 `DefaultToolDefinition`）；`call` 抛 `UnsupportedOperationException`；不引用任何 Tool 执行器
- [X] T035 [US4] Extend `lytgos-provider/src/test/java/io/lytgos/provider/ProviderServiceTest.java` with tool options test：捕获传给 `ChatModel.call` 的 `Prompt`，断言 options 是 `ToolCallingChatOptions`、`getToolCallbacks()` 非空、`getInternalToolExecutionEnabled()` 为 `false`
- [X] T036 [US4] Implement tool options handling in `lytgos-provider/src/main/java/io/lytgos/provider/ProviderService.java`：从 `Prompt.getOptions()` 提取回调，构造 `OpenAiChatOptions` 时保留 `data-model.md` §5 的 `ToolDescriptor` 翻译结果并强制 `internalToolExecutionEnabled(false)`；不执行工具

**Checkpoint**: US4 可独立验证；工具 schema 与自动执行关闭均有回归测试。

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 全量门禁、注释规范、人工验收与 reviewer 导读

- [X] T037 [P] Audit all new/modified files for Chinese comments on every field, method parameter, and local variable; ensure class Javadoc has `@author LytgOS2 Team`; files under `lytgos-core`, `lytgos-provider`, `lytgos-storage`, `lytgos-boot`
- [X] T038 [P] Update `specs/002-agent-provider/quickstart.md` with actual dependency versions, test commands, and integration profile commands discovered during implementation
- [X] T039 Run `./mvnw -B -ntp clean verify`；修复所有失败，预期 BUILD SUCCESS，`integration` 组默认排除
- [X] T040 [P] Run manual quickstart checks：`./mvnw -B -ntp -pl lytgos-provider dependency:tree -Dincludes=org.springframework.ai`、`grep -r "sk-" --exclude-dir=target --exclude-dir=.git .`、有 key 时运行 `ProviderSmokeIT`
- [X] T041 [P] Run `git status --short` / `git diff --stat`；确认没有 `.lytgos/`、密钥或构建产物；准备 reviewer 变更摘要

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately. T002/T003/T004/T005/T006 可并行；T009 依赖 T002/T003。
- **Foundational (Phase 2)**: Depends on Setup; T010、T011、T012、T013、T014、T016 可并行；T015 依赖 T011/T012/T013/T014；T017 依赖 T005 的配置 schema，不依赖 T016；T018 依赖 T017。
- **US1 (Phase 3)**: Depends on T010/T011/T017/T018；T019（tests）先于/同步 T020；T021 依赖 T020；T022 依赖 T020/T021；T023 依赖 T018。
- **US2 (Phase 4)**: Depends on T010/T017/T016；T024 先于/同步 T025；T026 依赖 T025/T016/T017；T027 依赖 T016。
- **US3 (Phase 5)**: Depends on US1（T020）与 T011/T012/T013/T014/T015；T028 先于/同步 T029；T030 依赖 T012/T013/T014；T031 依赖 T020；T032 依赖 T015/T012/T013。
- **US4 (Phase 6)**: Depends on US1（T020）；T033 先于/同步 T034；T035 先于/同步 T036；T036 依赖 T034/T035。
- **Polish (Phase 7)**: Depends on all desired stories; T039 最后运行；T037/T038/T040/T041 可在 T039 前后并行（但 T039 失败时先修复）。

### User Story Dependencies

- **US1 (P1)**: 无其他故事依赖，MVP。
- **US2 (P2)**: 依赖 Foundational；与 US1 独立，可并行。
- **US3 (P2)**: 依赖 US1 的 `ProviderService` 与 Foundational 存储；与 US2 可并行。
- **US4 (P3)**: 依赖 US1 的 `ProviderService`；与 US2/US3 可并行。

### Within Each User Story

- Tests first or in the same change as implementation; key regression tests must be written before claiming completion.
- Models/ports before services; services before boot wiring; boot wiring before smoke tests.
- Each task's DoD includes running the relevant module tests.

### Parallel Opportunities

- Setup: T002、T003、T004、T005、T006 可并行（不同 POM/配置文件）。
- Foundational: T011、T012、T013、T014、T016 可并行；T010 可与 T011–T014 并行（不同文件）。
- Story tests: US1 routing test (T019) 可与 US2 ProfileLoaderTest (T024) 并行；US3 audit tests (T028/T030) 与 US4 adapter test (T033) 在各自前置完成后可并行。
- Polish: T037/T038/T040/T041 可并行；T039 最后执行。

---

## Parallel Example: User Story 1

```bash
# 在同一变更中先写测试，再实现：
./mvnw -B -ntp -pl lytgos-provider -Dtest=ProviderServiceTest test
# 预期的关键测试：
# - routingUsesOnlyTargetProvider
# - unknownProviderFailsBeforeModelCall
```

## Parallel Example: US2 + US3

```bash
# 两个故事在 Foundational 完成后可并行：
./mvnw -B -ntp -pl lytgos-core -Dtest=ProfileLoaderTest test
./mvnw -B -ntp -pl lytgos-storage -Dtest=LlmCallRepositoryTest test
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: US1（ProviderService + routing tests + smoke）
4. **STOP and VALIDATE**: 路由测试通过；人工 smoke 可按 quickstart 运行
5. 继续 US2 → US3 → US4

### Incremental Delivery

1. Setup + Foundational → schema/端口/工厂就绪
2. US1 → 路由与一次调用可用（MVP）
3. US2 → Profile 自动加载与索引
4. US3 → 审计落库与失败路径
5. US4 → 工具 schema 翻译与自动执行关闭
6. Polish → 全量门禁、注释审计、人工验收

### Notes

- 本节所有新增/修改代码必须使用中文注释；字段、方法参数、局部变量都要有注释。
- 数据模型约束以 `data-model.md` 的 `Entity.field` 引用为准；不要在本文件重复长篇约束。
- 课件中的 `OryxTool`/`.oryxos` 只作对照；代码使用 `LytgTool`/`.lytgos`。
- 不自动 commit/push；用户决定同步时机。
