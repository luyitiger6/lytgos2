package io.lytgos.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

/**
 * ProviderModelFactory 的显式映射构造与跳过规则测试。
 *
 * @author LytgOS2 Team
 */
class ProviderModelFactoryTest {

  /** 测试用 DeepSeek provider 名称。 */
  private static final String DEEPSEEK_PROVIDER = "deepseek";

  /** 测试用 DeepSeek base URL。 */
  private static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com";

  /** 测试用 Kimi provider 名称。 */
  private static final String KIMI_PROVIDER = "kimi";

  /** 测试用 Kimi base URL。 */
  private static final String KIMI_BASE_URL = "https://api.moonshot.cn/v1";

  @Test
  @DisplayName("为每个有凭证的 provider 构造一个模型，且不访问网络")
  void createsModelForEachProviderWithCredentials() {
    // properties 包含两个完整 provider 配置。
    ProviderProperties properties =
        new ProviderProperties(
            List.of(
                new ProviderProperties.ProviderConfig(
                    DEEPSEEK_PROVIDER, DEEPSEEK_BASE_URL, "dummy-key-deepseek"),
                new ProviderProperties.ProviderConfig(
                    KIMI_PROVIDER, KIMI_BASE_URL, "dummy-key-kimi")));
    // factory 是被测工厂。
    ProviderModelFactory factory = new ProviderModelFactory();

    // models 是显式 provider 名称到模型的映射。
    Map<String, ChatModel> models = factory.create(properties);

    assertThat(models).containsOnlyKeys(DEEPSEEK_PROVIDER, KIMI_PROVIDER);
  }

  @Test
  @DisplayName("缺少 api-key 的 provider 不构造模型，其他 provider 不受影响")
  void skipsProviderWithBlankApiKey() {
    // properties 中 deepseek 缺少 key，kimi 正常。
    ProviderProperties properties =
        new ProviderProperties(
            List.of(
                new ProviderProperties.ProviderConfig(DEEPSEEK_PROVIDER, DEEPSEEK_BASE_URL, " "),
                new ProviderProperties.ProviderConfig(
                    KIMI_PROVIDER, KIMI_BASE_URL, "dummy-key-kimi")));
    // factory 是被测工厂。
    ProviderModelFactory factory = new ProviderModelFactory();

    // models 是显式 provider 名称到模型的映射。
    Map<String, ChatModel> models = factory.create(properties);

    assertThat(models).containsOnlyKeys(KIMI_PROVIDER);
  }

  @Test
  @DisplayName("缺少 base-url 的 provider 不构造模型")
  void skipsProviderWithBlankBaseUrl() {
    // properties 中唯一的 provider 缺少 base-url。
    ProviderProperties properties =
        new ProviderProperties(
            List.of(
                new ProviderProperties.ProviderConfig(
                    DEEPSEEK_PROVIDER, " ", "dummy-key-deepseek")));
    // factory 是被测工厂。
    ProviderModelFactory factory = new ProviderModelFactory();

    // models 是显式 provider 名称到模型的映射。
    Map<String, ChatModel> models = factory.create(properties);

    assertThat(models).isEmpty();
  }

  @Test
  @DisplayName("重复 provider 名称保留首个，不抛异常")
  void duplicateProviderNameKeepsFirst() {
    // properties 中同名 provider 出现两次。
    ProviderProperties properties =
        new ProviderProperties(
            List.of(
                new ProviderProperties.ProviderConfig(
                    DEEPSEEK_PROVIDER, DEEPSEEK_BASE_URL, "dummy-key-first"),
                new ProviderProperties.ProviderConfig(
                    DEEPSEEK_PROVIDER, DEEPSEEK_BASE_URL, "dummy-key-second")));
    // factory 是被测工厂。
    ProviderModelFactory factory = new ProviderModelFactory();

    // models 是显式 provider 名称到模型的映射。
    Map<String, ChatModel> models = factory.create(properties);

    assertThat(models).containsOnlyKeys(DEEPSEEK_PROVIDER);
  }

  @Test
  @DisplayName("空 provider 配置返回空映射")
  void emptyProviderConfigurationReturnsEmptyMap() {
    // properties 不包含 provider。
    ProviderProperties properties = new ProviderProperties(List.of());
    // factory 是被测工厂。
    ProviderModelFactory factory = new ProviderModelFactory();

    // models 是显式 provider 名称到模型的映射。
    Map<String, ChatModel> models = factory.create(properties);

    assertThat(models).isEmpty();
  }
}
