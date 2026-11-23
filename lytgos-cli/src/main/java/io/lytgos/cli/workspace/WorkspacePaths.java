package io.lytgos.cli.workspace;

import java.nio.file.Path;
import java.util.Objects;

/**
 * CLI 使用的工作区路径集合。
 *
 * <p>轻命令直接基于这些路径读写文件或 SQLite，不启动 Spring；路径默认取当前目录下的 {@code .lytgos}， 可通过系统属性 {@code
 * lytgos.workspace} 覆盖，便于测试与部署。
 *
 * @author LytgOS2 Team
 */
public final class WorkspacePaths {

  /** 工作区默认目录名。 */
  private static final String DEFAULT_WORKSPACE = ".lytgos";

  /** 工作区配置键，与 application.yaml 中的 {@code lytgos.workspace} 保持一致。 */
  private static final String WORKSPACE_PROPERTY = "lytgos.workspace";

  /** SQLite 数据库文件名。 */
  private static final String DATABASE_FILE_NAME = "lytgos.db";

  /** 工作区根目录。 */
  private final Path rootDirectory;

  /** Agent 目录。 */
  private final Path agentsDirectory;

  /** 全局 Skill 目录。 */
  private final Path skillsDirectory;

  /** 长期记忆目录。 */
  private final Path memoryDirectory;

  /** 会话附件目录。 */
  private final Path sessionsDirectory;

  /** 日志目录。 */
  private final Path logsDirectory;

  /** AgentProfile 目录。 */
  private final Path profilesDirectory;

  /** SQLite 数据库文件。 */
  private final Path databaseFile;

  /**
   * 创建工作区路径集合。
   *
   * @param root 工作区根目录
   */
  public WorkspacePaths(Path root) {
    // 根目录是所有派生路径的基准，不能为空。
    this.rootDirectory = Objects.requireNonNull(root, "root");
    // 各领域目录统一从根目录派生，避免调用方各自拼接。
    this.agentsDirectory = root.resolve("agents");
    this.skillsDirectory = root.resolve("skills");
    this.memoryDirectory = root.resolve("memory");
    this.sessionsDirectory = root.resolve("sessions");
    this.logsDirectory = root.resolve("logs");
    this.profilesDirectory = root.resolve("profiles");
    this.databaseFile = root.resolve(DATABASE_FILE_NAME);
  }

  /**
   * 按默认配置创建工作区路径。
   *
   * @return 默认工作区路径
   */
  public static WorkspacePaths defaultPaths() {
    // configured 是系统属性覆盖后的工作区目录；未配置时使用 .lytgos。
    String configured = System.getProperty(WORKSPACE_PROPERTY, DEFAULT_WORKSPACE);
    return new WorkspacePaths(Path.of(configured));
  }

  /**
   * 返回工作区根目录。
   *
   * @return 工作区根目录
   */
  public Path root() {
    return rootDirectory;
  }

  /**
   * 返回 Agent 目录。
   *
   * @return Agent 目录
   */
  public Path agents() {
    return agentsDirectory;
  }

  /**
   * 返回全局 Skill 目录。
   *
   * @return Skill 目录
   */
  public Path skills() {
    return skillsDirectory;
  }

  /**
   * 返回长期记忆目录。
   *
   * @return 记忆目录
   */
  public Path memory() {
    return memoryDirectory;
  }

  /**
   * 返回会话附件目录。
   *
   * @return 会话目录
   */
  public Path sessions() {
    return sessionsDirectory;
  }

  /**
   * 返回日志目录。
   *
   * @return 日志目录
   */
  public Path logs() {
    return logsDirectory;
  }

  /**
   * 返回 AgentProfile 目录。
   *
   * @return AgentProfile 目录
   */
  public Path profiles() {
    return profilesDirectory;
  }

  /**
   * 返回 SQLite 数据库文件。
   *
   * @return 数据库文件路径
   */
  public Path database() {
    return databaseFile;
  }
}
