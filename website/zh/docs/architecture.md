# 架构

LytgOS2 是一个基于 Java 21 + Spring Boot 3.x 的单二进制 Agent OS。运行时内核——LLM Provider、ReAct 循环、记忆、Tool、REST 服务与 CLI——打包成一个 fat JAR。状态保存在 SQLite 和工作区文件系统中（默认 `.lytgos/`）。

LytgOS2 1.0 是**单机运行时内核**。架构为后续的分布式底座和跨节点 Agent 协作留出空间，但这些属于路线图阶段，不是 1.0 功能。

![LytgOS2 系统架构](/images/architecture.svg)

## 四层结构

```text
接入层     CLI Channel · Web Service · AgentScheduler
                 ↓
引擎层     AgentService → ReActLoop → PromptBuilder
                              ↓
                         ToolExecutor
                 ↓
能力层     ProviderService · MemoryService · ToolRegistry
                 ↓
基础层     AgentLoader · ContextLoader · Session 存储
            SQLite · ConfigLoader
```

三个触发源统一调用 `AgentService.process(...)`。引擎不感知消息来自 CLI、HTTP 还是 cron。

![LytgOS2 分层架构](/images/layer-diagram.svg)

## Agent 模型

一个目录 = 一个 Agent：

```text
.lytgos/agents/<name>/
├── AGENT.md          # frontmatter = Profile，正文 = 任务指令
├── skills/           # 指向 .lytgos/skills/ 的相对软连接
├── scripts/          # 可选，按需读取
└── REFERENCE.md      # 可选，按需读取
```

- `AGENT.md` 的 frontmatter 是运行时 Profile：`name`、`description`、`identity`、`provider`、`tools`、`mcp_servers`、`channels`、`bootstrap`、`schedules`、`settings`。
- Markdown 正文作为这个 Agent 的任务指令注入 system prompt。
- Skill 通过 Agent 本地 `skills/` 下的相对软连接绑定，不使用 frontmatter 的 `skills:` 字段。每轮只注入 Skill 的 name、description 和本地路径；正文、脚本和参考经 `read_file` / `shell` 按需读取。
- `ContextLoader` 不缓存。修改 `AGENT.md` 或重新绑定 Skill 后，下一轮立即生效。
- Agent 目录是上下文来源，不是 Tool，不注册到 `ToolRegistry`。

## 运行时流程

```text
消息
  → 追加到 Session
  → PromptBuilder：system prompt + 日期时间 + Memory + 历史 + Tool 列表
  → ProviderService.call()
  → 无 Tool 调用？→ 最终响应
  → 有 Tool 调用？→ ToolExecutor → Sandbox → 执行
      → 写 tool_invocations → 回填结果 → 继续循环
  → 达到 max_iterations → 结束
```

循环是自实现的。Spring AI 只用于 Provider 协议转换和 `@Tool` JSON Schema 生成；自动 Tool 执行已禁用。

## 模块

| 模块 | 职责 |
| --- | --- |
| `lytgos-core` | `LytgTool`、`Session`、`Profile`、`AgentLoader`、`ContextLoader`、`ReActLoop`、`PromptBuilder`、`ToolExecutor`、`AgentService`、`AgentScheduler` |
| `lytgos-provider` | `ProviderService`、Function Calling 适配、provider name → `ChatModel` 显式映射 |
| `lytgos-memory` | `MemoryService`、`LongTermMemoryStore`、`MarkdownMemoryStore`、记忆 Tool |
| `lytgos-tool` | 9 个内置 Tool、MCP Client、`ToolRegistry`、`Sandbox` / `WhitelistSandbox`、通知适配器 |
| `lytgos-channel-cli` | `CliChannel`、`lytgos chat` |
| `lytgos-web` | `WebServer`、6 个 Controller、统一 `ApiResponse`、OpenAPI、notify 与 scheduler 支撑端点 |
| `lytgos-storage` | SQLite、JPA Repository、Session/审计/定时任务/通知持久化、Flyway 迁移 |
| `lytgos-cli` | Picocli 主入口、12 个命令、`ConfigLoader` |
| `lytgos-boot` | Spring Boot 主类、自动配置、fat JAR 打包 |

## 存储与审计

`.lytgos/lytgos.db` 中的 6 张表：

| 表 | 用途 |
| --- | --- |
| `sessions` | Session 元数据与 JSON 对话历史 |
| `tool_invocations` | 每次 Tool 调用一行 |
| `llm_calls` | 每次 LLM 调用一行 |
| `scheduled_tasks` | 定时任务登记与运行状态 |
| `task_executions` | 执行历史 |
| `notify_channels` | 通知渠道全局注册表 |

`tool_invocations` 和 `llm_calls` 从第一个可用版本起写入。审计查询接口属于扩展；数据本身在 1.0 就会记录。

## 1.0 不包含什么

LytgOS2 1.0 不包含认证/RBAC、SSO、入站 IM 渠道、persona/知识库模块、向量记忆、Tool Policy、Provider fallback、SSE/WebSocket 流式响应、Web 仪表板、多租户或分布式调度。它是面向可信私有部署的单机运行时内核。阶段说明见[路线图](/zh/docs/roadmap)。
