package io.lytgos.cli.i18n;

import java.util.Objects;

/**
 * CLI 语言检测所需的可测试环境快照。
 *
 * @param osName 操作系统名称
 * @param languageOverride LYTGOS_LANG 覆盖值
 * @param stdoutEncoding Java 标准输出编码
 * @param nativeEncoding 操作系统原生编码
 * @param locale Unix 风格 locale 值
 * @author LytgOS2 Team
 */
public record CliEnvironment(
    String osName,
    String languageOverride,
    String stdoutEncoding,
    String nativeEncoding,
    String locale) {

  public CliEnvironment {
    // 所有字段归一为空字符串，避免后续空指针。
    osName = osName == null ? "" : osName;
    languageOverride = languageOverride == null ? "" : languageOverride;
    stdoutEncoding = stdoutEncoding == null ? "" : stdoutEncoding;
    nativeEncoding = nativeEncoding == null ? "" : nativeEncoding;
    locale = locale == null ? "" : locale;
  }

  /**
   * 读取当前进程环境。
   *
   * @return 环境快照
   */
  public static CliEnvironment system() {
    // locale 是 Unix 风格的区域设置，Windows 上通常为空。
    String locale =
        firstNonBlank(System.getenv("LC_ALL"), System.getenv("LC_CTYPE"), System.getenv("LANG"));
    // stdoutEncoding 优先取 Java 18+ 的 stdout.encoding，避免直接访问 System.out。
    String stdoutEncoding =
        System.getProperty("stdout.encoding", System.getProperty("file.encoding", ""));
    return new CliEnvironment(
        System.getProperty("os.name", ""),
        System.getenv("LYTGOS_LANG"),
        stdoutEncoding,
        System.getProperty("native.encoding", ""),
        locale);
  }

  /**
   * 返回第一个非空白值。
   *
   * @param values 候选值
   * @return 第一个非空白值；全部为空时返回空字符串
   */
  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return "";
  }

  /**
   * 判断操作系统是否为 Windows。
   *
   * @return Windows 返回 true
   */
  public boolean isWindows() {
    return Objects.requireNonNull(osName).toLowerCase(java.util.Locale.ROOT).contains("win");
  }
}
