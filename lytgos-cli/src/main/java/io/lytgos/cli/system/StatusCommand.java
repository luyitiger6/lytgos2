package io.lytgos.cli.system;

import io.lytgos.cli.i18n.CliMessages;
import io.lytgos.cli.profile.ProfileNames;
import io.lytgos.cli.provider.ProviderConfigReader;
import io.lytgos.cli.session.SessionSummaryReader;
import io.lytgos.cli.workspace.WorkspacePaths;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

/**
 * 展示工作区、Profile、Provider 与会话的只读状态。
 *
 * <p>不启动 Spring；数据库未初始化时显示未初始化，不创建任何文件或表。
 *
 * @author LytgOS2 Team
 */
@CommandLine.Command(name = "status", mixinStandardHelpOptions = true, description = "查看当前状态")
@SuppressWarnings("PMD.CloseResource")
public final class StatusCommand implements Callable<Integer> {

  /** 工作区路径。 */
  private final WorkspacePaths paths;

  /** Provider 配置读取器。 */
  private final ProviderConfigReader providerReader;

  /** 会话摘要读取器。 */
  private final SessionSummaryReader sessionReader;

  /** Picocli 注入的命令模型。 */
  @CommandLine.Spec private CommandSpec spec;

  /** 按默认工作区创建命令；Picocli 使用此构造器。 */
  public StatusCommand() {
    this(
        WorkspacePaths.defaultPaths(),
        new ProviderConfigReader(),
        new SessionSummaryReader(WorkspacePaths.defaultPaths().database()));
  }

  /**
   * 创建状态命令。
   *
   * @param paths 工作区路径
   * @param providerReader Provider 配置读取器
   * @param sessionReader 会话摘要读取器
   */
  public StatusCommand(
      WorkspacePaths paths,
      ProviderConfigReader providerReader,
      SessionSummaryReader sessionReader) {
    this.paths = Objects.requireNonNull(paths, "paths");
    this.providerReader = Objects.requireNonNull(providerReader, "providerReader");
    this.sessionReader = Objects.requireNonNull(sessionReader, "sessionReader");
  }

  @Override
  public Integer call() {
    try {
      // out 是命令标准输出。
      PrintWriter out = spec.commandLine().getOut();
      // err 是命令错误输出，用于非致命读取警告。
      PrintWriter err = spec.commandLine().getErr();
      // profileCount 是 profiles 目录下的 YAML 数量。
      long profileCount = countProfiles();
      // providerCount 是全局配置中的 Provider 数量；读取失败时保持 0 并给出警告。
      int providerCount = 0;
      // providerReadable 标记 Provider 配置是否可以读取。
      boolean providerReadable = true;
      try {
        providerCount = providerReader.read().size();
      } catch (RuntimeException exception) {
        providerReadable = false;
        err.println(CliMessages.get("cli.status.warning.provider", exception.getMessage()));
      }
      // databaseInitialized 表示 sessions 表是否已经由 Flyway 创建。
      boolean databaseInitialized = sessionReader.isInitialized();
      // sessionCount 是会话数量；数据库未初始化时为 0。
      long sessionCount = databaseInitialized ? sessionReader.count() : 0L;
      out.println(CliMessages.get("cli.status.workspace", paths.root().toAbsolutePath()));
      out.println(
          CliMessages.get(
              "cli.status.database",
              databaseInitialized
                  ? CliMessages.get("cli.status.database.initialized")
                  : CliMessages.get("cli.status.database.uninitialized")));
      out.println(CliMessages.get("cli.status.profile", profileCount));
      out.println(
          CliMessages.get(
              "cli.status.provider",
              providerReadable
                  ? providerCount
                  : CliMessages.get("cli.status.provider.unreadable")));
      out.println(
          CliMessages.get(
              "cli.status.sessions",
              databaseInitialized
                  ? sessionCount
                  : CliMessages.get("cli.status.database.uninitialized")));
      return 0;
    } catch (RuntimeException exception) {
      spec.commandLine().getErr().println(CliMessages.errorPrefix() + exception.getMessage());
      return 1;
    }
  }

  /**
   * 统计 profiles 目录下的 YAML 数量。
   *
   * @return YAML 数量；目录不存在时为 0
   */
  private long countProfiles() {
    if (!Files.isDirectory(paths.profiles())) {
      return 0L;
    }
    try (Stream<Path> files = Files.list(paths.profiles())) {
      return files.filter(Files::isRegularFile).filter(ProfileNames::isYaml).count();
    } catch (IOException exception) {
      throw new IllegalStateException(
          CliMessages.get("cli.profile.list.error", paths.profiles()), exception);
    }
  }
}
