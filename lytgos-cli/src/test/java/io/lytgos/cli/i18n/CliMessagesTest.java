package io.lytgos.cli.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 中英文资源包一致性测试。
 *
 * @author LytgOS2 Team
 */
class CliMessagesTest {

  @AfterEach
  void resetToEnglish() {
    // 其他测试使用英文作为稳定默认值。
    CliMessages.initialize(Locale.ENGLISH);
  }

  @Test
  @DisplayName("中英文资源包键集合一致")
  void bundlesHaveSameKeys() {
    // chinese 是中文资源包。
    ResourceBundle chinese = ResourceBundle.getBundle("i18n.cli", Locale.SIMPLIFIED_CHINESE);
    // english 是英文资源包。
    ResourceBundle english = ResourceBundle.getBundle("i18n.cli", Locale.ENGLISH);

    assertThat(keys(chinese)).isEqualTo(keys(english));
  }

  @Test
  @DisplayName("语言切换后错误前缀和格式化文本正确")
  void formatsMessagesForBothLanguages() {
    CliMessages.initialize(Locale.ENGLISH);
    assertThat(CliMessages.errorPrefix()).isEqualTo("Error: ");
    assertThat(CliMessages.get("cli.profile.create.exists", "demo"))
        .isEqualTo("AgentProfile already exists: demo");

    CliMessages.initialize(Locale.SIMPLIFIED_CHINESE);
    assertThat(CliMessages.errorPrefix()).isEqualTo("错误: ");
    assertThat(CliMessages.get("cli.profile.create.exists", "demo"))
        .isEqualTo("AgentProfile 已存在: demo");
  }

  /**
   * 读取资源包全部键。
   *
   * @param bundle 资源包
   * @return 有序键集合
   */
  private static Set<String> keys(ResourceBundle bundle) {
    // result 保存有序键，便于断言差异。
    Set<String> result = new TreeSet<>();
    for (String key : bundle.keySet()) {
      result.add(key);
    }
    return result;
  }
}
