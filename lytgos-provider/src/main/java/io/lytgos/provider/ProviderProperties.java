package io.lytgos.provider;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 全局 provider 配置绑定。
 *
 * <p>对应 {@code lytgos.providers}；只声明连接信息，AgentProfile 层声明具体 model/temperature。
 *
 * @param providers 本实例可用的 provider 列表
 * @author LytgOS2 Team
 */
@ConfigurationProperties(prefix = "lytgos")
public record ProviderProperties(List<ProviderConfig> providers) {

  public ProviderProperties {
    // 配置缺失时使用空列表，避免启动阶段出现 null。
    providers = providers == null ? List.of() : List.copyOf(providers);
  }

  /**
   * 单个 provider 的连接配置。
   *
   * @param name provider 名称，AgentProfile 通过它引用
   * @param baseUrl OpenAI 兼容 API 的 base URL
   * @param apiKey 凭证占位符解析后的值，可空
   * @author LytgOS2 Team
   */
  public record ProviderConfig(String name, String baseUrl, String apiKey) {}
}
