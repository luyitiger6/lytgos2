package io.lytgos.cli.profile;

import io.lytgos.cli.i18n.CliMessages;
import io.lytgos.cli.workspace.WorkspacePaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

/**
 * 创建一个 AgentProfile YAML。
 *
 * <p>名称只允许字母、数字、下划线和连字符；已存在的文件不会被覆盖。
 *
 * @author LytgOS2 Team
 */
@CommandLine.Command(
    name = "create",
    mixinStandardHelpOptions = true,
    description = "创建 AgentProfile")
public final class ProfileCreateCommand implements Callable<Integer> {

  /** 工作区路径。 */
  private final WorkspacePaths paths;

  /** Picocli 注入的命令模型。 */
  @CommandLine.Spec private CommandSpec spec;

  /** 待创建的 AgentProfile 名称。 */
  @CommandLine.Parameters(
      index = "0",
      paramLabel = "<name>",
      description = "AgentProfile 名称",
      descriptionKey = "cli.profile.param.name")
  private String name;

  /** 按默认工作区创建命令；Picocli 使用此构造器。 */
  public ProfileCreateCommand() {
    this(WorkspacePaths.defaultPaths());
  }

  /**
   * 创建 AgentProfile 创建命令。
   *
   * @param paths 工作区路径
   */
  public ProfileCreateCommand(WorkspacePaths paths) {
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
      Files.createDirectories(paths.profiles());
      if (Files.exists(target)) {
        spec.commandLine()
            .getErr()
            .println(
                CliMessages.errorPrefix()
                    + CliMessages.get("cli.profile.create.exists", target.toAbsolutePath()));
        return 1;
      }
      Files.writeString(target, ProfileTemplate.defaultYaml(name), StandardCharsets.UTF_8);
      spec.commandLine()
          .getOut()
          .println(CliMessages.get("cli.profile.create.created", target.toAbsolutePath()));
      return 0;
    } catch (IOException exception) {
      spec.commandLine()
          .getErr()
          .println(
              CliMessages.errorPrefix()
                  + CliMessages.get("cli.profile.create.error", exception.getMessage()));
      return 1;
    }
  }
}
