package io.lytgos.boot.agent;

import io.lytgos.core.agent.AgentProfile;
import io.lytgos.core.agent.AgentProfileLoader;
import io.lytgos.core.agent.AgentProfileRegistry;
import io.lytgos.provider.ProviderProperties;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AgentProfile 启动装配：启动时扫描工作区下的 AgentProfile 并注册到内存索引。
 *
 * @author LytgOS2 Team
 */
@Configuration
public class AgentProfileBootstrapConfiguration {

  /** 日志记录器。 */
  private static final Logger LOG =
      LoggerFactory.getLogger(AgentProfileBootstrapConfiguration.class);

  /**
   * 注册 AgentProfile 内存索引。
   *
   * @return 空的 AgentProfileRegistry
   */
  @Bean
  public AgentProfileRegistry agentProfileRegistry() {
    return new AgentProfileRegistry();
  }

  /**
   * 注册 AgentProfile 加载器。
   *
   * @param properties 全局 provider 配置
   * @return AgentProfileLoader
   */
  @Bean
  public AgentProfileLoader agentProfileLoader(ProviderProperties properties) {
    // providerNames 是全局配置声明的 provider 名称集合。
    Set<String> providerNames = new LinkedHashSet<>();
    for (ProviderProperties.ProviderConfig provider : properties.providers()) {
      // providerName 是单个全局 provider 的名称。
      String providerName = provider.name();
      if (providerName != null && !providerName.isBlank()) {
        providerNames.add(providerName);
      }
    }
    return new AgentProfileLoader(providerNames, System::getenv);
  }

  /**
   * 注册启动期 AgentProfile 扫描器。
   *
   * @param loader AgentProfile 加载器
   * @param registry AgentProfile 内存索引
   * @param workspace 工作区目录
   * @return 启动期 ApplicationRunner
   */
  @Bean
  public ApplicationRunner profileRegistrationRunner(
      AgentProfileLoader loader,
      AgentProfileRegistry registry,
      @Value("${lytgos.workspace:.lytgos}") String workspace) {
    return new ApplicationRunner() {
      @Override
      public void run(ApplicationArguments args) {
        // args：启动参数；本扫描器不消费启动参数。
        // profilesDirectory 是工作区下的 AgentProfile 目录。
        Path profilesDirectory = Path.of(workspace).resolve("profiles");
        // profiles 是本次加载成功的 AgentProfile 列表。
        List<AgentProfile> profiles = loader.load(profilesDirectory);
        for (AgentProfile agentProfile : profiles) {
          registry.register(agentProfile);
        }
        if (LOG.isInfoEnabled()) {
          LOG.info(
              "AgentProfile 启动加载完成: directory={}, profiles={}",
              profilesDirectory.toAbsolutePath(),
              profiles.size());
        }
      }
    };
  }
}
