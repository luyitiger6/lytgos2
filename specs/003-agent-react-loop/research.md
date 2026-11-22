# Research: ReAct Agent Loop

## 1. Core 与 Provider 的依赖方向

- **Decision**: 在 `lytgos-core` 新增 `LlmGateway` 端口与 `AgentPrompt`/`AgentResponse`/`AgentToolCall`/`AgentMessage` 领域类型；`lytgos-provider` 的 `ProviderService` 实现 `LlmGateway`，内部完成与 Spring AI `Prompt`/`ChatResponse` 的互转。第 16 节的 `ProviderService.chat(String, AgentProfile, Prompt)` 保留为兼容入口。
- **Rationale**: 宪章要求 ReAct 自实现且 core 不依赖 Spring AI；端口把协议适配留在 provider，ReActLoop 只依赖 core 类型。
- **Alternatives considered**: 把 ReActLoop 放 provider（违反模块落位）；core 直接依赖 Spring AI（破坏依赖方向与模块规范）。

## 2. Session 与历史截断

- **Decision**: `Session` 从第 16 节的元数据 record 扩展为内存领域对象，保存 `AgentMessage` 列表；`AgentMessage` 包含角色、文本、工具名、工具调用 ID 和 assistant 的工具调用列表。历史按“最近 N 个完整轮次”截断：每轮从一条用户消息开始，包含其后的 assistant 与 tool 消息。
- **Rationale**: 一轮的工具调用链不能被截断拆散；否则模型会看到孤立的工具结果。第 18 节持久化时可直接序列化该消息模型。
- **Alternatives considered**: 只保存纯字符串（丢失工具调用关联）；按消息数截断（可能拆散轮次）；本节直接建 sessions 表（超出本节交付物）。

## 3. ToolExecutor 的工具查找与审计

- **Decision**: `ToolExecutor` 构造参数注入只读的 `Map<String, LytgTool>`，不新建工具注册表接口；执行前保留 Sandbox 接入位注释，第 24 节调用 `Sandbox.enforce`。成功和失败都通过 core 的 `ToolInvocationAuditor` 端口记录，storage 提供 `JpaToolInvocationAuditor`。
- **Rationale**: core 不能依赖 `lytgos-tool` 的 `ToolRegistry`；Map 注入是最小契约，且不抢占第 20 节的 ToolRegistry 交付物。审计端口延续第 16 节 `LlmCallAuditor` 的依赖倒置方式。
- **Alternatives considered**: 在 core 新建 ToolRegistry 接口（与第 20 节冲突）；ToolExecutor 放 tool 模块（违反模块落位）。

## 4. AgentService 的会话保存

- **Decision**: 本节 `AgentService` 接收 `Consumer<Session>` 保存钩子；成功跑完 ReAct 后调用，钩子异常向上抛；`finally` 清理 `AgentProfileContext`。第 18 节用 SessionManager 替换该钩子。
- **Rationale**: 第 18 节才交付 sessions 表与 SessionManager；保存钩子能验证“处理完成后持久化”的编排契约，又不提前造表。
- **Alternatives considered**: 本节提前实现 SessionManager（范围前移）；不保存（无法验证编排契约）。

## 5. ContextLoader 的工作区布局

- **Decision**: Agent 指令取 `AgentProfile.identity.prompt`；Bootstrap 文件从工作区根目录按 `bootstrap` 列表读取；Skill 元数据从工作区 `.lytgos/skills/<name>/SKILL.md` frontmatter 读取 name/description 与绝对路径。每次调用重新读取，不缓存；Skill 缺失抛 `IllegalArgumentException`；Bootstrap 缺失记录 WARN 并继续。
- **Rationale**: 与 TechnicalSolution §8.3 的 Bootstrap/Skill 元数据注入方向一致，同时适配第 16 节尚未引入 AGENT.md 的课程阶段。
- **Alternatives considered**: 读取不存在的 AGENT.md（超出本节范围）；缓存文件内容（与“修改立即生效”验收冲突）。

## 6. tool_invocations 表

- **Decision**: 新增 Flyway `V2__init.sql` 创建 `tool_invocations`：`id`、`session_id`、`tool_name`、`input_json`、`success`、`error_message`、`duration_ms`、`created_at`，并为 `session_id`、`created_at` 建索引；不修改 V1 的 `llm_calls`。
- **Rationale**: 审计 day one；V1 已在本分支完成，新增 V2 保持迁移历史稳定。
- **Alternatives considered**: 改 V1（已执行迁移不可变）；把工具审计塞进 `llm_calls`（语义混杂，违反表职责）。

## 7. Spring AI 转换 API（本地依赖已核实）

- **Decision**: 使用已核实存在的 Spring AI 1.1.2 API：
  - `AssistantMessage.builder().content(...).toolCalls(...).build()`
  - `AssistantMessage.ToolCall(id, type, name, arguments)`
  - `ToolResponseMessage.builder().responses(List.of(new ToolResponse(id, name, responseData))).build()`
  - `SystemMessage(String)`、`UserMessage(String)`
  - `ChatResponse.getResult().getOutput().getText()/getToolCalls()`
- **Rationale**: 避免计划阶段臆造方法；ProviderService 的转换实现以这些签名为准。
- **Alternatives considered**: 自定义消息序列化协议（不必要）；使用 Spring AI Agent 抽象（宪章禁止）。

## 8. 第 16 节命名与合并提交

- **Decision**: 第 16 节的 `Profile`/`ProfileLoader`/`ProfileRegistry` 已按业务命名统一为 `AgentProfile`/`AgentProfileLoader`/`AgentProfileRegistry`，包为 `io.lytgos.core.agent`；数据库 `llm_calls.profile_name` 与审计字段 `profileName` 保持不变，表示 AgentProfile 名称。第 16、17 节合并为一个以 ReAct 为主的 PR/提交。
- **Rationale**: 用户明确要求业务命名；保留列名避免无必要的迁移与历史审计破坏。
- **Alternatives considered**: 只改包名（类型名仍偏技术化）；把 `profile_name` 改名为 `agent_name`（扩大迁移范围，未获确认）。

## 9. 测试策略

- **Decision**: 使用课程 harness 五个测试类，补充 `ToolInvocationRepositoryTest`；全部单测默认运行，使用 Mockito 替身与临时目录，不访问网络。真实模型冒烟继续使用第 16 节 `ProviderSmokeIT`。
- **Rationale**: 本节所有依赖都能 mock；真实模型不进入默认构建。
- **Alternatives considered**: 集成测试真实工具（`http_get` 尚未在第 17 节交付）。

## 10. 真实 Demo 的课程阶段限制

- **Decision**: 课件“五、做完怎么验”中的 Demo 一需要 `http_get` 工具；该内置 Tool 属于第 20 节。第 17 节完成后，真实模型多轮工具 Demo 作为剩余人工项列出，等第 20 节工具交付后再端到端跑通；本节用工具替身钉死 ReAct 行为。
- **Rationale**: 不提前实现第 20 节内置 Tool；不把“未实际运行的真实 Demo”写成已通过。
- **Alternatives considered**: 本节临时实现 http_get（超出交付物）；跳过工具执行（无法验证 ReAct 核心）。
