package io.lytgos.core.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

/**
 * ContextLoader 的无缓存、错误可见与路径边界测试。
 *
 * @author LytgOS2 Team
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class ContextLoaderTest {

  /** 每个测试独占的临时工作区。 */
  @TempDir private Path workspace;

  @Test
  @DisplayName("加载 Bootstrap 与 Skill 元数据")
  void loadsBootstrapAndSkillMetadata() throws IOException {
    // 写入测试 Bootstrap 与 Skill。
    writeFile("SOUL.md", "你是运维助手");
    writeSkill("daily-report", "daily-report", "生成每日运维简报");
    // loader 是被测对象。
    ContextLoader loader = new ContextLoader(workspace);
    // agentProfile 引用一个 Bootstrap 和一个 Skill。
    AgentProfile agentProfile = agentProfile(List.of("SOUL.md"), List.of("daily-report"));

    // context 是加载结果。
    String context = loader.buildContext(agentProfile);

    assertThat(context).contains("## Bootstrap: SOUL.md");
    assertThat(context).contains("你是运维助手");
    assertThat(context).contains("## Skill: daily-report");
    assertThat(context).contains("生成每日运维简报");
    assertThat(context)
        .contains(workspace.resolve("skills/daily-report/SKILL.md").toAbsolutePath().toString());
  }

  @Test
  @DisplayName("修改 Bootstrap 后下一次加载立即可见，不使用缓存")
  void readsFileChangesWithoutCache() throws IOException {
    // 先写入旧内容。
    writeFile("SOUL.md", "旧内容");
    // loader 是被测对象。
    ContextLoader loader = new ContextLoader(workspace);
    // agentProfile 引用该 Bootstrap。
    AgentProfile agentProfile = agentProfile(List.of("SOUL.md"), List.of());
    // firstContext 是第一次加载结果。
    String firstContext = loader.buildContext(agentProfile);

    writeFile("SOUL.md", "新内容");

    // secondContext 是修改后的加载结果。
    String secondContext = loader.buildContext(agentProfile);
    assertThat(firstContext).contains("旧内容");
    assertThat(secondContext).contains("新内容");
    assertThat(secondContext).doesNotContain("旧内容");
  }

  @Test
  @DisplayName("显式引用的 Skill 缺失时抛清晰错误")
  void missingSkillFails() {
    // loader 是被测对象。
    ContextLoader loader = new ContextLoader(workspace);
    // agentProfile 引用不存在的 Skill。
    AgentProfile agentProfile = agentProfile(List.of(), List.of("missing"));

    assertThatThrownBy(() -> loader.buildContext(agentProfile))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("missing");
  }

  @Test
  @DisplayName("Bootstrap 缺失时记录 WARN 并继续加载 Skill")
  void missingBootstrapWarnsAndContinues() throws IOException {
    // 写入一个合法 Skill。
    writeSkill("daily-report", "daily-report", "生成每日运维简报");
    // loader 是被测对象。
    ContextLoader loader = new ContextLoader(workspace);
    // agentProfile 引用缺失的 Bootstrap 和合法 Skill。
    AgentProfile agentProfile = agentProfile(List.of("missing.md"), List.of("daily-report"));
    // logger 是被测类的日志记录器。
    Logger logger = (Logger) LoggerFactory.getLogger(ContextLoader.class);
    // appender 捕获日志事件。
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      // context 是加载结果，缺失 Bootstrap 不应抛异常。
      String context = loader.buildContext(agentProfile);
      assertThat(context).contains("daily-report");
      assertThat(appender.list)
          .anyMatch(event ->
              event.getLevel() == Level.WARN && event.getFormattedMessage().contains("missing.md"));
    } finally {
      logger.detachAppender(appender);
    }
  }

  @Test
  @DisplayName("Bootstrap 路径越界时被拒绝")
  void rejectsPathTraversal() {
    // loader 是被测对象。
    ContextLoader loader = new ContextLoader(workspace);
    // agentProfile 引用工作区外的路径。
    AgentProfile agentProfile = agentProfile(List.of("../secret.md"), List.of());

    assertThatThrownBy(() -> loader.buildContext(agentProfile))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("越界");
  }

  /**
   * 写入工作区根目录文件。
   *
   * @param fileName 文件名
   * @param content 文件内容
   * @throws IOException 写入失败时抛出
   */
  private void writeFile(String fileName, String content) throws IOException {
    // file 是目标文件路径。
    Path file = workspace.resolve(fileName);
    Files.writeString(file, content, StandardCharsets.UTF_8);
  }

  /**
   * 写入 Skill 元数据文件。
   *
   * @param directoryName Skill 目录名
   * @param name frontmatter name
   * @param description frontmatter description
   * @throws IOException 写入失败时抛出
   */
  private void writeSkill(String directoryName, String name, String description)
      throws IOException {
    // skillDirectory 是 Skill 目录。
    Path skillDirectory = workspace.resolve("skills").resolve(directoryName);
    Files.createDirectories(skillDirectory);
    // content 是带 frontmatter 的 SKILL.md 内容。
    String content = "---\nname: " + name + "\ndescription: " + description + "\n---\n\n# 正文\n";
    Files.writeString(skillDirectory.resolve("SKILL.md"), content, StandardCharsets.UTF_8);
  }

  /**
   * 创建测试 AgentProfile。
   *
   * @param bootstrap Bootstrap 文件列表
   * @param skills Skill 名称列表
   * @return 测试 AgentProfile
   */
  private static AgentProfile agentProfile(List<String> bootstrap, List<String> skills) {
    // identity 是测试身份。
    AgentProfile.Identity identity = new AgentProfile.Identity("测试助手", "你是测试助手");
    // providerRef 是测试 provider 引用。
    AgentProfile.ProviderRef providerRef =
        new AgentProfile.ProviderRef("deepseek", "deepseek-chat", 0.0D);
    // settings 是测试运行时设置。
    AgentProfile.Settings settings = new AgentProfile.Settings(10, 20);
    return new AgentProfile(
        "test-agent",
        "测试 Agent",
        identity,
        providerRef,
        List.of(),
        skills,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        bootstrap,
        settings);
  }
}
