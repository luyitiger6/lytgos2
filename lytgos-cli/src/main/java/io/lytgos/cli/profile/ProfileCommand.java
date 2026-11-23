package io.lytgos.cli.profile;

import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

/**
 * AgentProfile 管理命令的父命令。
 *
 * @author LytgOS2 Team
 */
@CommandLine.Command(
    name = "profile",
    mixinStandardHelpOptions = true,
    description = "管理 AgentProfile",
    subcommands = {
      ProfileListCommand.class,
      ProfileCreateCommand.class,
      ProfileShowCommand.class,
      ProfileDeleteCommand.class
    })
public final class ProfileCommand implements Callable<Integer> {

  /** Picocli 注入的命令模型。 */
  @CommandLine.Spec private CommandSpec spec;

  @Override
  public Integer call() {
    // 父命令没有子命令时展示用法并返回用法错误码。
    spec.commandLine().usage(spec.commandLine().getOut());
    return 2;
  }
}
