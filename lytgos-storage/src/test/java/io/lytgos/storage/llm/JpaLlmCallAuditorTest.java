package io.lytgos.storage.llm;

import static org.assertj.core.api.Assertions.assertThat;

import io.lytgos.core.audit.LlmCallAuditor.LlmCallRecord;
import io.lytgos.storage.StorageTestApplication;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * JpaLlmCallAuditor 从 core 记录到数据库实体的集成测试。
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
class JpaLlmCallAuditorTest {

  /** 本测试类独占的数据库目录。 */
  private static final Path DATABASE_DIRECTORY = createDatabaseDirectory();

  /** 被测审计实现。 */
  @Autowired private JpaLlmCallAuditor auditor;

  /** LlmCall 仓库，用于查询审计结果。 */
  @Autowired private LlmCallRepository repository;

  /**
   * 注册临时 SQLite 数据源地址。
   *
   * @param registry 动态属性注册表
   */
  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    // databaseFile 是本次测试使用的 SQLite 文件绝对路径。
    String databaseFile =
        DATABASE_DIRECTORY.resolve("lytgos-auditor-test.db").toAbsolutePath().toString();
    registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + databaseFile);
  }

  @BeforeEach
  void clearTable() {
    // 每个测试前清空审计表，避免测试之间互相影响。
    repository.deleteAll();
  }

  @Test
  @DisplayName("成功和失败记录都映射为实体并补上 created_at")
  void recordsSuccessAndFailureWithCreatedAt() {
    // successRecord 是成功调用记录。
    LlmCallRecord successRecord =
        new LlmCallRecord(
            "session-1", "ops-agent", "deepseek", "deepseek-chat", 10, 20, 30, 42L, true, null);
    // failureRecord 是失败调用记录。
    LlmCallRecord failureRecord =
        new LlmCallRecord(
            "session-2",
            "ops-agent",
            "kimi",
            "moonshot-v1-8k",
            null,
            null,
            null,
            99L,
            false,
            "timeout");

    auditor.record(successRecord);
    auditor.record(failureRecord);

    // saved 是仓库读回的审计记录。
    List<LlmCall> saved = repository.findAll();
    assertThat(saved).hasSize(2);
    // successEntity 是成功记录。
    LlmCall successEntity = saved.stream().filter(LlmCall::isSuccess).findFirst().orElseThrow();
    // failureEntity 是失败记录。
    LlmCall failureEntity =
        saved.stream().filter(entity -> !entity.isSuccess()).findFirst().orElseThrow();

    assertThat(successEntity.getSessionId()).isEqualTo("session-1");
    assertThat(successEntity.getPromptTokens()).isEqualTo(10);
    assertThat(successEntity.getCompletionTokens()).isEqualTo(20);
    assertThat(successEntity.getTotalTokens()).isEqualTo(30);
    assertThat(successEntity.getErrorMessage()).isNull();
    assertThat(successEntity.getCreatedAt()).isNotBlank();
    assertThat(Instant.parse(successEntity.getCreatedAt())).isNotNull();

    assertThat(failureEntity.getErrorMessage()).isEqualTo("timeout");
    assertThat(failureEntity.getPromptTokens()).isNull();
    assertThat(failureEntity.getCreatedAt()).isNotBlank();
    assertThat(Instant.parse(failureEntity.getCreatedAt())).isNotNull();
  }

  /**
   * 创建本测试类独占的临时目录。
   *
   * @return 临时目录
   */
  private static Path createDatabaseDirectory() {
    try {
      return Files.createTempDirectory("lytgos-auditor-test-");
    } catch (IOException exception) {
      throw new IllegalStateException("无法创建测试数据库目录", exception);
    }
  }
}
