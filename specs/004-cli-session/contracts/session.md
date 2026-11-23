# Contract: Session Manager and Persistence

**Feature**: CLI and Session Persistence  
**Consumers**: `AgentService`、CLI `chat` 命令、后续 Web/Scheduler 入口

## 1. 公开类型

| 类型 | 模块/包 | 职责 |
|---|---|---|
| `SessionManager` | `lytgos-core` / `io.lytgos.core.session` | 会话统一门面接口 |
| `Session` | `lytgos-core` / `io.lytgos.core.session` | 会话领域对象（已有） |
| `SessionEntity` | `lytgos-storage` / `io.lytgos.storage.session` | `sessions` 表 JPA 实体 |
| `SessionRepository` | `lytgos-storage` / `io.lytgos.storage.session` | `JpaRepository<SessionEntity, String>` |
| `SessionJsonCodec` | `lytgos-storage` / `io.lytgos.storage.session` | 消息列表 JSON 编解码 |
| `JpaSessionManager` | `lytgos-storage` / `io.lytgos.storage.session` | `SessionManager` 的 JPA 实现，Spring `@Component` |

## 2. `SessionManager` 接口

```java
public interface SessionManager {
  Session getOrCreate(String channel, String user, String profileName);

  Optional<Session> get(String sessionId);

  void save(Session session);
}
```

### 2.1 `getOrCreate(channel, user, profileName)`

**前置条件**

- 三个参数均非 `null`、非空白（去首尾空格后非空）。
- 三个参数均不包含 `:`。
- AgentProfile 是否存在由调用方（`AgentService`）校验；本方法不查询 Profile。

**行为**

1. 按 `channel + ":" + user + ":" + profileName` 计算 `session_id`。
2. 查询 `sessions` 表；存在则把 `messages_json` 解码为 `Session` 并返回。
3. 不存在则创建空历史 `Session`，以 `status=ACTIVE`、当前时间为 `created_at`/`last_active_at` 立即插入，并返回。
4. 重复调用同一三元组返回相同 `session_id` 和相同历史。
5. 任一参数不同则生成不同 `session_id`，互不影响。

**异常**

| 情况 | 异常 |
|---|---|
| 参数为 `null` | `NullPointerException` |
| 参数为空白或含 `:` | `IllegalArgumentException`，消息包含非法字段名 |
| 数据库读取/写入失败 | 运行时异常向上抛出，不返回空会话 |

### 2.2 `get(sessionId)`

**行为**

- `sessionId` 为 `null`、空白或不存在：返回 `Optional.empty()`。
- 存在：返回携带完整历史的新 `Session` 实例。
- 不修改数据库。

**异常**

- 数据读取失败：运行时异常向上抛出。
- `messages_json` 非法：`IllegalStateException`（由 `SessionJsonCodec` 抛出），不静默返回空历史。

### 2.3 `save(session)`

**前置条件**

- `session` 非 `null`。
- `Session` 的三元组字段与 `session_id` 一致；`JpaSessionManager` 不重新生成 `session_id`。

**行为**

1. 以 `session.sessionId()` 为键执行 upsert。
2. 新记录：`created_at` = 当前时间，`status` = `ACTIVE`，`archived_at` = `null`。
3. 已有记录：保留 `created_at` 与 `status`，覆盖 `messages_json`，刷新 `last_active_at`。
4. 消息历史通过 `SessionJsonCodec.encode` 整体写入 `messages_json`。
5. 保存失败抛出运行时异常，不吞掉。

## 3. 会话标识规则

```text
session_id = channel + ":" + user + ":" + profileName
```

| 输入 | 结果 |
|---|---|
| `("cli", "wang", "default")` | `cli:wang:default` |
| `("web", "wang", "default")` | `web:wang:default` |
| `("cli", "li", "default")` | `cli:li:default` |
| `("cli", "wang", "weather")` | `cli:wang:weather` |
| `("cli", "wang:li", "default")` | `IllegalArgumentException` |

**唯一生成点**：`JpaSessionManager` 的私有方法。CLI、AgentService、后续 Web/Scheduler 都不得拼接 `session_id`。

## 4. 持久化契约

### 4.1 表结构

Flyway `lytgos-storage/src/main/resources/db/migration/sqlite/V3__init.sql`：

```sql
CREATE TABLE IF NOT EXISTS sessions (
    session_id TEXT PRIMARY KEY,
    profile_name TEXT NOT NULL,
    channel TEXT NOT NULL,
    user_id TEXT NOT NULL,
    messages_json TEXT NOT NULL DEFAULT '[]',
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at TEXT NOT NULL,
    last_active_at TEXT NOT NULL,
    archived_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_sessions_last_active_at ON sessions (last_active_at);
```

### 4.2 JPA 映射

- 实体使用 `@Entity`、`@Table(name = "sessions")`、`@Id` 字符串主键。
- 无 `@GeneratedValue`；主键由 `JpaSessionManager` 写入。
- 时间字段使用 `String` 存储 ISO-8601 文本，与 V1/V2 保持一致。
- 提供 JPA 需要的 `protected` 无参构造器和完整业务构造器。
- 所有列名通过 `@Column(name = "...")` 显式映射，不依赖命名策略。

### 4.3 JSON 编解码

```java
String encode(List<AgentMessage> messages);

List<AgentMessage> decode(String json);
```

| 输入 | 输出 |
|---|---|
| `null` / 空列表 | `"[]"` |
| 非空列表 | 合法 JSON 数组，元素字段为 `role`、`content`、`toolName`、`toolCallId`、`toolCalls` |
| `null` / 空白字符串 | 空列表 |
| `"[]"` | 空列表 |
| 非法 JSON | `IllegalStateException("会话消息 JSON 无法解析: ...")` |

编解码必须保留消息顺序、角色、文本、工具名、工具调用 ID 与工具调用参数。`ObjectMapper` 由 storage 模块创建并持有，不使用全局静态实例。

## 5. Spring 装配契约

| Bean | 来源 | 说明 |
|---|---|---|
| `SessionRepository` | Spring Data JPA | 由 `@EnableJpaRepositories(basePackages = "io.lytgos")` 扫描 |
| `JpaSessionManager` | `@Component` | 注入 `SessionRepository`、`SessionJsonCodec`；时间使用 UTC 系统时钟 |
| `SessionManager` | `JpaSessionManager` | 注入接口类型的使用方 |
| `AgentService` | `AgentRuntimeConfiguration` | 构造参数改为 `SessionManager` |

`LytgosApplication` 启动日志必须出现非零的 `Finished Spring Data repository scanning ... found N JPA repository interfaces`。实体与仓库扫描由 `PersistenceConfiguration` 上的 `@EntityScan("io.lytgos")` 与 `@EnableJpaRepositories("io.lytgos")` 显式声明，覆盖 storage 的 `session`、`llm`、`tool` 三个仓库包。

## 6. AgentService 契约变更

```java
public AgentService(
    AgentProfileRegistry registry,
    ReActLoop reActLoop,
    SessionManager sessionManager)
```

| 场景 | 行为 |
|---|---|
| 正常处理 | `reActLoop.run` → `sessionManager.save(session)` → 返回回复 |
| Profile 不存在 | 抛出 `IllegalArgumentException`，不保存 |
| ReAct 抛异常 | 异常向上抛出，不保存不完整会话；`AgentProfileContext` 在 `finally` 清理 |
| 保存失败 | 异常向上抛出，不返回“已保存”假象 |

## 7. 验收映射

| 测试 | 覆盖 |
|---|---|
| `SessionManagerTest.sameTripleReturnsSameSession` | 同一三元组幂等 |
| `SessionManagerTest.differentChannelCreatesDifferentSession` | channel 差异隔离 |
| `SessionManagerTest.differentUserCreatesDifferentSession` | user 差异隔离 |
| `SessionManagerTest.differentProfileCreatesDifferentSession` | profile 差异隔离 |
| `SessionManagerTest.rejectsSegmentContainingColon` | 标识只由门面生成，非法输入拒绝 |
| `SessionRepositoryTest.flywayCreatesSessionsTable` | V3 表结构与 `Flyway` 迁移 |
| `SessionRepositoryTest.messagesRoundTripKeepsHistory` | `messages_json` 回读完整 |
| `SessionRepositoryTest.historySurvivesNewContext` | 新 Spring 上下文（模拟重启）仍能读取历史 |

测试方法名为英文，`@DisplayName` 使用中文描述。`SessionRepositoryTest` 使用 `@Tag("integration")` 之外的默认测试组，确保随 `mvn verify` 运行；如需要临时目录 SQLite，使用 `@TempDir` 与独立 `lytgos.workspace`。
