package com.pulseops.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI pulseOpsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("PulseOps API")
                        .version("v1")
                        .description("API de observabilidade, incidentes, deploys e qualidade de software.")
                        .contact(new Contact().name("PulseOps Engineering"))
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes(
                        "bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
