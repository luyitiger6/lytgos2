package io.lytgos.cli.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CLI 语言选择规则测试。
 *
 * @author LytgOS2 Team
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class CliLocaleResolverTest {

  /** 被测解析器。 */
  private final CliLocaleResolver resolver = new CliLocaleResolver();

  @Test
  @DisplayName("LYTGOS_LANG 显式覆盖优先")
  void explicitOverrideWins() {
    assertThat(resolver.resolve(environment("Windows 11", "zh", "GBK", "GBK", "")))
        .isEqualTo(Locale.SIMPLIFIED_CHINESE);
    assertThat(resolver.resolve(environment("Linux", "en", "UTF-8", "UTF-8", "zh_CN.UTF-8")))
        .isEqualTo(Locale.ENGLISH);
  }

  @Test
  @DisplayName("Windows 下 stdout/native 编码一致时选择对应语言")
  void windowsEncodingMatch() {
    assertThat(resolver.resolve(environment("Windows 11", "", "UTF-8", "UTF-8", "")))
        .isEqualTo(Locale.SIMPLIFIED_CHINESE);
    assertThat(resolver.resolve(environment("Windows 11", "", "GBK", "GBK", "")))
        .isEqualTo(Locale.SIMPLIFIED_CHINESE);
  }

  @Test
  @DisplayName("Windows 下编码不一致时回退英文")
  void windowsEncodingMismatchFallsBackToEnglish() {
    assertThat(resolver.resolve(environment("Windows 11", "", "UTF-8", "GBK", "")))
        .isEqualTo(Locale.ENGLISH);
    assertThat(resolver.resolve(environment("Windows 11", "", "GBK", "UTF-8", "")))
        .isEqualTo(Locale.ENGLISH);
  }

  @Test
  @DisplayName("Unix UTF-8 locale 选择中文")
  void unixUtf8Locale() {
    assertThat(resolver.resolve(environment("Linux", "", "UTF-8", "UTF-8", "zh_CN.UTF-8")))
        .isEqualTo(Locale.SIMPLIFIED_CHINESE);
    assertThat(resolver.resolve(environment("Mac OS X", "", "UTF-8", "UTF-8", "en_US.UTF-8")))
        .isEqualTo(Locale.SIMPLIFIED_CHINESE);
  }

  @Test
  @DisplayName("Unix 非 UTF-8 locale 回退英文")
  void unixNonUtf8LocaleFallsBackToEnglish() {
    assertThat(resolver.resolve(environment("Linux", "", "", "", "C"))).isEqualTo(Locale.ENGLISH);
    assertThat(resolver.resolve(environment("Linux", "", "", "", ""))).isEqualTo(Locale.ENGLISH);
  }

  @Test
  @DisplayName("未知覆盖值回退英文")
  void unknownOverrideFallsBackToEnglish() {
    assertThat(resolver.resolve(environment("Windows 11", "fr", "UTF-8", "UTF-8", "")))
        .isEqualTo(Locale.ENGLISH);
  }

  /**
   * 创建测试环境。
   *
   * @param osName 系统名
   * @param override 语言覆盖
   * @param stdoutEncoding 标准输出编码
   * @param nativeEncoding 原生编码
   * @param locale locale
   * @return 环境快照
   */
  private static CliEnvironment environment(
      String osName, String override, String stdoutEncoding, String nativeEncoding, String locale) {
    return new CliEnvironment(osName, override, stdoutEncoding, nativeEncoding, locale);
  }
}
