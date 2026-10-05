package com.mediassistrover.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String USER_BEARER_SCHEME = "bearerAuth";
    public static final String DEVICE_BEARER_SCHEME = "deviceBearerAuth";

    @Bean
    OpenAPI mediAssistRoverOpenApi() {
        Components components = new Components()
                .addSecuritySchemes(USER_BEARER_SCHEME, bearerScheme("JWT dành cho ADMIN và STAFF"))
                .addSecuritySchemes(DEVICE_BEARER_SCHEME, bearerScheme("Device JWT dành riêng cho Raspberry Pi"));

        return new OpenAPI()
                .info(new Info()
                        .title("MediAssistRover Backend API")
                        .version("v1")
                        .description("REST API quản lý bệnh viện, workflow, job và giao tiếp Server–Raspberry Pi."))
                .components(components)
                .addSecurityItem(new SecurityRequirement().addList(USER_BEARER_SCHEME));
    }

    private SecurityScheme bearerScheme(String description) {
        return new SecurityScheme()
                .name("Authorization")
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description(description);
    }
}
