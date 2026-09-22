package com.academia.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

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
                // URL relativa fija: si no se fija, springdoc infiere el "servers.url" de la
                // petición que generó la especificación. En los tests de integración eso es un
                // puerto aleatorio (RANDOM_PORT), que cambiaría en cada ejecución y rompería la
                // comparación de docs/openapi.json en CI aunque el contrato no hubiera cambiado.
                .servers(List.of(new Server().url("/")))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("SESSION")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName));
    }

    // Registrado como bean para que springdoc lo recoja automáticamente (ModelConverters
    // añade cada ModelConverter del contexto de Spring a la cadena de resolución de esquemas).
    @Bean
    ResponseSchemaModelConverter responseSchemaModelConverter() {
        return new ResponseSchemaModelConverter();
    }
}
