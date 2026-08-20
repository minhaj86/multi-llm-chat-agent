package com.example.aichat.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI description (Swagger UI at {@code /swagger-ui.html}).
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI aiWorkspaceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Workspace API")
                        .description("Provider-agnostic chat API (OpenAI, Qwen, Llama and any OpenAI-compatible "
                                + "server). Streaming endpoints use Server-Sent Events.")
                        .version("1.0"))
                .components(new Components().addSecuritySchemes("bearer", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
