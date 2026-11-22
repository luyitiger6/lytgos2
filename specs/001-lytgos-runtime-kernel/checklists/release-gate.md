# 发布门禁需求质量清单：LytgOS2 1.0 运行时内核

**Purpose**: 在实现前与发布前审查 spec、plan、data-model、contracts 与 tasks 中需求本身的质量：完整性、清晰度、一致性、安全性、可测性、边界覆盖与追溯性。本清单不用于验证实现行为。
**Created**: 2026-11-22
**Feature**: [spec.md](../spec.md)
**Checklist Type**: release-gate
**Focus**: 安全与审计边界、ReAct 与 Tool 语义、API/CLI 契约一致性、全特征一致性、验收/NFR 可测性
**Depth**: 正式发布门禁（75 项）

**Note**: 本清单由 `/speckit-checklist` 基于当前 feature 上下文生成，检查对象是需求文档，不是代码或运行时行为。
**Review Ownership**: 本清单是 reviewer 所有的需求质量审查产物。只有 reviewer 判定某条需求质量准则已满足时，才可标记 `[x]`。
**Marker Semantics**: `[x]` 表示需求质量准则已评审满足，不表示实现工作完成。

## 1. 需求完整性与追溯

- [ ] CHK001 术语“轮次”“迭代”“模型请求”是否在规格中有唯一且互斥的定义，并说明它们与 `max_history_turns`、`max_iterations` 的对应关系？[Clarity, Spec §FR-012/§FR-013/§Assumptions]
- [ ] CHK002 通知渠道 CRUD 与定时任务 v2 支撑端点的路径、方法、状态码和成功响应是否在需求中有完整枚举，而不是只存在于 REST 契约？[Completeness, Spec §FR-030, Contract rest-api §3]
- [ ] CHK003 Provider 配置的唯一来源与优先级（打包内 `application.yaml`、外部配置、环境变量、Agent frontmatter）是否明确，足以支撑 SC-002 的“只改 Agent 配置即可切换 Provider”？[Completeness, Spec §FR-007/§SC-002, Plan §Technical Context]
- [ ] CHK004 system prompt 的组成顺序与优先级（identity、Bootstrap、Agent 正文、核心记忆、Skill 元数据、Tool 定义、历史）是否被规定为可测试的需求？[Completeness, Spec §FR-005/§FR-017, Contract agent-definition §4, Tasks T058]
- [ ] CHK005 “每个功能模块至少一个端到端测试”的模块级验收定义是否明确，特别是 `lytgos-channel-cli` 是否有可追踪的测试任务？[Completeness/Coverage, Spec §FR-036, Tasks T049/T064/T133]
- [ ] CHK006 定时任务的 misfire、DST、进程停机期间错过执行和补跑策略是否定义？[Completeness, Spec §FR-026, DataModel §1.3]
- [ ] CHK007 长期记忆是工作区全局共享还是按 Agent 隔离，是否明确？并发 `save_memory` 的写入顺序与丢失更新防护是否定义？[Ambiguity, Spec §FR-016/§FR-017, DataModel §1.6]
- [ ] CHK008 审计写入失败时的行为（fail-closed 返回错误还是 fail-open 返回结果）是否作为需求规定？[Completeness, Spec §FR-009/§FR-014, DataModel §6]
- [ ] CHK009 追溯方案是否覆盖验收场景稳定 ID（如 US1-AS1）、FR/SC 双向映射，以及 1.0 明确不做项到 `docs/ReleaseBaseline.md` 第 3.3 节的映射？[Traceability, Spec §User Scenarios/§Success Criteria/§Assumptions, Constitution §1.0 范围与扩展边界]

## 2. 需求清晰度

- [ ] CHK010 是否明确“同一轮”指一条用户消息及其最终回复，还是指 ReAct 的一次模型往返？FR-012 与 FR-013 的用法是否一致？[Clarity, Spec §FR-012/§FR-013/§Assumptions]
- [ ] CHK011 “不支持的模型”如何判定（模型 allowlist、Provider capability，还是调用时由 Provider 报错），是否足以满足“调用前报错”的 US1-AS4？[Clarity/Conflict, Spec §FR-007/§US1-AS4, DataModel §1.2]
- [ ] CHK012 Shell 白名单是否只约束可执行文件，还是也约束 `args`、`cwd` 和脚本内容？边界是否明确？[Clarity, Spec §FR-022, Contract builtin-tools §4]
- [ ] CHK013 CLI/REST/日志中的“可操作错误”是否定义了必需字段、语言与禁止内容，并能支撑 SC-012 的逐命令验收？[Clarity/Measurability, Spec §FR-028/§SC-012, Contract rest-api §4, Contract cli §1]
- [ ] CHK014 “无状态 invoke”是否明确仍会注入 Agent 上下文与长期记忆，只是不创建/持久化 Session？[Clarity, Spec §FR-027, Contract rest-api §2.5]
- [ ] CHK015 `GET /api/v1/tools` 与 `lytgos tool list` 返回的是全局注册表还是某个 Agent 可见的 Tool 集合？`/api/v1/info` 的 `tools: 9` 是内置数量还是注册表总数？[Ambiguity, Spec §FR-027, Contract rest-api §2.8/§2.10, Contract cli §2.11]
- [ ] CHK016 `channels` frontmatter 在 1.0 的语义是什么（仅 cli 声明、校验用途，还是决定可用入口）？REST 与调度 Session 是否需要声明？`gateway` 所称“未来配置的常驻 Channel”是否属于 1.0 范围？[Ambiguity, Spec §FR-002/§FR-006, DataModel §1.2, Contract cli §2.5]
- [ ] CHK017 在 1.0 使用 `/api/v2/schedules` 的版本策略与兼容承诺是否说明，避免与产品 1.0 混淆？[Clarity, Spec §FR-030, Contract rest-api §3.2]
- [ ] CHK018 prompt 历史窗口（`max_history_turns`）、REST 历史返回上限（最近 100 条）与 32KB 单消息上限是否是刻意区分，并定义了三者交界处的行为？[Clarity, Spec §FR-013, DataModel §2.7, Contract rest-api §2.2–§2.3]
- [ ] CHK019 文件白名单的默认值是空、仅工作区还是其他？“相对路径”相对于工作区还是进程 CWD？[Ambiguity, Spec §FR-022, Contract builtin-tools §1–§3, Quickstart §5.1]

## 3. 跨文档一致性

- [ ] CHK020 FR-002 的“frontmatter 必须声明 Provider、模型、Tool、MCP Server、Channel、定时任务和运行参数”与 data-model 中多数字段可选是否存在口径冲突？[Consistency, Spec §FR-002, DataModel §1.2]
- [ ] CHK021 FR-009 要求记录 token 用量，而 token 列允许 NULL；Provider 未返回用量时的验收标准是否明确？[Conflict, Spec §FR-009, DataModel §2.3]
- [ ] CHK022 builtin-tools 说审计 `result_json` 保存截断内容并带 `truncated` 标记，但 data-model 没有对应列/结构；两者是否一致？[Conflict, Contract builtin-tools 通用规则, DataModel §2.2]
- [ ] CHK023 FR-008 要求密钥缺失/非法时清晰报错，而 mcp-config 对缺失 `${ENV_VAR}` 只告警并继续启动；MCP env 是否属于 FR-008 的范围？[Conflict, Spec §FR-008, Contract mcp-config §1]
- [ ] CHK024 CLI 将 `status`/`provider list` 定义为无需 Spring 上下文的文件/配置命令，但二者输出又依赖已注册 Tool/Provider 配置；来源与降级行为是否一致？[Conflict, Contract cli §1/§2.2/§2.10]
- [ ] CHK025 CLI 规定 Provider 错误退出码 3，而 `chat` 又要求 Provider 缺 key 时进程保持可用；交互式 REPL 的退出/错误语义是否明确？[Conflict, Spec §US1-AS3, Contract cli §1/§2.3]
- [ ] CHK026 FR-028 要求统一信封包含“错误详情”，而 REST 契约失败响应为 `data=null` 且详情只在 `message`；结构化错误详情是否缺失？[Conflict, Spec §FR-028, Contract rest-api §1]
- [ ] CHK027 FR-022 要求 Shell 只按可执行文件白名单执行，而 mcp-config 允许 `command` 不受 Shell 白名单约束；该豁免是否与“MCP server 为受信进程、不沙箱化其文件/网络访问”的假设一起被明确批准并记录？[Conflict/Assumption, Spec §FR-022, Contract mcp-config §5]

## 4. 安全与审计门禁

- [ ] CHK028 沙箱默认值是否按 secure-by-default 明确规定（空白名单还是仅工作区），并在 `init`、示例和文档中保持一致？[Security/Completeness, Spec §FR-022, Contract builtin-tools 通用规则, Quickstart §5.1]
- [ ] CHK029 HTTP 安全需求是否覆盖重定向再校验、DNS rebinding、IP 字面量、非 http(s) scheme、URL 编码绕过和 Host 覆盖？[Security/Gap, Spec §FR-022, Contract builtin-tools §5–§6]
- [ ] CHK030 文件写入是否明确禁止覆盖 `AGENT.md`、Bootstrap、`MEMORY.md` 等控制面文件，或规定仅能通过专用 Tool 修改？[Security/Gap, Spec §FR-022, DataModel §1.1]
- [ ] CHK031 路径穿越与软链接逃逸的验收范围是否覆盖 `..`、绝对路径、相对软连接、符号链接到工作区外和 TOCTOU？[Security/Edge, Spec §FR-022, DataModel §1.4]
- [ ] CHK032 `tool_invocations.input_json`/`result_json` 可能包含模型提供的 Authorization、webhook key 或文件内容；审计脱敏要求与越权请求的失败审计字段是否明确？[Security/Gap, Spec §FR-008/§FR-022, DataModel §2.2]
- [ ] CHK033 Java `@Tool` 的扫描范围与启用方式是否明确，避免把无关 Spring Bean 暴露为 Agent Tool？[Security/Gap, Contract builtin-tools §10.2]
- [ ] CHK034 Tool 返回内容（网页、MCP、文件）作为不可信输入进入 prompt 时，提示注入防护是明确不做还是需要最低限度隔离？[Security/Assumption, Spec §FR-021/§FR-023, Contract builtin-tools]
- [ ] CHK035 密钥不泄露是否有可度量验收项，覆盖日志、错误响应、审计表、CLI 输出和 `provider list`？[Security/Measurability, Spec §FR-008, Contract rest-api §2.10, Contract cli §2.10]
- [ ] CHK036 无认证部署的绑定地址、CORS 全开、TLS/反向代理与网络隔离要求是否明确写入部署约束？[Security/NFR, Spec §FR-031/§Assumptions, Contract rest-api preamble]

## 5. 验收标准可测性

- [ ] CHK037 SC-003 的“每个模型请求恰好一次”在单消息多 `toolCalls`、重试和未知 Tool 场景下是否仍可度量？[Measurability/Conflict, Spec §SC-003/§FR-012]
- [ ] CHK038 SC-004 的“100% active Session 恢复”是否定义恢复条件、数据规模、并发与版本边界？[Measurability, Spec §SC-004]
- [ ] CHK039 SC-005 的“偏好被 Agent 使用”是通过 prompt 内容、回答行为还是审计记录判定？[Measurability, Spec §SC-005]
- [ ] CHK040 SC-006 的“稳定运行”是否量化错误率、内存增长、重启次数、硬件与并发定义？[Measurability, Spec §SC-006]
- [ ] CHK041 SC-007 的 P99 与内部转发开销是否定义测量点、负载、预热、样本量和环境？[Measurability, Spec §SC-007]
- [ ] CHK042 SC-008 的“100% 越权请求”是否有明确的攻击语料与拒绝判定标准？[Measurability, Spec §SC-008]
- [ ] CHK043 SC-009 的“按时”“成功”与“连续 3 天”是否有容差、观测方式和失败重试规则？[Measurability, Spec §SC-009]
- [ ] CHK044 SC-010 的“30 分钟完成无状态调用集成”是否定义起点、终点、文档范围和协助边界？[Measurability, Spec §SC-010]
- [ ] CHK045 SC-001/SC-013 的 15/10 分钟是否定义参与者画像、起点和“完成”的判定？[Measurability, Spec §SC-001/§SC-013]

## 6. 场景与边界覆盖

- [ ] CHK046 一个用户轮次内多个 Tool 依次执行时，部分成功/部分失败的回填顺序与最终终止原因是否定义？[Coverage, Spec §US2/§FR-011]
- [ ] CHK047 Session 在请求执行中归档、或 Agent Profile 在执行中被删除/改名时，进行中请求、历史 Session、`scheduled_tasks` 与 `task_executions` 的归属是否定义？[Coverage/Gap, Spec §FR-027, Contract cli §2.9, DataModel §2.4]
- [ ] CHK048 同一 Session 的并发请求按到达顺序处理时，排队上限、等待超时、过载拒绝与 60 秒请求超时的组合行为是否定义？100 并发 Session 下 SQLite 单写者与锁等待是否纳入该定义？[Coverage/NFR, Spec §Edge Cases/§SC-006, DataModel §6]
- [ ] CHK049 `MEMORY.md` 被手工改坏（缺分区、重复分区、非列表行、超长单行）时的容错边界是否定义？[Coverage/Edge, Spec §Edge Cases, DataModel §1.6]
- [ ] CHK050 归档记忆的截断阈值、截断单位（字符/条目）、顺序（最旧优先）与可配置性是否在需求中明确？[Edge/Clarity, Spec §FR-017, DataModel §1.6]
- [ ] CHK051 核心记忆与 Bootstrap 全量注入导致上下文超限时的行为是否定义（拒绝、提示、压缩还是其他）？[Edge/Gap, Spec §FR-017, DataModel §1.6]
- [ ] CHK052 `max_iterations` 在 Tool 调用中途达到、尚有未回填结果时的终止与消息持久化规则是否定义？[Edge, Spec §US2-AS4, DataModel §2.7]
- [ ] CHK053 未知 Tool 的失败审计、错误回填和循环继续是否在所有契约中一致？[Edge, Spec §Edge Cases, Contract builtin-tools §10.3]
- [ ] CHK054 Provider 超时、空响应、格式异常是否分别定义失败类型、可重试性与审计字段？[Edge, Spec §Edge Cases/§FR-010, DataModel §2.3]
- [ ] CHK055 MCP Server 启动失败、运行中断连、重启后 `tools/list` 刷新与 Tool 可见性是否定义？[Coverage, Contract mcp-config §2/§4]
- [ ] CHK056 REST 查询 Session 历史时，tool 角色消息与 assistant `toolCalls` 是否在响应契约中有完整结构与示例？[Coverage, Contract rest-api §2.3, DataModel §2.7]

## 7. 非功能与运维

- [ ] CHK057 结构化日志的字段、级别、保留周期与敏感信息脱敏是否有可验收要求？[NFR/Gap, Spec §FR-034, DataModel §6]
- [ ] CHK058 SQLite 与 `MEMORY.md` 的备份、恢复、损坏检测与升级策略是否定义？[NFR/Gap, Spec §FR-015, DataModel §6]
- [ ] CHK059 健康检查的 UP/DOWN 判定是否纳入 SQLite、Provider、MCP 依赖状态？[NFR/Gap, Spec §FR-027/§FR-034, Contract rest-api §2.9]
- [ ] CHK060 Session、审计、任务历史的保留与清理策略是否定义？1.0 不物理删除的承诺是否与长期运行和磁盘增长假设一致？[NFR/Gap, DataModel §6]
- [ ] CHK061 项目主页的托管方式、可访问性、语言版本和验收环境是否定义？[NFR/Gap, Spec §FR-037/§SC-013]
- [ ] CHK062 哪些 Agent 配置改动下一轮生效、哪些需要重启（正文/Skill、Schedule、Provider、Tool、MCP）是否定义？[Operations/Clarity, Contract agent-definition §1/§2.2, Contract mcp-config §2]

## 8. 依赖与假设

- [ ] CHK063 外部依赖（DeepSeek/Kimi、公开天气/新闻 API、webhook）的可用性假设、降级与验收替身是否明确？Provider 默认模型、base URL、temperature 默认值的来源与校验责任是否同时明确？[Dependency, Spec §Assumptions/§FR-007/§FR-035, DataModel §1.2, Plan §Technical Context]
- [ ] CHK064 “可信内网、无认证”的假设是否配套明确部署控制（反向代理、网络隔离、绑定地址）并在文档中承诺？[Assumption, Spec §FR-031/§Assumptions]
- [ ] CHK065 单节点与 `serve`/`gateway` 二选一的假设是否说明违反后果与检测方式？[Assumption, Spec §Assumptions, Contract cli §2.4/§2.5]
- [ ] CHK066 Windows 开发与 Linux 部署的路径、软链接、Shell 白名单差异是否明确为假设或需求？[Assumption, Spec §Assumptions]
- [ ] CHK067 规格对 `docs/ReleaseBaseline.md` 的依赖是否固定到版本/提交，避免基线漂移后验收口径失效？[Traceability/Dependency, Spec §Input, Constitution Governance]

## 9. 待裁决冲突与歧义

- [ ] CHK068 单消息多 `toolCalls` 时，审计粒度应裁决为“每个 toolCall 一行”还是“每个模型请求一行”，以同时满足 FR-012/FR-014 与 DataModel §2.2？[Conflict, Spec §FR-012/§FR-014, DataModel §2.2]
- [ ] CHK069 定时任务重叠触发被跳过时，是否应写入 `task_executions` 或等效审计行，以同时满足 Spec Edge Cases 的“记录每次执行状态”与 DataModel §2.5 的“跳过不写行”？[Conflict, Spec §Edge Cases, DataModel §2.5]
- [ ] CHK070 通知渠道 URL 中的 webhook key 应视为明文密钥禁止，还是作为 URL 凭据豁免？DataModel §2.6 与 REST 契约示例需要统一。[Conflict, DataModel §2.6, Contract rest-api §3.1]
- [ ] CHK071 `session_id` 的 128 字符上限如何与 user/profile 最大长度共存？需要缩短、转义还是提高上限？[Conflict, DataModel §2.1, Contract rest-api §2.1]
- [ ] CHK072 REST 60 秒请求超时与 `shell` 最长 300 秒、`http` 最长 60 秒的 Tool 超时冲突时，应采用哪个口径？请求超时是否取消正在执行的 Tool？[Conflict, Contract rest-api §2.2, Contract builtin-tools §4–§5]
- [ ] CHK073 `AGENT.md` 正文究竟可以为空，还是必须包含正文标题？需要统一加载与校验口径。[Conflict, Contract agent-definition §5]
- [ ] CHK074 SC-009 的“通过 CLI 手动补跑”具体指哪个命令？是否需要新增 schedule run，还是用 `chat` 并接受不同的审计归属？[Ambiguity, Spec §SC-009, Contract cli]
- [ ] CHK075 各 `notify` 渠道类型的 payload schema、成功判定和可重试性是否需要定义，`generic` 类型的契约是什么？[Ambiguity, DataModel §2.6, Contract builtin-tools §9]

## Notes

- 只有 reviewer 在评审后确认某条需求质量准则已满足时，才将该项标记为 `[x]`。
- 需求仍含糊、缺失或存在冲突时保持未勾选，并在条目下追加评审意见。
- `/speckit-implement` 会读取 checklist 的勾选状态作为门禁，但不得修改标记。
- 本文件是自定义发布门禁清单；`checklists/requirements.md` 是 `/speckit-specify` 与 `/speckit-clarify` 维护的内置规格质量清单，二者生命周期独立。
- 标有 `[Conflict]` 或 `[Ambiguity]` 的条目应在实现前裁决，并回写到 spec、data-model 或对应契约，不应在代码中静默选择。
- 评审依据：`spec.md`、`plan.md`、`data-model.md`、`contracts/`、`tasks.md`、`.specify/memory/constitution.md` 与 `docs/ReleaseBaseline.md`。
