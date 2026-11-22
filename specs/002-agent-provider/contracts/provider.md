# Contract: ProviderService

## 1. 类型与依赖

- `ProviderService` 位于 `lytgos-provider`。
- `ProviderService` 只依赖 `lytgos-core` 的 `Profile` 与 `LlmCallAuditor`；不依赖 `lytgos-storage`。
- 底层模型使用 Spring AI `ChatModel`；本节的具体实现为 OpenAI 兼容的 `OpenAiChatModel`，由显式 provider 名称到 `ChatModel` 的不可变 Map 提供。

## 2. 构造

```java
ProviderService(Map<String, ChatModel> providerModels, LlmCallAuditor auditor)
```

- `providerModels`：provider 名称到具体模型的显式映射；构造时复制为不可变 Map。
- `auditor`：审计端口；每次调用尝试都会调用一次 `record`。

## 3. 调用入口

```java
ChatResponse chat(String sessionId, Profile profile, Prompt prompt)
```

### 3.1 前置条件

- `profile` 非空，`profile.provider()`、`profile.provider().name()`、`profile.provider().model()` 非空。
- `prompt` 非空，`prompt.getInstructions()` 非空。
- `providerModels` 中存在 `profile.provider().name()`。

### 3.2 行为

1. 按 `profile.provider().name()` 从显式映射取出 `ChatModel`。
2. 取 `prompt.getOptions()` 中的工具回调；如果 options 不是 `ToolCallingChatOptions`，工具回调为空。
3. 构造本次调用的 `OpenAiChatOptions`：
   - `model` = `profile.provider().model()`；
   - `temperature` = `profile.provider().temperature()`（为空时 0.7）；
   - `toolCallbacks` = 第 2 步得到的回调；
   - `internalToolExecutionEnabled(false)`。
4. 用原 `Prompt` 的消息和本次 options 构造请求，调用 `ChatModel.call(...)`。
5. 成功时写一条成功审计记录并返回 `ChatResponse`；失败时写失败审计记录并上抛原异常。

### 3.3 后置条件

- 成功：返回的 `ChatResponse` 是模型原始响应；审计记录 `success=true`，token 字段来自 `ChatResponse.getMetadata().getUsage()`，不可用时为 `null`。
- 模型异常：审计记录 `success=false`，`errorMessage` 包含原始异常消息；原异常继续上抛。
- 审计写入异常：不吞掉；成功调用的审计异常直接上抛；失败调用场景下，审计异常作为原异常的 suppressed 异常保留，然后上抛原异常。
- 工具执行：本方法不会执行任何工具；工具回调的 `call` 方法必须拒绝执行。

## 4. 错误语义

| 场景 | 行为 |
|---|---|
| `providerModels` 中找不到 provider | 在模型调用前抛 `IllegalArgumentException`，消息包含 provider 名称；不产生 `llm_calls` 记录 |
| provider 名称存在但模型未构建（例如缺少 key） | 同“找不到 provider”，错误消息说明该 provider 不可用 |
| 模型调用超时/限流/返回错误 | 先写失败审计，再上抛原异常 |
| 审计写入失败 | 不静默吞掉；按 3.3 处理 |

## 5. ToolSchemaAdapter

```java
List<ToolCallback> toToolCallbacks(List<LytgTool> tools)
```

- 对每个 `LytgTool` 生成一个只含 `ToolDefinition` 的 `ToolCallback`：
  - `name()` → `ToolDefinition.name()`
  - `description()` → `ToolDefinition.description()`
  - `inputSchema()` → `ToolDefinition.inputSchema()`
- 生成的 `ToolCallback.call(String)` 直接抛 `UnsupportedOperationException`，消息说明“工具执行归 ReAct/ToolExecutor”。
- 空列表返回空列表；不返回 `null`。
- 该适配器是独立工具类，供调用方在构造 Spring AI `Prompt` 的 options 时使用。

## 6. 线程与外部依赖

- `ProviderService` 无共享可变状态；可被多个虚拟线程并发调用。
- 每次调用只发起一次 `ChatModel.call`；不做 fallback、hedge racing、circuit breaker 或额外重试。
- 模型构造使用 `NeverRetryPolicy` 的 `RetryTemplate`，避免 Spring AI 默认重试策略改变“一次调用”的语义。
- 不引入 Reactor、`CompletableFuture` 或自建线程池。
