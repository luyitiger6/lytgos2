package io.lytgos.boot;

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
    SpringApplication.run(LytgosApplication.class, args);
  }

  @Bean
  ApplicationRunner startupBanner() {
    return args -> LOG.info("{} {} skeleton started", Lytgos.NAME, Lytgos.VERSION);
  }
}
