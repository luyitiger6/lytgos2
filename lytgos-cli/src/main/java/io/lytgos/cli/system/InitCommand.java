package io.lytgos.cli.system;

import io.lytgos.cli.i18n.CliMessages;
import io.lytgos.cli.profile.ProfileTemplate;
import io.lytgos.cli.workspace.WorkspacePaths;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

/**
 * 初始化 LytgOS2 工作区。
 *
 * <p>幂等创建目录、Bootstrap 占位文件、长期记忆占位文件和默认 AgentProfile；已有文件保持原内容。
 *
 * @author LytgOS2 Team
 */
@CommandLine.Command(name = "init", mixinStandardHelpOptions = true, description = "初始化工作区")
@SuppressWarnings("PMD.CloseResource")
public final class InitCommand implements Callable<Integer> {

  /** AGENTS.md 占位内容。 */
  private static final String AGENTS_CONTENT = "# AGENTS\n\n在此维护全局 Agent 指令。\n";

  /** SOUL.md 占位内容。 */
  private static final String SOUL_CONTENT = "# SOUL\n\n在此维护 Agent 的人格与原则。\n";

  /** USER.md 占位内容。 */
  private static final String USER_CONTENT = "# USER\n\n在此描述用户偏好与背景。\n";

  /** MEMORY.md 占位内容；分区名称与长期记忆模型一致。 */
  private static final String MEMORY_CONTENT = "# MEMORY\n\n## 核心记忆\n\n## 归档记忆\n";

  /** mcp_servers.yaml 占位内容。 */
  private static final String MCP_CONTENT = "# MCP server 配置\nservers: []\n";

  /** 默认 AgentProfile 名称。 */
  private static final String DEFAULT_PROFILE = "default";

  /** 工作区路径。 */
  private final WorkspacePaths paths;

  /** Picocli 注入的命令模型。 */
  @CommandLine.Spec private CommandSpec spec;

  /** 按默认工作区创建命令；Picocli 使用此构造器。 */
  public InitCommand() {
    this(WorkspacePaths.defaultPaths());
  }

  /**
   * 创建初始化命令。
   *
   * @param paths 工作区路径
   */
  public InitCommand(WorkspacePaths paths) {
    this.paths = Objects.requireNonNull(paths, "paths");
  }

  @Override
  public Integer call() {
    // out 是命令标准输出。
    PrintWriter out = spec.commandLine().getOut();
    // created 记录本次新建的目录与文件。
    List<Path> created = new ArrayList<>();
    // existing 记录已存在并跳过的目录与文件。
    List<Path> existing = new ArrayList<>();
    try {
      // directories 是工作区需要保证存在的领域目录。
      List<Path> directories =
          List.of(
              paths.agents(),
              paths.skills(),
              paths.memory(),
              paths.sessions(),
              paths.logs(),
              paths.profiles());
      for (Path directory : directories) {
        createDirectory(directory, created, existing);
      }
      // bootstrapFiles 是需要占位的文件和默认 AgentProfile。
      Map<Path, String> bootstrapFiles = new LinkedHashMap<>();
      bootstrapFiles.put(paths.root().resolve("AGENTS.md"), AGENTS_CONTENT);
      bootstrapFiles.put(paths.root().resolve("SOUL.md"), SOUL_CONTENT);
      bootstrapFiles.put(paths.root().resolve("USER.md"), USER_CONTENT);
      bootstrapFiles.put(paths.root().resolve("MEMORY.md"), MEMORY_CONTENT);
      bootstrapFiles.put(paths.root().resolve("mcp_servers.yaml"), MCP_CONTENT);
      bootstrapFiles.put(
          paths.profiles().resolve(DEFAULT_PROFILE + ".yaml"),
          ProfileTemplate.defaultYaml(DEFAULT_PROFILE));
      for (Map.Entry<Path, String> entry : bootstrapFiles.entrySet()) {
        createFile(entry.getKey(), entry.getValue(), created, existing);
      }
    } catch (IOException exception) {
      spec.commandLine()
          .getErr()
          .println(
              CliMessages.errorPrefix()
                  + CliMessages.get("cli.init.error", exception.getMessage()));
      return 1;
    }
    out.println(CliMessages.get("cli.init.header", paths.root().toAbsolutePath()));
    for (Path path : created) {
      out.println(CliMessages.get("cli.init.created", path));
    }
    for (Path path : existing) {
      out.println(CliMessages.get("cli.init.existing", path));
    }
    return 0;
  }

  /**
   * 创建目录并记录结果。
   *
   * @param directory 目录路径
   * @param created 新建列表
   * @param existing 已存在列表
   * @throws IOException 创建失败
   */
  private static void createDirectory(Path directory, List<Path> created, List<Path> existing)
      throws IOException {
    if (Files.isDirectory(directory)) {
      existing.add(directory);
      return;
    }
    Files.createDirectories(directory);
    created.add(directory);
  }

  /**
   * 创建占位文件并记录结果。
   *
   * @param file 文件路径
   * @param content 文件内容
   * @param created 新建列表
   * @param existing 已存在列表
   * @throws IOException 写入失败
   */
  private static void createFile(Path file, String content, List<Path> created, List<Path> existing)
      throws IOException {
    if (Files.exists(file)) {
      existing.add(file);
      return;
    }
    Files.writeString(file, content, StandardCharsets.UTF_8);
    created.add(file);
  }
}
