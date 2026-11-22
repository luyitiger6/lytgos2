# Feature Specification: Agent Provider

**Feature Branch**: `002-lesson16-provider`

**Created**: 2026-11-23

**Status**: Draft

**Input**: User description: "第16节需求：Agent Provider——统一的大模型调用入口"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 按 Profile 选择模型并完成一次调用 (Priority: P1)

一个 Agent 的 Profile 指定了 `deepseek` 和具体模型。用户发起一次对话，系统按 Profile 选择对应模型，完成一次调用并把模型响应返回给调用方。调用方不需要知道底层是哪家模型。

**Why this priority**: 这是 Provider 的最小可用价值；没有这条，后续对话与 ReAct 循环无法工作。

**Independent Test**: 用一个合法 Profile 发起一次调用，断言返回非空响应；将 Profile 的 provider 改为另一个已配置的 provider，断言响应仍然成功且使用的是新 provider。

**Acceptance Scenarios**:

1. **Given** 一个合法 Profile 和已配置的 provider，**When** 调用方发起调用，**Then** 返回该 provider 的模型响应。
2. **Given** Profile 指定的 provider 不在已配置列表中，**When** 调用方发起调用，**Then** 在调用模型前返回清晰错误，不静默使用其他模型。
3. **Given** Profile 中的 provider 从一个已配置项改为另一个，**When** 调用方再次发起调用，**Then** 不需修改代码即可使用新 provider。

---

### User Story 2 - 加载 Profile 并建立内存索引 (Priority: P2)

系统启动时读取 Profile 配置，解析全部约定字段，校验 Profile 引用的 provider 是否存在，并把合法 Profile 放进内存索引，供调用方按名称查找。

**Why this priority**: Profile 是 Provider 选择模型的输入；没有可靠的加载与校验，后续调用无法确定该用哪个模型。

**Independent Test**: 准备一个合法 Profile、一个引用不存在 provider 的 Profile 和一个格式损坏的 Profile，启动加载过程；断言合法项可查询、坏项产生清晰错误且不影响其他项。

**Acceptance Scenarios**:

1. **Given** 一个字段完整的 Profile，**When** 加载完成，**Then** 所有约定字段都能被读取，且可按名称查询到该 Profile。
2. **Given** Profile 引用了全局配置中不存在的 provider，**When** 加载该 Profile，**Then** 产生清晰的校验错误。
3. **Given** 一个格式损坏的 Profile 与其他合法 Profile 并存，**When** 加载完成，**Then** 损坏项被记录为错误，其他合法项仍然可用。

---

### User Story 3 - 成功和失败调用都留下审计记录 (Priority: P2)

每一次模型调用尝试都必须留下审计记录。成功时记录 provider、model、token 用量和耗时；失败时记录失败标志和错误原因，同时把异常继续抛给调用方。

**Why this priority**: 可审计是运行时内核的差异化能力；漏记失败调用会让真实事故在系统中完全消失。

**Independent Test**: 构造一次成功调用和一次会抛异常的调用；断言成功记录 `success=true`，失败记录 `success=false` 且包含错误原因，并且异常确实被抛给调用方。

**Acceptance Scenarios**:

1. **Given** 模型调用成功，**When** 调用结束，**Then** 产生一条成功审计记录，包含 provider、model、token 用量和耗时。
2. **Given** 模型调用超时或报错，**When** 调用失败，**Then** 产生一条失败审计记录，包含错误原因，并且异常继续上抛。
3. **Given** 同一个会话发生多次调用，**When** 调用结束，**Then** 每次尝试都有独立记录，不把多次调用合并成一条。

---

### User Story 4 - 工具 schema 对模型可见但不在本能力内执行 (Priority: P3)

调用方可以提供本次可用的工具说明。系统把工具的 name、description 和输入 schema 翻译成模型能理解的格式，并在请求中关闭自动工具执行；模型可以请求工具，但工具执行不属于本能力。

**Why this priority**: 工具调用是 Agent 的核心能力，但执行权和沙箱检查属于后续引擎模块；本节只负责让模型看到正确的工具说明。

**Independent Test**: 提供一个带工具说明的调用，检查模型请求中的工具定义字段与输入逐项一致，并确认自动执行处于关闭状态；断言本能力没有执行任何工具。

**Acceptance Scenarios**:

1. **Given** 调用方提供一个工具定义，**When** 发起模型调用，**Then** 模型请求中的工具 name、description 和输入 schema 与声明逐字段一致。
2. **Given** 调用方提供工具定义，**When** 发起模型调用，**Then** 自动工具执行被关闭，工具不会被本能力执行。
3. **Given** 调用方没有提供工具，**When** 发起模型调用，**Then** 调用仍然正常工作，不要求工具列表非空。

### Edge Cases

- Profile 引用的 provider 名称不存在：必须产生清晰错误，不能静默回退到其他 provider；该校验发生在模型调用前，不产生模型调用审计记录。
- Profile 文件缺少必填字段或格式损坏：记录错误，不阻断其他 Profile 加载。
- 运行时缺少 provider 凭证：调用前给出可诊断的错误，不把明文凭证写入日志或错误消息。
- 模型调用超时、限流或返回错误：异常上抛，同时写入失败审计记录。
- 模型返回空内容：把响应原样返回给调用方，由调用方决定如何处理。
- 同时存在多个 provider 和多个 Profile：按名称显式选择，不能串台。
- 调用方没有提供工具：正常调用，不要求工具列表存在。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 在启动时加载全部 Profile 配置，解析约定字段，并校验 Profile 引用的 provider 是否存在于全局 provider 配置中。
- **FR-002**: 单个损坏或校验失败的 Profile MUST 只记录错误，不得阻断其他合法 Profile 的加载。
- **FR-003**: 系统 MUST 维护显式的 provider 名称到具体模型的映射；禁止通过扫描全部模型来猜测 provider 身份。
- **FR-004**: 当 Profile 引用的 provider 不存在时，系统 MUST 在发起模型调用前返回清晰错误。
- **FR-005**: 系统 MUST 提供一个调用入口，接收会话标识、Profile 和待发送内容，按 Profile 选择模型并完成一次调用。
- **FR-006**: 系统 MUST 把可用工具的 name、description 和输入 schema 翻译成模型可理解的格式，并 MUST 关闭自动工具执行；工具执行不属于本能力。
- **FR-007**: 系统 MUST 为每次调用尝试写入一条审计记录，包含会话标识、Profile 名称、provider、model、token 用量（可用时）、耗时、成功标志和错误信息。
- **FR-008**: 模型调用失败时，系统 MUST 先写入失败审计记录，再把异常继续抛给调用方，不得吞掉异常。
- **FR-009**: 系统 MUST 提供 Profile 的内存索引，支持按名称查找；本节只提供启动加载这一条注册路径。
- **FR-010**: 全局配置 MUST 声明本实例可用的 provider 及连接信息；Profile MUST 声明具体使用的 provider、model 和温度；两层职责不能混淆。

### Key Entities *(include if feature involves data)*

- **Profile**: Agent 的运行时配置，包含名称、描述、身份信息、provider 选择（名称、模型、温度）、工具、技能、MCP server、渠道、调度、Bootstrap 和设置。本节保留课件的完整字段集，其中通知渠道字段作为兼容字段保留，本节不使用。
- **Provider Connection**: 全局层声明的可用 provider，包含 provider 名称、连接信息和凭证引用；凭证不在配置中保存明文。
- **LLM Call Audit**: 一次模型调用尝试的审计记录，包含会话标识、Profile 名称、provider、模型、token 用量、耗时、成功标志、错误信息和时间戳。
- **Tool Descriptor**: 调用方提供给模型的工具说明，包含 name、description 和输入 schema。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 使用合法 Profile 完成一次真实模型调用，调用方拿到非空响应。
- **SC-002**: 仅修改 Profile 中的 provider，不修改代码，即可切换到另一个已配置 provider；审计记录中的 provider 和 model 随之变化。
- **SC-003**: 100% 的模型调用尝试都产生且只产生一条审计记录；失败记录包含 `success=false` 和可诊断的错误原因。
- **SC-004**: 引用未知 provider 时，0 次静默回退；错误在模型调用前返回。
- **SC-005**: 提供给模型的工具 name、description 和输入 schema 与声明 100% 对齐；本能力执行工具的次数为 0。
- **SC-006**: 一个损坏的 Profile 不会阻止其他合法 Profile 加载和查询。

## Assumptions

- 本节 Profile 使用独立配置文件；后续 Agent 目录与 `AGENT.md` 课程再做迁移。
- Profile 保留课件的完整字段集；通知渠道字段作为兼容字段保留，本节不使用，后续通知课程再迁移到全局注册表。
- 现有工程地基提供模型连接与持久化能力；DeepSeek/Kimi 等外部模型账号存在，凭证通过环境变量在运行时提供。
- 自动验收由本节验收测试套件承载：单元测试默认运行；需要真实模型账号的冒烟测试带集成标记，CI 默认跳过，由人工在本地执行。
- 产品命名使用项目既定命名；课件中的旧产品名仅作为对照，不进入实现。
- 开发约定：本节新增或修改的代码全部使用中文注释；每个字段、方法参数和局部变量都要有注释。
- Profile 名称在内存索引中视为唯一；出现重复名称时保留已加载项并记录错误，不静默覆盖。
- 审计写入失败不被静默吞掉；异常上抛，由上层处理。
- 明确不做：fallback、hedge racing、熔断、成本看板、工具执行、Agent 目录运行时导入、Memory 向量检索。
