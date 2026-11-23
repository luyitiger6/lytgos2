# Research: CLI and Session Persistence

## 1. CLI 重命令如何启动运行时

- **Decision**: `lytgos-cli` 不编译依赖 `lytgos-boot`；重命令通过反射加载 `io.lytgos.boot.LytgosApplication`，用 `SpringApplicationBuilder` 启动 Spring。boot fat JAR 的 `mainClass` 改为 `io.lytgos.cli.LytgosCli`，CLI 与 boot 组装根在同一可执行 JAR 中。
- **Rationale**: boot 已经依赖 cli（聚合所有模块），cli 再依赖 boot 会形成 Maven 循环。反射只跨越运行时类名，编译期保持单向依赖。
- **Alternatives considered**: cli 直接依赖 boot（循环依赖，不可构建）；把 Spring 组装复制到 cli（重复组装根，违反模块边界）；重命令调用外部进程（部署复杂、无法共享上下文）。

## 2. SessionManager 的接口与实现位置

- **Decision**: `SessionManager` 接口放 `lytgos-core/io.lytgos.core.session`；`JpaSessionManager` 放 `lytgos-storage/io.lytgos.storage.session`。接口只有 `getOrCreate(channel,user,profileName)`、`get(sessionId)`、`save(session)`。
- **Rationale**: 延续 core 端口 + storage 实现的依赖倒置；AgentService 只依赖 core，不依赖 JPA。
- **Alternatives considered**: 实现放 core（core 依赖 JPA/SQLite，违反模块规范）；每个入口各自拼接 session_id（已明确禁止）。

## 3. session_id 公式

- **Decision**: `JpaSessionManager` 内部按 `channel + ":" + user + ":" + profile` 生成；三个组成部分必须非空且不得包含冒号；调用方只提供三元组。
- **Rationale**: 确定性、可读、便于排障；标识生成只有一处，避免不同入口格式漂移。
- **Alternatives considered**: UUID（无法按三元组幂等查找）；哈希（不可读且仍要处理碰撞）；调用方拼接（已明确禁止）。

## 4. 消息历史序列化

- **Decision**: storage 新增 Jackson databind，`SessionJsonCodec` 使用 `ObjectMapper` 序列化 `List<AgentMessage>`；`AgentMessage`/`AgentToolCall` 是 record，Jackson 直接支持。JSON 存 `sessions.messages_json` 一列。
- **Rationale**: 结构化、可读、跨语言；Jackson 版本由 Spring Boot BOM 管理，不手写转义逻辑。
- **Alternatives considered**: 手写 JSON（转义、嵌套工具调用容易出错）；Java 序列化（不可读、类演进脆弱）；按消息拆表（超出核心阶段范围）。

## 5. sessions 表与迁移

- **Decision**: 新增 Flyway `V3__init.sql` 创建 `sessions`：`session_id` 主键、`profile_name`、`channel`、`user_id`、`messages_json`、`status`、`created_at`、`last_active_at`、`archived_at`；V1/V2 不改。
- **Rationale**: 迁移历史不可变；`sessions` 是 1.0 六张表之一，跨重启恢复依赖真实数据库。
- **Alternatives considered**: 修改 V1（已应用迁移不可变）；依赖 `ddl-auto=update`（SQLite 演进不可靠）。

## 6. 轻命令的数据访问

- **Decision**: 轻命令不启动 Spring。`init`/`status`/`profile` 管理直接操作工作区文件；`provider list` 用 SnakeYAML 读取 classpath `application.yaml`；`session list`/`status` 用 sqlite-jdbc 直读 `.lytgos/lytgos.db`。
- **Rationale**: 满足“查询秒回”；SQLite 文件是本地只读查询，不需要 JPA 上下文。
- **Alternatives considered**: 所有命令都启动 Spring（查询慢，违背分流）；为查询单独建一套 repository（重复实现）。

## 7. CLI 输入输出与 ArchUnit 约束

- **Decision**: 输出统一用 Picocli `CommandSpec.commandLine().getOut()`；chat 输入用 `FileDescriptor.in` 包装 `BufferedReader`，测试注入内存流。代码不直接访问 `System.out/err/in`。
- **Rationale**: 项目 ArchUnit 规则 `NO_STANDARD_STREAMS` 禁止标准流直接访问；Picocli 输出可测试，`FileDescriptor.in` 保留真实终端交互。
- **Alternatives considered**: 修改 ArchUnit 放行 CLI（削弱全局约束）；`System.console()`（IDE/管道下可能为空）。

## 8. AgentService 保存路径

- **Decision**: `AgentService` 构造函数从 `Consumer<Session>` 改为 `SessionManager`；处理成功后调用 `sessionManager.save(session)`；异常路径仍由 `finally` 清理 AgentProfileContext。
- **Rationale**: 去掉临时回调，会话持久化只有一个门面；第 18 节后 CLI/Web/定时入口共享同一保存路径。
- **Alternatives considered**: 保留 Consumer 并让 boot 适配（多一层无意义间接）；AgentService 直接依赖 JPA（core 依赖 storage，禁止）。

## 9. 命令行为与默认值

- **Decision**:
  - `init` 幂等创建 `.lytgos/{agents,skills,memory,sessions,logs,profiles}`、`AGENTS.md`、`SOUL.md`、`USER.md`、`MEMORY.md`、`mcp_servers.yaml`、`profiles/default.yaml`；已有文件不覆盖。
  - `profile create <name>` 默认 `provider=deepseek`、`model=deepseek-chat`；名称只允许 `[A-Za-z0-9_-]+`。
  - `profile list/show/delete` 只操作 `profiles/` 下的 YAML。
  - `provider list` 只显示 name/base-url，不读取/打印 api-key。
  - `tool list` 在工具能力接入前返回空说明。
  - `session list` 展示 session_id/profile/channel/user/status/last_active_at。
  - `status` 展示 workspace、profile 数、provider 数、session 数、数据库是否存在。
- **Rationale**: 课件未给出每个查询命令的详细参数，采用最小可用、可脚本化的默认行为。
- **Alternatives considered**: 为每个命令增加复杂参数（超出核心阶段）；交互式确认（不利于脚本与自动化）。

## 10. 运行模式

- **Decision**: `chat` 用 `WebApplicationType.NONE` 启动 Spring，获取 AgentService/SessionManager 后运行 CliChannel，退出时关闭上下文；`serve` 用 `SERVLET` 启动并保持运行；`gateway` 用 `NONE` 启动后阻塞，作为守护进程骨架。
- **Rationale**: 三种模式共享同一 Profile/Session 存储；核心阶段只有 CLI Channel，gateway 先保留可启动骨架。
- **Alternatives considered**: gateway 与 chat 相同（语义不符）；不实现 gateway（缺少 12 命令之一）。

## 11. 测试策略

- **Decision**: `SessionManagerTest` 验证三元组幂等/隔离和 id 生成；`SessionRepositoryTest` 验证 Flyway V3 schema、JSON 回读、使用第二个 Spring 上下文读取同一 SQLite 文件模拟重启；`LytgosCliTest` 验证 12 个叶子命令和 root help。现有 67 个测试保持回归。
- **Rationale**: 会话层是后续所有入口的地基；命令 help 可以低成本自动化，轻/重启动行为留人工验证。
- **Alternatives considered**: 为每个 CLI 命令写端到端测试（成本高、收益低）；只做手动测试（会话口径回归风险高）。

## 12. 依赖版本核实

- Picocli 4.7.6（项目 BOM 已锁定）。
- Jackson databind 由 Spring Boot 3.5.16 BOM 管理，storage 只声明 groupId/artifactId。
- sqlite-jdbc 3.49.1.0 已在 storage 使用，CLI 复用同一 BOM 版本。
- Flyway 11.7.2 的 SQLite 支持内置于 `flyway-core`，不引入不存在的 `flyway-database-sqlite`。
