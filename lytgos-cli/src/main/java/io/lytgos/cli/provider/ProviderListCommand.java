package io.lytgos.cli.provider;

import io.lytgos.cli.i18n.CliMessages;
import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

/**
 * 列出全局 Provider 的名称与连接地址。
 *
 * <p>不读取、不解析、不输出 api-key；凭证缺失也不影响列表。
 *
 * @author LytgOS2 Team
 */
@CommandLine.Command(
    name = "list",
    mixinStandardHelpOptions = true,
    description = "列出 Provider 名称与连接地址")
@SuppressWarnings("PMD.CloseResource")
public final class ProviderListCommand implements Callable<Integer> {

  /** Provider 配置读取器。 */
  private final ProviderConfigReader reader;

  /** Picocli 注入的命令模型。 */
  @CommandLine.Spec private CommandSpec spec;

  /** 按默认 classpath 配置创建命令；Picocli 使用此构造器。 */
  public ProviderListCommand() {
    this(new ProviderConfigReader());
  }

  /**
   * 创建 Provider 列表命令。
   *
   * @param reader Provider 配置读取器
   */
  public ProviderListCommand(ProviderConfigReader reader) {
    this.reader = Objects.requireNonNull(reader, "reader");
  }

  @Override
  public Integer call() {
    try {
      // providers 是配置中声明的 Provider 视图列表。
      List<ProviderView> providers = reader.read();
      // out 是命令标准输出。
      PrintWriter out = spec.commandLine().getOut();
      if (providers.isEmpty()) {
        out.println(CliMessages.get("cli.provider.list.empty"));
        return 0;
      }
      out.printf(
          "%-12s %s%n",
          CliMessages.get("cli.provider.list.headerName"),
          CliMessages.get("cli.provider.list.headerBaseUrl"));
      for (ProviderView provider : providers) {
        // baseUrl 缺失时显示占位符，避免输出 null。
        String baseUrl = provider.baseUrl() == null ? "-" : provider.baseUrl();
        out.printf("%-12s %s%n", provider.name(), baseUrl);
      }
      return 0;
    } catch (RuntimeException exception) {
      // 配置读取失败时返回非零退出码，不打印堆栈。
      spec.commandLine().getErr().println(CliMessages.errorPrefix() + exception.getMessage());
      return 1;
    }
  }
}
