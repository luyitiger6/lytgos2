# Feature Specification: LytgOS2 1.0 运行时内核

**Feature Branch**: `N/A（当前直接在 main 上实施；未配置 before_specify hook）`

**Created**: 2026-11-22

**Status**: Draft

**Input**: User description: "LytgOS2 1.0 运行时内核：用户通过目录定义 Agent，系统提供 Provider、ReAct、Memory、Tool、Web Service 五大核心能力。需求来源：`docs/ReleaseBaseline.md` 与 `docs/DemandAnalysis.md`。"

## Clarifications

### Session 2026-11-22

- Q: 在 1.0 没有 IM 入站渠道的情况下，`lytgos gateway` 启动后应当承担什么职责？ → A: 无 HTTP 的常驻守护进程：只启动 `AgentScheduler` 和未来配置的常驻 Channel，不暴露 REST 端口。
- Q: 当一个 Session 通过 `DELETE /api/v1/sessions/{id}` 归档后，它应当仍然可以继续对话，还是只能查看历史？ → A: 归档即只读：仍可按 ID 查询历史，但拒绝新消息且默认不出现在 `session list`；继续对话需新建 Session。
- Q: 当一次 Tool 调用因可重试错误而重试时，`tool_invocations` 应当为每次实际执行尝试各写一行，还是每次模型请求只写一行并附带重试信息？ → A: 每次模型请求一行：记录重试次数、各次尝试的错误摘要与最终结果；SC-003 按模型请求计数。
- Q: `lytgos chat --profile <name>` 每次启动时，应当自动恢复该 Agent 最近一个 active Session，还是每次新建 Session 并允许用户显式恢复某个 Session？ → A: 自动恢复最近一个 active Session，没有则新建；新增 `chat --new` 显式开新会话。
- Q: `POST /api/v1/agents/{name}/invoke` 所说的“无状态调用”，是否应当创建并持久化一个 Session？ → A: 不创建、不持久化 Session；结果直接返回，审计记录允许 `session_id` 为空。

## User Scenarios & Testing *(mandatory)*

本规格覆盖 LytgOS2 1.0 的运行时内核，按 5 个 user story 组织。US-1 与 US-2 是主链路；US-3 与 US-4 可在 US-2 之后并行实施；US-5 依赖前四个 story。每个 story 都可以独立演示和验证。

### User Story 1 - 配置 Provider 并完成多轮对话 (Priority: P1)

企业开发者希望用自己的 LLM 账号配置一个 Agent，并通过命令行与它进行多轮对话。Agent 使用哪个 Provider 和模型由 Agent 配置决定，切换 Provider 不需要改代码；每次 LLM 调用都要留下可审计的记录。

**Why this priority**: 没有可用的 LLM 调用，Agent OS 的其余能力都无法演示。这是最小可行产品（MVP）的第一条垂直切片。

**Independent Test**: 创建一个工作区和 Agent，配置一个 Provider，执行对话并得到回复；再切换到另一个 Provider，确认模型调用和审计记录随之变化。该测试不依赖 Tool、Memory 或 Web Service。

**Acceptance Scenarios**:

1. **Given** 工作区已初始化且 Agent 配置了可用的 Provider，**When** 用户发起一轮对话，**Then** 用户收到模型回复，且该轮对话被保存到 Session 历史中。
2. **Given** 同一个 Agent 配置了两个 Provider，**When** 用户选择其中一个 Provider 发起对话，**Then** 实际调用的是所选 Provider 和模型，审计记录中的 Provider、模型、token 用量和耗时与实际调用一致。
3. **Given** Provider 的密钥缺失或无效，**When** 用户发起对话，**Then** 系统返回清晰可操作的错误，不泄露密钥，进程保持可用。
4. **Given** 用户配置了不支持的 Provider 名称或模型，**When** 对话启动，**Then** 系统在真正调用前给出明确错误，而不是静默回退或崩溃。

---

### User Story 2 - Agent 自主调用 Tool 完成任务 (Priority: P2)

用户提出一个需要外部信息的任务，Agent 能自主判断是否需要调用 Tool，按需执行一次或多次 Tool 调用，把结果吸收进上下文，直到给出最终答复。循环次数有上限，工具出错时不会无限重试。

**Why this priority**: ReAct 循环是 Agent 与普通聊天机器人的分水岭；它依赖 US-1，但也是 Memory、Tool 和 Demo 链路的前置条件。

**Independent Test**: 使用一个带确定性测试 Tool 的 Agent，分别提出只需要直接回答、需要一次 Tool 调用、需要连续三次 Tool 调用的问题；验证调用顺序、结果回填和最大迭代终止行为。

**Acceptance Scenarios**:

1. **Given** 问题不需要任何 Tool，**When** 用户发消息，**Then** Agent 直接给出最终回答，且不产生 Tool 调用记录。
2. **Given** 问题需要一次 Tool 调用，**When** 模型请求调用 Tool，**Then** 系统只对该模型请求发起一次逻辑 Tool 调用（可重试错误的重试属于同一次调用），把最终结果回填给模型，并返回最终回答。
3. **Given** 问题需要多轮 Tool 调用，**When** 模型连续请求 Tool，**Then** 同一轮内的 Tool 按顺序执行，消息历史按顺序累积，最终回答基于全部结果。
4. **Given** Agent 达到配置的最大迭代次数（默认 10），**When** 循环仍未得到最终回答，**Then** 系统强制结束循环，返回可识别的终止原因，并保留完整的调用审计。
5. **Given** Tool 执行失败，**When** 错误回填给模型，**Then** 错误包含可诊断信息和是否可重试的标识；可重试错误按指数退避执行首次尝试加最多三次重试，最终结果与各次尝试摘要写入同一条审计记录。

---

### User Story 3 - 跨会话记忆与重启恢复 (Priority: P3)

用户希望 Agent 记得之前的对话和长期偏好。当前对话在进程重启后可以继续；用户明确要求记住的信息会进入长期记忆，并在后续对话中自动生效；历史事实可以按关键词找回，但核心偏好永远不会被截断。

**Why this priority**: 记忆是 Agent OS 区别于一次性聊天工具的核心体验；它依赖 US-2 的对话链路，但可以独立于 Tool 扩展和 Web Service 验证。与 US-4 并行实施。

**Independent Test**: 进行一段对话，重启进程后继续同一 Session；让 Agent 保存一条核心偏好，新开 Session 确认偏好自动出现；再保存一条归档记忆，用关键词召回并确认命中。

**Acceptance Scenarios**:

1. **Given** 一个已有历史消息的 Session，**When** 进程重启后用户通过 `lytgos chat` 继续该 Session，**Then** CLI 自动恢复该 Agent 最近一个 active Session，之前的对话历史完整可读，后续对话可以基于该历史继续。
2. **Given** 用户表达了一条需要长期记住的偏好，**When** Agent 将其保存为核心记忆，**Then** 新 Session 的 system prompt 中包含该偏好，且核心记忆永远不会被截断。
3. **Given** 长期记忆中已有归档内容，**When** Agent 按关键词召回，**Then** 返回相关归档条目；没有匹配时返回明确的空结果，不报错。
4. **Given** 一段超过历史窗口的对话，**When** 系统组装 prompt，**Then** 只保留最近 N 轮（默认 20），核心长期记忆不受影响。
5. **Given** 长期记忆文件不存在或为空，**When** Agent 启动或读写记忆，**Then** 系统按空记忆处理并保持可用，不因缺少文件而失败。

---

### User Story 4 - 使用内置与插件 Tool，并受安全边界保护 (Priority: P3)

用户希望 Agent 能读写文件、执行受控命令、访问受控网络、保存记忆和发送通知；业务团队还能通过复用 MCP Server 或编写 Java Tool 扩展能力。所有文件、Shell 和 HTTP 访问都必须经过白名单校验，越权行为被拒绝并留下审计。

**Why this priority**: Tool 是 Agent 从“会说”到“会做”的关键；US-4 与 US-3 可并行，二者都依赖 US-2，但互不依赖。

**Independent Test**: 在受控工作区中逐一调用 9 个内置 Tool；配置一个 MCP Server 和一个 Java Tool；再构造路径穿越、非白名单命令和非白名单域名请求，确认全部被拒绝并写入失败审计。

**Acceptance Scenarios**:

1. **Given** 一个内置 Tool 已注册且 Agent 有权限使用，**When** 模型请求调用，**Then** Tool 执行成功并返回结果，调用记录包含名称、入参、结果、状态和耗时。
2. **Given** 一个 MCP Server 已配置并通过 stdio 启动，**When** Agent 调用其提供的 Tool，**Then** 调用结果正确回填，来源对 ReAct 循环透明。
3. **Given** 一个通过 Java 注解注册的 Tool，**When** Agent 调用，**Then** 结果正确返回，且与内置 Tool 使用同一注册和调用路径。
4. **Given** 请求包含 `../` 路径穿越、非白名单可执行文件或非白名单域名，**When** Tool 执行，**Then** 系统拒绝执行、返回安全违规错误、写入失败审计，且不产生副作用。
5. **Given** 已配置通知渠道，**When** Agent 调用 `notify`，**Then** 消息通过该渠道发送；发送失败时返回错误并写入审计，不导致 Agent 崩溃。
6. **Given** Agent 目录和已绑定 Skill，**When** 用户查看 Tool 列表，**Then** 它们不会出现在 Tool 列表中，只作为上下文参与 prompt 组装。

---

### User Story 5 - 通过 REST API 集成 Agent (Priority: P4)

业务系统希望通过 HTTP 接入 LytgOS2，复用 Agent 的对话、工具和记忆能力。系统提供会话管理、无状态 Agent 调用、Profile/Memory/Tool 查询以及健康检查等核心接口；所有响应使用统一信封，错误可诊断，接口契约有文档可查。

**Why this priority**: Web Service 是 LytgOS2 对外交付的门面，也是业务系统集成的主要方式；它依赖前四个 story 的能力，但在 API 层可以独立验收。

**Independent Test**: 启动服务并配置一个 Agent，使用 HTTP 客户端完成“创建 Session → 发消息 → 查历史 → 归档会话”的完整流程；再验证无状态调用、列表接口、健康检查和错误响应。

**Acceptance Scenarios**:

1. **Given** 服务已启动且 Agent 可用，**When** 业务系统创建 Session 并发送消息，**Then** 返回模型回复和 Session 标识；随后查询历史能看到完整对话；删除后该 Session 被归档，历史仍可查询但不能追加消息，且默认不出现在会话列表中。
2. **Given** 业务系统只需要一次性调用，**When** 调用无状态 Agent invoke 接口，**Then** 直接返回结果，不创建也不持久化 Session，且该调用不会出现在会话历史中。
3. **Given** 业务系统查询 Profile、Memory 或 Tool 列表，**When** 调用对应接口，**Then** 返回统一信封中的数据列表，字段清晰且可用于集成。
4. **Given** 服务运行中，**When** 调用健康检查和运行信息接口，**Then** 返回健康状态、版本和运行信息。
5. **Given** 请求缺少必填字段、Session 不存在或 Agent 名称未知，**When** 调用接口，**Then** 返回可操作的结构化错误，服务保持健康。
6. **Given** 1.0 的通知渠道注册和定时任务管理需求，**When** 调用对应支撑接口，**Then** 可以查询状态、手动执行任务并维护通知渠道。

---

### Edge Cases

- 工作区已存在时重复执行初始化：必须幂等，不覆盖用户已有文件。
- 两个 Agent 或多个 Session 并存：必须按 Agent 名称和 Session 标识隔离，互不串扰。
- Provider 超时、返回空响应或返回格式异常：必须给出可诊断错误，不能把空内容当作成功。
- 模型请求了不存在的 Tool：必须返回“Tool 不存在”的结构化错误并继续循环，不能崩溃。
- 模型连续请求同一个 Tool 多次：每个模型请求必须只触发一次逻辑 Tool 调用，不能重复执行；可重试错误的重试属于同一次逻辑调用。
- 达到最大迭代次数：必须终止并说明原因，不能无限循环。
- 长期记忆文件被手工修改、包含缺失分区或为空：必须容错并保持可用的核心/归档结构。
- 核心记忆内容过长：核心记忆仍不得截断；归档记忆可按限制截断。
- MCP Server 未启动、进程退出或返回非法响应：必须返回可诊断错误并写入失败审计。
- 通知渠道 URL 缺失或不可达：`notify` 必须失败并审计，不影响其他 Tool 和对话。
- 定时任务重叠触发：同一任务必须避免进程内并发执行，并记录每次执行状态。
- 会话历史超过窗口：按配置截断早期对话，最近上下文和核心记忆保持可用。
- 并发请求同一 Session：1.0 必须按接收顺序处理，不能丢失消息或产生交叉污染。
- 已归档 Session 收到新消息：必须拒绝并返回可诊断错误，不得自动复活或静默丢弃消息。
- 无状态 invoke 调用：不得创建 Session 或写入会话历史，但 LLM 与 Tool 审计记录仍必须写入。
- 服务收到畸形 JSON 或未知枚举值：返回统一错误信封，不泄露堆栈和内部路径。

## Requirements *(mandatory)*

### Functional Requirements

**工作区与 Agent**

- **FR-001**: 用户必须能通过 `lytgos init` 初始化工作区；重复执行必须幂等，已存在的目录和文件不得被覆盖。
- **FR-002**: 用户必须能通过目录定义一个 Agent；`AGENT.md` 的 frontmatter 必须声明 Provider、模型、Tool、MCP Server、Channel、定时任务和运行参数，正文必须作为任务指令使用。
- **FR-003**: 用户必须能通过 `lytgos profile` 命令创建、列出、查看和删除 Agent；同一实例必须支持多个 Agent 并存。
- **FR-004**: Skill 绑定必须由 Agent 目录下 `skills/` 中的相对软连接表达；每轮只注入 Skill 的 name、description 和本地路径，正文、脚本和参考资料必须按需读取。
- **FR-005**: 系统必须在 Agent 启动时加载 `AGENTS.md`、`SOUL.md`、`USER.md` 三个 Bootstrap 文件，并将其纳入 system prompt。
- **FR-006**: `lytgos chat`、`lytgos serve`、`lytgos gateway` 三种运行模式必须共享同一份 Agent 配置和 Session 存储；`gateway` 必须作为无 HTTP 的常驻守护进程运行，只启动 `AgentScheduler` 与已配置的常驻 Channel，不暴露 REST 端点。

**Provider 与 LLM 调用**

- **FR-007**: 用户必须能配置至少 DeepSeek 和 Kimi 两个 Provider，并按 Agent 选择 Provider 和模型；不支持的名称必须在调用前报错。
- **FR-008**: 敏感配置必须通过 `${ENV_VAR}` 环境变量占位符注入；密钥缺失或非法时必须给出清晰错误，且不得出现在日志或提交的配置中。
- **FR-009**: 每次 LLM 调用必须记录 Provider、模型、prompt/completion/total token 用量和耗时，形成持久化审计记录；1.0 不提供审计查询端点，查询接口属于扩展范围。
- **FR-010**: Provider 故障时必须向 Agent 返回可诊断错误；1.0 不得实现自动 fallback、hedge racing 或 circuit breaker。

**ReAct 循环**

- **FR-011**: 系统必须实现“调用模型 → 执行 Tool → 回填结果 → 继续”的 ReAct 循环；没有 Tool 调用时返回最终回答，达到最大迭代次数时强制结束。
- **FR-012**: 最大迭代次数必须可由 Agent 配置覆盖，默认 10；同一轮多个 Tool 调用必须按顺序执行，且每个模型请求只触发一次逻辑 Tool 调用，不能重复执行。
- **FR-013**: 对话历史必须按 `max_history_turns`（默认 20）截断，保留最近上下文；1.0 不做上下文动态压缩或总结。
- **FR-014**: Tool 失败必须返回结构化错误和是否可重试的标识；仅对可重试错误按指数退避执行“首次尝试 + 最多 3 次重试”，并只写一条审计记录：记录重试次数、各次尝试的错误摘要和最终结果。

**Memory**

- **FR-015**: Session 历史必须持久化，进程重启后可以恢复并继续对话。
- **FR-016**: 长期记忆必须存储为人类可读的 Markdown 文件，包含 `## 核心记忆` 和 `## 归档记忆` 两个一级分区。
- **FR-017**: 核心记忆必须全量注入 system prompt，永不截断、不参与检索；归档记忆必须可截断、可按关键词检索。
- **FR-018**: Agent 必须能通过 `save_memory(content, scope)` 写入核心或归档区，通过 `recall_memory(query)` 检索归档区；没有匹配时必须返回空结果。
- **FR-019**: 1.0 不得自动从对话中抽取事实，不得使用向量检索、语义召回、情景记忆或 Memory Wiki。
- **FR-020**: 长期记忆文件缺失、为空或分区不完整时，系统必须容错并保持可用，不得因文件问题阻断对话。

**Tool 与安全边界**

- **FR-021**: 系统必须提供 9 个内置 Tool：`read_file`、`write_file`、`list_dir`、`shell`、`http_get`、`http_post`、`save_memory`、`recall_memory`、`notify`。
- **FR-022**: 文件操作必须受路径白名单限制，Shell 必须只按可执行文件白名单精确匹配，HTTP 必须受域名白名单限制；路径穿越、非白名单命令和非白名单域名必须被拒绝、审计并返回安全违规错误。
- **FR-023**: 用户必须能通过三种方式扩展 Tool：复用现成 MCP Server（零代码，主推）、自写 MCP Server（轻代码）、Java `@Tool`（重代码）；1.0 的 MCP 只支持 stdio。
- **FR-024**: Agent 目录和 Skill 必须作为上下文，不得注册为 Tool，也不得出现在 Tool 列表中。
- **FR-025**: `notify` 必须通过已注册的通知渠道发送消息；渠道缺失或发送失败必须返回错误并写入审计，不得导致 Agent 崩溃。
- **FR-026**: 定时任务必须由 `AGENT.md` 的 `schedules` 定义，到点通过同一 Agent 处理链路触发；1.0 必须防止同一任务的进程内重叠执行，不支持运行时增删改 cron 定义。

**Web Service**

- **FR-027**: `lytgos serve` 必须暴露 10 个核心 REST 端点：创建 Session、发送消息、查询 Session、归档 Session、无状态 Agent 调用、列出 Profile、查询 Memory、列出 Tool、健康检查、运行信息。归档必须将 Session 置为只读状态：历史仍可按 ID 查询，但不得再接受新消息，且默认不出现在会话列表中。无状态 Agent 调用不得创建或持久化 Session，其结果直接返回，审计记录允许不关联 Session。
- **FR-028**: 所有 REST 响应必须使用统一信封，包含成功/失败标识、数据和错误详情；错误必须可诊断且不得泄露堆栈或内部路径。
- **FR-029**: 系统必须通过 OpenAPI 文档暴露接口契约，业务系统可以仅依据文档完成集成。
- **FR-030**: 1.0 必须由 `lytgos serve` 提供通知渠道注册和定时任务 v2 的支撑端点，包括渠道 CRUD、任务状态查询和手动执行。
- **FR-031**: 1.0 不实现认证、RBAC、SSO 或 API Key；服务按可信内网部署，该限制必须在文档中明确说明。
- **FR-032**: 服务必须在收到无效请求时保持健康，不得因单个请求失败而退出。

**CLI、运维与验收**

- **FR-033**: 系统必须提供 12 个 CLI 命令：`init`、`status`、`chat`、`serve`、`gateway`、`profile list/create/show/delete`、`provider list`、`tool list`、`session list`。`session list` 默认只列出 active Session，归档 Session 需显式筛选才可见；`lytgos chat --profile <name>` 必须自动恢复该 Agent 最近一个 active Session，没有则新建，`--new` 必须显式创建新 Session。
- **FR-034**: 系统必须提供结构化日志、健康检查和运行信息；1.0 不实现 Prometheus 指标、Grafana Dashboard 或 Web 仪表板。
- **FR-035**: 1.0 必须交付 2 个硬性端到端 Demo（每日天气、每日科技日报）和 1 个可选 Demo；硬性 Demo 必须能定时自动运行，也能通过 CLI 或 REST 手动补跑，且两条入口使用同一处理链路。
- **FR-036**: 每个功能模块必须至少有一个端到端测试；未实际运行的测试不得声称通过。
- **FR-037**: 项目主页必须可访问，讲清楚 LytgOS2 是什么、能做什么以及如何在 10 分钟内开始使用。

### Key Entities *(include if feature involves data)*

- **Workspace**: 用户的工作区，包含 Agent 目录、Skill 库、长期记忆、Session、日志、产出物和 Bootstrap 文件。
- **Agent Profile**: 一个 Agent 的运行时配置，包含名称、描述、身份提示、Provider/模型、Tool 列表、MCP Server、Channel、定时任务和运行参数。
- **Skill**: 可复用的任务说明与附属资源；通过 Agent 本地软连接绑定，仅元数据进入每轮 prompt，正文按需读取。
- **Session**: 一次对话的上下文容器，包含 Channel、用户、Agent、历史消息、时间和状态；必须跨进程重启保留。
- **Message**: Session 中的一条用户消息、模型回复、Tool 调用请求或 Tool 结果，按顺序组成对话历史。
- **Memory Entry**: 长期记忆中的一条内容，属于核心区或归档区；核心区永不截断，归档区可检索和截断。
- **Tool Definition**: 可被 Agent 调用的能力描述，包含名称、说明、输入结构和来源（内置、MCP 或 Java Tool）。
- **Tool Invocation**: 一次模型请求的 Tool 调用记录，包含 Tool 名称、入参、重试次数、各次尝试的错误摘要、最终结果或错误、状态、耗时和所属 Session（无状态调用时为空）。
- **LLM Call**: 一次模型调用记录，包含 Provider、模型、token 用量、耗时和所属 Session（无状态调用时为空）。
- **Scheduled Task**: 由 Agent 配置定义的定时任务，包含调度表达式、启用状态和最近执行状态。
- **Notification Channel**: 全局注册的通知目标，包含名称、类型、地址和配置。
- **API Response Envelope**: 所有 REST 响应共用的外层结构，包含成功标识、数据和错误详情。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 新用户可以在 15 分钟内完成“初始化工作区 → 定义 Agent → 完成多轮对话”，全程不写代码。
- **SC-002**: 用户只修改 Agent 配置即可在两个 LLM Provider 之间切换，无需改代码或重新打包。
- **SC-003**: Agent 完成一个需要连续 3 次 Tool 调用的任务，每个模型请求的 Tool 调用在审计记录中恰好出现一次，且顺序与模型请求一致；同一次调用内部的重试不产生额外审计行。
- **SC-004**: 进程重启后，100% 的已有 active Session 可以恢复历史并继续对话；归档 Session 仍可恢复历史，但不接受新消息。
- **SC-005**: 用户在旧 Session 中保存的偏好，在新 Session 中无需重复输入即可被 Agent 使用。
- **SC-006**: 单节点在 4 小时压测中稳定运行至少 10 个 Agent 和 100 个并发 Session。
- **SC-007**: Session 创建的 P99 延迟不超过 200ms，内部转发开销不超过 50ms（不含 LLM 调用本身耗时）。
- **SC-008**: 100% 的越权文件、Shell 和 HTTP 请求被拒绝并写入失败审计，验收中没有白名单绕过。
- **SC-009**: 两个硬性 Demo 连续 3 天按时自动运行成功，且每次都能通过 CLI 或 REST 手动补跑，历史对话可查询。
- **SC-010**: 业务系统仅依据公开的接口文档，在 30 分钟内完成一次无状态 Agent 调用集成。
- **SC-011**: 非 Java 开发者只通过 Agent 目录和现成 MCP Server 就能新增一个可用 Agent，全程不写代码。
- **SC-012**: 12 个 CLI 命令均提供帮助和可操作的错误提示，验收中不存在无文档命令。
- **SC-013**: 新用户访问项目主页后，可以在 10 分钟内找到快速开始并完成首次运行。

## Assumptions

- 目标用户是企业内部的开发者、运维人员和业务系统集成方，默认在可信内网中部署和使用。
- 1.0 不提供认证和 RBAC；部署方必须通过网络隔离、反向代理或现有网关补足访问控制。
- 至少有一个可用的 DeepSeek 或 Kimi 账号；API key 通过环境变量提供，不写入仓库。
- Demo 使用公开可访问的天气/新闻 HTTP 接口和 webhook 通知渠道；企业 IM 入站渠道不在 1.0 范围内。
- MCP Server 以 stdio 方式运行，且在其自身进程内负责鉴权和外部系统访问。
- 1.0 按单节点部署设计，不要求集群、分布式调度或外部状态存储。
- 运行时以 JDK 21+ 和 Linux 为主要部署目标；开发环境可以使用 Windows。
- Tool 失败默认采用指数退避重试：首次执行后最多再重试三次（共 4 次尝试），且只对标记为可重试的错误生效；这是 1.0 的可靠性默认值，Provider 本身的重试不计入。
- 长期记忆以 Markdown 文件为唯一 1.0 后端；核心记忆永不截断，归档记忆可以截断和关键词检索。
- 本规格把 1.0 的 5 个 user story 作为一个整体基线管理，而不是拆成 5 个独立 feature；实施时仍按 US-1 → US-2 →（US-3 ∥ US-4）→ US-5 的顺序推进。
- 同一工作区不得同时以两个独立进程运行 `lytgos serve` 和 `lytgos gateway`，否则同一批定时任务会被两个进程各触发一次；部署时必须二选一，或由部署方提供进程级互斥。
- 归档只改变 Session 状态，不物理删除历史；1.0 没有自动清理归档数据的机制。
- CLI 会话的 channel 固定为 `cli`，user 默认取当前操作系统用户名，取不到时使用固定值 `local`；`lytgos chat` 按 channel + user + profile 查找 active Session。
- `max_history_turns` 的“一轮”指一条用户消息及其对应的模型最终回复；同轮内的 Tool 调用往返不计为额外轮次，默认保留最近 20 轮。
