# LytgOS2 1.0 基线

> 本文档是 **LytgOS2 1.0** 的唯一口径基线，用于裁决四份设计文档之间的命名、范围和数字冲突。它与设计文档冲突时，以本文为准。
>
> 生效日期：2026-11-21 · 状态：生效 · 适用版本：LytgOS2 1.0

---

## 1. 文档定位

### 1.1 这份文档解决什么

四份设计文档分别回答 Why、What、How 和 How to build，但它们经过多轮评审和全局改名后，出现了模块数、Demo 数、Tool 数、命令数等口径不一致。本文不再重复设计论证，只做三件事：

1. 固化 **产品身份与命名规范**
2. 固化 **1.0 交付范围**和明确不做的部分
3. 固化 **验收标准与冲突裁决**

### 1.2 与四份设计文档的关系

| 文档 | 在 1.0 中的角色 |
|---|---|
| `docs/TechnicalSolution.md` | 1.0 的机制与实现细节参考；与本文冲突处以本文为准 |
| `docs/DemandAnalysis.md` | 需求来源和验收下限；与本文冲突处以本文为准 |
| `docs/IndustryResearch.md` | 定位与背景，不参与 1.0 功能裁决 |
| `docs/AiProgrammingGuide.md` | 实施方法与 user story 顺序；其中的旧模块数/Demo 映射以本文为准 |

**权威顺序：ReleaseBaseline > TechnicalSolution > DemandAnalysis > IndustryResearch > AiProgrammingGuide。**

本文只覆盖 1.0。1.0 之后的能力继续以 TechnicalSolution 和后续基线为准。

### 1.3 变更规则

- 任何改变 1.0 范围的决策，先改本文，再改四份设计文档。
- 不允许在实施中静默偏离本文；发现冲突先停下来记录，再更新本文。
- 本文的数字和清单是验收依据，不是建议值。

---

## 2. 产品身份与命名

### 2.1 命名规范

| 项目 | 规范值 |
|---|---|
| 产品名 | **LytgOS2** |
| 发布版本 | **LytgOS2 1.0** |
| CLI 入口 | **`lytgos`**，子命令形式为 `lytgos <command>` |
| 工作区目录 | **`.lytgos/`** |
| SQLite 数据库 | **`.lytgos/lytgos.db`** |
| Agent 目录 | **`.lytgos/agents/<name>/`** |
| 全局 Skill 库 | **`.lytgos/skills/<name>/`** |
| Maven 模块前缀 | **`lytgos-*`** |
| Spring 配置前缀 | **`lytgos.*`** |
| Java 类名前缀 | **`Lytgos*`**（如 `LytgosCli`） |
| Java 包名 / groupId | 建议 `io.lytgos`，在 Maven 骨架创建时最终确认 |

### 2.2 命令与路径示例

```bash
lytgos init
lytgos status
lytgos chat --profile daily-weather
lytgos serve
lytgos gateway
lytgos profile create daily-weather
lytgos profile list
lytgos provider list
lytgos tool list
lytgos session list
```

```text
.lytgos/
├── agents/            # 每个子目录一个 Agent
├── skills/            # 全局 Skill 实体库
├── memory/
│   └── MEMORY.md      # 长期记忆
├── sessions/          # Session 数据
├── logs/              # 结构化日志
├── output/            # Agent 产出物
├── mcp_servers.yaml   # MCP 配置
├── AGENTS.md          # Bootstrap：项目级行为说明
├── SOUL.md            # Bootstrap：默认人格
├── USER.md            # Bootstrap：用户偏好
└── lytgos.db          # SQLite
```

### 2.3 命名红线

- 不再使用旧占位产品名。
- 不再使用产品名与版本号、命令、路径、模块名粘连的写法。
- 命令、路径、Maven 模块名、配置前缀一律使用小写 `lytgos`。
- Java 类名使用 `Lytgos` 前缀，不把产品名写成全大写变量式缩写。

---

## 3. 1.0 范围

### 3.1 一句话范围

1.0 交付 **Agent OS 的运行时内核**：让单个 Agent 跑得好，并让多个 Agent 在单实例里被配置、被触发、被审计地跑起来。企业治理层不是 1.0 的交付物。

### 3.2 1.0 必须交付

| 能力 | 1.0 交付内容 |
|---|---|
| 工作区与 Agent 定义 | `lytgos init` 创建 `.lytgos/`；`AGENT.md` frontmatter 派生 Profile；Bootstrap 三文件加载；Skill 软连接绑定与渐进式披露 |
| 对接 LLM | Provider 抽象；显式 provider name → `ChatModel` 映射；至少跑通 DeepSeek 和 Kimi；每次调用写 `llm_calls` |
| ReAct 循环 | 自实现 `ReActLoop`；`PromptBuilder` 四段式组装；`ToolExecutor`；默认最多 10 轮；同步执行 |
| Memory | `MemoryService` 统一门面；会话记忆落 SQLite；长期记忆用 `MEMORY.md` 的 Markdown 后端；核心/归档双分区 |
| Tool | 9 个内置 Tool；MCP Client（stdio）；`@Tool` Java Bean 扩展；`LytgTool` + `ToolRegistry`；`WhitelistSandbox` |
| 出站通知 | `notify` Tool；`notify_channels` 注册表；Webhook 适配器；复用 HTTP 域名白名单 |
| Channel | CLI Channel；`lytgos chat` 交互对话 |
| Web Service | 10 个核心 REST 端点；`ApiResponse` 统一信封；OpenAPI 文档；notify-channels 与 scheduler v2 支撑端点 |
| 定时任务 | `AgentScheduler` 第三触发源；`schedules` frontmatter；状态表；进程内防重叠；最小运行管理端点 |
| 持久化与审计 | SQLite 6 张表；`tool_invocations` 与 `llm_calls` day one 写入；Session 跨重启恢复 |
| 工程化 | Picocli 12 个命令；三种运行模式；配置与密钥加载；结构化日志；项目主页 |

### 3.3 1.0 明确不做

以下能力属于扩展阶段或社区共建，1.0 不实现、不预留半成品入口：

- 企业 IM 入站渠道：飞书、企业微信、钉钉、Slack
- Persona 人格库、Knowledge 知识库、Agent 上传/生命周期 API、WorkspaceWatcher
- SQLite/Mem0 Memory 后端、向量检索、语义召回、自动记忆抽取、情景记忆、Memory Wiki
- Tool Policy、Tool LRU 加载、MCP Server 暴露
- Provider fallback、hedge racing、自适应路由
- 容器/microVM Sandbox、多租户隔离
- 认证、RBAC、SSO、API Key、CORS 白名单
- SSE/WebSocket 流式响应、异步 Tool 调用
- Webhook 触发入口
- Web 仪表板、Prometheus 指标、Grafana Dashboard
- 审计查询接口、SIEM 导出、合规报表、敏感信息脱敏、trace ID 串联
- Email SMTP 通知适配器
- AgentScheduler 的运行时增删改 cron 定义（1.0 只支持状态管理与手动执行）
- 集群部署、分布式调度、Redis/PostgreSQL 外置状态
- GraalVM Native Image
- SDK、Skills Marketplace、移动端、Voice Channel、边缘部署

### 3.4 范围边界示意

```text
1.0 交付（运行时内核）
  CLI / REST / Scheduler
        ↓
  AgentService → ReActLoop → Provider / Memory / Tool
        ↓
  SQLite + 文件系统 + 审计

扩展阶段（治理与企业集成）
  IM Channel / Persona / Knowledge / SSO / 多租户 / 完整审计
  容器 Sandbox / 向量 Memory / Web 仪表板 / 集群
```

---

## 4. 模块与架构

### 4.1 1.0 的九个模块

| 模块 | 职责 |
|---|---|
| `lytgos-core` | 核心契约与引擎：`LytgTool`、`Session`、`Profile`、`AgentLoader`、`ContextLoader`、`ReActLoop`、`PromptBuilder`、`ToolExecutor`、`AgentService`、`AgentScheduler` |
| `lytgos-provider` | `ProviderService`、Function Calling 适配、provider name → `ChatModel` 显式映射 |
| `lytgos-memory` | `MemoryService` 门面、`LongTermMemoryStore` 契约、`MarkdownMemoryStore`、`MemoryTools` |
| `lytgos-tool` | 9 个内置 Tool、MCP Client、`ToolRegistry`、`Sandbox` + `WhitelistSandbox`、`NotifyChannelAdapter` + `WebhookNotifyAdapter` |
| `lytgos-channel-cli` | `CliChannel`、`lytgos chat` 实现 |
| `lytgos-web` | `WebServer`、6 个 Controller、`GlobalExceptionHandler`、OpenAPI、notify-channels 与 scheduler v2 端点 |
| `lytgos-storage` | SQLite、JPA Repository、Session/审计/定时任务/通知渠道持久化、Flyway 迁移 |
| `lytgos-cli` | Picocli 主入口、12 个子命令、`ConfigLoader` |
| `lytgos-boot` | Spring Boot 主类、自动配置、依赖聚合、fat JAR 打包 |

### 4.2 扩展模块（不在 1.0）

`lytgos-persona`、`lytgos-knowledge`、`lytgos-channel-feishu`、`lytgos-channel-wecom`、`lytgos-channel-dingtalk` 保留为扩展阶段模块设计，1.0 不创建这些 Maven 模块。

### 4.3 分层与主链路

```text
接入层     CLI Channel / Web Service / AgentScheduler
           ↓
引擎层     AgentService → ReActLoop
                        → PromptBuilder
                        → ToolExecutor
           ↓
能力层     ProviderService / MemoryService / ToolRegistry
           ↓
基础层     AgentLoader / ContextLoader / Session 存储 / SQLite / ConfigLoader
```

三个触发源统一调用 `AgentService.process`，引擎不感知消息来自 CLI、HTTP 还是 cron。`AgentService` 负责设置 `ProfileContext`、执行 `ReActLoop`、持久化 Session，并在 `finally` 清理上下文。

### 4.4 Agent 定义模型

```text
.lytgos/agents/<name>/
├── AGENT.md          # frontmatter = Profile，正文 = 任务指令
├── skills/           # 指向 .lytgos/skills/<name>/ 的相对软连接
├── scripts/          # 可选脚本
└── REFERENCE.md      # 可选参考资料
```

- 一个目录就是一个 Agent；Agent 不是写代码写出来的。
- frontmatter 字段：`name`、`description`、`identity`、`provider`、`tools`、`mcp_servers`、`channels`、`bootstrap`、`schedules`、`settings`。
- Skill 绑定不写入 frontmatter；由 Agent 本地 `skills/` 软连接表达，软连接集合是唯一绑定真相源。
- 每轮只注入 `AGENT.md` 正文、Bootstrap、已绑定 Skill 的 name/description/本地路径；Skill 正文、脚本、参考经 `read_file`/`shell` 按需读取。
- `ContextLoader` 不缓存，修改 `AGENT.md` 或重新绑定 Skill 后下一轮立即生效。
- Agent 目录是上下文来源，不是 Tool；不进入 `ToolRegistry`。

---

## 5. 核心机制

### 5.1 Provider

- 基于 Spring AI Alibaba 的 `ChatModel`，在其上做 `ProviderService` 薄封装。
- 维护显式的 **provider name → `ChatModel`** 映射，禁止按类型扫描容器中的全部 `ChatModel`。
- 1.0 至少支持 DeepSeek 和 Kimi；其他 Provider 可作为配置扩展。
- 1.0 不做 fallback、hedge racing、circuit breaker。
- 每次调用记录 provider、model、prompt/completion/total tokens、耗时，写入 `llm_calls`。

### 5.2 ReAct 循环

```text
用户消息 / 定时消息 / HTTP 请求
  → 追加到 Session
  → PromptBuilder 组装（system prompt + 日期时间 + Memory + 历史 + Tool 列表）
  → ProviderService 调 LLM
  → 无 Tool 调用：返回最终响应
  → 有 Tool 调用：ToolExecutor 执行并回填 → 继续循环
  → 达到 max_iterations（默认 10）强制结束
```

- 自实现循环，不依赖 Spring AI 的 Agent 抽象。
- **禁用 Spring AI 的自动 tool 执行**；Tool 调度只由 `ReActLoop` + `ToolExecutor` 控制。
- 同一轮多个 Tool 调用按顺序执行，不做并行。
- 历史按 `max_history_turns`（默认 20）截断；扩展阶段再做总结压缩。
- 每次 LLM 调用与 Tool 调用都写审计表。

### 5.3 Memory

- 对外只有 `MemoryService` 一个门面；会话记忆委托 `SessionManager`，长期记忆委托 `LongTermMemoryStore`。
- 1.0 的唯一长期记忆后端是 `MarkdownMemoryStore`，文件为 `.lytgos/memory/MEMORY.md`。
- 文件分两个一级分区：`## 核心记忆` 和 `## 归档记忆`。
- 核心记忆区全量注入 system prompt，永不截断，不参与检索；归档记忆区可截断、可关键词检索。
- Agent 通过 `save_memory(content, scope)` 显式选择写核心区还是归档区；通过 `recall_memory(query)` 检索归档区。
- 1.0 不做自动抽取、向量检索、语义召回、情景记忆、Memory Wiki。
- `LongTermMemoryStore` 契约保留，SQLite/Mem0 后端在扩展阶段实现。
- `USER.md` 是用户手写的初始设定，`MEMORY.md` 是 Agent 用 `save_memory` 写入的成长记录；两者都进 system prompt，但来源和写入方不同。

### 5.4 Tool 与 Sandbox

1.0 内置 9 个 Tool：

| Tool | 说明 |
|---|---|
| `read_file` | 读取文件，受路径白名单限制 |
| `write_file` | 写入文件，受路径白名单限制 |
| `list_dir` | 列目录，受路径白名单限制 |
| `shell` | 执行白名单内的可执行文件与参数数组，带超时，不经 Shell 解释 |
| `http_get` | 发起 HTTP GET，受域名白名单限制 |
| `http_post` | 发起 HTTP POST，受域名白名单限制 |
| `save_memory` | 写入长期记忆，支持 `scope` |
| `recall_memory` | 按关键词检索归档记忆 |
| `notify` | 按渠道名推送消息 |

- 所有 Tool 统一包装为 `LytgTool`，注册到 `ToolRegistry`；ReAct 循环不感知 Tool 来源。
- Plugin Tool 三档：`AGENT.md` + 现成 MCP（零代码，主推）、自写 MCP server（轻代码）、`@Tool` Java Bean（重代码）。
- MCP 1.0 只做 stdio transport；SSE 放扩展。
- `Sandbox` 接口只表达“在受控环境执行一个动作”，1.0 唯一实现 `WhitelistSandbox`：
  - `file.allowed_paths`
  - `shell.allowed_commands`
  - `http.allowed_domains`
- 路径校验必须处理 `../` 路径穿越；Shell 只按可执行文件白名单精确匹配；HTTP 解析 host 后做通配符匹配。
- 任意校验失败抛 `SandboxViolationException`，走既有 Tool 失败审计路径。
- 不使用 Java `SecurityManager`；完整容器/microVM 隔离放扩展。

### 5.5 通知推送

- `notify(content, channel)` 是内置 Tool；`channel` 是全局注册名，不是 URL。
- `notify_channels` 表保存 `name`、`type`、`url`、`description`、`config`；通过 `/api/v1/notify-channels` 做最小 CRUD。
- 1.0 唯一适配器是 `WebhookNotifyAdapter`，按企业微信/飞书/钉钉群机器人 webhook 约定的 JSON 发送。
- 发送前同样过 `Sandbox.enforce(HTTP_REQUEST, url)`，复用 `http.allowed_domains`。
- 通知调用与普通 Tool 一样写 `tool_invocations`。
- Email SMTP 适配器、官方富文本卡片消息放扩展。

### 5.6 定时任务

- `AgentScheduler` 基于 `ThreadPoolTaskScheduler` + `CronTrigger` 动态注册。
- 任务定义只来自 `AGENT.md` frontmatter 的 `schedules` 字段；1.0 不支持通过 API 增删改 cron 定义。
- 每个任务按任务 id 持有进程内 `ReentrantLock`；上一次未结束时跳过本次触发，不排队、不并行。
- 这是单实例锁，不是分布式锁。
- 任务失败只记录日志，不能导致调度器崩溃；失败调用仍走完整审计链路。
- 钟推 Session 的 channel 和 user 都固定为 `scheduler`；同一 Profile 的历次定时触发复用同一 Session。
- 运行状态落 `scheduled_tasks`，执行历史落 `task_executions`；重启不丢。
- 1.0 提供最小 v2 管理端点：任务列表、执行历史、立即执行、启停。
- 修改 `schedules` 后需要重启或触发重新加载才生效。

### 5.7 Web Service 与 CLI

- `lytgos serve` 启动 Spring MVC + virtual thread，默认端口 8989。
- 6 个 Controller：Session、Agent、Profile、Memory、Tool、System。
- 统一响应信封 `ApiResponse`：`code`、`message`、`data`、`timestamp`；成功和错误共用。
- OpenAPI 通过 springdoc 暴露在 `/swagger-ui`。
- 1.0 无认证、无 RBAC、CORS 全开；假设内网部署。
- 请求限制：单条消息最大 32KB；Session 历史最多返回最近 100 条；Agent 调用最长 60 秒，超时返回 504。
- CLI 使用 Picocli；不需要 Spring 上下文的命令（如 `init`、`profile list`）直接走文件操作，需要 LLM 的命令才启动 Spring。

### 5.8 配置与密钥

- 敏感配置通过环境变量注入或本地独立配置文件加载。
- `AGENT.md` / `application.yaml` 中使用 `${ENV_VAR}` 占位，加载时解析。
- 加载时校验必填项和格式，缺失或非法时给出明确报错。
- 1.0 不做加密存储、密钥轮转、KMS/Vault 对接。

---

## 6. 数据与接口

### 6.1 SQLite 表（1.0 共 6 张）

| 表 | 用途 |
|---|---|
| `sessions` | Session 元数据 + JSON 序列化对话历史 |
| `tool_invocations` | 每次 Tool 调用记录（参数、结果、成功、错误、耗时） |
| `llm_calls` | 每次 LLM 调用记录（provider、model、token、耗时） |
| `scheduled_tasks` | 定时任务登记信息与运行状态 |
| `task_executions` | 每次定时任务执行历史 |
| `notify_channels` | 通知渠道全局注册表 |

- `tool_invocations` 和 `llm_calls` 从第一个可用版本起写入，查询接口可以后补。
- `memory_entries` 只在启用 SQLite Memory 后端时创建，不属于 1.0。
- 表结构由 Flyway 管理，迁移目录按数据库厂商分轨；SQLite 是默认零配置档。

### 6.2 CLI 命令（12 个）

| 类别 | 命令 |
|---|---|
| 启动与状态 | `lytgos init`、`lytgos status`、`lytgos chat`、`lytgos serve`、`lytgos gateway` |
| Profile 管理 | `lytgos profile list`、`lytgos profile create`、`lytgos profile show`、`lytgos profile delete` |
| 查询 | `lytgos provider list`、`lytgos tool list`、`lytgos session list` |

`lytgos agent import` 属于扩展阶段命令，不计入 12 个。

### 6.3 REST 端点

**核心 10 个：**

| 类别 | 端点 |
|---|---|
| 会话管理 | `POST /api/v1/sessions` |
| 会话管理 | `POST /api/v1/sessions/{id}/messages` |
| 会话管理 | `GET /api/v1/sessions/{id}` |
| 会话管理 | `DELETE /api/v1/sessions/{id}` |
| Agent 调用 | `POST /api/v1/agents/{name}/invoke` |
| Profile 信息 | `GET /api/v1/profiles` |
| Memory 信息 | `GET /api/v1/memory` |
| Tool 信息 | `GET /api/v1/tools` |
| 系统状态 | `GET /api/v1/health` |
| 系统状态 | `GET /api/v1/info` |

**1.0 支撑端点：**

| 类别 | 端点 |
|---|---|
| 通知渠道 | `GET/POST /api/v1/notify-channels` |
| 通知渠道 | `PUT/DELETE /api/v1/notify-channels/{id}` |
| 定时任务 | `GET /api/v2/schedules` |
| 定时任务 | `GET /api/v2/schedules/{scheduleId}/executions` |
| 定时任务 | `POST /api/v2/schedules/{scheduleId}/run` |
| 定时任务 | `PUT /api/v2/schedules/{scheduleId}` |

按 key 精确定位的 `POST /api/v2/agents/{profileName}/schedules/{key}/run` 放扩展阶段。

---

## 7. 验收基线

### 7.1 功能验收

- [ ] `lytgos init` 幂等创建工作区
- [ ] `AGENT.md` frontmatter 正确派生 Profile；多 Agent 并存
- [ ] Provider 至少跑通 DeepSeek 和 Kimi；显式映射生效
- [ ] ReAct 循环支持多轮 Tool 调用、消息累积、最大迭代终止
- [ ] `save_memory` 写入、`recall_memory` 关键词检索、启动时注入长期记忆
- [ ] 9 个内置 Tool 全部可用，Sandbox 白名单生效
- [ ] MCP stdio 连接外部 server；`@Tool` Java Bean 示例可用
- [ ] CLI Channel 与 12 个 CLI 命令完整
- [ ] AgentScheduler 到点触发；状态表与执行历史可查；可手动执行
- [ ] 10 个核心 REST 端点 + notify-channels/scheduler v2 支撑端点跑通
- [ ] Session 持久化到 SQLite，跨重启恢复
- [ ] `tool_invocations` 和 `llm_calls` 每次调用都有记录
- [ ] 配置与密钥从环境变量加载，缺失时清晰报错
- [ ] 项目主页可访问

### 7.2 非功能指标

| 指标 | 目标 |
|---|---|
| 单节点 Agent 数 | ≥ 10 |
| 单节点并发 Session 数 | ≥ 100 |
| Session 创建 P99 延迟 | ≤ 200ms |
| 内部转发开销 | ≤ 50ms |
| Agent 调用超时 | 60 秒，超时返回 504 |

### 7.3 验收 Demo

| Demo | 是否阻塞发布 | 验证能力 | 通过标准 |
|---|---|---|---|
| Demo 一：每日天气 | ✅ 硬性 | LLM + ReAct + HTTP Tool + notify + AgentScheduler + Session 查询 | 到点自动运行；查天气和推送各一次 HTTP 调用，均过域名白名单并写 `tool_invocations`；`GET /api/v1/sessions/{id}` 可查完整对话；可通过 CLI 或 invoke 手动补跑 |
| Demo 二：每日科技日报 | ✅ 硬性 | Agent 目录 + 公共 Skill 软连接 + MCP + Memory + notify + AgentScheduler | 业务方不写 Java 代码；prompt 只出现 Skill 元数据；正文经 `read_file` 按需读取；日报体现记忆偏好 |
| Demo 三：每日 GitHub 日报 | ⏳ 可选 | Agent 目录 + `scripts/` + 专用 Tool/MCP | 脚本产出进入上下文、脚本代码不进入；不作为 1.0 发布阻塞项 |

Demo 一和 Demo 二必须同时支持“钟推”（Scheduler 自动触发）和“人推”（`lytgos chat` 或 `POST /api/v1/agents/{name}/invoke`），复用同一条 `AgentService` 链路。Demo 三若纳入验收，同样走这条链路。

### 7.4 完成定义

1.0 完成必须同时满足：

- `mvn clean package` 成功，生成可执行 fat JAR
- 功能验收清单全部通过
- Demo 一和 Demo 二每日自动运行通过
- Session、审计、定时任务数据在重启后可恢复
- 项目主页、部署说明、CLI 帮助可用
- 没有把扩展阶段能力以半成品形式塞进 1.0

---

## 8. 冲突裁决记录

| # | 主题 | 文档现状 | 1.0 裁决 | 理由 |
|---|---|---|---|---|
| 1 | 模块数 | 9 个 vs 14 个 | **1.0 用 9 个模块**；persona、knowledge、三个 IM 渠道共 5 个模块放扩展 | 需求文档核心阶段是 9 个；14 个是全量演进蓝图 |
| 2 | 验收 Demo | 2 个 / 3 个 / 5 个 | **2 个硬性 + 1 个可选**；5 个是旧版映射，作废 | 前两个覆盖全部五能力 + 定时任务；GitHub 日报验证脚本路径，可延后 |
| 3 | 内置 Tool | 5 / 7 / 9 | **9 个**，以 TechnicalSolution 为准 | `notify` 是定时 Demo 的硬依赖；文件 3 + Shell 1 + HTTP 2 + Memory 2 + Notify 1 |
| 4 | CLI 命令 | 12 / 13 | **12 个**；`agent import` 放扩展 | 12 个覆盖核心阶段；agent import 依赖扩展的导入链路 |
| 5 | Memory 后端 | Markdown / 三档一次交付 | **1.0 只交付 Markdown 后端**；SQLite/Mem0 实现 `LongTermMemoryStore` 接口，放扩展 | 保持单二进制和 12 小时核心范围；接口先行，后续换后端不改上层 |
| 6 | Skill 绑定 | frontmatter 引用 / 软连接 | **全局 Skill 库 + Agent 本地相对软连接 + 渐进式披露**；不再使用 frontmatter `skills:` | 技术方案最新修订；软连接集合是唯一绑定真相源 |
| 7 | 定时任务范围 | 仅触发 / 含 v2 管理 | **触发 + 状态表 + 最小 v2 管理**；cron 定义仍只来自 `AGENT.md` | Demo 需要可查、可补跑；定义热更新依赖扩展的 Agent 生命周期 API |
| 8 | Persona / Knowledge / IM | 需求文档放扩展；技术方案有模块设计 | **不属于 1.0** | 保持运行时内核范围；设计保留为扩展蓝图 |
| 9 | 版本表述 | 存在粘连写法 | **统一 `LytgOS2 1.0`** | 产品名与版本分开，避免误读 |
| 10 | JDK | 文档要求 JDK 21+；项目与 IDEA 已统一到 JDK 21 | **1.0 以 JDK 21 为编译/运行基线** | POM、IDEA project SDK、Maven 构建保持一致 |
| 11 | 包名 / groupId | 文档未定 | 建议 `io.lytgos`，Maven 骨架创建时确认 | 不阻塞其余裁决 |
| 12 | 主页 / OpenAPI | 需求有主页；技术方案有 OpenAPI | **都属于 1.0** | 主页是发布门面；OpenAPI 是 Web Service 的一部分 |

---

## 9. 实施顺序

### 9.1 User Story 依赖

```text
US-1 Provider → US-2 ReAct → (US-3 Memory ∥ US-4 Tool) → US-5 Web Service
```

每个 user story 完成后跑 `/speckit.analyze`，检查 constitution、spec、plan、代码一致。

### 9.2 四周节奏

| 周次 | 投入 | 主线 | 可演示成果 |
|---|---|---|---|
| 第一周 | 3 小时 | Provider + ReAct | `lytgos chat` 多轮对话，Agent 调 HTTP Tool 完成天气查询 |
| 第二周 | 3 小时 | Memory + Tool | Agent 记住偏好；文件/Shell/MCP Tool 可用 |
| 第三周 | 3 小时 | Web Service | 10 个核心端点跑通 |
| 第四周 | 3 小时 | 多 Agent + 工程收尾 | Session 跨重启恢复；AgentScheduler 自动触发；主页可访问；Demo 一/二通过 |

### 9.3 准备阶段产物

- `.specify/memory/constitution.md`：7 条非协商原则
- `spec.md`：5 个 user story
- `plan.md`：9 个模块 + 技术决策
- 本文 `docs/ReleaseBaseline.md`：1.0 范围与裁决

---

## 10. 维护

### 10.1 变更流程

1. 提出变更：说明影响的模块、Demo、验收项。
2. 更新本文：先改范围、数字或裁决表。
3. 同步文档：再改 `docs/` 下相关设计文档。
4. 记录版本：在下方变更记录追加一行。

### 10.2 变更记录

| 日期 | 变更 |
|---|---|
| 2026-11-21 | 初始基线：固化命名、1.0 范围、9 模块、9 Tool、12 CLI、6 表、2 硬性 Demo + 1 可选 Demo、冲突裁决 |
