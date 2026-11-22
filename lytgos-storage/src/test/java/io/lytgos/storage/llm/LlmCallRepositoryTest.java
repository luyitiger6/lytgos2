package io.lytgos.storage.llm;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 使用临时 SQLite 与真实 Flyway 迁移的 LlmCall 仓库测试。
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
class LlmCallRepositoryTest {

  /** 本测试类独占的数据库目录。 */
  private static final Path DATABASE_DIRECTORY = createDatabaseDirectory();

  /** LlmCall 仓库。 */
  @Autowired private LlmCallRepository repository;

  /** 用于检查 SQLite 表结构的 JDBC 模板。 */
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
        DATABASE_DIRECTORY.resolve("lytgos-repository-test.db").toAbsolutePath().toString();
    registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + databaseFile);
  }

  @BeforeEach
  void clearTable() {
    // 每个测试前清空审计表，避免测试之间互相影响。
    repository.deleteAll();
  }

  @Test
  @DisplayName("Flyway 建出的 llm_calls 包含 success、error_message 与可空 token 列")
  void flywaySchemaContainsAuditColumns() {
    // columns 是 SQLite PRAGMA 返回的列信息。
    List<Map<String, Object>> columns = jdbcTemplate.queryForList("PRAGMA table_info(llm_calls)");
    // columnsByName 便于按列名检查 notnull 约束。
    Map<String, Map<String, Object>> columnsByName = new LinkedHashMap<>();
    for (Map<String, Object> column : columns) {
      columnsByName.put((String) column.get("name"), column);
    }

    assertThat(columnsByName)
        .containsKeys(
            "success", "error_message", "prompt_tokens", "completion_tokens", "total_tokens");
    assertThat(notNull(columnsByName, "success")).isEqualTo(1);
    assertThat(notNull(columnsByName, "error_message")).isZero();
    assertThat(notNull(columnsByName, "prompt_tokens")).isZero();
    assertThat(notNull(columnsByName, "completion_tokens")).isZero();
    assertThat(notNull(columnsByName, "total_tokens")).isZero();
  }

  @Test
  @DisplayName("成功调用记录可保存并读回全部字段")
  void saveAndReadSuccessRecord() {
    // entity 是待保存的成功调用记录。
    LlmCall entity =
        new LlmCall(
            "session-1",
            "ops-agent",
            "deepseek",
            "deepseek-chat",
            10,
            20,
            30,
            123L,
            true,
            null,
            "2026-11-23T00:00:00Z");

    // saved 是保存后的实体，应带自增主键。
    LlmCall saved = repository.save(entity);

    assertThat(saved.getId()).isNotNull();
    // loaded 是从数据库读回的记录。
    LlmCall loaded = repository.findById(saved.getId()).orElseThrow();
    assertThat(loaded.getSessionId()).isEqualTo("session-1");
    assertThat(loaded.getProfileName()).isEqualTo("ops-agent");
    assertThat(loaded.getProvider()).isEqualTo("deepseek");
    assertThat(loaded.getModel()).isEqualTo("deepseek-chat");
    assertThat(loaded.getPromptTokens()).isEqualTo(10);
    assertThat(loaded.getCompletionTokens()).isEqualTo(20);
    assertThat(loaded.getTotalTokens()).isEqualTo(30);
    assertThat(loaded.getDurationMs()).isEqualTo(123L);
    assertThat(loaded.isSuccess()).isTrue();
    assertThat(loaded.getErrorMessage()).isNull();
    assertThat(loaded.getCreatedAt()).isEqualTo("2026-11-23T00:00:00Z");
  }

  @Test
  @DisplayName("失败调用记录的 success=0、error_message 与空 token 可保存并读回")
  void saveAndReadFailureRecord() {
    // entity 是待保存的失败调用记录。
    LlmCall entity =
        new LlmCall(
            null,
            "ops-agent",
            "kimi",
            "moonshot-v1-8k",
            null,
            null,
            null,
            250L,
            false,
            "timeout",
            "2026-11-23T00:00:01Z");

    // saved 是保存后的失败记录。
    LlmCall saved = repository.save(entity);

    // loaded 是从数据库读回的失败记录。
    LlmCall loaded = repository.findById(saved.getId()).orElseThrow();
    assertThat(loaded.getSessionId()).isNull();
    assertThat(loaded.getPromptTokens()).isNull();
    assertThat(loaded.getCompletionTokens()).isNull();
    assertThat(loaded.getTotalTokens()).isNull();
    assertThat(loaded.isSuccess()).isFalse();
    assertThat(loaded.getErrorMessage()).isEqualTo("timeout");
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
   * 创建本测试类独占的临时目录。
   *
   * @return 临时目录
   */
  private static Path createDatabaseDirectory() {
    try {
      return Files.createTempDirectory("lytgos-repository-test-");
    } catch (IOException exception) {
      throw new IllegalStateException("无法创建测试数据库目录", exception);
    }
  }
}
