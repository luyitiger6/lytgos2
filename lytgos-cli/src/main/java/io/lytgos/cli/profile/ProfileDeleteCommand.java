package io.lytgos.cli.profile;

import io.lytgos.cli.i18n.CliMessages;
import io.lytgos.cli.workspace.WorkspacePaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

/**
 * 删除一个 AgentProfile YAML。
 *
 * <p>无交互确认，便于脚本使用；文件不存在时返回失败退出码。
 *
 * @author LytgOS2 Team
 */
@CommandLine.Command(
    name = "delete",
    mixinStandardHelpOptions = true,
    description = "删除 AgentProfile")
public final class ProfileDeleteCommand implements Callable<Integer> {

  /** 工作区路径。 */
  private final WorkspacePaths paths;

  /** Picocli 注入的命令模型。 */
  @CommandLine.Spec private CommandSpec spec;

  /** 待删除的 AgentProfile 名称。 */
  @CommandLine.Parameters(
      index = "0",
      paramLabel = "<name>",
      description = "AgentProfile 名称",
      descriptionKey = "cli.profile.param.name")
  private String name;

  /** 按默认工作区创建命令；Picocli 使用此构造器。 */
  public ProfileDeleteCommand() {
    this(WorkspacePaths.defaultPaths());
  }

  /**
   * 创建 AgentProfile 删除命令。
   *
   * @param paths 工作区路径
   */
  public ProfileDeleteCommand(WorkspacePaths paths) {
    this.paths = Objects.requireNonNull(paths, "paths");
  }

  @Override
  public Integer call() {
    // target 是受控的 YAML 文件路径。
    Path target;
    try {
      target = ProfileNames.resolve(paths.profiles(), name);
    } catch (IllegalArgumentException exception) {
      spec.commandLine().getErr().println(CliMessages.errorPrefix() + exception.getMessage());
      return 1;
    }
    try {
      if (!Files.deleteIfExists(target)) {
        spec.commandLine()
            .getErr()
            .println(
                CliMessages.errorPrefix()
                    + CliMessages.get("cli.profile.delete.missing", target.toAbsolutePath()));
        return 1;
      }
      spec.commandLine()
          .getOut()
          .println(CliMessages.get("cli.profile.delete.deleted", target.toAbsolutePath()));
      return 0;
    } catch (IOException exception) {
      spec.commandLine()
          .getErr()
          .println(
              CliMessages.errorPrefix()
                  + CliMessages.get("cli.profile.delete.error", exception.getMessage()));
      return 1;
    }
  }
}
