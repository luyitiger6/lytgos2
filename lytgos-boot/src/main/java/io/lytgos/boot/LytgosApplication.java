package io.lytgos.boot;

import io.lytgos.core.Lytgos;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * LytgOS2 Spring Boot entry point.
 */
@SpringBootApplication(scanBasePackages = "io.lytgos")
public class LytgosApplication {

    public static void main(String[] args) {
        SpringApplication.run(LytgosApplication.class, args);
    }

    @Bean
    CommandLineRunner startupBanner() {
        return args -> System.out.println(Lytgos.NAME + " " + Lytgos.VERSION + " skeleton started");
    }
}
