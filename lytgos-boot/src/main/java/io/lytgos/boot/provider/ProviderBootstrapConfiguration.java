package io.lytgos.boot.provider;

import io.lytgos.core.audit.LlmCallAuditor;
import io.lytgos.provider.ProviderModelFactory;
import io.lytgos.provider.ProviderProperties;
import io.lytgos.provider.ProviderService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provider 启动装配：绑定全局配置、构造显式模型映射并注册 ProviderService。
 *
 * @author LytgOS2 Team
 */
@Configuration
@EnableConfigurationProperties(ProviderProperties.class)
public class ProviderBootstrapConfiguration {

  /**
   * 注册 provider 模型工厂。
   *
   * @return 模型工厂
   */
  @Bean
  public ProviderModelFactory providerModelFactory() {
    return new ProviderModelFactory();
  }

  /**
   * 注册 ProviderService。
   *
   * @param properties 全局 provider 配置
   * @param factory provider 模型工厂
   * @param auditor LLM 调用审计端口
   * @return ProviderService
   */
  @Bean
  public ProviderService providerService(
      ProviderProperties properties, ProviderModelFactory factory, LlmCallAuditor auditor) {
    return new ProviderService(factory.create(properties), auditor);
  }
}
