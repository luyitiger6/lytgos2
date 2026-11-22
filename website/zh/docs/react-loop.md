# ReAct 循环

推理循环是自实现的。LytgOS2 不使用 Spring AI 的 Agent 抽象，并且**禁用** Spring AI 的自动 Tool 执行：Tool 调度只由 `ReActLoop` + `ToolExecutor` 控制。

![ReAct 循环](/images/react-loop.svg)

## 输入

循环由三种入口之一触发：

- `lytgos chat` 中的 CLI 消息
- `POST /api/v1/agents/{name}/invoke` 或 `POST /api/v1/sessions/{id}/messages` 的 HTTP 请求
- `AgentScheduler` 的 cron 消息

三者最终都进入 `AgentService.process(...)`。

## 一次迭代

```text
1. 把用户消息追加到 Session
2. PromptBuilder 组装 prompt：
   system prompt（identity + AGENT.md 正文 + bootstrap）
   + 当前日期时间
   + 核心记忆 + 召回的归档记忆
   + 最近对话历史
   + 可用 Tool 列表
3. ProviderService 调用配置的 ChatModel
4. 没有 Tool 调用 → 返回最终响应
5. 有 Tool 调用 → ToolExecutor 按顺序执行每次调用
   → Sandbox 校验
   → 执行
   → 写 tool_invocations
   → 把 Tool 结果追加到历史
6. 重复，直到最终响应或达到 max_iterations
```

## PromptBuilder

每次迭代都会重新组装 prompt，不做缓存：

| 部分 | 内容 |
| --- | --- |
| System prompt | `identity` + `AGENT.md` 正文 + Bootstrap 文件（`AGENTS.md`、`SOUL.md`、`USER.md`） |
| 日期时间 | 当前日期和时间，让定时任务知道“今天”是哪天 |
| Memory | 核心记忆（永不截断）与归档记忆 |
| 历史 | 最近对话轮次，按 `max_history_turns`（默认 20）截断 |
| Tool | 该 Agent 启用的 Tool 子集 |

## ToolExecutor

对每次 Tool 调用，`ToolExecutor`：

1. 在 `ToolRegistry` 中查找 `LytgTool`
2. 执行 `WhitelistSandbox` 策略校验
3. 执行 Tool
4. 写入 `tool_invocations`
5. 把结果返回给循环

同一轮模型响应中的多个 Tool 调用按顺序执行，不并行。

## 限制

| 配置 | 默认值 | 含义 |
| --- | --- | --- |
| `settings.max_iterations` | `10` | 每条消息的最大循环次数。达到上限后返回已有结果。 |
| `settings.max_history_turns` | `20` | 注入 prompt 的最近对话轮次。 |

1.0 不做历史总结或压缩；旧轮次直接丢弃。总结属于扩展能力。

## 审计

每次 LLM 调用写入 `llm_calls`，每次 Tool 调用写入 `tool_invocations`。审计数据从第一个可用版本起写入；审计查询接口属于扩展。

## 边界

- 只做同步执行；不做 SSE/WebSocket 流式响应。
- 不做异步或并行 Tool 执行。
- 循环内不做多 Agent 编排。
- 不使用 Spring AI 的自动 Tool 执行。
