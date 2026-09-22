package com.academia.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.academia.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Verifica el contrato de {@link ResponseSchemaModelConverter} sobre el contrato real
 * (docs/PROGRESO.md, "Pendientes conocidos"): toda componente de un record de respuesta es
 * {@code required}, las anotadas con {@code @Nullable} salen como {@code type: [X, "null"]} (o
 * {@code anyOf} si son un {@code $ref}), y los {@code *Request} no se ven afectados.
 */
@AutoConfigureRestTestClient
class ResponseSchemaModelConverterIT extends AbstractIntegrationTest {

    @Autowired
    private RestTestClient restTestClient;

    private JsonNode schemas() {
        byte[] body = restTestClient.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult()
                .getResponseBody();
        try {
            return new ObjectMapper().readTree(body).path("components").path("schemas");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void un_campo_obligatorio_de_un_record_de_respuesta_sale_como_required_y_sin_null() {
        JsonNode schema = schemas().path("UserDetailDto");

        assertThat(requiredOf(schema)).contains("email");
        JsonNode email = schema.path("properties").path("email");
        assertThat(email.path("type").asText()).isEqualTo("string");
    }

    @Test
    void un_campo_nulable_de_un_record_de_respuesta_sale_como_required_y_como_type_con_null() {
        JsonNode schema = schemas().path("UserDetailDto");

        assertThat(requiredOf(schema)).contains("lastLoginAt");
        JsonNode lastLoginAt = schema.path("properties").path("lastLoginAt");
        assertThat(typesOf(lastLoginAt)).containsExactlyInAnyOrder("string", "null");
    }

    @Test
    void un_campo_nulable_que_referencia_otro_esquema_sale_envuelto_en_anyof() {
        JsonNode schema = schemas().path("StudentAdminDto");

        assertThat(requiredOf(schema)).contains("sportsProfile");
        JsonNode sportsProfile = schema.path("properties").path("sportsProfile");
        assertThat(sportsProfile.has("$ref")).isFalse();
        assertThat(sportsProfile.path("anyOf")).hasSize(2);
        assertThat(StreamSupport.stream(sportsProfile.path("anyOf").spliterator(), false)
                .anyMatch(node -> node.path("$ref").asText().endsWith("/SportsProfileDto")))
                .isTrue();
        assertThat(StreamSupport.stream(sportsProfile.path("anyOf").spliterator(), false)
                .anyMatch(node -> typesOf(node).contains("null")))
                .isTrue();
    }

    @Test
    void un_request_no_se_ve_afectado_por_el_conversor() {
        JsonNode schema = schemas().path("UpdateUserRequest");

        // UpdateUserRequest (PATCH) no lleva @NotNull/@NotBlank en ningún campo: "null" = "no
        // tocar" (docs/PROGRESO.md, pendiente "PATCH no puede vaciar un campo opcional"). El
        // conversor de respuestas no debe añadirle required ni tocar su nulabilidad.
        assertThat(requiredOf(schema)).isEmpty();
        JsonNode displayName = schema.path("properties").path("displayName");
        assertThat(displayName.path("type").asText()).isEqualTo("string");
    }

    private static List<String> requiredOf(JsonNode schema) {
        return StreamSupport.stream(schema.path("required").spliterator(), false)
                .map(JsonNode::asText)
                .toList();
    }

    private static List<String> typesOf(JsonNode propertySchema) {
        JsonNode type = propertySchema.path("type");
        if (type.isArray()) {
            return StreamSupport.stream(type.spliterator(), false).map(JsonNode::asText).toList();
        }
        return type.isMissingNode() ? List.of() : List.of(type.asText());
    }
}
