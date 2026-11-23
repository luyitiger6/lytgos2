# CLAUDE.md

本文件为 Claude Code 在本仓库中工作时提供指引。

## 项目概述

LytgOS2 是一个 Java 原生、可私有部署、可审计的企业级 Agent OS。1.0 交付运行时内核——五大核心能力：LLM Provider 抽象、ReAct 循环、Memory、Tool 体系和 Web Service。企业治理层（多租户、SSO、完整审计、IM 渠道）属于扩展/社区范围。Agent 由目录定义，不是写后端代码写出来的。

**当前仓库状态：** 工程地基已完成：九模块 Maven 骨架、Maven Wrapper、Spring Boot 3.5.16、Spring AI 1.1.2 + Spring AI Alibaba 1.1.2.4-security-fix、统一 API 信封、OpenAPI、结构化日志、虚拟线程、Spotless/Checkstyle/PMD 7/SpotBugs/ArchUnit/JaCoCo 门禁、CI 与 pre-commit 已就位；`./mvnw clean verify` 通过，`lytgos-boot` 可启动并暴露 `/api/v1/health`、`/api/v1/info` 与 `/v3/api-docs`。已有 context smoke、HTTP 契约、模块架构和 ToolRegistry 测试；尚无 Agent 业务实现或 CLI 子命令。provider/storage 仍是占位类，JPA/SQLite/MCP 依赖在 US-1/US-4 引入。

## 权威文档

| 文档 | 用途 |
|---|---|
| `docs/ReleaseBaseline.md` | **LytgOS2 1.0 权威基线**：范围、命名、模块、Tool、CLI、端点、Demo 裁决。优先级高于四份设计文档 |
| `docs/TechnicalSolution.md` | 最新的机制设计：模块地图、数据模型、流程、接口 |
| `docs/DemandAnalysis.md` | 需求与 1.0 验收标准 |
| `docs/IndustryResearch.md` | 定位、竞争背景、项目为什么存在 |
| `docs/AiProgrammingGuide.md` | Spec-Kit 流程与 US-1…US-5 实施顺序 |
| `docs/engineering/` | 工程基线：版本、构建、运行时、API、质量、安全、CI、模块规范 |
| `docs/adr/` | 架构决策记录（Boot 3.5 + SAA、Google vs P3C、无 Prometheus） |
| `README.md` | 面向人的项目概览、快速开始、路线图和安全边界 |

文档冲突时的优先级：**ReleaseBaseline > TechnicalSolution > DemandAnalysis > IndustryResearch > AiProgrammingGuide**。

基线已经裁决了主要口径冲突；以下是它解决的原始冲突：

- **Maven 模块数：** 9 个核心模块（TechnicalSolution 第 13 章、AI 指南）vs 14 个模块（TechnicalSolution 第 10 章，含 `persona`、`knowledge` 和三个 IM 渠道模块）。
- **验收 Demo：** 2 个硬性 Demo（DemandAnalysis）vs 3 个（TechnicalSolution）vs 5 个（旧版 AI 指南映射）。
- **内置 Tool：** DemandAnalysis 的表格写 5/7 个；TechnicalSolution 写 9 个。
- **CLI 命令：** 12 vs 13（`agent import` 是额外的扩展命令）。
- **Skill 绑定：** 旧的「frontmatter `skills:` / SKILL.md 引用」写法 vs 最新模型：全局 `.lytgos/skills/<name>/` + Agent 本地相对软连接 + 渐进式披露。

实现按基线执行：**9 个模块、9 个内置 Tool、12 个 CLI 命令、10 个核心 REST 端点**，2 个硬性 Demo + 1 个可选 Demo。`persona`、`knowledge`、IM 渠道、SQLite/Mem0 Memory 后端、Agent 上传/生命周期 API、完整审计查询/SIEM 和 AgentScheduler 运行时 cron 增删改属于扩展工作。

## 命名规范（已确认）

| 项目 | 值 |
|---|---|
| 产品名 | **LytgOS2** |
| 发布版本 | **LytgOS2 1.0** |
| CLI | `lytgos <command>` |
| 工作区 | `.lytgos/` |
| SQLite 数据库 | `.lytgos/lytgos.db` |
| Maven 模块 | `lytgos-core`、`lytgos-provider`、`lytgos-memory`、`lytgos-tool`、`lytgos-channel-cli`、`lytgos-web`、`lytgos-storage`、`lytgos-cli`、`lytgos-boot` |
| 配置前缀 | `lytgos.*` |
| Java 类名前缀 | `Lytgos*`（如 `LytgosCli`） |

不要重新引入旧占位产品名，也不要把产品名与版本号、命令、路径、模块名写成粘连形式。

## 目标技术栈

- JDK 21（基线；允许 JDK 21+ 运行；项目 POM 和 IDEA project SDK 已统一为 21）。Spring Boot 3.5.16
- Spring AI 1.1.2 + Spring AI Alibaba 1.1.2.4-security-fix，用于 Provider 抽象和 LLM connector；按设计文档保留 SAA（ADR 0004）
- SAA starter 在 `lytgos-provider` classpath，默认 `spring.ai.dashscope.enabled=false`；启用 DashScope/Qwen 时配置 `DASHSCOPE_API_KEY`
- Spring Boot 3.5 OSS 已于 2026-06-30 EOL；当前为兼容 SAA 的设计基线，升级计划见 ADR 0004
- 自实现 ReAct 循环
- SQLite + Spring Data JPA
- Picocli
- SnakeYAML、MCP Java SDK 2.2.0、Logback/SLF4J
- 观测：health/info + 结构化日志 + 审计表；1.0 不做 Prometheus/Grafana（ADR 0003）
- 质量：Spotless + google-java-format（GOOGLE 为格式权威）、Checkstyle、PMD 7、SpotBugs + FindSecBugs、ArchUnit、JaCoCo；P3C 为阻断门禁（ADR 0002）

## 架构

**九个核心 Maven 模块：** `lytgos-core`、`lytgos-provider`、`lytgos-memory`、`lytgos-tool`、`lytgos-channel-cli`、`lytgos-web`、`lytgos-storage`、`lytgos-cli`、`lytgos-boot`。根 `pom.xml` 是 `packaging=pom` 的聚合父 POM，不包含源码；所有代码在九个模块内。扩展模块（1.0 默认不做）：`lytgos-persona`、`lytgos-knowledge`、`lytgos-channel-feishu`、`lytgos-channel-wecom`、`lytgos-channel-dingtalk`。

四层结构：

1. **接入层：** CLI Channel、REST API、`AgentScheduler`（cron）。三者都调用同一个 `AgentService.process`；引擎不感知触发入口。
2. **引擎层：** `ReActLoop` → `PromptBuilder` → `ProviderService`；工具调用经过 `ToolExecutor`；`AgentService` 负责编排最终 Session 持久化。
3. **能力层：** Provider（LLM）、Memory（会话 + 长期）、Tool（内置、MCP、`@Tool`）。
4. **基础层：** 工作区/Agent/Skill 加载、Session 存储、SQLite、配置与密钥加载。

**Agent 模型：** 一个目录 = 一个 Agent。`.lytgos/agents/<name>/AGENT.md` 的 frontmatter 是运行时 Profile（provider/model、tools、MCP server、channels、schedules、settings），正文是任务指令。`AgentLoader.deriveProfile()` 把它映射成 `Profile`。全局 Skill 存在 `.lytgos/skills/<name>/`；Agent 通过自己 `skills/` 下的相对软连接选择可见集合。每轮只注入 Skill 的 name、description 和本地路径；正文/脚本/参考经 `read_file`/`shell` 按需读取。

**持久化：** `sessions`、`tool_invocations`、`llm_calls`、`scheduled_tasks`、`task_executions`、`notify_channels`。表结构由 Flyway 管理（`db/migration/{vendor}/` 分轨）。审计表从 day one 写入，即使暂时没有查询端点。用户维护的数据（Agent 目录、Bootstrap 文件、`MEMORY.md`、MCP 配置）留在文件系统。

**Web：** 10 个核心端点（会话 CRUD + 发消息、无状态 Agent invoke、profile/memory/tool 列表、health/info），加上 1.0 的 `notify-channels` 和 scheduler v2 支撑端点；OpenAPI 文档通过 springdoc 暴露。

**三种运行模式：** `lytgos chat`、`lytgos serve`、`lytgos gateway`；它们共享同一份 Profile 和 Session 存储。

## 不可协商的设计规则

这些规则来自项目宪章和技术设计；违反会导致已知 bug 或范围漂移：

1. **自实现 ReAct 循环。** 不使用 Spring AI 的 Agent 抽象。
2. **Spring AI 只用三件事：** Provider 抽象、协议转换、`@Tool` JSON Schema 生成。**必须禁用自动 tool 执行**——Tool 调度归 `ReActLoop` + `ToolExecutor`。如果工具被调了两次，先查这里。
3. **Provider 显式映射。** 维护 provider name → `ChatModel` 映射；不要按类型扫描所有 `ChatModel` Bean。
4. **只有一个 `lytgos-tool` 模块。** 不要把内置 Tool、MCP Client 和 Sandbox 拆成多个模块。
5. **Agent 目录是上下文，不是 Tool。** `AGENT.md` 由 core 的 `AgentLoader`/`ContextLoader` 加载；Skill 不进入 `ToolRegistry`。
6. **Memory 只有一个门面。** `MemoryService` 隐藏会话记忆和长期记忆。1.0 默认后端是 `MarkdownMemoryStore`，使用 `## 核心记忆` / `## 归档记忆` 分区；核心记忆永不截断、不参与检索。1.0 不做自动事实抽取或向量检索。
7. **审计 day one。** 从第一版实现起写 `tool_invocations` 和 `llm_calls`，不要后期从日志反解析。
8. **Sandbox 接口先行。** 1.0 只实现 `WhitelistSandbox`（路径/Shell 命令/域名白名单）。不要使用 Java `SecurityManager`；它在受支持的 JDK 上不可用。
9. **密钥通过环境变量占位符注入**（`${ENV_VAR}`），绝不把明文密钥写进 `AGENT.md` 或提交的配置。
10. **核心 1.0 不做认证、SSE/WebSocket、多租户、SSO、IM 渠道、persona、知识库、向量记忆、Tool Policy、Agent 上传/生命周期 API、完整容器沙箱、Provider failover、Web 仪表板、Prometheus/Grafana、trace ID 串联、完整审计查询/SIEM、AgentScheduler 运行时 cron 增删改。** 完整清单以 `docs/ReleaseBaseline.md` 第 3.3 节为准。

## 构建与运行（工程地基阶段）

```bash
# 构建 + 全部质量门禁（推荐）
./mvnw clean verify

# 只编译
./mvnw -DskipTests compile

# 运行 boot fat JAR
java -jar lytgos-boot/target/lytgos-boot-1.0.0-SNAPSHOT.jar

# 验证端点与 OpenAPI
curl http://localhost:8989/api/v1/health
curl http://localhost:8989/api/v1/info
curl http://localhost:8989/v3/api-docs

# P3C 阻断门禁
./mvnw -Pp3c verify

# 安全扫描（需要 NVD_API_KEY）
NVD_API_KEY=... ./mvnw -Psecurity verify
```

Windows 用 `mvnw.cmd`。`mvn package` 不触发全部门禁，以 `./mvnw verify` 为准。

`lytgos` 子命令和 Agent 功能尚未实现；当前 boot JAR 只提供 `health` / `info` 和 OpenAPI。`lytgos-boot` 的 `LytgosApplication` 是 Spring Boot 可执行入口；`lytgos-cli` 的 `LytgosCli` 是 Picocli 骨架，尚未打成可执行 jar。JPA/SQLite/MCP 依赖在 US-1 / US-4 补入。

构建前确认 `mvn -v` 使用 JDK 21；如果仍显示 JDK 17，重启终端或先把 `JAVA_HOME` 指向 JDK 21。

工程基线文档在 `docs/engineering/`；质量审计用 `lytgos-quality` skill。测试已存在（context smoke、HTTP 契约、模块架构、ToolRegistry），但不要声称未实际运行的测试通过；后续每个功能模块至少补一个端到端测试，对应 `docs/DemandAnalysis.md` 的功能验收清单。

## 实施顺序

遵循 `docs/AiProgrammingGuide.md` 的依赖顺序：

```text
US-1 (Provider) → US-2 (ReAct) → US-3 (Memory) ∥ US-4 (Tool) → US-5 (Web Service)
```

主体开发使用 Spec-Kit：`constitution.md` → `spec.md` → `plan.md` → `tasks.md` → 实现，每个 user story 后跑 `analyze`。宪章的非协商原则见上面「不可协商的设计规则」。1.0 之后，增量工作切换到手动 Claude Code 提示词。

## 工作约定

- 文档正文用中文；代码标识符、命令、模块名、文件路径用英文。
- Git 提交信息用中文；保留 `feat:`、`fix:`、`chore:` 等英文类型前缀和 `Co-Authored-By` 尾注。
- 编辑四份设计文档时，保持现有格式：H2/H3 编号、代码块语言标记（`bash`、`yaml`、`text`）、`| --- | --- |` 表格分隔行。
- 不做未经检查的批量重命名；要同时检查代码块、路径和命令示例。
- 架构决策变化时，在同一次变更中更新 `docs/ReleaseBaseline.md` 和相关设计文档。
- 优先接口先行：契约放 `lytgos-core`，实现放 storage/web/channel 模块。新增 Channel 或 Tool 应新增模块/实现，而不是改引擎。
