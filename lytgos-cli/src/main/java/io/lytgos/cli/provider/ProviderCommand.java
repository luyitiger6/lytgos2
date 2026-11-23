package io.lytgos.cli.provider;

import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

/**
 * Provider 查询命令的父命令。
 *
 * @author LytgOS2 Team
 */
@CommandLine.Command(
    name = "provider",
    mixinStandardHelpOptions = true,
    description = "查询全局 Provider 配置",
    subcommands = {ProviderListCommand.class})
public final class ProviderCommand implements Callable<Integer> {

  /** Picocli 注入的命令模型。 */
  @CommandLine.Spec private CommandSpec spec;

  @Override
  public Integer call() {
    // 父命令没有子命令时展示用法并返回用法错误码。
    spec.commandLine().usage(spec.commandLine().getOut());
    return 2;
  }
}
