package io.lytgos.cli.profile;

import io.lytgos.cli.i18n.CliMessages;
import io.lytgos.cli.workspace.WorkspacePaths;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

/**
 * 列出工作区中的 AgentProfile。
 *
 * <p>直接读取 profiles 目录下的 YAML，不启动 Spring；解析失败的文件给出警告但仍列出名称。
 *
 * @author LytgOS2 Team
 */
@CommandLine.Command(
    name = "list",
    mixinStandardHelpOptions = true,
    description = "列出 AgentProfile")
@SuppressWarnings("PMD.CloseResource")
public final class ProfileListCommand implements Callable<Integer> {

  /** 工作区路径。 */
  private final WorkspacePaths paths;

  /** Picocli 注入的命令模型。 */
  @CommandLine.Spec private CommandSpec spec;

  /** 按默认工作区创建命令；Picocli 使用此构造器。 */
  public ProfileListCommand() {
    this(WorkspacePaths.defaultPaths());
  }

  /**
   * 创建 AgentProfile 列表命令。
   *
   * @param paths 工作区路径
   */
  public ProfileListCommand(WorkspacePaths paths) {
    this.paths = Objects.requireNonNull(paths, "paths");
  }

  @Override
  public Integer call() {
    // out 是命令标准输出。
    PrintWriter out = spec.commandLine().getOut();
    // err 是命令错误输出，用于解析警告。
    PrintWriter err = spec.commandLine().getErr();
    // summaries 保存本次列出的 AgentProfile。
    List<ProfileSummary> summaries = new ArrayList<>();
    try {
      if (Files.isDirectory(paths.profiles())) {
        try (Stream<Path> files = Files.list(paths.profiles())) {
          // candidates 是按文件名排序的 YAML 候选文件。
          List<Path> candidates =
              files.filter(Files::isRegularFile).filter(ProfileNames::isYaml).sorted().toList();
          for (Path file : candidates) {
            summaries.add(readSummary(file, err));
          }
        }
      }
    } catch (IOException exception) {
      err.println(
          CliMessages.errorPrefix()
              + CliMessages.get("cli.profile.list.error", exception.getMessage()));
      return 1;
    }
    if (summaries.isEmpty()) {
      out.println(CliMessages.get("cli.profile.list.empty"));
      return 0;
    }
    out.printf(
        "%-24s %-12s %s%n",
        CliMessages.get("cli.profile.list.headerName"),
        CliMessages.get("cli.profile.list.headerProvider"),
        CliMessages.get("cli.profile.list.headerModel"));
    for (ProfileSummary summary : summaries) {
      out.printf(
          "%-24s %-12s %s%n",
          summary.name(), valueOrDash(summary.provider()), valueOrDash(summary.model()));
    }
    return 0;
  }

  /**
   * 读取单个摘要；解析失败时给出警告并保留名称。
   *
   * @param file YAML 文件
   * @param err 错误输出
   * @return AgentProfile 摘要
   */
  private static ProfileSummary readSummary(Path file, PrintWriter err) {
    try {
      return ProfileSummary.read(file);
    } catch (RuntimeException exception) {
      err.println(CliMessages.get("cli.profile.list.warning", file, exception.getMessage()));
      return new ProfileSummary(ProfileNames.nameOf(file), null, null, file);
    }
  }

  /**
   * 空值显示为短横线。
   *
   * @param value 字段值
   * @return 展示文本
   */
  private static String valueOrDash(String value) {
    return value == null || value.isBlank() ? "-" : value;
  }
}
