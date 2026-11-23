package io.lytgos.cli.i18n;

import java.util.Map;
import picocli.CommandLine;

/**
 * 把本地化文案应用到 Picocli 命令树。
 *
 * <p>Picocli 的 {@code @Command} 没有 descriptionKey，因此命令描述在这里按命令路径覆盖； 选项和位置参数的 descriptionKey 由资源包解析。
 *
 * @author LytgOS2 Team
 */
public final class CliLocalization {

  /** 命令路径到描述键的映射。 */
  private static final Map<String, String> COMMAND_KEYS =
      Map.ofEntries(
          Map.entry("lytgos", "cli.root.description"),
          Map.entry("lytgos init", "cli.init.description"),
          Map.entry("lytgos status", "cli.status.description"),
          Map.entry("lytgos chat", "cli.chat.description"),
          Map.entry("lytgos serve", "cli.serve.description"),
          Map.entry("lytgos gateway", "cli.gateway.description"),
          Map.entry("lytgos profile", "cli.profile.description"),
          Map.entry("lytgos profile list", "cli.profile.list.description"),
          Map.entry("lytgos profile create", "cli.profile.create.description"),
          Map.entry("lytgos profile show", "cli.profile.show.description"),
          Map.entry("lytgos profile delete", "cli.profile.delete.description"),
          Map.entry("lytgos provider", "cli.provider.description"),
          Map.entry("lytgos provider list", "cli.provider.list.description"),
          Map.entry("lytgos tool", "cli.tool.description"),
          Map.entry("lytgos tool list", "cli.tool.list.description"),
          Map.entry("lytgos session", "cli.session.description"),
          Map.entry("lytgos session list", "cli.session.list.description"));

  /** 工具类不允许实例化。 */
  private CliLocalization() {}

  /**
   * 应用当前语言到命令树。
   *
   * @param commandLine Picocli 根命令
   */
  public static void apply(CommandLine commandLine) {
    commandLine.setResourceBundle(CliMessages.bundle());
    applyCommand(commandLine, "lytgos");
  }

  /**
   * 递归应用命令描述。
   *
   * @param commandLine 当前命令
   * @param path 命令路径
   */
  private static void applyCommand(CommandLine commandLine, String path) {
    // descriptionKey 是当前命令对应的资源键。
    String descriptionKey = COMMAND_KEYS.get(path);
    if (descriptionKey != null) {
      commandLine.getCommandSpec().usageMessage().description(CliMessages.get(descriptionKey));
    }
    commandLine.setResourceBundle(CliMessages.bundle());
    for (Map.Entry<String, CommandLine> entry : commandLine.getSubcommands().entrySet()) {
      applyCommand(entry.getValue(), path + " " + entry.getKey());
    }
  }
}
