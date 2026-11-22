package io.lytgos.web.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata for the 1.0 REST contract. */
@Configuration
@OpenAPIDefinition(
    info = @Info(title = "LytgOS2 API", version = "1.0", description = "Runtime kernel REST API"))
public class OpenApiConfig {}
