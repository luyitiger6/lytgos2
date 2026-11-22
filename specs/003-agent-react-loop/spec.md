# Feature Specification: ReAct Agent Loop

**Feature Branch**: `003-lesson17-react`

**Created**: 2026-11-23

**Status**: Draft

**Input**: User description: "第17节需求：ReAct 循环——Agent 的核心推理与工具执行引擎"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 多轮工具调用完成一次任务 (Priority: P1)

用户提出一个需要外部信息的问题，例如“查一下今天天气再给穿搭建议”。Agent 第一轮决定调用天气工具；系统执行工具并把结果回填；Agent 第二轮基于工具结果给出最终建议。调用方只收到最终答复，不需要自己驱动多轮循环。

**Why this priority**: 这是 ReAct 的核心用户价值；没有“想一步、做一步、看结果”的闭环，Agent 与普通聊天机器人没有区别。

**Independent Test**: 用一个按顺序返回“工具调用响应 → 最终文本响应”的模型替身运行一次会话；断言工具被调用一次、工具结果进入下一轮请求、最终返回模型文本。

**Acceptance Scenarios**:

1. **Given** 模型第一轮返回一个天气工具调用，**When** 系统处理该消息，**Then** 工具被执行一次，结果被回填到会话，模型收到包含该结果的下一轮请求。
2. **Given** 模型第二轮不再返回工具调用，**When** 系统完成本轮，**Then** 返回模型的最终文本并停止循环。
3. **Given** 一次响应里有多个工具调用，**When** 系统处理该响应，**Then** 按顺序逐个执行并回填，不并行执行。

---

### User Story 2 - 循环边界与上下文控制 (Priority: P2)

模型可能一直要求调用工具，也可能因为历史不断增长而撑爆上下文。系统必须在“模型不再调用工具”或“达到最大轮数”时停止，并且每轮响应和工具结果都进入会话历史；历史超过上限时只保留最近若干轮。

**Why this priority**: 没有停止条件和历史截断，ReAct 会变成死循环或上下文溢出；没有历史累积，后续轮次接不上，事后也无法审计。

**Independent Test**: 构造一个每轮都返回工具调用的模型替身，断言提供方恰好被调用 10 次并返回强制结束消息；再构造超过历史上限的会话，断言请求只包含最近 N 轮。

**Acceptance Scenarios**:

1. **Given** 模型某轮没有返回工具调用，**When** 系统处理该轮，**Then** 立即返回最终文本，不再发起下一轮。
2. **Given** 模型每轮都返回工具调用，**When** 达到默认最大轮数 10，**Then** 系统停止并返回明确的“达到最大轮数，已停止”消息。
3. **Given** 会话历史超过配置的最近轮数上限，**When** 系统组装下一轮请求，**Then** 只包含最近 N 轮，且 system prompt 与当前用户消息不丢失。
4. **Given** 模型返回了响应或工具执行返回了结果，**When** 本轮结束，**Then** 该响应和结果都被追加到会话历史。

---

### User Story 3 - 工具调用审计与请求级上下文隔离 (Priority: P2)

每次工具调用无论成功失败都必须留下审计记录；工具不存在或执行抛异常时，系统返回失败结果并把失败原因写进审计，不能静默吞掉。处理期间工具可以读取当前 AgentProfile，但请求结束或异常退出后必须清理，不能泄漏到复用同一线程的后续请求。

**Why this priority**: 审计是运行时内核的差异化能力；上下文泄漏在并发复用时会串号，是最难排查的一类事故。

**Independent Test**: 用成功工具和抛异常工具各执行一次，断言两条审计记录分别为成功和失败；让 AgentService 调用抛异常，断言请求结束后当前 AgentProfile 已被清理。

**Acceptance Scenarios**:

1. **Given** 工具执行成功，**When** 调用结束，**Then** 产生一条成功审计记录，包含会话、工具名、输入、耗时。
2. **Given** 工具不存在或工具执行抛异常，**When** 调用结束，**Then** 产生一条失败审计记录，包含错误原因，并返回失败结果。
3. **Given** AgentService 正在处理请求，**When** 工具执行读取当前 AgentProfile，**Then** 能读到本次请求的配置。
4. **Given** AgentService 处理过程中抛出异常，**When** 请求退出，**Then** 当前 AgentProfile 仍被清理。

---

### User Story 4 - 上下文加载无缓存与错误可见 (Priority: P3)

Agent 的 Bootstrap 文件和 Skill 元数据在每一轮组装请求时重新读取；用户修改文件后下一轮立即生效。显式引用的 Skill 缺失必须报错，Bootstrap 文件缺失至少记录警告，不能静默跳过造成“人格悄悄丢了”。

**Why this priority**: 上下文是 Agent 行为的一部分；缓存会让修改失效，静默跳过会把配置错误伪装成模型行为异常。

**Independent Test**: 用临时工作区加载一次上下文，修改文件后再次加载，断言第二次读到新内容；删除一个显式引用的 Skill，断言加载失败；缺少一个 Bootstrap 文件，断言产生警告但合法内容仍可用。

**Acceptance Scenarios**:

1. **Given** 一个 Bootstrap 文件和 Skill 元数据存在，**When** 组装请求，**Then** 内容按约定顺序进入 system prompt，末尾包含当前日期时间。
2. **Given** 用户在两轮之间修改了 Bootstrap 文件，**When** 下一轮组装请求，**Then** 使用修改后的内容，不使用旧缓存。
3. **Given** 显式引用的 Skill 不存在，**When** 加载上下文，**Then** 产生清晰错误。
4. **Given** 引用的 Bootstrap 文件不存在，**When** 加载上下文，**Then** 记录警告并继续处理其余内容。

### Edge Cases

- 模型返回空文本且没有工具调用：按最终响应处理，返回空文本，不继续循环。
- 模型返回工具调用但工具名为空或不存在：返回失败结果并写失败审计，不调用任何工具。
- 工具执行返回失败结果：仍回填到会话，让模型下一轮决定是否重试或换方案。
- 工具执行抛出运行时异常：包装为失败结果并写失败审计，异常不穿透到调用方，除非属于程序性错误。
- 会话没有任何历史：仅发送 system prompt 与当前用户消息。
- 最大轮数配置为 0 或负数：使用默认值 10，避免不执行任何模型调用。
- 同一轮多个工具调用中某个失败：继续执行后续工具，所有结果按顺序回填并各自审计。
- AgentProfile 在注册表中不存在：在进入循环前返回清晰错误，不调用模型。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 在每次处理开始时把用户消息追加到会话历史。
- **FR-002**: 系统 MUST 最多执行配置的最大轮数次模型调用，默认 10 次；达到上限后停止并返回明确的强制结束消息。
- **FR-003**: 系统 MUST 每轮只调用一次模型，并把模型响应追加到会话历史。
- **FR-004**: 模型某轮没有返回工具调用时，系统 MUST 立即返回该轮文本并结束循环。
- **FR-005**: 模型返回工具调用时，系统 MUST 按顺序逐个执行工具，把每个工具结果追加到会话历史，然后进入下一轮。
- **FR-006**: 系统 MUST 按顺序组装 system prompt（Agent 指令、Bootstrap、Skill 元数据、当前日期时间）、长期记忆扩展点、最近 N 轮会话历史、当前可用工具；历史默认最多 20 轮。
- **FR-007**: 系统 MUST 在每次工具调用尝试后写审计记录，包含会话标识、工具名、输入、成功标志、错误原因、耗时和创建时间；工具不存在或执行异常时也必须记录失败。
- **FR-008**: 系统 MUST 通过统一入口按会话所属 AgentProfile 处理请求：处理期间当前 AgentProfile 可被工具读取，处理结束或异常退出后必须清理。
- **FR-009**: 系统 MUST 在处理成功后通过可注入的保存钩子持久化会话；本节不要求 SQLite 会话持久化。
- **FR-010**: 系统 MUST 每次组装 prompt 时重新读取 Bootstrap 与 Skill 元数据，不缓存；显式引用的 Skill 缺失必须报错，Bootstrap 缺失至少记录警告。
- **FR-011**: 系统 MUST 保持 core 引擎不依赖具体模型 SDK；模型调用通过 core 的 provider 端口完成，具体 provider 适配器负责请求与响应转换。

### Key Entities *(include if data involved)*

- **Session**: 一次对话的上下文，包含会话标识、所属 AgentProfile 名称、接入渠道、用户标识和按时间顺序累积的消息；支持追加用户消息、模型响应、工具结果，并能截取最近 N 轮。
- **AgentProfile**: 第 16 节加载的运行时 Agent 配置，提供最大轮数、历史上限、Bootstrap 和 Skill 引用、可用工具等设置。
- **Prompt**: 一轮模型请求的输入，包含 system prompt、按顺序排列的会话消息和可用工具说明。
- **Tool Call / Tool Result**: 模型请求执行工具的结构化调用，以及工具执行后的成功或失败结果；结果必须能回填到会话。
- **Tool Invocation**: 一次工具调用尝试的审计记录，关联会话与工具，记录输入、成功标志、错误原因、耗时和创建时间。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 一个需要一次工具调用的多轮场景可以在不修改调用方代码的情况下完成，最终答复包含工具结果带来的信息。
- **SC-002**: 模型每轮都要求调用工具时，系统恰好发起配置的最大轮数（默认 10）次模型调用，并返回强制结束消息；不会出现第 11 次调用。
- **SC-003**: 会话历史超过配置上限时，每次模型请求包含的历史不超过配置轮数，同时保留 system prompt 和当前用户消息。
- **SC-004**: 每一次工具调用尝试都能在审计记录中查到一条对应记录；成功与失败场景的 `success` 和错误原因字段符合预期。
- **SC-005**: 每次请求结束后当前 AgentProfile 上下文都被清理；成功和异常退出两种路径都不会留下请求级状态。
- **SC-006**: 用户修改 Bootstrap 或 Skill 元数据后，下一次请求立即读取新内容；显式 Skill 缺失会阻止请求继续。
- **SC-007**: 本节五个 ReAct harness 测试类默认随 `mvn test` 运行并在秒级完成，不依赖真实模型或外部网络；全量 reactor 门禁仍包含第 16 节与 boot/storage 回归。

## Assumptions

- 本节继续使用第 16 节的 AgentProfile YAML 配置；`AGENT.md`/`AgentLoader` 运行时导入留到后续课程。
- “最近 N 轮”按完整对话轮次计算：每轮从一条用户消息开始，包含其后的 assistant 消息与 tool 结果；截断保留最近 N 个完整轮次，不拆散同一轮的工具调用链。
- 会话保存钩子抛异常时向上抛出，不静默吞掉；成功返回答复前必须完成保存钩子调用。
- Agent 指令取自 AgentProfile 的 `identity.prompt`；Bootstrap 文件位于工作区根目录，Skill 元数据位于 `.lytgos/skills/<name>/SKILL.md`。
- 长期记忆不在本节接入；PromptBuilder 保留扩展点，但只处理会话历史。
- Sandbox 白名单检查不在本节实现，只在工具执行前留出接入位，第 24 节接线。
- Session 的 SQLite 持久化与 SessionManager 属于第 18 节；本节只通过可注入的保存钩子表达“处理完成后保存”的契约。
- 工具集合、Provider 端口、会话保存钩子都通过构造参数注入，单元测试可使用模型替身、工具替身和临时目录。
- 本节不引入 Spring AI 的 Agent 抽象；Spring AI 只用于 Provider 协议适配和工具 schema 生成。
- 不实现工具并行调用、Agent 间委托、流式输出、上下文压缩和 CLI/REST 入口。
