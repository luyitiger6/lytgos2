package io.lytgos.storage.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.lytgos.core.llm.AgentResponse;
import io.lytgos.core.llm.AgentRole;
import io.lytgos.core.llm.AgentToolCall;
import io.lytgos.core.session.Session;
import io.lytgos.core.session.SessionManager;
import io.lytgos.core.tool.ToolResult;
import io.lytgos.storage.StorageTestApplication;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 使用临时 SQLite 与真实 Flyway 迁移的会话仓库测试。
 *
 * <p>覆盖 V3 表结构、消息 JSON 往返、时间戳更新，以及新建 Spring 上下文后的历史恢复。
 *
 * @author LytgOS2 Team
 */
@SpringBootTest(
    classes = StorageTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "spring.datasource.driver-class-name=org.sqlite.JDBC",
      "spring.datasource.hikari.maximum-pool-size=1",
      "spring.jpa.hibernate.ddl-auto=none",
      "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
      "spring.flyway.enabled=true",
      "spring.flyway.locations=classpath:db/migration/sqlite",
      "spring.main.banner-mode=off"
    })
class SessionRepositoryTest {

  /** 本测试类独占的数据库目录。 */
  private static final Path DATABASE_DIRECTORY = createDatabaseDirectory();

  /** 测试渠道。 */
  private static final String CHANNEL = "cli";

  /** 测试 AgentProfile。 */
  private static final String PROFILE = "default";

  /** 会话门面。 */
  @Autowired private SessionManager sessionManager;

  /** 会话仓库，用于测试间清表。 */
  @Autowired private SessionRepository sessionRepository;

  /** 用于检查 SQLite 表结构与时间戳的 JDBC 模板。 */
  @Autowired private JdbcTemplate jdbcTemplate;

  /**
   * 注册临时 SQLite 数据源地址。
   *
   * @param registry 动态属性注册表
   */
  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    // databaseFile 是本次测试使用的 SQLite 文件绝对路径。
    String databaseFile =
        DATABASE_DIRECTORY.resolve("lytgos-session-test.db").toAbsolutePath().toString();
    registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + databaseFile);
  }

  /** 每个测试前清空会话表，避免测试之间互相影响。 */
  @BeforeEach
  void clearTable() {
    sessionRepository.deleteAll();
  }

  @Test
  @DisplayName("Flyway 建出的 sessions 包含全部列、非空约束与默认值")
  void flywayCreatesSessionsTableWithExpectedColumns() {
    // columns 是 SQLite PRAGMA 返回的列信息。
    List<Map<String, Object>> columns = jdbcTemplate.queryForList("PRAGMA table_info(sessions)");
    // columnsByName 便于按列名检查约束与默认值。
    Map<String, Map<String, Object>> columnsByName = new LinkedHashMap<>();
    for (Map<String, Object> column : columns) {
      columnsByName.put((String) column.get("name"), column);
    }

    assertThat(columnsByName)
        .containsKeys(
            "session_id",
            "profile_name",
            "channel",
            "user_id",
            "messages_json",
            "status",
            "created_at",
            "last_active_at",
            "archived_at");
    assertThat(primaryKey(columnsByName, "session_id")).isEqualTo(1);
    assertThat(notNull(columnsByName, "profile_name")).isEqualTo(1);
    assertThat(notNull(columnsByName, "channel")).isEqualTo(1);
    assertThat(notNull(columnsByName, "user_id")).isEqualTo(1);
    assertThat(notNull(columnsByName, "messages_json")).isEqualTo(1);
    assertThat(notNull(columnsByName, "status")).isEqualTo(1);
    assertThat(notNull(columnsByName, "created_at")).isEqualTo(1);
    assertThat(notNull(columnsByName, "last_active_at")).isEqualTo(1);
    assertThat(notNull(columnsByName, "archived_at")).isZero();
    assertThat(defaultValue(columnsByName, "messages_json")).isEqualTo("'[]'");
    assertThat(defaultValue(columnsByName, "status")).isEqualTo("'ACTIVE'");
  }

  @Test
  @DisplayName("消息 JSON 往返后顺序、角色与工具调用完整")
  void messagesRoundTripKeepsOrderAndToolCalls() {
    // session 是本次测试的会话。
    Session session = sessionManager.getOrCreate(CHANNEL, "roundtrip", PROFILE);
    session.appendUser("你好");
    // call 是模型请求的工具调用。
    AgentToolCall call =
        new AgentToolCall("call-1", "http_get", "{\"url\":\"https://example.com\"}");
    session.appendAssistant(new AgentResponse("", List.of(call)));
    session.appendToolResult(call, ToolResult.ok("ok"));
    session.appendUser("继续");
    sessionManager.save(session);

    // loaded 是从 SQLite 重新还原的会话。
    Session loaded = sessionManager.get("cli:roundtrip:default").orElseThrow();

    assertThat(loaded.messageCount()).isEqualTo(4);
    assertThat(loaded.messages().get(0).role()).isEqualTo(AgentRole.USER);
    assertThat(loaded.messages().get(0).content()).isEqualTo("你好");
    assertThat(loaded.messages().get(1).role()).isEqualTo(AgentRole.ASSISTANT);
    assertThat(loaded.messages().get(1).toolCalls()).hasSize(1);
    assertThat(loaded.messages().get(1).toolCalls().get(0).id()).isEqualTo("call-1");
    assertThat(loaded.messages().get(1).toolCalls().get(0).name()).isEqualTo("http_get");
    assertThat(loaded.messages().get(1).toolCalls().get(0).arguments())
        .isEqualTo("{\"url\":\"https://example.com\"}");
    assertThat(loaded.messages().get(2).role()).isEqualTo(AgentRole.TOOL);
    assertThat(loaded.messages().get(2).toolName()).isEqualTo("http_get");
    assertThat(loaded.messages().get(2).toolCallId()).isEqualTo("call-1");
    assertThat(loaded.messages().get(2).content()).isEqualTo("ok");
    assertThat(loaded.messages().get(3).role()).isEqualTo(AgentRole.USER);
    assertThat(loaded.messages().get(3).content()).isEqualTo("继续");
  }

  @Test
  @DisplayName("再次保存刷新最后活跃时间并保留创建时间")
  void saveUpdatesLastActiveAndKeepsCreatedAt() {
    // session 是本次测试的会话。
    Session session = sessionManager.getOrCreate(CHANNEL, "time", PROFILE);
    // sessionId 是会话主键。
    String sessionId = session.sessionId();
    // createdAt 是首次创建时间。
    String createdAt = queryCreatedAt(sessionId);
    // 人为把最后活跃时间改成过去，确保后续保存必须刷新它。
    jdbcTemplate.update(
        "update sessions set last_active_at = ? where session_id = ?",
        "2000-01-01T00:00:00Z",
        sessionId);

    session.appendUser("刷新时间");
    sessionManager.save(session);

    assertThat(queryCreatedAt(sessionId)).isEqualTo(createdAt);
    assertThat(queryLastActiveAt(sessionId)).isNotEqualTo("2000-01-01T00:00:00Z");
  }

  @Test
  @DisplayName("新建 Spring 上下文后仍能读取完整历史")
  void historySurvivesNewApplicationContext() {
    // restartDatabase 是模拟重启使用的独立数据库文件。
    Path restartDatabase = DATABASE_DIRECTORY.resolve("lytgos-session-restart-test.db");
    try (ConfigurableApplicationContext first = startContext(restartDatabase)) {
      // manager 是第一个上下文中的会话门面。
      SessionManager manager = first.getBean(SessionManager.class);
      // session 是重启前保存的会话。
      Session session = manager.getOrCreate(CHANNEL, "restart", PROFILE);
      session.appendUser("重启前的消息");
      session.appendAssistant(new AgentResponse("重启前的回复", List.of()));
      manager.save(session);
    }

    try (ConfigurableApplicationContext second = startContext(restartDatabase)) {
      // manager 是第二个上下文中的会话门面。
      SessionManager manager = second.getBean(SessionManager.class);
      // loaded 是从同一个 SQLite 文件恢复的会话。
      Session loaded = manager.get("cli:restart:default").orElseThrow();

      assertThat(loaded.messageCount()).isEqualTo(2);
      assertThat(loaded.messages().get(0).content()).isEqualTo("重启前的消息");
      assertThat(loaded.messages().get(1).content()).isEqualTo("重启前的回复");
    }
  }

  /**
   * 启动一个指向指定 SQLite 文件的最小 Spring 上下文。
   *
   * @param databaseFile 数据库文件
   * @return 已启动的上下文
   */
  private static ConfigurableApplicationContext startContext(Path databaseFile) {
    return new SpringApplicationBuilder(StorageTestApplication.class)
        .web(WebApplicationType.NONE)
        .properties(
            "spring.datasource.url=jdbc:sqlite:" + databaseFile.toAbsolutePath(),
            "spring.datasource.driver-class-name=org.sqlite.JDBC",
            "spring.datasource.hikari.maximum-pool-size=1",
            "spring.jpa.hibernate.ddl-auto=none",
            "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
            "spring.flyway.enabled=true",
            "spring.flyway.locations=classpath:db/migration/sqlite",
            "spring.main.banner-mode=off")
        .run();
  }

  /**
   * 查询指定会话的创建时间。
   *
   * @param sessionId 会话标识
   * @return 创建时间文本
   */
  private String queryCreatedAt(String sessionId) {
    return jdbcTemplate.queryForObject(
        "select created_at from sessions where session_id = ?", String.class, sessionId);
  }

  /**
   * 查询指定会话的最后活跃时间。
   *
   * @param sessionId 会话标识
   * @return 最后活跃时间文本
   */
  private String queryLastActiveAt(String sessionId) {
    return jdbcTemplate.queryForObject(
        "select last_active_at from sessions where session_id = ?", String.class, sessionId);
  }

  /**
   * 读取指定列的 notnull 标记。
   *
   * @param columnsByName 列名到 PRAGMA 信息的映射
   * @param columnName 列名
   * @return 1 表示 NOT NULL，0 表示可空
   */
  private static int notNull(Map<String, Map<String, Object>> columnsByName, String columnName) {
    // column 是目标列的 PRAGMA 信息。
    Map<String, Object> column = columnsByName.get(columnName);
    return ((Number) column.get("notnull")).intValue();
  }

  /**
   * 读取指定列的主键标记。
   *
   * @param columnsByName 列名到 PRAGMA 信息的映射
   * @param columnName 列名
   * @return 大于 0 表示主键
   */
  private static int primaryKey(Map<String, Map<String, Object>> columnsByName, String columnName) {
    // column 是目标列的 PRAGMA 信息。
    Map<String, Object> column = columnsByName.get(columnName);
    return ((Number) column.get("pk")).intValue();
  }

  /**
   * 读取指定列的默认值表达式。
   *
   * @param columnsByName 列名到 PRAGMA 信息的映射
   * @param columnName 列名
   * @return 默认值表达式文本
   */
  private static String defaultValue(
      Map<String, Map<String, Object>> columnsByName, String columnName) {
    // column 是目标列的 PRAGMA 信息。
    Map<String, Object> column = columnsByName.get(columnName);
    return (String) column.get("dflt_value");
  }

  /**
   * 创建本测试类独占的临时目录。
   *
   * @return 临时目录
   */
  private static Path createDatabaseDirectory() {
    try {
      return Files.createTempDirectory("lytgos-session-test-");
    } catch (IOException exception) {
      throw new IllegalStateException("无法创建测试数据库目录", exception);
    }
  }
}
