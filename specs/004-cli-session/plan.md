# Implementation Plan: CLI and Session Persistence

**Branch**: `004-lesson18-cli-session` | **Date**: 2026-11-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-cli-session/spec.md`

## Summary

交付 Picocli 命令行入口、12 个叶子命令、CLI Channel 交互对话和 SQLite 会话持久化。CLI 分轻命令和重命令：轻命令直接做文件/SQLite 读取，不启动 Spring；重命令通过反射加载 boot 组装根并启动 Spring，获取 `AgentService` 与 `SessionManager`。core 新增 `SessionManager` 门面，storage 新增 `SessionEntity`/`SessionRepository`/`SessionJsonCodec`/`JpaSessionManager` 和 Flyway V3 `sessions` 表；`AgentService` 改为依赖 `SessionManager` 保存会话。可执行 JAR 的 mainClass 切换为 `LytgosCli`。

## Technical Context

**Language/Version**: Java 21；Spring Boot 3.5.16；Picocli 4.7.6

**Primary Dependencies**: Picocli、Spring Boot（CLI 重命令启动）、Spring Data JPA/SQLite/Flyway、Jackson databind（storage 消息 JSON）、sqlite-jdbc（CLI 轻命令直读）；不新增日志/JSON 之外的框架

**Storage**: SQLite `.lytgos/lytgos.db`；Flyway `V3__init.sql` 新增 `sessions`；V1 `llm_calls`、V2 `tool_invocations` 保持不变

**Testing**: `SessionManagerTest`、`SessionRepositoryTest`、`LytgosCliTest`；保留现有 67 个测试回归；`integration` 组默认排除

**Target Platform**: JVM 21，Windows/Linux；单二进制 fat JAR

**Project Type**: Maven 多模块 Java CLI/服务

**Performance Goals**: 轻命令不启动 Spring，直接返回；重命令启动一次运行时；chat 每行输入只调用一次 `AgentService.process`；session save 每个处理回合一次

**Constraints**: CLI 不依赖 boot；CLI 不使用 `System.out/err/in`（ArchUnit 禁止），输出用 Picocli `getOut`，输入用 `FileDescriptor.in` 包装；不引入 Reactor/`CompletableFuture`/自建线程池；不实现 Web/Scheduler/Tool/MCP/Sandbox/AGENT.md/IM

**Scale/Scope**: 12 个 CLI 叶子命令、4 个父命令；sessions 表每次会话一行；消息历史单列 JSON；单实例本地 CLI

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 门禁结果 | 证据 |
|---|---|---|
| I. 单二进制 9 模块 | PASS | 只改 core/storage/channel-cli/cli/boot；boot fat JAR mainClass 切换为 CLI |
| II. 五大核心能力优先 | PASS | CLI 与会话持久化支撑 ReAct 入口；不引入扩展渠道或 Web 端点 |
| III. 自实现 ReAct 循环 | PASS | ReActLoop 不变；CLI 只调用 AgentService |
| IV. Spring AI 受限使用与 Provider 显式映射 | PASS | CLI/session 不触碰 Spring AI；Provider 映射不变 |
| V. Tool 统一注册、上下文分离与 Sandbox 先行 | PASS | tool list 在工具能力未接入时为空说明；CLI 不执行工具 |
| VI. Memory 单门面、Flyway 与审计 day one | PASS | sessions 表由 Flyway V3 创建；`llm_calls`/`tool_invocations` 保持写入；跨重启恢复由 SessionManager 保证 |
| VII. 每个 User Story 可演示 | PASS | chat/session 单测 + 12 命令 help/轻命令/重命令人工验收 |

**Post-design re-check**: PASS。CLI 通过反射启动 boot 组装根，避免 boot→cli 的 Maven 依赖环；轻命令直读 SQLite 是课程阶段性能优化，不改变数据归属。

### 课程阶段偏差（记录）

- 轻命令 `session list`/`status` 使用 sqlite-jdbc 直读 SQLite，不启动 Spring；存储 schema 仍由 storage 的 Flyway 管理。
- `provider list` 从 classpath `application.yaml` 读取全局 provider，不启动 Spring；只显示 name/base-url。
- `tool list` 在 Tool 能力接入前返回空说明。
- `gateway` 只提供守护进程骨架；IM 渠道在扩展阶段。
- 真实 Demo 的 `http_get` 仍在后续 Tool 能力交付后补跑。

## Project Structure

### Documentation (this feature)

```text
specs/004-cli-session/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── cli.md
│   └── session.md
├── checklists/
│   └── requirements.md
└── tasks.md              # 由 /speckit-tasks 生成
```

### Source Code (repository root)

```text
lytgos-core/
└── src/main/java/io/lytgos/core/session/
    ├── Session.java                 # 已有，消息历史领域对象
    ├── SessionManager.java          # 新增门面接口
    └── package-info.java

lytgos-storage/
├── src/main/java/io/lytgos/storage/session/
│   ├── SessionEntity.java
│   ├── SessionRepository.java
│   ├── SessionJsonCodec.java
│   ├── JpaSessionManager.java
│   └── package-info.java
├── src/main/resources/db/migration/sqlite/
│   └── V3__init.sql                 # sessions 表
└── src/test/java/io/lytgos/storage/session/
    ├── SessionManagerTest.java
    └── SessionRepositoryTest.java

lytgos-channel-cli/
├── src/main/java/io/lytgos/channel/cli/
│   ├── CliChannel.java              # 读入/转交/打印
│   └── package-info.java
└── src/test/java/io/lytgos/channel/cli/
    └── CliChannelTest.java          # 内存流交互测试（可选补充）

lytgos-cli/
├── src/main/java/io/lytgos/cli/
│   ├── LytgosCli.java               # 根命令，注册 12 个叶子命令
│   ├── system/
│   │   ├── InitCommand.java
│   │   └── StatusCommand.java
│   ├── chat/
│   │   └── ChatCommand.java
│   ├── runtime/
│   │   ├── ServeCommand.java
│   │   ├── GatewayCommand.java
│   │   └── SpringRuntime.java
│   ├── profile/
│   │   ├── ProfileCommand.java
│   │   ├── ProfileListCommand.java
│   │   ├── ProfileCreateCommand.java
│   │   ├── ProfileShowCommand.java
│   │   └── ProfileDeleteCommand.java
│   ├── provider/
│   │   ├── ProviderCommand.java
│   │   └── ProviderListCommand.java
│   ├── tool/
│   │   ├── ToolCommand.java
│   │   └── ToolListCommand.java
│   ├── session/
│   │   ├── SessionCommand.java
│   │   └── SessionListCommand.java
│   └── workspace/
│       ├── WorkspacePaths.java
│       ├── ProviderView.java
│       ├── ProviderConfigReader.java
│       └── SessionSummaryReader.java
└── src/test/java/io/lytgos/cli/
    └── LytgosCliTest.java

lytgos-boot/
├── pom.xml                          # mainClass → io.lytgos.cli.LytgosCli
└── src/main/java/io/lytgos/boot/agent/AgentRuntimeConfiguration.java

lytgos-channel-cli/pom.xml           # 如需测试依赖
lytgos-cli/pom.xml                   # spring-boot、channel-cli、sqlite-jdbc
lytgos-storage/pom.xml               # jackson-databind
```

**Structure Decision**: 保持 9 模块；core 只放 `SessionManager` 契约；storage 的 `session` 领域负责 JPA/JSON/Flyway；channel-cli 负责终端交互壳；cli 按 system/chat/runtime/profile/provider/tool/session/workspace 领域分包；boot 只调整 mainClass 与 Spring 装配。

## Complexity Tracking

| 决策 | 为什么需要 | 被拒绝的更简单方案 |
|---|---|---|
| CLI 反射启动 boot 组装根 | CLI 不能依赖 boot（boot 已依赖 CLI），否则 Maven 循环 | CLI 直接依赖 boot（循环依赖）；把 Spring 组装复制到 CLI（重复组装根） |
| CLI 直读 SQLite 做 session list/status | 轻命令不启动 Spring，满足秒回 | 为查询启动 Spring（违背轻重分流） |
| storage 新增 Jackson databind | sessions.messages_json 需要可靠 JSON 序列化 | 手写 JSON 编码（转义/兼容性风险）；Java 序列化（不可读） |
| `AgentService` 改为依赖 `SessionManager` | 去掉临时保存回调，统一会话持久化 | 保留 Consumer 回调（第 18 节后无实际实现方） |
