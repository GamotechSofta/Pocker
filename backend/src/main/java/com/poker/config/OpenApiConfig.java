package com.poker.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI pokerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Poker Game API")
                .version("0.7.0")
                .description("Play-money poker API. Phase 7 adds lobby table REST endpoints."))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local development")
                ))
                .components(new Components().addSecuritySchemes(
                        "bearer-jwt",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                ));
    }
}
