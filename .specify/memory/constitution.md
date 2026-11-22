<!--
Sync Impact Report
- 版本变化：1.0.0 → 1.1.0（平台基线更新）→ 1.2.0（恢复 SAA 兼容基线）
- 批准日期：2026-11-22
- 修改原则：I（保持 Spring Boot 3.5.x）；IV 保持 Spring AI 受限使用，1.0 引入 Spring AI Alibaba（ADR 0004）
- 新增章节：无
- 删除章节：无
- 推迟事项：Boot 4 升级待 SAA 的 Boot 4 兼容版本 GA
- 说明：本报告是修订审查用的临时材料，提交前应删除。
-->

# LytgOS2 Constitution

## Core Principles

### I. 单二进制模块化内核

LytgOS2 1.0 必须是一个 JDK 21 + Spring Boot 3.5.x 的单体应用，以 Maven 多模块组织，固定 9 个核心模块：`lytgos-core`、`lytgos-provider`、`lytgos-memory`、`lytgos-tool`、`lytgos-channel-cli`、`lytgos-web`、`lytgos-storage`、`lytgos-cli`、`lytgos-boot`。根 `pom.xml` 只做聚合父 POM，业务代码必须落在上述模块内；交付物是单一可执行 fat JAR。Java 包名与 groupId 使用 `io.lytgos`。1.0 不创建 `lytgos-persona`、`lytgos-knowledge` 或三个 IM 渠道模块。

**Rationale**: 私有部署与可审计要求优先于微服务弹性；单二进制降低安装、升级和排障成本，9 模块边界防止能力耦合。

**Verification**: `mvn -v` 显示 JDK 21；`mvn clean package` 成功；仓库只有 9 个核心 Maven 模块；boot JAR 可启动并暴露 `/api/v1/health`。

### II. 五大核心能力优先

1.0 只交付运行时内核的五大能力：LLM Provider 抽象、自实现 ReAct 循环、Memory、Tool 体系、Web Service。多租户、SSO、认证/RBAC、IM 入站渠道、Persona、Knowledge、向量 Memory、Tool Policy、SSE/WebSocket、异步 Tool、Web 仪表板、完整审计查询/SIEM、容器 Sandbox、Provider failover、集群调度都属于扩展或社区范围。1.0 不实现、也不预留半成品入口；任何范围扩张必须先修订 `docs/ReleaseBaseline.md`。

**Rationale**: 运行时内核先跑通、可演示、可审计，企业治理层才有稳定的承载基础；提前铺开会导致范围漂移和交付失焦。

**Verification**: 每个新增能力都能映射到五大核心能力之一；扩展能力只出现在 `docs/ReleaseBaseline.md` 第 3.3 节的“明确不做”清单中，不出现在 1.0 代码与端点里。

### III. 自实现 ReAct 循环

ReAct 循环必须由项目自实现，不得使用 Spring AI 的 Agent 抽象。循环默认 `max_iterations=10`，历史默认 `max_history_turns=20`；同一轮多个 Tool 调用按顺序执行，不做并行。每次 LLM 调用与 Tool 调用都必须进入审计路径。

**Rationale**: Tool 调度、轮次上限和失败处理是 Agent OS 的核心控制面，必须显式可读、可测试、可审计，不能隐藏在第三方 Agent 抽象里。

**Verification**: 代码中不存在 Spring AI Agent 运行时依赖；`ReActLoop` 与 `ToolExecutor` 是 Tool 调度的唯一入口；构造一个需要多轮 Tool 调用的场景，确认每轮只执行一次且审计表有对应记录。

### IV. Spring AI 受限使用与 Provider 显式映射

Spring AI 只允许用于三件事：Provider 抽象、协议转换、`@Tool` JSON Schema 生成。必须禁用 Spring AI 的自动 Tool 执行；Tool 调度只能由 `ReActLoop` + `ToolExecutor` 控制。Provider 必须维护显式的 provider name → `ChatModel` 映射，禁止按类型扫描全部 `ChatModel` Bean。1.0 至少支持 DeepSeek 和 Kimi；不做 fallback、hedge racing、circuit breaker。每次 LLM 调用的 provider、model、token 用量和耗时写入 `llm_calls`。密钥只能通过 `${ENV_VAR}` 环境变量占位符注入，严禁把明文密钥写入 `AGENT.md` 或提交的配置。

**Rationale**: 受限使用避免框架替项目做调度决策；显式映射让 Provider 选择可预测、可配置；密钥占位符保证仓库可公开审计。

**Verification**: 代码审查确认自动 Tool 执行已关闭；Provider 通过名称映射获取；仓库与 `AGENT.md` 中不存在明文密钥；每次 LLM 调用都有 `llm_calls` 记录。

### V. Tool 统一注册、上下文分离与 Sandbox 先行

- 只允许一个 `lytgos-tool` 模块；所有 Tool 统一包装为 `LytgTool` 并注册到 `ToolRegistry`，ReAct 循环不感知 Tool 来源。
- Plugin Tool 采用三档接入：`AGENT.md` + 现成 MCP（零代码，主推）、自写 MCP server（轻代码）、`@Tool` Java Bean（重代码）；1.0 的 MCP 只支持 stdio。
- Agent 目录是上下文来源，不是 Tool，不进入 `ToolRegistry`；Skill 同样不进入 `ToolRegistry`。Skill 绑定由 Agent 本地 `skills/` 下的相对软连接表达，每轮只注入 name、description 和本地路径，正文/脚本/参考经 `read_file`/`shell` 按需读取。
- Sandbox 接口先行，1.0 只实现 `WhitelistSandbox`，覆盖路径、Shell 命令、HTTP 域名三类白名单。必须处理 `../` 路径穿越；Shell 只做可执行文件白名单精确匹配；HTTP 解析 host 后做通配符匹配。禁止使用 Java `SecurityManager`；违规抛 `SandboxViolationException` 并走 Tool 失败审计。

**Rationale**: Tool 是能力扩展点，上下文是 Agent 定义的一部分；把两者混在同一注册表会让权限、审计和渐进式披露同时失控。白名单沙箱是 1.0 唯一可审计的最小安全边界。

**Verification**: `ToolRegistry` 只包含 Tool；Agent/Skill 目录不出现在 Tool 列表中；所有文件、Shell、HTTP Tool 都经过 `WhitelistSandbox`；构造路径穿越、非白名单命令和非白名单域名场景，均被拒绝并写入失败审计。

### VI. Memory 单门面、文件持久化与审计 day one

对外只暴露 `MemoryService` 一个门面；会话记忆落 SQLite，长期记忆的唯一 1.0 后端是 `MarkdownMemoryStore`，文件为 `.lytgos/memory/MEMORY.md`。文件必须包含 `## 核心记忆` 和 `## 归档记忆` 两个一级分区：核心记忆全量注入 system prompt，永不截断、不参与检索；归档记忆可截断、可关键词检索。1.0 不做自动事实抽取、向量检索、语义召回或 Memory Wiki。SQLite 固定 6 张表：`sessions`、`tool_invocations`、`llm_calls`、`scheduled_tasks`、`task_executions`、`notify_channels`，由 Flyway 按 vendor 分轨管理；`tool_invocations` 和 `llm_calls` 必须从第一版实现起写入，不得后期从日志反解析。Session 必须跨重启恢复。用户维护的数据（Agent 目录、Bootstrap 文件、`MEMORY.md`、MCP 配置）保留在文件系统。

**Rationale**: 单一门面让上层不感知存储形态；核心/归档分区保证关键偏好永不被截断；审计从第一版落库，避免后期反解析日志的高成本与不可靠。

**Verification**: 上层只依赖 `MemoryService`；`MEMORY.md` 两个分区存在且行为符合约定；6 张表由 Flyway 创建；发起一次对话后 `llm_calls` 与 `tool_invocations` 有记录；重启后 Session 可恢复。

### VII. 每个 User Story 必须可演示

每个 user story 完成后必须有一个可运行、可演示的端到端结果；1.0 验收包含 2 个硬性 Demo 和 1 个可选 Demo。每个 user story 结束时必须运行 `/speckit-analyze`，检查 constitution、spec、plan、tasks 与代码的一致性。不得在未实际运行的情况下声称测试通过；优先级是端到端跑通，而不是局部完美。

**Rationale**: 可演示结果是范围、机制和验收是否真正对齐的唯一证据；一致性检查能在漂移进入下一阶段前暴露问题。

**Verification**: 每个 user story 都有可复现的演示步骤和实际运行记录；`/speckit-analyze` 报告存在且无未处理的高优先级漂移；提交说明不包含未经验证的“测试通过”声明。

## 1.0 范围与扩展边界

| 维度 | 1.0 基线 |
|---|---|
| 核心能力 | Provider、ReAct、Memory、Tool、Web Service |
| Maven 模块 | 9 个核心模块（见原则 I） |
| 内置 Tool | 9 个 |
| CLI 命令 | 12 个 |
| 核心 REST 端点 | 10 个 |
| SQLite 表 | 6 张 |
| 验收 Demo | 2 个硬性 + 1 个可选 |
| 扩展模块 | `lytgos-persona`、`lytgos-knowledge`、`lytgos-channel-feishu`、`lytgos-channel-wecom`、`lytgos-channel-dingtalk` 不在 1.0 |

1.0 明确不做（完整清单以 `docs/ReleaseBaseline.md` 第 3.3 节为准）：

- 企业 IM 入站渠道：飞书、企业微信、钉钉、Slack。
- Persona、Knowledge、Agent 上传/生命周期 API、WorkspaceWatcher。
- SQLite/Mem0 Memory 后端、向量检索、语义召回、自动记忆抽取、情景记忆、Memory Wiki。
- Tool Policy、Tool LRU 加载、MCP Server 暴露。
- Provider fallback、hedge racing、自适应路由。
- 容器/microVM Sandbox、多租户隔离。
- 认证、RBAC、SSO、API Key、CORS 白名单。
- SSE/WebSocket 流式响应、异步 Tool 调用、Webhook 触发入口。
- Web 仪表板、Prometheus 指标、Grafana Dashboard。
- 审计查询接口、SIEM 导出、合规报表、敏感信息脱敏、trace ID 串联。
- Email SMTP 通知适配器、运行时 cron 增删改。
- 集群部署、分布式调度、Redis/PostgreSQL 外置状态、GraalVM Native Image。
- SDK、Skills Marketplace、移动端、Voice Channel、边缘部署。

## 开发工作流与质量门禁

- **Spec-Kit 流程**：`constitution.md` → `spec.md` → `plan.md` → `tasks.md` → 实现；主体开发按 `US-1 → US-2 → (US-3 ∥ US-4) → US-5` 推进。
- **一致性检查**：每个 user story 结束后运行 `/speckit-analyze`，不得跳过。
- **测试门禁**：每个模块至少一个端到端测试；未实际运行的测试不得声称通过。
- **文档优先级**：`ReleaseBaseline > TechnicalSolution > DemandAnalysis > IndustryResearch > AiProgrammingGuide`；架构决策变化时，在同一次变更中更新 `docs/ReleaseBaseline.md` 和相关设计文档。
- **接口先行**：契约放 `lytgos-core`，实现放 `storage`/`web`/`channel` 模块；新增 Channel 或 Tool 应新增模块/实现，而不是改引擎。
- **变更安全**：不做未经检查的批量重命名；同时检查代码块、路径和命令示例。
- **提交规范**：Git 提交信息用中文；保留 `feat:`、`fix:`、`chore:` 等英文类型前缀和 `Co-Authored-By` 尾注。

## Governance

本宪章是 LytgOS2 的非协商原则集合；`docs/ReleaseBaseline.md` 是 1.0 范围、数字与裁决的唯一口径。两者冲突时，必须先停下记录，并修订宪章或基线，禁止静默偏离。

**修订程序**：

1. 提出修订：说明动机、受影响的模块、Demo、验收项和迁移影响。
2. 评审与批准：由项目所有者批准；AI agent 可以起草修订，但不得自行批准或修改宪章。
3. 同步变更：涉及范围或架构时，同一次变更中更新 `docs/ReleaseBaseline.md` 和相关设计文档。
4. 记录版本：更新版本号、`Last Amended` 日期和变更说明。

**版本策略**（语义化版本）：

- MAJOR：删除或重新定义不兼容的治理原则。
- MINOR：新增原则/章节，或对现有原则做实质性扩展。
- PATCH：澄清、措辞修正、非语义调整。

批准日期 `Ratified` 只在首次批准时写入，后续修订保持不变。

**合规审查**：

- 每个 user story、PR 和 `/speckit-analyze` 都必须核对本宪章。
- 违反 MUST 条款的变更不得合并；复杂度必须说明理由。
- 范围扩张必须先修订 `ReleaseBaseline`，不得在实现中静默加入。
- 运行时开发指引见 `CLAUDE.md`；1.0 范围与验收以 `docs/ReleaseBaseline.md` 为准。

**Version**: 1.2.0 | **Ratified**: 2026-11-22 | **Last Amended**: 2026-11-22
