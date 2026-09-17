package com.academia.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI apiInfo() {
        String securitySchemeName = "session";
        return new OpenAPI()
                .info(new Info()
                        .title("API de gestión de la academia")
                        .version("1.0")
                        .description("API privada de gestión de estudiantes, familias y documentación."))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("SESSION")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName));
    }
}
