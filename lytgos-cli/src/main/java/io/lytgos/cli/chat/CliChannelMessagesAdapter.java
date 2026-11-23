package io.lytgos.cli.chat;

import io.lytgos.channel.cli.CliChannelMessages;
import io.lytgos.cli.i18n.CliMessages;

/**
 * 把 CLI 国际化资源适配为 channel 模块需要的文案接口。
 *
 * @author LytgOS2 Team
 */
final class CliChannelMessagesAdapter implements CliChannelMessages {

  @Override
  public String inputReadError(String detail) {
    return CliMessages.errorPrefix() + CliMessages.get("cli.channel.inputReadError", detail);
  }

  @Override
  public String agentError(String detail) {
    return CliMessages.errorPrefix() + CliMessages.get("cli.channel.agentError", detail);
  }
}
