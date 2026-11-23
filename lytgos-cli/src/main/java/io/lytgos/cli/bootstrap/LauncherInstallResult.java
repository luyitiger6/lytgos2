package io.lytgos.cli.bootstrap;

import java.util.List;

/**
 * launcher 安装结果。
 *
 * @param messages 需要展示给用户的提示
 * @author LytgOS2 Team
 */
public record LauncherInstallResult(List<String> messages) {

  public LauncherInstallResult {
    // 提示列表做防御性复制。
    messages = messages == null ? List.of() : List.copyOf(messages);
  }

  /**
   * 空结果。
   *
   * @return 不包含提示的结果
   */
  public static LauncherInstallResult empty() {
    return new LauncherInstallResult(List.of());
  }
}
