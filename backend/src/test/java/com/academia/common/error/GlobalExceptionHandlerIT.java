package com.academia.common.error;

import com.academia.support.AbstractIntegrationTest;
import com.academia.support.AuthTestSupport;
import com.academia.support.AuthTestSupport.SesionAutenticada;
import com.academia.support.StudentTestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Lo que {@link GlobalExceptionHandlerTest} no puede probar con el manejador suelto: que el
 * enrutado real de Spring MVC produce las excepciones que el manejador espera.
 */
class GlobalExceptionHandlerIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private StudentTestData data;

    /** Con sesión: sin ella, el filtro de seguridad responde 401 antes de llegar al enrutado. */
    @Test
    void una_ruta_inexistente_devuelve_404_con_problem_detail() {
        RestTestClient client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        String csrf = AuthTestSupport.obtenerTokenCsrf(client);
        String email = StudentTestData.unique("admin") + "@example.com";
        data.user(email, "ADMIN");
        SesionAutenticada admin = AuthTestSupport.login(client, csrf, email, StudentTestData.PASSWORD);

        client.get().uri("/api/v1/no-existe")
                .cookie("SESSION", admin.sessionCookie())
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.traceId").doesNotExist();
    }
}
