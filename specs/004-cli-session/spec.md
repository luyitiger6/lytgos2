# Feature Specification: CLI and Session Persistence

**Feature Branch**: `004-lesson18-cli-session`

**Created**: 2026-11-23

**Status**: Draft

**Input**: User description: "CLI 与会话持久化——把 Agent 引擎变成可操作的命令行产品入口"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 在终端里与 Agent 多轮对话 (Priority: P1)

用户在终端执行交互式对话命令，进入一问一答模式。每次输入交给 Agent 引擎处理，最终回复打印到终端；多轮对话共享同一个会话历史；输入 `/quit` 或结束输入后正常退出。

**Why this priority**: 这是 Agent 从“库”变成“可操作产品”的最小用户价值；没有交互入口，Provider 和 ReAct 能力只能由测试驱动。

**Independent Test**: 用可控的 AgentService 替身和内存会话运行一轮交互，断言用户输入被交给引擎、回复被打印、`/quit` 结束循环；不需要真实模型。

**Acceptance Scenarios**:

1. **Given** 工作区已初始化且 profile 存在，**When** 用户进入对话并输入一句话，**Then** 这句话被交给 Agent 引擎，最终回复显示在终端。
2. **Given** 用户连续输入多句话，**When** 每轮处理完成，**Then** 所有轮次复用同一个会话，历史持续累积。
3. **Given** 用户输入 `/quit` 或输入流结束，**When** 循环检测到退出信号，**Then** 命令正常结束，不打印错误。

---

### User Story 2 - 会话身份确定且跨重启保留 (Priority: P1)

同一个渠道、用户和 AgentProfile 组合始终对应同一个会话；任一维度不同就是不同会话。会话消息保存后，进程重启再读取仍然完整。

**Why this priority**: 会话是后续 CLI、Web、定时触发共享的地基；身份公式不唯一或历史丢失会让同一用户出现多条互不相认的对话。

**Independent Test**: 用同一个三元组两次获取会话，断言标识相同；改变任一维度，断言标识不同；保存消息后重新创建会话读取，断言历史完整。

**Acceptance Scenarios**:

1. **Given** 相同的 channel、user、profile 三元组，**When** 连续两次获取会话，**Then** 返回同一个会话标识。
2. **Given** channel、user、profile 中任意一个不同，**When** 获取会话，**Then** 得到不同会话标识。
3. **Given** 一个会话包含多轮消息，**When** 保存后在新进程/新上下文读取，**Then** 消息顺序和内容完整保留。

---

### User Story 3 - 12 个子命令与轻重命令分流 (Priority: P2)

用户通过命令行完成初始化、状态查询、对话、服务启动、Profile 管理、Provider/Tool/Session 查询。查询类命令不启动完整服务，快速返回；对话、服务、守护进程命令才启动运行时。

**Why this priority**: 12 个命令是 CLI 的产品边界；轻重分流决定日常查询是否可用。

**Independent Test**: 调用命令行的帮助输出，断言 12 个叶子命令都存在；运行轻命令时不出现运行时启动日志，运行重命令时出现运行时启动日志。

**Acceptance Scenarios**:

1. **Given** 安装后的可执行 JAR，**When** 用户对任一子命令请求帮助，**Then** 显示该命令的参数和说明，并以成功状态退出。
2. **Given** 用户执行初始化、状态、Profile 管理、Provider/Tool/Session 查询，**When** 命令完成，**Then** 不启动完整运行时，直接返回结果。
3. **Given** 用户执行对话、服务或守护进程命令，**When** 命令启动，**Then** 运行时被加载，且能够读取同一份 Profile 与会话存储。

---

### User Story 4 - 工作区初始化与配置查询 (Priority: P3)

用户首次使用时初始化工作区；命令幂等创建目录、Bootstrap 占位文件、长期记忆占位文件和默认 AgentProfile，不覆盖已有文件。用户还可以列出、创建、查看、删除 Profile，查看 Provider 和会话摘要。

**Why this priority**: 没有可用的工作区和默认 Profile，对话命令无法启动；查询命令是日常排障入口。

**Independent Test**: 在空临时目录执行初始化两次，断言目录和默认文件存在且第二次不覆盖修改；创建 Profile 后列出、查看、删除，断言文件状态符合预期。

**Acceptance Scenarios**:

1. **Given** 空目录，**When** 执行初始化，**Then** 工作区目录、默认 Profile 和基础占位文件被创建。
2. **Given** 工作区已初始化并有用户修改，**When** 再次执行初始化，**Then** 已有文件保持原内容，不报错。
3. **Given** 一个合法 Profile 名称，**When** 创建 Profile，**Then** 生成可被运行时加载的 YAML 模板；列出、查看、删除命令能正确反映文件状态。
4. **Given** Provider 配置存在，**When** 执行 Provider 列表，**Then** 仅显示名称和连接地址，不显示任何凭证。
5. **Given** 尚无会话或工具注册，**When** 执行会话/工具列表，**Then** 返回空列表说明，不报错。

### Edge Cases

- 工作区不存在：查询命令给出“请先初始化工作区”的提示，不抛出堆栈。
- Profile 文件不存在或名称包含路径分隔符：创建/查看/删除给出清晰错误，不越出 profiles 目录。
- 会话数据库或 sessions 表不存在：会话列表返回空说明，不创建隐式表。
- Provider 配置缺少凭证：列表只展示已声明的名称和地址，不因为凭证缺失而失败。
- 输入流立即结束：对话命令正常退出，不进入死循环。
- Agent 处理抛异常：对话命令打印错误并保持进程可退出，不吞掉错误。
- 保存会话失败：异常向上抛出，不返回“已保存”的假象。
- 同一轮重复启动对话：同一三元组继续使用原会话，不重复创建。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 提供 12 个 CLI 叶子命令：初始化、状态、对话、服务、守护进程、Profile 列表/创建/查看/删除、Provider 列表、Tool 列表、Session 列表；参数解析与帮助输出使用统一命令框架。
- **FR-002**: 系统 MUST 将命令分为轻命令与重命令：初始化、状态、Profile 管理、Provider/Tool/Session 查询不启动完整运行时；对话、服务、守护进程启动运行时。
- **FR-003**: 系统 MUST 在对话命令中读取终端输入、交给 Agent 统一入口处理、打印最终回复，并在 `/quit` 或输入结束时退出。
- **FR-004**: 系统 MUST 提供会话统一门面，支持按 channel/user/profile 获取或创建会话、按标识查询会话、保存会话。
- **FR-005**: 会话标识 MUST 只由会话门面按 channel、user、profile 三元组生成；调用方只提供三元组，不得自行拼接。
- **FR-006**: 同一三元组 MUST 返回同一个会话；任一维度不同 MUST 返回不同会话。
- **FR-007**: 系统 MUST 将会话消息整体序列化保存；进程重启或新建运行时上下文后仍能读取完整历史。
- **FR-008**: 系统 MUST 新增 sessions 持久化表，至少包含会话标识、Profile 名称、渠道、用户标识、消息历史、状态、创建时间、最后活跃时间、归档时间；表结构由迁移脚本管理。
- **FR-009**: Agent 统一入口 MUST 在处理完成后通过会话门面保存会话，不得继续使用临时保存回调。
- **FR-010**: CLI 对话渠道 MUST 只负责读取、转交和打印，不包含 Agent 推理、模型调用或工具执行逻辑。
- **FR-011**: 初始化命令 MUST 幂等创建工作区目录、Bootstrap 占位文件、长期记忆占位文件和默认 AgentProfile；已有文件不得被覆盖。
- **FR-012**: Profile 管理命令 MUST 在受控的 profiles 目录内创建、列出、查看和删除 YAML；非法名称或缺失文件必须给出清晰错误。
- **FR-013**: Provider 列表 MUST 只展示 provider 名称和连接地址，禁止输出 api-key；Tool 列表在工具能力未接入时返回空说明；Session 列表直接读取会话表并展示摘要。
- **FR-014**: 服务与守护进程命令 MUST 启动运行时，并与对话命令共享同一份 Profile 与会话存储。
- **FR-015**: 重命令启动运行时时 MUST 显式覆盖实体与仓库扫描范围，确保审计和会话仓库被加载。

### Key Entities *(include if data involved)*

- **Session**: 一次对话的身份与消息历史，包含渠道、用户、AgentProfile 名称和按时间顺序排列的消息。
- **SessionManager**: 会话统一门面，负责按三元组生成/查找会话、按标识查询、保存会话。
- **Stored Session**: 持久化层的会话记录，包含会话标识、Profile 名称、渠道、用户标识、JSON 消息历史、状态、创建时间、最后活跃时间、归档时间。
- **CLI Command**: 一个可执行子命令，包含名称、参数、帮助说明、轻/重命令属性和退出状态。
- **AgentProfile**: 运行时可用的 Agent 配置，由 Profile 管理命令在 profiles 目录中维护。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 同一 channel/user/profile 组合连续获取会话得到相同标识；改变任一维度得到不同标识，覆盖全部三种差异。
- **SC-002**: 保存包含多轮消息的会话后，在新的运行时上下文读取，消息数量、顺序和文本内容与保存前完全一致。
- **SC-003**: 12 个叶子命令均可展示帮助并以成功状态退出；轻命令执行时不出现完整运行时启动日志，重命令执行时出现。
- **SC-004**: 用户在终端完成至少两轮对话并输入 `/quit` 后进程正常退出，第二轮请求能带上第一轮历史。
- **SC-005**: 初始化在空目录创建完整工作区；第二次执行不修改已有文件内容。
- **SC-006**: Provider 列表输出不包含任何凭证值；会话/工具列表在空数据时返回可读说明而非堆栈。
- **SC-007**: 全量质量门禁通过，会话门面与会话仓库验收测试默认随构建运行。

## Assumptions

- 默认工作区为 `.lytgos`，默认 Profile 名称为 `default`；Profile 创建模板默认使用 `deepseek` 和 `deepseek-chat`。
- 会话标识采用 `channel:user:profile` 的确定性格式；包含分隔符的取值由调用方避免，标识生成只发生在会话门面内部。
- 消息历史使用 JSON 序列化，sessions 表通过 Flyway 迁移创建；核心阶段不按消息拆表。
- 轻命令中的 Session 列表与状态查询直接读取本地 SQLite 文件，不启动完整运行时；重命令通过组合根加载运行时。
- CLI 是最终可执行 JAR 的主入口；重命令启动运行时时使用显式实体与仓库扫描范围。
- 守护进程命令在本功能中提供可启动骨架；实际 IM 渠道属于扩展范围。
- 不实现 Web Service 端点、定时任务、内置 Tool/MCP/Sandbox、AGENT.md 运行时导入、IM 渠道、认证、多租户或 Agent 生命周期 API。
- 所有新增/修改代码使用中文注释；字段、方法参数和局部变量均有注释；测试方法名英文 + 中文 `@DisplayName`；模块内按领域分包。
