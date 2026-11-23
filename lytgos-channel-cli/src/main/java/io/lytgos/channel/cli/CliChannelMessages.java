package io.lytgos.channel.cli;

/**
 * CLI 渠道需要的本地化文案。
 *
 * <p>由上层 CLI 模块实现，避免 channel 模块反向依赖 CLI 的国际化资源。
 *
 * @author LytgOS2 Team
 */
public interface CliChannelMessages {

  /**
   * 输入读取失败提示。
   *
   * @param detail 错误详情
   * @return 完整提示
   */
  String inputReadError(String detail);

  /**
   * Agent 处理失败提示。
   *
   * @param detail 错误详情
   * @return 完整提示
   */
  String agentError(String detail);
}
