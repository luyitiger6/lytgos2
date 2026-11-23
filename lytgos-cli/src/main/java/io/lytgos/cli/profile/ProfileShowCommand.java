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
 * 查看一个 AgentProfile YAML 的原始内容。
 *
 * @author LytgOS2 Team
 */
@CommandLine.Command(
    name = "show",
    mixinStandardHelpOptions = true,
    description = "查看 AgentProfile")
public final class ProfileShowCommand implements Callable<Integer> {

  /** 换行符，用于保证输出以换行结束。 */
  private static final String NEWLINE = "\n";

  /** 工作区路径。 */
  private final WorkspacePaths paths;

  /** Picocli 注入的命令模型。 */
  @CommandLine.Spec private CommandSpec spec;

  /** 待查看的 AgentProfile 名称。 */
  @CommandLine.Parameters(
      index = "0",
      paramLabel = "<name>",
      description = "AgentProfile 名称",
      descriptionKey = "cli.profile.param.name")
  private String name;

  /** 按默认工作区创建命令；Picocli 使用此构造器。 */
  public ProfileShowCommand() {
    this(WorkspacePaths.defaultPaths());
  }

  /**
   * 创建 AgentProfile 查看命令。
   *
   * @param paths 工作区路径
   */
  public ProfileShowCommand(WorkspacePaths paths) {
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
    if (!Files.isRegularFile(target)) {
      spec.commandLine()
          .getErr()
          .println(
              CliMessages.errorPrefix()
                  + CliMessages.get("cli.profile.show.missing", target.toAbsolutePath()));
      return 1;
    }
    try {
      // content 是 YAML 原始文本。
      String content = Files.readString(target, StandardCharsets.UTF_8);
      spec.commandLine().getOut().print(content);
      if (!content.endsWith(NEWLINE)) {
        spec.commandLine().getOut().println();
      }
      spec.commandLine().getOut().flush();
      return 0;
    } catch (IOException exception) {
      spec.commandLine()
          .getErr()
          .println(
              CliMessages.errorPrefix()
                  + CliMessages.get("cli.profile.show.error", exception.getMessage()));
      return 1;
    }
  }
}
