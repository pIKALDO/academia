package com.academia.guardians;

import static org.assertj.core.api.Assertions.assertThat;

import com.academia.support.AbstractIntegrationTest;
import com.academia.support.AuthTestSupport;
import com.academia.support.AuthTestSupport.SesionAutenticada;
import com.academia.support.StudentTestData;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Tutores y vínculo estudiante-tutor (docs/diseno-api.md sección 5.4). */
class GuardianControllerIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private StudentTestData data;

    @Autowired
    private JdbcTemplate jdbc;

    private RestTestClient client;
    private String csrf;
    private SesionAutenticada admin;

    @BeforeEach
    void autenticarAdmin() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        csrf = AuthTestSupport.obtenerTokenCsrf(client);
        String email = StudentTestData.unique("admin") + "@example.com";
        data.user(email, "ADMIN");
        admin = AuthTestSupport.login(client, csrf, email, StudentTestData.PASSWORD);
    }

    @Test
    void admin_crea_un_tutor_enlazado_a_una_cuenta_de_familia() {
        UUID cuenta = data.user(StudentTestData.unique("olena") + "@example.com", "GUARDIAN");

        client.post().uri("/api/v1/guardians")
                .cookie("SESSION", admin.sessionCookie())
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("userId", cuenta.toString(), "firstName", "Olena", "lastName", "Kovalenko"))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().value("Location", location -> assertThat(location)
                        .matches("http://localhost:\\d+/api/v1/guardians/[0-9a-f-]{36}"))
                .expectBody()
                .jsonPath("$.userId").isEqualTo(cuenta.toString());
    }

    @Test
    void enlazar_un_tutor_a_una_cuenta_que_no_es_de_familia_devuelve_422() {
        UUID cuentaAdmin = data.user(StudentTestData.unique("otro-admin") + "@example.com", "ADMIN");

        crearTutor(Map.of("userId", cuentaAdmin.toString(), "firstName", "X", "lastName", "Y"))
                .expectStatus().isEqualTo(422);
        crearTutor(Map.of("userId", UUID.randomUUID().toString(), "firstName", "X", "lastName", "Y"))
                .expectStatus().isEqualTo(422);
    }

    @Test
    void una_cuenta_no_puede_enlazarse_a_dos_tutores() {
        UUID cuenta = data.user(StudentTestData.unique("taras") + "@example.com", "GUARDIAN");
        data.guardian(cuenta, "Taras", "Kovalenko");

        crearTutor(Map.of("userId", cuenta.toString(), "firstName", "Otro", "lastName", "Tutor"))
                .expectStatus().isEqualTo(409);
    }

    /**
     * PUT idempotente sobre la pareja de ids: repetir la llamada no duplica el vínculo, lo
     * reescribe. Es también la forma de retirar el acceso sin desvincular.
     */
    @Test
    void put_del_vinculo_es_idempotente_y_reemplaza_sus_atributos() {
        UUID estudiante = data.student("Danylo", StudentTestData.unique("Kovalenko"));
        UUID tutor = data.guardian(null, "Olena", "Kovalenko");
        String uri = "/api/v1/students/" + estudiante + "/guardians/" + tutor;

        for (int i = 0; i < 2; i++) {
            putVinculo(uri, Map.of("relationship", "MOTHER", "isPrimary", true, "hasAccess", true))
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.isPrimary").isEqualTo(true)
                    .jsonPath("$.hasAccess").isEqualTo(true);
        }
        putVinculo(uri, Map.of("relationship", "LEGAL_GUARDIAN", "isPrimary", false, "hasAccess", false))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.relationship").isEqualTo("LEGAL_GUARDIAN")
                .jsonPath("$.hasAccess").isEqualTo(false);

        Integer vinculos = jdbc.queryForObject(
                "SELECT count(*) FROM student_guardians WHERE student_id = ? AND guardian_id = ?",
                Integer.class, estudiante, tutor);
        assertThat(vinculos).isEqualTo(1);
    }

    /** Sin {@code hasAccess} explícito no hay vínculo: un valor por defecto podría dar acceso. */
    @Test
    void put_del_vinculo_exige_los_tres_campos() {
        UUID estudiante = data.student("Danylo", StudentTestData.unique("Kovalenko"));
        UUID tutor = data.guardian(null, "Olena", "Kovalenko");

        putVinculo("/api/v1/students/" + estudiante + "/guardians/" + tutor,
                Map.of("relationship", "MOTHER", "isPrimary", true))
                .expectStatus().isEqualTo(422);
    }

    @Test
    void put_del_vinculo_con_estudiante_o_tutor_inexistente_devuelve_404() {
        UUID estudiante = data.student("Danylo", StudentTestData.unique("Kovalenko"));
        UUID tutor = data.guardian(null, "Olena", "Kovalenko");
        Map<String, Object> cuerpo = Map.of("relationship", "MOTHER", "isPrimary", true, "hasAccess", true);

        putVinculo("/api/v1/students/" + UUID.randomUUID() + "/guardians/" + tutor, cuerpo)
                .expectStatus().isNotFound();
        putVinculo("/api/v1/students/" + estudiante + "/guardians/" + UUID.randomUUID(), cuerpo)
                .expectStatus().isNotFound();
    }

    @Test
    void al_desvincular_la_familia_deja_de_ver_al_estudiante() {
        StudentTestData.Family familia = data.family(true);
        String uri = "/api/v1/students/" + familia.studentId() + "/guardians/" + familia.guardianId();

        client.delete().uri(uri)
                .cookie("SESSION", admin.sessionCookie())
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .exchange()
                .expectStatus().isNoContent();
        client.delete().uri(uri)
                .cookie("SESSION", admin.sessionCookie())
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .exchange()
                .expectStatus().isNotFound();

        SesionAutenticada tutor = AuthTestSupport.login(client, csrf, familia.email(), StudentTestData.PASSWORD);
        client.get().uri("/api/v1/students/" + familia.studentId())
                .cookie("SESSION", tutor.sessionCookie())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void familia_no_puede_gestionar_tutores_ni_vinculos() {
        StudentTestData.Family familia = data.family(true);
        SesionAutenticada tutor = AuthTestSupport.login(client, csrf, familia.email(), StudentTestData.PASSWORD);

        client.get().uri("/api/v1/guardians")
                .cookie("SESSION", tutor.sessionCookie())
                .exchange()
                .expectStatus().isForbidden();
        client.put().uri("/api/v1/students/" + familia.studentId() + "/guardians/" + familia.guardianId())
                .cookie("SESSION", tutor.sessionCookie())
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("relationship", "MOTHER", "isPrimary", true, "hasAccess", true))
                .exchange()
                .expectStatus().isForbidden();
    }

    private RestTestClient.ResponseSpec crearTutor(Map<String, ?> body) {
        return client.post().uri("/api/v1/guardians")
                .cookie("SESSION", admin.sessionCookie())
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private RestTestClient.ResponseSpec putVinculo(String uri, Map<String, ?> body) {
        return client.put().uri(uri)
                .cookie("SESSION", admin.sessionCookie())
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }
}
