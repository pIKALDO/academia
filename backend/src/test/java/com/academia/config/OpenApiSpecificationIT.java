package com.academia.config;

import com.academia.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No verifica el contenido del contrato (para eso están los tests de cada controlador):
 * su única función es dejar el contrato actualizado en docs/openapi.json en cada build.
 * El paso "verificar contrato de API" de backend.yml falla si este fichero difiere del
 * commiteado, así que una ruptura de la API aparece en el diff de la pull request.
 */
@AutoConfigureRestTestClient
class OpenApiSpecificationIT extends AbstractIntegrationTest {

    private static final Path OPENAPI_JSON = Path.of("..", "docs", "openapi.json").normalize();

    @Autowired
    private RestTestClient restTestClient;

    @Test
    void la_especificacion_openapi_se_exporta_a_docs_openapi_json() throws IOException {
        byte[] body = restTestClient.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult()
                .getResponseBody();

        ObjectMapper objectMapper = new ObjectMapper();
        Object spec = objectMapper.readValue(body, Object.class);
        String formatted = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(spec) + System.lineSeparator();

        Files.writeString(OPENAPI_JSON, formatted, StandardCharsets.UTF_8);

        assertThat(Files.exists(OPENAPI_JSON)).isTrue();
    }
}
