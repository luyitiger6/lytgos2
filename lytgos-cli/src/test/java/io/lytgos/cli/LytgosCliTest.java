package io.lytgos.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.lytgos.cli.i18n.CliLocalization;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/**
 * 命令行 12 个叶子命令注册与帮助输出测试。
 *
 * <p>只验证 Picocli 命令树与帮助，不启动 Spring，也不访问真实终端。
 *
 * @author LytgOS2 Team
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class LytgosCliTest {

  /** 12 个叶子命令的路径。 */
  private static final List<List<String>> LEAF_PATHS =
      List.of(
          List.of("init"),
          List.of("status"),
          List.of("chat"),
          List.of("serve"),
          List.of("gateway"),
          List.of("profile", "list"),
          List.of("profile", "create"),
          List.of("profile", "show"),
          List.of("profile", "delete"),
          List.of("provider", "list"),
          List.of("tool", "list"),
          List.of("session", "list"));

  /** 默认测试语言。 */
  private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

  @Test
  @DisplayName("根命令帮助成功并列出主要命令")
  void rootHelpExitsSuccessfully() {
    // result 是根命令 --help 的执行结果。
    ExecutionResult result = execute("--help");

    assertThat(result.exitCode()).isZero();
    assertThat(result.output()).contains("init").contains("chat").contains("status");
    assertThat(result.output()).contains("Initialize the workspace");
    assertThat(result.error()).isEmpty();
  }

  @Test
  @DisplayName("中文模式帮助使用中文描述")
  void chineseHelpUsesChineseDescriptions() {
    // result 是中文模式下的根帮助。
    ExecutionResult result = execute(Locale.SIMPLIFIED_CHINESE, "--help");

    assertThat(result.exitCode()).isZero();
    assertThat(result.output()).contains("初始化工作区").contains("查看当前状态");
  }

  @Test
  @DisplayName("英文模式帮助使用英文描述")
  void englishHelpUsesEnglishDescriptions() {
    // result 是英文模式下的根帮助。
    ExecutionResult result = execute(Locale.ENGLISH, "--help");

    assertThat(result.exitCode()).isZero();
    assertThat(result.output())
        .contains("Initialize the workspace")
        .contains("Show current status");
  }

  @Test
  @DisplayName("12 个叶子命令全部注册")
  void allTwelveLeafCommandsAreRegistered() {
    // root 是命令树根节点。
    CommandLine root = newCommandLine();

    for (List<String> path : LEAF_PATHS) {
      assertThat(resolve(root, path)).as("命令路径 %s", path).isNotNull();
    }
    assertThat(LEAF_PATHS).hasSize(12);
  }

  @Test
  @DisplayName("每个叶子命令的帮助都以成功状态退出")
  void everyLeafCommandHelpExitsSuccessfully() {
    for (List<String> path : LEAF_PATHS) {
      // result 是单个叶子命令帮助的执行结果。
      ExecutionResult result = executeLeafHelp(path);

      assertThat(result.exitCode()).as("命令路径 %s", path).isZero();
      assertThat(result.output()).as("命令路径 %s", path).containsIgnoringCase("Usage");
    }
  }

  /**
   * 执行单个叶子命令的帮助。
   *
   * @param path 叶子命令路径
   * @return 执行结果
   */
  private static ExecutionResult executeLeafHelp(List<String> path) {
    // args 是叶子命令路径加 --help。
    String[] args = new String[path.size() + 1];
    for (int index = 0; index < path.size(); index++) {
      args[index] = path.get(index);
    }
    args[path.size()] = "--help";
    return execute(args);
  }

  @Test
  @DisplayName("父命令暴露预期的子命令")
  void parentCommandsExposeExpectedSubcommands() {
    // root 是命令树根节点。
    CommandLine root = newCommandLine();
    // profile 是 AgentProfile 父命令。
    CommandLine profile = resolve(root, List.of("profile"));
    // provider 是 Provider 父命令。
    CommandLine provider = resolve(root, List.of("provider"));
    // tool 是 Tool 父命令。
    CommandLine tool = resolve(root, List.of("tool"));
    // session 是 Session 父命令。
    CommandLine session = resolve(root, List.of("session"));

    assertThat(profile).isNotNull();
    assertThat(profile.getSubcommands()).containsKeys("list", "create", "show", "delete");
    assertThat(provider).isNotNull();
    assertThat(provider.getSubcommands()).containsKey("list");
    assertThat(tool).isNotNull();
    assertThat(tool.getSubcommands()).containsKey("list");
    assertThat(session).isNotNull();
    assertThat(session.getSubcommands()).containsKey("list");
  }

  /**
   * 创建带内存输出流的根命令。
   *
   * @return Picocli 根命令
   */
  private static CommandLine newCommandLine() {
    return newCommandLine(DEFAULT_LOCALE);
  }

  /**
   * 创建带内存输出流和指定语言的根命令。
   *
   * @param locale 文案语言
   * @return Picocli 根命令
   */
  private static CommandLine newCommandLine(Locale locale) {
    // commandLine 是新的根命令实例，避免多次执行之间共享状态。
    CommandLine commandLine = new CommandLine(new LytgosCli(locale));
    CliLocalization.apply(commandLine);
    commandLine.setOut(new PrintWriter(new StringWriter(), true));
    commandLine.setErr(new PrintWriter(new StringWriter(), true));
    return commandLine;
  }

  /**
   * 执行一次命令并捕获输出。
   *
   * @param args 命令行参数
   * @return 执行结果
   */
  private static ExecutionResult execute(String... args) {
    return execute(DEFAULT_LOCALE, args);
  }

  /**
   * 以指定语言执行一次命令并捕获输出。
   *
   * @param locale 文案语言
   * @param args 命令行参数
   * @return 执行结果
   */
  private static ExecutionResult execute(Locale locale, String... args) {
    // out 收集标准输出。
    StringWriter out = new StringWriter();
    // err 收集错误输出。
    StringWriter err = new StringWriter();
    // commandLine 是本次执行的根命令。
    CommandLine commandLine = new CommandLine(new LytgosCli(locale));
    CliLocalization.apply(commandLine);
    commandLine.setOut(new PrintWriter(out, true));
    commandLine.setErr(new PrintWriter(err, true));
    // exitCode 是 Picocli 执行后的退出码。
    int exitCode = commandLine.execute(args);
    return new ExecutionResult(exitCode, out.toString(), err.toString());
  }

  /**
   * 按路径解析子命令。
   *
   * @param root 根命令
   * @param path 子命令路径
   * @return 目标命令；任一层不存在时返回 null
   */
  private static CommandLine resolve(CommandLine root, List<String> path) {
    // current 是逐层解析后的当前命令。
    CommandLine current = root;
    for (String segment : path) {
      // subcommands 是当前命令的直接子命令。
      Map<String, CommandLine> subcommands = current.getSubcommands();
      current = subcommands.get(segment);
      if (current == null) {
        return null;
      }
    }
    return current;
  }

  /**
   * 一次命令执行的退出码与输出。
   *
   * @param exitCode 退出码
   * @param output 标准输出
   * @param error 错误输出
   */
  private record ExecutionResult(int exitCode, String output, String error) {}
}
