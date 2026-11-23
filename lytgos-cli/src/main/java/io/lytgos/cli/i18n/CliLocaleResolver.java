package io.lytgos.cli.i18n;

import java.util.Locale;

/**
 * 根据终端编码能力选择 CLI 文案语言。
 *
 * <p>规则：显式覆盖优先；Windows 比较 stdout/native 编码是否匹配；Unix 检查 UTF-8 locale。 无法确认时回退英文，避免中文乱码。
 *
 * @author LytgOS2 Team
 */
public final class CliLocaleResolver {

  /** 中文 locale。 */
  private static final Locale CHINESE = Locale.SIMPLIFIED_CHINESE;

  /** 英文 locale。 */
  private static final Locale ENGLISH = Locale.ENGLISH;

  /** 中文覆盖前缀。 */
  private static final String CHINESE_PREFIX = "zh";

  /** 英文覆盖前缀。 */
  private static final String ENGLISH_PREFIX = "en";

  /** UTF-8 编码名。 */
  private static final String UTF8_HYPHEN = "utf-8";

  /** UTF-8 紧凑编码名。 */
  private static final String UTF8_COMPACT = "utf8";

  /** GBK 编码名。 */
  private static final String GBK = "gbk";

  /** GB2312 编码名。 */
  private static final String GB2312 = "gb2312";

  /** GB18030 编码名。 */
  private static final String GB18030 = "gb18030";

  /** Windows 936 代码页。 */
  private static final String CODE_PAGE_936 = "936";

  /**
   * 解析语言。
   *
   * @param environment 环境快照
   * @return 中文或英文 locale
   */
  public Locale resolve(CliEnvironment environment) {
    // override 是用户显式指定的语言。
    String override = environment.languageOverride().trim().toLowerCase(Locale.ROOT);
    if (override.startsWith(CHINESE_PREFIX)) {
      return CHINESE;
    }
    if (override.startsWith(ENGLISH_PREFIX)) {
      return ENGLISH;
    }
    if (!override.isEmpty()) {
      return ENGLISH;
    }
    if (environment.isWindows()) {
      return windowsSupportsChinese(environment) ? CHINESE : ENGLISH;
    }
    return unixSupportsChinese(environment) ? CHINESE : ENGLISH;
  }

  /**
   * 判断 Windows 终端是否可能正常显示中文。
   *
   * @param environment 环境快照
   * @return 可能显示中文时返回 true
   */
  private static boolean windowsSupportsChinese(CliEnvironment environment) {
    // stdout 与 native 编码必须同时为 UTF-8，或同时为中文编码；不一致通常就是乱码来源。
    String stdout = normalize(environment.stdoutEncoding());
    String nativeEncoding = normalize(environment.nativeEncoding());
    if (stdout.isEmpty() || nativeEncoding.isEmpty()) {
      return false;
    }
    if (isUtf8(stdout) && isUtf8(nativeEncoding)) {
      return true;
    }
    return isChinese(stdout) && isChinese(nativeEncoding);
  }

  /**
   * 判断 Unix 终端是否可能正常显示中文。
   *
   * @param environment 环境快照
   * @return 可能显示中文时返回 true
   */
  private static boolean unixSupportsChinese(CliEnvironment environment) {
    // locale 明确是 UTF-8 时认为可以显示中文。
    if (normalize(environment.locale()).contains(UTF8_HYPHEN)
        || normalize(environment.locale()).contains(UTF8_COMPACT)) {
      return true;
    }
    // locale 缺失时，stdout 与 native 都为 UTF-8 也视为可用。
    return isUtf8(normalize(environment.stdoutEncoding()))
        && isUtf8(normalize(environment.nativeEncoding()));
  }

  /**
   * 归一化编码名称。
   *
   * @param value 原始值
   * @return 小写编码名
   */
  private static String normalize(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }

  /**
   * 判断是否为 UTF-8 编码。
   *
   * @param encoding 编码名
   * @return UTF-8 返回 true
   */
  private static boolean isUtf8(String encoding) {
    return encoding.contains(UTF8_HYPHEN) || encoding.contains(UTF8_COMPACT);
  }

  /**
   * 判断是否为中文编码。
   *
   * @param encoding 编码名
   * @return GBK/GB2312/GB18030 返回 true
   */
  private static boolean isChinese(String encoding) {
    return encoding.contains(GBK)
        || encoding.contains(GB2312)
        || encoding.contains(GB18030)
        || encoding.contains(CODE_PAGE_936);
  }
}
