# Contract: ReAct Engine

## 1. 依赖方向

- 本节所有引擎类型位于 `lytgos-core`，按领域分包：`agent`、`llm`、`session`、`tool`、`audit`。
- `lytgos-core` 不依赖 Spring AI、`lytgos-provider` 或 `lytgos-tool`。
- `lytgos-provider` 的 `ProviderService` 实现 core 的 `LlmGateway`。
- `lytgos-storage` 实现 core 的 `ToolInvocationAuditor`。
- `lytgos-boot` 负责装配引擎、Provider、审计与保存钩子。

## 2. LlmGateway

```java
AgentResponse chat(String sessionId, AgentProfile agentProfile, AgentPrompt prompt)
```

前置条件：

- `agentProfile` 非空，provider/model 有效。
- `prompt` 非空，systemPrompt 非空，messages 可为空。

行为：

- 每次调用只发起一次模型请求。
- 返回 `AgentResponse`，包含文本与工具调用列表。
- 模型调用的成功/失败审计仍由 Provider 层写入 `llm_calls`；本节不改变第 16 节审计语义。
- 失败时原异常上抛；ReActLoop 不吞异常。

## 3. AgentPrompt / AgentResponse

- `AgentPrompt(systemPrompt, messages, tools)`：systemPrompt 为空时使用空字符串；messages/tools 为不可变列表。
- `AgentResponse(text, toolCalls)`：text 为空时使用空字符串；toolCalls 为不可变列表；`hasToolCalls()` 表示是否需要执行工具。
- `AgentToolCall(id, name, arguments)`：name 非空；arguments 为空时使用 `{}`。
- `AgentMessage(role, content, toolName, toolCallId, toolCalls)`：角色为 SYSTEM/USER/ASSISTANT/TOOL；assistant 消息可携带工具调用；tool 消息携带工具名与调用 ID。

## 4. Session

```java
public Session(String sessionId, String profileName, String channel, String userId)
public void appendUser(String content)
public void appendAssistant(AgentResponse response)
public void appendToolResult(AgentToolCall call, ToolResult result)
public List<AgentMessage> messages()
public List<AgentMessage> recentMessages(int maxTurns)
public String sessionId()
public String profileName()
public String channel()
public String userId()
```

规则：

- 构造函数要求 sessionId/profileName 非空。
- `messages()` 返回不可变快照。
- `recentMessages(maxTurns)` 保留最近 N 个完整轮次；每轮从 USER 消息开始，包含其后的 ASSISTANT/TOOL 消息；`maxTurns <= 0` 返回空列表。
- 本节不负责 SQLite 持久化。

## 5. ContextLoader

```java
String buildContext(AgentProfile agentProfile)
```

规则：

- Agent 指令不在此方法读取；由 PromptBuilder 取 `identity.prompt`。
- 按 `bootstrap` 列表读取工作区根目录文件；文件缺失记录 WARN 并继续；读取失败记录 WARN 并继续。
- 按 `skills` 列表读取 `.lytgos/skills/<name>/SKILL.md` frontmatter 的 name/description，并附加 Skill 本地绝对路径；Skill 文件缺失抛 `IllegalArgumentException`。
- 每次调用重新读取文件，不缓存。
- 解析后的路径必须位于工作区根目录内，拒绝 `../` 逃逸。
- 返回文本按“Bootstrap 段落 → Skill 元数据段落”顺序拼接；没有内容时返回空字符串。

## 6. PromptBuilder

```java
PromptBuilder(Map<String, LytgTool> tools, ContextLoader contextLoader, Clock clock)
AgentPrompt build(Session session, AgentProfile agentProfile)
```

规则：

- system prompt 顺序：`identity.prompt` → `ContextLoader` 返回的 Bootstrap/Skill 文本 → 当前日期时间。
- system prompt 末尾必须包含 `当前时间: <ISO-8601>`。
- 历史取 `session.recentMessages(effectiveHistoryTurns(agentProfile))`；`maxHistoryTurns` 为空或非正数时使用 20。
- 工具取构造时注入的映射中、名称出现在 `agentProfile.tools()` 的工具；顺序按 `agentProfile.tools()` 的顺序；未知名称忽略。
- 长期记忆只留扩展点，本节不注入。
- 每次 build 重新调用 ContextLoader，不缓存。

## 7. ToolExecutor

```java
ToolExecutor(Map<String, LytgTool> tools, ToolInvocationAuditor auditor)
ToolResult execute(String sessionId, AgentToolCall call)
```

规则：

- 按 `call.name()` 查找工具；不存在时返回 `ToolResult.error("工具不存在: <name>")` 并写失败审计。
- 执行前保留 Sandbox 接入位；第 24 节接入 `Sandbox.enforce`。
- 工具执行成功但返回 `success=false`：结果原样返回，写失败审计。
- 工具执行抛运行时异常：包装为 `ToolResult.error(...)`，写失败审计，不向调用方抛工具异常。
- 工具返回 null：按失败处理。
- 每次执行尝试恰好写一条 `ToolInvocationRecord`。
- 审计写入异常不静默吞掉；向上抛出程序性错误。

## 8. ReActLoop

```java
ReActLoop(PromptBuilder promptBuilder, LlmGateway llmGateway, ToolExecutor toolExecutor)
String run(Session session, String userMessage, AgentProfile agentProfile)
```

规则：

1. 追加用户消息。
2. 有效最大轮数 = `settings.maxIterations`；为空或非正数时使用 10。
3. 每轮：
   - `PromptBuilder.build(session, agentProfile)`。
   - `LlmGateway.chat(session.sessionId(), agentProfile, prompt)`。
   - 追加 assistant 消息。
   - 无工具调用：返回 `AgentResponse.text()`。
   - 有工具调用：按顺序逐个 `ToolExecutor.execute(...)`，把每个结果追加到 Session；继续下一轮。
4. 达到最大轮数仍未收敛：返回固定文本 `达到最大轮数，已停止`。
5. 模型调用异常：不写 tool_invocations，原异常上抛；llm_calls 审计由 Provider 负责。
6. 不并行执行工具，不使用 Spring AI Agent 抽象，不自动执行工具。

## 9. AgentProfileContext

```java
static void set(AgentProfile agentProfile)
static AgentProfile current()
static void clear()
```

规则：

- 使用 ThreadLocal；未设置时 `current()` 返回 null。
- 由 AgentService 在请求入口 set，在 `finally` clear。
- 不跨请求复用；不在工具执行结束后提前 clear。

## 10. AgentService

```java
AgentService(AgentProfileRegistry registry, ReActLoop reActLoop, Consumer<Session> sessionSaver)
String process(Session session, String userMessage)
```

规则：

- 按 `session.profileName()` 查找 AgentProfile；不存在时在调用模型前抛 `IllegalArgumentException`。
- set AgentProfileContext。
- 调用 `ReActLoop.run`。
- 成功后调用 `sessionSaver.accept(session)`；保存钩子异常向上抛。
- `finally` 中 clear AgentProfileContext，成功和异常路径都执行。
- 本节不实现 SessionManager、CLI/REST、Session SQLite 持久化。

## 11. 错误语义

| 场景 | 行为 |
|---|---|
| AgentProfile 不存在 | 调用模型前抛 `IllegalArgumentException`，不清空 Session |
| 模型调用失败 | 原异常上抛；Provider 写失败 llm_calls |
| 工具不存在 | 返回失败 ToolResult，写失败 tool_invocations，循环继续 |
| 工具抛异常 | 返回失败 ToolResult，写失败 tool_invocations，循环继续 |
| 审计写入失败 | 向上抛出，不静默吞掉 |
| 保存钩子失败 | 向上抛出；finally 仍清理上下文 |
| 达到最大轮数 | 返回固定强制结束文本 |
