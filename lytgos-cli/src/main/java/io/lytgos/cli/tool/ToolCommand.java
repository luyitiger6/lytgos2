package io.lytgos.cli.tool;

import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

/**
 * Tool 查询命令的父命令。
 *
 * @author LytgOS2 Team
 */
@CommandLine.Command(
    name = "tool",
    mixinStandardHelpOptions = true,
    description = "查询已注册工具",
    subcommands = {ToolListCommand.class})
public final class ToolCommand implements Callable<Integer> {

  /** Picocli 注入的命令模型。 */
  @CommandLine.Spec private CommandSpec spec;

  @Override
  public Integer call() {
    // 父命令没有子命令时展示用法并返回用法错误码。
    spec.commandLine().usage(spec.commandLine().getOut());
    return 2;
  }
}
