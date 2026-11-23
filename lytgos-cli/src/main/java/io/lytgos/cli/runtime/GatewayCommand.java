package io.lytgos.cli.runtime;

import io.lytgos.cli.i18n.CliMessages;
import java.util.Objects;
import java.util.concurrent.Callable;
import org.springframework.boot.WebApplicationType;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

/**
 * 启动守护进程骨架并保持运行。
 *
 * <p>1.0 只验证共享 Profile 与 Session 存储的启动路径；实际 IM 渠道属于扩展范围。
 *
 * @author LytgOS2 Team
 */
@CommandLine.Command(name = "gateway", mixinStandardHelpOptions = true, description = "启动守护进程模式")
public final class GatewayCommand implements Callable<Integer> {

  /** Spring 运行时启动器。 */
  private final SpringRuntime runtime;

  /** Picocli 注入的命令模型。 */
  @CommandLine.Spec private CommandSpec spec;

  /** 创建 gateway 命令；Picocli 使用此构造器。 */
  public GatewayCommand() {
    this(new SpringRuntime());
  }

  /**
   * 创建 gateway 命令。
   *
   * @param runtime Spring 运行时启动器
   */
  public GatewayCommand(SpringRuntime runtime) {
    this.runtime = Objects.requireNonNull(runtime, "runtime");
  }

  @Override
  public Integer call() {
    try {
      runtime.start(WebApplicationType.NONE);
      spec.commandLine().getOut().println(CliMessages.get("cli.gateway.started"));
      runtime.awaitTermination();
      return 0;
    } catch (InterruptedException exception) {
      // 恢复中断标记，让上层感知线程被中断。
      Thread.currentThread().interrupt();
      return 1;
    } catch (RuntimeException exception) {
      spec.commandLine().getErr().println(CliMessages.errorPrefix() + exception.getMessage());
      return 1;
    } finally {
      // 无论正常关闭还是启动失败都释放上下文。
      runtime.close();
    }
  }
}
