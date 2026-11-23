package io.lytgos.cli.bootstrap;

import java.nio.file.Path;

/**
 * 生成跨平台 lytgos 启动脚本。
 *
 * <p>脚本只包含 ASCII 字符，避免在 Windows cmd 下因文件编码产生乱码。
 *
 * @author LytgOS2 Team
 */
public final class LauncherScriptFactory {

  /** Windows 脚本标记。 */
  private static final String WINDOWS_MARKER = "REM LytgOS2 CLI launcher (auto-generated)";

  /** Unix 脚本标记。 */
  private static final String UNIX_MARKER = "# LytgOS2 CLI launcher (auto-generated)";

  /** 工具类不允许实例化。 */
  private LauncherScriptFactory() {}

  /**
   * 生成指定平台的启动脚本。
   *
   * @param platform 平台
   * @param jarPath JAR 绝对路径
   * @return 脚本内容
   */
  public static String render(LauncherPlatform platform, Path jarPath) {
    return platform == LauncherPlatform.WINDOWS ? renderWindows(jarPath) : renderUnix(jarPath);
  }

  /**
   * 判断脚本是否由 LytgOS2 创建。
   *
   * @param platform 平台
   * @param content 脚本内容
   * @return 是托管脚本时返回 true
   */
  public static boolean isManaged(LauncherPlatform platform, String content) {
    if (content == null) {
      return false;
    }
    if (content.contains(platform == LauncherPlatform.WINDOWS ? WINDOWS_MARKER : UNIX_MARKER)) {
      return true;
    }
    // 兼容早期手工创建、没有标记的 LytgOS2 启动脚本。
    return content.contains("lytgos") && content.contains("java -jar");
  }

  /**
   * 生成 Windows 批处理脚本。
   *
   * @param jarPath JAR 绝对路径
   * @return 脚本内容
   */
  private static String renderWindows(Path jarPath) {
    // windowsJar 是 cmd 中安全的路径文本。
    String windowsJar = jarPath.toAbsolutePath().normalize().toString().replace("%", "%%");
    return "@echo off\r\n"
        + WINDOWS_MARKER
        + "\r\n"
        + "setlocal\r\n"
        + "chcp 65001 >nul\r\n"
        + "java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dfile.encoding=UTF-8"
        + " -jar \""
        + windowsJar
        + "\" %*\r\n"
        + "exit /b %ERRORLEVEL%\r\n";
  }

  /**
   * 生成 Unix shell 脚本。
   *
   * @param jarPath JAR 绝对路径
   * @return 脚本内容
   */
  private static String renderUnix(Path jarPath) {
    // unixJar 是 shell 双引号内安全的路径文本。
    String unixJar =
        jarPath
            .toAbsolutePath()
            .normalize()
            .toString()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("$", "\\$")
            .replace("`", "\\`");
    return "#!/usr/bin/env sh\n"
        + UNIX_MARKER
        + "\n"
        + "exec java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dfile.encoding=UTF-8"
        + " -jar \""
        + unixJar
        + "\" \"$@\"\n";
  }
}
