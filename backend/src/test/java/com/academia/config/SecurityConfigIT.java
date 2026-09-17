package com.academia.config;

import com.academia.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.RestTestClient;

@AutoConfigureRestTestClient
class SecurityConfigIT extends AbstractIntegrationTest {

    @Autowired
    private RestTestClient restTestClient;

    @Test
    void una_peticion_sin_autenticar_a_un_recurso_protegido_responde_401_sin_cuerpo() {
        restTestClient.get().uri("/test/protected")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectBody().isEmpty();
    }

    @Test
    void el_endpoint_de_salud_es_publico() {
        restTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }
}
