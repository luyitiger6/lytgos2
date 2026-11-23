package io.lytgos.cli.tool;

import io.lytgos.cli.i18n.CliMessages;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

/**
 * 列出已注册工具。
 *
 * <p>Tool 能力尚未接入，当前固定返回空说明；命令不启动 Spring。
 *
 * @author LytgOS2 Team
 */
@CommandLine.Command(name = "list", mixinStandardHelpOptions = true, description = "列出已注册工具")
public final class ToolListCommand implements Callable<Integer> {

  /** Picocli 注入的命令模型。 */
  @CommandLine.Spec private CommandSpec spec;

  @Override
  public Integer call() {
    spec.commandLine().getOut().println(CliMessages.get("cli.tool.list.empty"));
    return 0;
  }
}
