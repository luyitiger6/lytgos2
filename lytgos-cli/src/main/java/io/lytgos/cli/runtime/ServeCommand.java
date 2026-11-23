package io.lytgos.cli.runtime;

import io.lytgos.cli.i18n.CliMessages;
import java.util.Objects;
import java.util.concurrent.Callable;
import org.springframework.boot.WebApplicationType;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

/**
 * 启动 Web Service 并保持运行。
 *
 * <p>命令只负责启动组装根与阻塞主线程；HTTP 端点属于后续 Web 能力。
 *
 * @author LytgOS2 Team
 */
@CommandLine.Command(
    name = "serve",
    mixinStandardHelpOptions = true,
    description = "启动 Web Service")
public final class ServeCommand implements Callable<Integer> {

  /** Spring 运行时启动器。 */
  private final SpringRuntime runtime;

  /** Picocli 注入的命令模型。 */
  @CommandLine.Spec private CommandSpec spec;

  /** 创建 serve 命令；Picocli 使用此构造器。 */
  public ServeCommand() {
    this(new SpringRuntime());
  }

  /**
   * 创建 serve 命令。
   *
   * @param runtime Spring 运行时启动器
   */
  public ServeCommand(SpringRuntime runtime) {
    this.runtime = Objects.requireNonNull(runtime, "runtime");
  }

  @Override
  public Integer call() {
    try {
      runtime.start(WebApplicationType.SERVLET);
      spec.commandLine().getOut().println(CliMessages.get("cli.serve.started"));
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
