# Tasks: CLI and Session Persistence

**Input**: Design documents from `/specs/004-cli-session/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/cli.md, contracts/session.md, quickstart.md

**Tests**: 本功能明确要求测试：`SessionManagerTest`、`SessionRepositoryTest`、`LytgosCliTest`，并补充 `CliChannelTest`。测试任务先于或与对应实现任务同步落地。

**Organization**: 任务按用户故事分组。`US2`（会话身份与持久化）是 `US1`（终端对话）、`US3`（命令面）和 `US4`（工作区/配置）的共同地基，因此先于同优先级的 `US1` 执行；依赖关系见文末。

## 全局编码约定（所有任务必须遵守）

- 所有新增/修改代码使用中文注释；每个字段、方法参数、局部变量都要有注释。
- 测试方法名用英文（驼峰或 snake_case），中文语义放 `@DisplayName`。
- 每个模块按领域分包；不新增第 10 个 Maven 模块。
- 不直接访问 `System.out/err/in`；输出用 Picocli `getOut()/getErr()`，输入用 `FileDescriptor.in` 包装。
- `session_id` 只在 `JpaSessionManager` 内拼接；其他任何类不得出现 `channel + ":" + user` 形式的拼接。
- 不引入 Reactor、`CompletableFuture` 或自建线程池。
- 不自动 commit/push。

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 依赖与打包入口调整，为后续实现提供编译基础。

- [X] T001 Add Jackson databind to storage for message JSON in `lytgos-storage/pom.xml`, version managed by Spring Boot BOM
- [X] T002 Add `spring-boot`, `lytgos-channel-cli`, `sqlite-jdbc`, and `snakeyaml` dependencies to `lytgos-cli/pom.xml`
- [X] T003 Switch the executable JAR main class to `io.lytgos.cli.LytgosCli` in `lytgos-boot/pom.xml`
- [X] T004 [P] Add `package-info.java` for new domain packages under `lytgos-storage/src/main/java/io/lytgos/storage/session/` and `lytgos-cli/src/main/java/io/lytgos/cli/{system,chat,runtime,profile,provider,tool,session,workspace}/`
- [X] T005 Run `./mvnw -B -ntp -DskipTests compile` and confirm the dependency changes compile before feature code; record the result in `specs/004-cli-session/tasks.md`

**Checkpoint**: 模块依赖与入口就位，仍保持 9 个 Maven 模块。

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: CLI 轻/重命令共享的工作区路径与 Spring 启动器。

**⚠️ CRITICAL**: 所有命令和重命令都依赖本阶段。

- [X] T006 [P] Create `WorkspacePaths` in `lytgos-cli/src/main/java/io/lytgos/cli/workspace/WorkspacePaths.java`; fields `root`, `agents`, `skills`, `memory`, `sessions`, `logs`, `profiles`, `database` follow `data-model.md §4.1 WorkspacePaths.root/agents/skills/memory/sessions/logs/profiles/database`; provide default `.lytgos` resolution and a constructor for tests
- [X] T007 Create `SpringRuntime` in `lytgos-cli/src/main/java/io/lytgos/cli/runtime/SpringRuntime.java`; implement `start(WebApplicationType)` via reflection on `io.lytgos.boot.LytgosApplication` with `SpringApplicationBuilder`, plus `awaitTermination()` and `close()` per `contracts/cli.md §5`
- [X] T008 Run `./mvnw -B -ntp -DskipTests compile` and confirm `SpringRuntime` compiles without a compile-time dependency on `lytgos-boot`; record the result in `specs/004-cli-session/tasks.md`

**Checkpoint**: 轻命令可解析路径，重命令可反射启动运行时；CLI 模块仍未依赖 boot。

---

## Phase 3: User Story 2 - 会话身份确定且跨重启保留 (Priority: P1)

**Goal**: 同一 channel/user/profile 始终对应同一会话，消息历史写入 SQLite 并在新上下文中完整恢复。

**Independent Test**: `SessionManagerTest` 验证三元组幂等与隔离；`SessionRepositoryTest` 验证 Flyway 表结构、JSON 回读和跨上下文恢复。

### Tests for User Story 2

- [X] T009 [P] [US2] Create `SessionManagerTest` in `lytgos-storage/src/test/java/io/lytgos/storage/session/SessionManagerTest.java` with tests `sameTripleReturnsSameSession`, `differentChannelCreatesDifferentSession`, `differentUserCreatesDifferentSession`, `differentProfileCreatesDifferentSession`, `rejectsBlankSegment`, `rejectsSegmentContainingColon`, `getUnknownReturnsEmpty`; assert the `SessionManager.getOrCreate/get/save` contract in `data-model.md §2.1`
- [X] T010 [P] [US2] Create `SessionRepositoryTest` in `lytgos-storage/src/test/java/io/lytgos/storage/session/SessionRepositoryTest.java` with tests `flywayCreatesSessionsTableWithExpectedColumns`, `messagesRoundTripKeepsOrderAndToolCalls`, `saveUpdatesLastActiveAndKeepsCreatedAt`, `historySurvivesNewApplicationContext`; use a temporary SQLite database and a second Spring context to simulate restart

### Implementation for User Story 2

- [X] T011 [US2] Create `SessionManager` in `lytgos-core/src/main/java/io/lytgos/core/session/SessionManager.java` with `getOrCreate(String, String, String)`, `Optional<Session> get(String)`, `void save(Session)`; Javadoc cites `data-model.md §1.1 Session.sessionIdentifier/agentProfileName/channelName/userIdentifier/history` and `§2.1`
- [X] T012 [US2] Create Flyway migration `lytgos-storage/src/main/resources/db/migration/sqlite/V3__init.sql` for the `sessions` table and `idx_sessions_last_active_at`, exactly matching `data-model.md §3.2`; do not modify V1/V2
- [X] T013 [US2] Create `SessionEntity` in `lytgos-storage/src/main/java/io/lytgos/storage/session/SessionEntity.java`; map every field `sessionId`, `profileName`, `channel`, `userId`, `messagesJson`, `status`, `createdAt`, `lastActiveAt`, `archivedAt` per `data-model.md §3.1 SessionEntity.*`; include a protected no-arg constructor and a full business constructor
- [X] T014 [US2] Create `SessionRepository` in `lytgos-storage/src/main/java/io/lytgos/storage/session/SessionRepository.java` as `JpaRepository<SessionEntity, String>`; no custom query methods in this feature
- [X] T015 [US2] Create `SessionJsonCodec` in `lytgos-storage/src/main/java/io/lytgos/storage/session/SessionJsonCodec.java` with `encode`/`decode`; preserve `AgentMessage.role/content/toolName/toolCallId/toolCalls` and `AgentToolCall.id/name/arguments` per `data-model.md §1.2`, `§1.3`, `§3.3`; invalid JSON throws `IllegalStateException`
- [X] T016 [US2] Implement `JpaSessionManager` in `lytgos-storage/src/main/java/io/lytgos/storage/session/JpaSessionManager.java` as a Spring `@Component`; generate `session_id` only inside this class; validate non-blank segments and reject `:`; implement upsert semantics and preserve `created_at` on update per `data-model.md §2.1` and `§3.1 SessionEntity.sessionId/profileName/channel/userId/messagesJson/status/createdAt/lastActiveAt/archivedAt`
- [X] T017 [US2] Change `AgentService` in `lytgos-core/src/main/java/io/lytgos/core/agent/AgentService.java` to take `SessionManager` instead of `Consumer<Session>`; save after a successful ReAct run; keep `AgentProfileContext` cleanup in `finally`; update `lytgos-core/src/test/java/io/lytgos/core/agent/AgentServiceTest.java` to mock `SessionManager` and verify `save`; behavior follows `contracts/session.md §6`
- [X] T018 [US2] Update `AgentRuntimeConfiguration` in `lytgos-boot/src/main/java/io/lytgos/boot/agent/AgentRuntimeConfiguration.java` to inject `SessionManager` into `AgentService` and remove the no-op save callback; rely on `PersistenceConfiguration` `@EntityScan("io.lytgos")` and `@EnableJpaRepositories("io.lytgos")` per `contracts/session.md §5`
- [X] T019 [US2] Run `./mvnw -B -ntp -pl lytgos-core,lytgos-storage -am test -Dtest=SessionManagerTest,SessionRepositoryTest,AgentServiceTest` and fix failures before continuing; record the result in `specs/004-cli-session/tasks.md`

**Checkpoint**: `SessionManagerTest` 与 `SessionRepositoryTest` 全绿；会话可跨上下文恢复；`AgentService` 已通过门面保存。

---

## Phase 4: User Story 1 - 在终端里与 Agent 多轮对话 (Priority: P1) 🎯 MVP

**Goal**: `lytgos chat` 读入一句话、交给 AgentService、打印最终回复，多轮共享同一会话，`/quit` 或 EOF 退出。

**Independent Test**: `CliChannelTest` 用替身 AgentService 和内存 SessionManager 验证两轮输入、回复打印、`/quit`、EOF 和异常继续；真实模型对话留人工验收。

### Tests for User Story 1

- [X] T020 [US1] Create `CliChannelTest` in `lytgos-channel-cli/src/test/java/io/lytgos/channel/cli/CliChannelTest.java` with tests `printsReplyAndContinuesUntilQuit`, `exitsOnEndOfInput`, `agentFailurePrintsErrorAndContinues`, `usesOneSessionForAllTurns`; inject `BufferedReader`/`PrintWriter` and mock `AgentService`/`SessionManager`; method names English, `@DisplayName` Chinese

### Implementation for User Story 1

- [X] T021 [US1] Replace the skeleton `CliChannel` in `lytgos-channel-cli/src/main/java/io/lytgos/channel/cli/CliChannel.java` with the 7-argument constructor and `int run()` contract in `contracts/cli.md §6`; call `getOrCreate("cli", user, profileName)` exactly once; `/quit` and EOF exit; Agent exceptions print to error output and continue; no reasoning, model calls, tool execution, or session ID concatenation
- [X] T022 [US1] Create `ChatCommand` in `lytgos-cli/src/main/java/io/lytgos/cli/chat/ChatCommand.java` with `--profile`/`-p` defaulting to `default`; validate `profiles/<name>.yaml` before starting Spring; start `WebApplicationType.NONE`; wrap `FileDescriptor.in`; obtain `AgentService`/`SessionManager`; run `CliChannel`; close runtime and return its exit code per `contracts/cli.md §4.3`
- [X] T023 [US1] Run `./mvnw -B -ntp -pl lytgos-channel-cli,lytgos-cli -am test -Dtest=CliChannelTest` and confirm compilation plus green tests; record the result in `specs/004-cli-session/tasks.md`

**Checkpoint**: `CliChannelTest` 全绿；`chat` 命令可启动、两轮对话并正常退出。

---

## Phase 5: User Story 3 - 12 个子命令与轻重命令分流 (Priority: P2)

**Goal**: 提供 Provider/Tool/Session 查询、`serve`、`gateway`，并保证查询类命令不启动 Spring，运行类命令启动 Spring。

**Independent Test**: 命令类编译通过；轻命令手工执行无 Spring 日志；`serve`/`gateway` 启动日志含 `Starting LytgosCli` 与非零 JPA 仓库数；完整 12 命令注册由 Phase 7 的 `LytgosCliTest` 覆盖。

### Implementation for User Story 3

- [X] T024 [P] [US3] Create `ProviderView` and `ProviderConfigReader` in `lytgos-cli/src/main/java/io/lytgos/cli/workspace/` and `.../provider/`; `ProviderView.name` and `ProviderView.baseUrl` follow `data-model.md §4.2 ProviderView.name/baseUrl`; the type must have no api-key field; read `lytgos.providers` from classpath `application.yaml` with SnakeYAML `SafeConstructor`
- [X] T025 [US3] Create `ProviderCommand` and `ProviderListCommand` in `lytgos-cli/src/main/java/io/lytgos/cli/provider/`; output only name and base URL; empty list returns `暂无 Provider 配置`; no Spring startup per `contracts/cli.md §4.10`
- [X] T026 [P] [US3] Create `ToolCommand` and `ToolListCommand` in `lytgos-cli/src/main/java/io/lytgos/cli/tool/`; fixed `暂无已注册工具（Tool 能力尚未接入）`, exit 0, no Spring startup per `contracts/cli.md §4.11`
- [X] T027 [P] [US3] Create `SessionSummary` and `SessionSummaryReader` in `lytgos-cli/src/main/java/io/lytgos/cli/session/`; `SessionSummary.sessionId/profileName/channel/userId/status/lastActiveAt` follow `data-model.md §4.4`; use a read-only SQLite connection and never create tables
- [X] T028 [US3] Create `SessionCommand` and `SessionListCommand` in `lytgos-cli/src/main/java/io/lytgos/cli/session/`; query `sessions` ordered by `last_active_at DESC`; missing DB/table/empty table prints `暂无会话`, exit 0 per `contracts/cli.md §4.12`
- [X] T029 [US3] Create `ServeCommand` in `lytgos-cli/src/main/java/io/lytgos/cli/runtime/ServeCommand.java`; start `WebApplicationType.SERVLET` and block via `SpringRuntime.awaitTermination()` until shutdown per `contracts/cli.md §4.4`
- [X] T030 [US3] Create `GatewayCommand` in `lytgos-cli/src/main/java/io/lytgos/cli/runtime/GatewayCommand.java`; start `WebApplicationType.NONE`, register shutdown handling, block until termination, no IM channels per `contracts/cli.md §4.5`
- [X] T031 [US3] Run `./mvnw -B -ntp -pl lytgos-cli -am -DskipTests compile` and confirm all US3 command classes compile; record the result in `specs/004-cli-session/tasks.md`

**Checkpoint**: Provider/Tool/Session 查询与 `serve`/`gateway` 实现完成；轻命令不依赖 Spring 启动器。

---

## Phase 6: User Story 4 - 工作区初始化与配置查询 (Priority: P3)

**Goal**: `init` 幂等创建工作区与默认 Profile；`status` 和 Profile 管理命令可用；已有文件不被覆盖。

**Independent Test**: 在空临时目录执行 `init` 两次，确认目录/文件创建且第二次不覆盖；`profile create/list/show/delete` 冒烟；`status` 输出工作区、Profile 数、Provider 数、会话数。

### Implementation for User Story 4

- [X] T032 [US4] Create `InitCommand` in `lytgos-cli/src/main/java/io/lytgos/cli/system/InitCommand.java`; idempotently create the layout and placeholder files in `data-model.md §5.1 WorkspacePaths.*` and `§5.2`; never overwrite existing files; print created/skipped summary per `contracts/cli.md §4.1`
- [X] T033 [P] [US4] Create `ProfileSummary` and `ProfileCommand` in `lytgos-cli/src/main/java/io/lytgos/cli/profile/`; `ProfileSummary.name/provider/model/path` follow `data-model.md §4.3`; `ProfileCommand` is the parent for `list/create/show/delete`
- [X] T034 [US4] Create `ProfileListCommand` in `lytgos-cli/src/main/java/io/lytgos/cli/profile/ProfileListCommand.java`; list `*.yaml`/`*.yml` sorted by name; empty/missing directory prints `暂无 AgentProfile`, exit 0 per `contracts/cli.md §4.6`
- [X] T035 [US4] Create `ProfileCreateCommand` in `lytgos-cli/src/main/java/io/lytgos/cli/profile/ProfileCreateCommand.java`; validate `[A-Za-z0-9_-]+`, reject existing files, write the default template from `data-model.md §5.2` with `provider=deepseek` and `model=deepseek-chat` per `contracts/cli.md §4.7`
- [X] T036 [US4] Create `ProfileShowCommand` in `lytgos-cli/src/main/java/io/lytgos/cli/profile/ProfileShowCommand.java`; validate the name, resolve inside `profiles/`, print the raw YAML, and return exit 1 when missing per `contracts/cli.md §4.8`
- [X] T037 [US4] Create `ProfileDeleteCommand` in `lytgos-cli/src/main/java/io/lytgos/cli/profile/ProfileDeleteCommand.java`; validate the name, resolve inside `profiles/`, delete the YAML, and return exit 1 when missing per `contracts/cli.md §4.9`
- [X] T038 [US4] Create `StatusCommand` in `lytgos-cli/src/main/java/io/lytgos/cli/system/StatusCommand.java`; print workspace path, database initialized flag, Profile count, Provider count, Session count; missing workspace/DB is an informational state with exit 0 per `contracts/cli.md §4.2`
- [X] T039 [US4] Run `./mvnw -B -ntp -pl lytgos-cli -am -DskipTests compile` and confirm init/profile/status classes compile; record the result in `specs/004-cli-session/tasks.md`

**Checkpoint**: 工作区初始化与 Profile/状态查询实现完成；空目录二次 `init` 不覆盖已有文件。

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 组装 12 个叶子命令、验证全量门禁、完成人工证据与不变量审计。

- [X] T040 Assemble `LytgosCli` in `lytgos-cli/src/main/java/io/lytgos/cli/LytgosCli.java`: register `init`, `status`, `chat`, `serve`, `gateway`, `profile list/create/show/delete`, `provider list`, `tool list`, `session list` and the four parent commands; `main` executes Picocli and exits with its code; root help/version per `contracts/cli.md §1` and `§2`
- [X] T041 Create `LytgosCliTest` in `lytgos-cli/src/test/java/io/lytgos/cli/LytgosCliTest.java` with tests `rootHelpExitsSuccessfully`, `allTwelveLeafCommandsAreRegistered`, `everyLeafCommandHelpExitsSuccessfully`, `parentCommandsExposeExpectedSubcommands`; use Picocli `CommandLine` with in-memory writers; no Spring startup
- [X] T042 Run `./mvnw -B -ntp -pl lytgos-cli -am test -Dtest=LytgosCliTest` and fix failures until green; record the result in `specs/004-cli-session/tasks.md`
- [X] T043 Run `./mvnw -B -ntp clean verify` and confirm all quality gates (Spotless/Checkstyle/PMD/SpotBugs/FindSecBugs/ArchUnit/JaCoCo) and all tests pass, including existing 67 tests; record the result in `specs/004-cli-session/tasks.md`
- [X] T044 Run `./mvnw -B -ntp -Pp3c verify` and confirm the Alibaba Java coding guidelines gate passes; record the result in `specs/004-cli-session/tasks.md`
- [X] T045 Run the H4 invariant audit: grep for direct `System.out/err/in`, `session_id` concatenation outside `JpaSessionManager`, Reactor/`CompletableFuture`/custom thread pools, plaintext API keys, and Spring AI automatic tool execution paths; record file-level evidence in `specs/004-cli-session/tasks.md`
- [X] T046 Package the executable JAR with `./mvnw -B -ntp -DskipTests package` and run `java -jar lytgos-boot/target/lytgos-boot-1.0.0-SNAPSHOT.jar --help`; confirm the CLI is the entry point and all 12 leaf commands appear
- [X] T047 Execute `quickstart.md` manual checklist in an empty temporary directory: init twice, light-command timing/no Spring logs, profile CRUD, provider redaction, session empty state, two-round chat with `/quit`, restart recovery, serve health, gateway startup, and nonzero JPA repository scan count
- [X] T048 Mark completed tasks in `specs/004-cli-session/tasks.md` and produce the six-evidence acceptance report; do not commit or push

**Checkpoint**: 12 个叶子命令可执行；自动化测试与全量门禁全绿；剩余真实模型/网络人工项已明确列出。

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，先执行。
- **Foundational (Phase 2)**: 依赖 Setup；阻塞所有命令实现。
- **US2 (Phase 3)**: 依赖 Foundational；阻塞 US1、US3、US4 的运行时行为。
- **US1 (Phase 4)**: 依赖 US2 与 `SpringRuntime`；可与 US3/US4 的命令类实现部分并行。
- **US3 (Phase 5)**: 依赖 Foundational 与 US2；与 US1/US4 的文件不重叠，可并行。
- **US4 (Phase 6)**: 依赖 Foundational；与 US1/US3 的文件不重叠，可并行。
- **Polish (Phase 7)**: 依赖 US1、US3、US4 全部完成；根命令注册与全量验证最后执行。

### User Story Dependencies

- **US2 → US1**：没有持久化会话就无法验证跨轮历史和重启恢复。
- **US2 → US3**：`session list` 依赖 `sessions` 表。
- **US2 → US4**：`status` 的会话计数依赖 `sessions` 表（表缺失时显示未初始化）。
- **US3/US4 → Polish**：`LytgosCli` 必须等所有叶子命令类就绪后统一注册。

### Within Each User Story

- 测试任务先于或与对应实现任务同步；测试失败先修实现，不放宽断言。
- 领域类型与迁移先于服务实现；服务实现先于命令入口。
- `SessionManagerTest`/`SessionRepositoryTest` 在 US2 内闭环；`CliChannelTest` 在 US1 内闭环；`LytgosCliTest` 在 Polish 统一验证 12 命令注册。

### Parallel Opportunities

- T004 的 `package-info.java` 可按包并行创建。
- T009/T010 与 T011~T015 的类型创建可在不同文件中并行，但测试转绿依赖 T016。
- T024、T026、T027、T033 属于不同包/文件，可并行。
- T029/T030 与 US4 的 T032~T038 可并行。
- T041 测试编写可与 T040 根命令注册同步进行。

---

## Parallel Example: User Story 2

```text
T011 SessionManager 接口
T012 V3__init.sql
T013 SessionEntity
T014 SessionRepository
T015 SessionJsonCodec
T009 SessionManagerTest
T010 SessionRepositoryTest
```

以上文件互不重叠，可并行创建；随后 T016 实现 `JpaSessionManager`，T017/T018 接入 `AgentService`。

## Parallel Example: User Story 3 / US4

```text
T024 ProviderView/ProviderConfigReader
T026 ToolCommand/ToolListCommand
T027 SessionSummary/SessionSummaryReader
T033 ProfileSummary/ProfileCommand
```

四组文件位于不同领域包，可并行实现；父命令注册留给 T040。

---

## Implementation Strategy

### MVP First (US2 + US1)

1. 完成 Phase 1 Setup。
2. 完成 Phase 2 Foundational。
3. 完成 US2 会话层并通过 `SessionManagerTest`、`SessionRepositoryTest`。
4. 完成 US1 `CliChannel`/`ChatCommand` 并通过 `CliChannelTest`。
5. **STOP and VALIDATE**：真实模型两轮对话 + `/quit` + 重启恢复；此时 CLI 的最小产品价值已成立。

### Incremental Delivery

1. Setup + Foundational → 依赖与路径就绪。
2. US2 → 会话地基可独立验证。
3. US1 → 终端可对话（MVP）。
4. US3 → Provider/Tool/Session 查询、`serve`、`gateway` 可用。
5. US4 → 工作区初始化与 Profile 管理可用。
6. Polish → 12 命令统一注册、全量门禁与人工验收。

### Parallel Team Strategy

1. 全员完成 Setup + Foundational。
2. 一名开发者先做 US2；完成后 US1/US3/US4 可按文件并行。
3. 最后由一人执行 T040~T048 的组装、全量门禁与验收报告，避免根命令文件冲突。

---

## Implementation Evidence (2026-11-23)

**Branch**: `004-lesson18-cli-session`

### Automated gates

| 命令 | 结果 |
|---|---|
| `./mvnw -B -ntp clean verify` | BUILD SUCCESS；88 个测试，0 失败/错误/跳过；Spotless、Checkstyle、PMD 7、SpotBugs/FindSecBugs、ArchUnit、JaCoCo 全部通过 |
| `./mvnw -B -ntp -Pp3c verify` | BUILD SUCCESS；全部 9 个模块通过阿里巴巴 Java 规约门禁 |

新增测试类与用例数：

| 测试类 | 用例数 | 覆盖 |
|---|---:|---|
| `io.lytgos.storage.session.SessionManagerTest` | 9 | 三元组幂等、三种隔离、非法字段、保存与回读、标识一致性 |
| `io.lytgos.storage.session.SessionRepositoryTest` | 4 | Flyway V3 表结构、JSON 往返、时间戳更新、新上下文恢复 |
| `io.lytgos.channel.cli.CliChannelTest` | 4 | 回复打印、EOF、异常继续、单会话多轮 |
| `io.lytgos.cli.LytgosCliTest` | 4 | 12 叶子命令注册、根/叶子帮助、父命令子命令 |

既有 67 个测试全部回归通过，总计 88 个。

### 手工验收

- `--help` 退出码 0；根命令直接列出 5 个叶子命令与 4 个父命令（展开共 12 个叶子命令）。
- 空目录执行 `init` 两次：第一次创建 6 个目录、5 个占位文件和 `profiles/default.yaml`；第二次全部显示“已存在”，内容未覆盖。
- `status`：工作区绝对路径、数据库未初始化、AgentProfile 1、Provider 2、会话未初始化。
- `profile create/list/show/delete weather` 全部退出码 0；重复创建会报错且不覆盖；`profile show ../secret` 退出码 1，未越出 profiles 目录。
- `provider list` 只输出 `deepseek` 与 `kimi` 的名称和 base URL，没有任何 api-key。
- `tool list` 输出“暂无已注册工具（Tool 能力尚未接入）”。
- `session list` 在数据库未初始化时输出“暂无会话”且不创建数据库；首次 `chat` 后显示 `cli:luyi:default`。
- `chat` 后再次执行 `status`：数据库已初始化、AgentProfile 1、Provider 2、会话 1。
- 真实 DeepSeek 对话连续运行 3 次，始终复用同一会话；数据库最终 `messages_json` 有 6 条消息，`llm_calls` 有 3 条且全部成功；第三次询问“最开始说的第一句话是什么”时模型正确回答首轮内容，证明跨重启历史注入生效。
- `serve` 使用默认端口 8989 在本机启动；`/api/v1/health` 与 `/api/v1/info` 返回 HTTP 200。
- `gateway` 启动后保持运行，日志显示守护进程已启动；验证后手动终止测试进程。

### H4 不变量审计

| 不变量 | 结果 |
|---|---|
| 涉外 IO 过 Sandbox | 本功能不执行外部 IO 工具；现有 ToolExecutor Sandbox 接线保持不变 |
| LLM/工具审计 | 手工会话产生 3 条 `llm_calls`，全部成功；工具未注册，无 `tool_invocations` |
| 无明文密钥 | `application.yaml` 使用 `${DEEPSEEK_API_KEY:}` / `${KIMI_API_KEY:}`；grep 未发现明文赋值 |
| `session_id` 单点生成 | grep 未发现 `JpaSessionManager` 之外的拼接；测试仅断言期望值 |
| 无 Reactor/CompletableFuture/线程池 | grep 无命中；仅有 `SpringRuntime` 一个 JVM 关闭钩子线程 |
| 无 Spring AI 自动工具执行 | `ProviderService` 保持 `internalToolExecutionEnabled(false)` |

### 交付物存在性

- `lytgos-core/.../session/SessionManager.java`
- `lytgos-storage/.../session/{SessionEntity,SessionRepository,SessionJsonCodec,JpaSessionManager}.java`
- `lytgos-storage/src/main/resources/db/migration/sqlite/V3__init.sql`
- `lytgos-channel-cli/.../CliChannel.java`
- `lytgos-cli/.../LytgosCli.java` 与 12 个叶子命令、4 个父命令实现
- `lytgos-cli/.../runtime/SpringRuntime.java`
- `lytgos-boot/pom.xml` mainClass 为 `io.lytgos.cli.LytgosCli`

### 环境备注

- 构建使用 Maven 配置的 JDK 21；本机 `java` 命令为 JDK 25，运行 fat JAR 时出现 SQLite native-access 警告，不影响功能。
- `.claude/skills/lytgos-dev/SKILL.md` 是用户自有变更，不属于本功能交付物。

---

## Notes

- `[P]` 任务 = 不同文件、无未完成依赖。
- `[Story]` 标签映射到 `spec.md` 的用户故事，便于追溯。
- 数据模型约束以 `data-model.md` 为唯一来源；任务只引用 `Entity.field`，不复制未变更的约束。
- 不删除断言、不 `@Disabled`、不放宽阈值；失败先修实现。
- 测试方法名英文、`@DisplayName` 中文。
- 真实模型、真实 SQLite 文件、`serve` 健康端点与 `gateway` 进程行为属于人工验收项；自动化覆盖会话层与命令注册/帮助。
