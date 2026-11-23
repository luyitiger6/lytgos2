package io.lytgos.cli.bootstrap;

import java.util.Locale;

/**
 * launcher 安装目标平台。
 *
 * @author LytgOS2 Team
 */
public enum LauncherPlatform {
  /** Windows。 */
  WINDOWS,
  /** macOS。 */
  MACOS,
  /** Linux 及其他 Unix。 */
  LINUX;

  /** Windows 系统名标记。 */
  private static final String WINDOWS_MARKER = "win";

  /** macOS 系统名标记。 */
  private static final String MACOS_MARKER = "mac";

  /**
   * 根据操作系统名称判断平台。
   *
   * @param osName 操作系统名称
   * @return 目标平台
   */
  public static LauncherPlatform detect(String osName) {
    // name 是归一化后的系统名。
    String name = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
    if (name.contains(WINDOWS_MARKER)) {
      return WINDOWS;
    }
    if (name.contains(MACOS_MARKER)) {
      return MACOS;
    }
    return LINUX;
  }
}
