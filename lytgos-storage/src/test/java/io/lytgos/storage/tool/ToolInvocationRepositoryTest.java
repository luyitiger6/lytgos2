package io.lytgos.storage.tool;

import static org.assertj.core.api.Assertions.assertThat;

import io.lytgos.core.audit.ToolInvocationRecord;
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
 * 使用临时 SQLite 与 Flyway V1+V2 的 tool_invocations 仓库测试。
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
class ToolInvocationRepositoryTest {

  /** 本测试类独占的数据库目录。 */
  private static final Path DATABASE_DIRECTORY = createDatabaseDirectory();

  /** 工具调用仓库。 */
  @Autowired private ToolInvocationRepository repository;

  /** JPA 审计实现。 */
  @Autowired private JpaToolInvocationAuditor auditor;

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
        DATABASE_DIRECTORY.resolve("lytgos-tool-invocation-test.db").toAbsolutePath().toString();
    registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + databaseFile);
  }

  @BeforeEach
  void clearTable() {
    // 每个测试前清空工具审计表，避免测试之间互相影响。
    repository.deleteAll();
  }

  @Test
  @DisplayName("Flyway V2 建出的 tool_invocations 包含 success 与 error_message 列")
  void flywaySchemaContainsAuditColumns() {
    // columns 是 SQLite PRAGMA 返回的列信息。
    List<Map<String, Object>> columns =
        jdbcTemplate.queryForList("PRAGMA table_info(tool_invocations)");
    // columnsByName 便于按列名检查 notnull 约束。
    Map<String, Map<String, Object>> columnsByName = new LinkedHashMap<>();
    for (Map<String, Object> column : columns) {
      columnsByName.put((String) column.get("name"), column);
    }

    assertThat(columnsByName)
        .containsKeys(
            "session_id",
            "tool_name",
            "input_json",
            "success",
            "error_message",
            "duration_ms",
            "created_at");
    assertThat(notNull(columnsByName, "tool_name")).isEqualTo(1);
    assertThat(notNull(columnsByName, "success")).isEqualTo(1);
    assertThat(notNull(columnsByName, "duration_ms")).isEqualTo(1);
    assertThat(notNull(columnsByName, "created_at")).isEqualTo(1);
    assertThat(notNull(columnsByName, "error_message")).isZero();
    assertThat(notNull(columnsByName, "input_json")).isZero();
  }

  @Test
  @DisplayName("成功与失败工具调用记录可保存并读回")
  void saveAndReadSuccessAndFailureRecords() {
    // successEntity 是待保存的成功记录。
    ToolInvocation successEntity =
        new ToolInvocation("session-1", "weather", "{}", true, null, 12L, "2026-11-23T00:00:00Z");
    // failureEntity 是待保存的失败记录。
    ToolInvocation failureEntity =
        new ToolInvocation(
            "session-1",
            "shell",
            "{\"command\":\"dir\"}",
            false,
            "denied",
            5L,
            "2026-11-23T00:00:01Z");

    repository.save(successEntity);
    repository.save(failureEntity);

    // saved 是仓库读回的全部记录。
    List<ToolInvocation> saved = repository.findAll();
    assertThat(saved).hasSize(2);
    // loadedSuccess 是成功记录。
    ToolInvocation loadedSuccess =
        saved.stream().filter(ToolInvocation::isSuccess).findFirst().orElseThrow();
    // loadedFailure 是失败记录。
    ToolInvocation loadedFailure =
        saved.stream().filter(entity -> !entity.isSuccess()).findFirst().orElseThrow();
    assertThat(loadedSuccess.getToolName()).isEqualTo("weather");
    assertThat(loadedSuccess.getInputJson()).isEqualTo("{}");
    assertThat(loadedSuccess.getErrorMessage()).isNull();
    assertThat(loadedSuccess.getDurationMs()).isEqualTo(12L);
    assertThat(loadedFailure.getToolName()).isEqualTo("shell");
    assertThat(loadedFailure.getErrorMessage()).isEqualTo("denied");
  }

  @Test
  @DisplayName("JpaToolInvocationAuditor 映射 core 记录并补齐 created_at")
  void auditorMapsRecordAndFillsCreatedAt() {
    // record 是 core 审计记录。
    ToolInvocationRecord record =
        new ToolInvocationRecord("session-2", "echo", "{}", true, null, 3L);

    auditor.record(record);

    // saved 是仓库读回的审计记录。
    ToolInvocation saved = repository.findAll().get(0);
    assertThat(saved.getSessionId()).isEqualTo("session-2");
    assertThat(saved.getToolName()).isEqualTo("echo");
    assertThat(saved.getInputJson()).isEqualTo("{}");
    assertThat(saved.isSuccess()).isTrue();
    assertThat(saved.getErrorMessage()).isNull();
    assertThat(saved.getDurationMs()).isEqualTo(3L);
    assertThat(saved.getCreatedAt()).isNotBlank();
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
      return Files.createTempDirectory("lytgos-tool-invocation-test-");
    } catch (IOException exception) {
      throw new IllegalStateException("无法创建测试数据库目录", exception);
    }
  }
}
