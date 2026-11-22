package io.lytgos.boot;

import io.lytgos.boot.workspace.WorkspaceInitialization;
import io.lytgos.core.Lytgos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * LytgOS2 Spring Boot entry point.
 *
 * @author LytgOS2 Team
 */
@SpringBootApplication(scanBasePackages = "io.lytgos")
public class LytgosApplication {

  private static final Logger LOG = LoggerFactory.getLogger(LytgosApplication.class);

  public static void main(String[] args) {
    // 显式创建 SpringApplication，便于在数据源初始化前注册工作区目录初始化器。
    SpringApplication application = new SpringApplication(LytgosApplication.class);
    // 该初始化器会在 SQLite 数据源创建前执行，确保 .lytgos/ 存在。
    application.addInitializers(new WorkspaceInitialization());
    application.run(args);
  }

  @Bean
  ApplicationRunner startupBanner() {
    return args -> LOG.info("{} {} skeleton started", Lytgos.NAME, Lytgos.VERSION);
  }
}
