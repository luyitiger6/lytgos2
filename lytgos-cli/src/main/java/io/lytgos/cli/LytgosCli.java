package io.lytgos.cli;

import io.lytgos.cli.bootstrap.LauncherInstallResult;
import io.lytgos.cli.bootstrap.LauncherInstaller;
import io.lytgos.cli.chat.ChatCommand;
import io.lytgos.cli.i18n.CliEnvironment;
import io.lytgos.cli.i18n.CliLocaleResolver;
import io.lytgos.cli.i18n.CliLocalization;
import io.lytgos.cli.i18n.CliMessages;
import io.lytgos.cli.profile.ProfileCommand;
import io.lytgos.cli.provider.ProviderCommand;
import io.lytgos.cli.runtime.GatewayCommand;
import io.lytgos.cli.runtime.ServeCommand;
import io.lytgos.cli.session.SessionCommand;
import io.lytgos.cli.system.InitCommand;
import io.lytgos.cli.system.StatusCommand;
import io.lytgos.cli.tool.ToolCommand;
import io.lytgos.core.Lytgos;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

/**
 * LytgOS2 命令行入口。
 *
 * <p>根命令只负责组装 12 个叶子命令与 4 个父命令；轻命令直接读写文件/SQLite，重命令通过 {@code SpringRuntime} 反射启动组装根。可执行 JAR 的
 * mainClass 指向本类。
 *
 * @author LytgOS2 Team
 */
@CommandLine.Command(
    name = "lytgos",
    mixinStandardHelpOptions = true,
    version = Lytgos.NAME + " " + Lytgos.VERSION,
    description = "LytgOS2 命令行入口",
    subcommands = {
      InitCommand.class,
      StatusCommand.class,
      ChatCommand.class,
      ServeCommand.class,
      GatewayCommand.class,
      ProfileCommand.class,
      ProviderCommand.class,
      ToolCommand.class,
      SessionCommand.class
    })
public final class LytgosCli implements Callable<Integer> {

  /** Picocli 注入的命令模型。 */
  @CommandLine.Spec private CommandSpec spec;

  /** 按当前终端环境选择语言。 */
  public LytgosCli() {
    this(new CliLocaleResolver().resolve(CliEnvironment.system()));
  }

  /**
   * 使用指定语言创建根命令，便于测试。
   *
   * @param locale 文案语言
   */
  public LytgosCli(Locale locale) {
    // 根命令创建时初始化全局文案，子命令随后读取同一份资源。
    CliMessages.initialize(Objects.requireNonNull(locale, "locale"));
  }

  @Override
  public Integer call() {
    // 根命令没有子命令时展示用法并返回用法错误码。
    spec.commandLine().usage(spec.commandLine().getOut());
    return 2;
  }

  /**
   * 进程入口。
   *
   * @param args 命令行参数
   */
  public static void main(String[] args) {
    // locale 根据终端编码能力选择中文或英文。
    Locale locale = new CliLocaleResolver().resolve(CliEnvironment.system());
    // commandLine 是完成本地化和首启安装的根命令。
    CommandLine commandLine = new CommandLine(new LytgosCli(locale));
    CliLocalization.apply(commandLine);
    // installResult 是首次运行启动器安装结果。
    LauncherInstallResult installResult = LauncherInstaller.ensureInstalled();
    for (String message : installResult.messages()) {
      commandLine.getOut().println(message);
    }
    // exitCode 是 Picocli 解析与执行后的退出码。
    int exitCode = commandLine.execute(args);
    System.exit(exitCode);
  }
}
