package com.academia.students;

import static org.assertj.core.api.Assertions.assertThat;

import com.academia.support.AbstractIntegrationTest;
import com.academia.support.AuthTestSupport;
import com.academia.support.AuthTestSupport.SesionAutenticada;
import com.academia.support.StudentTestData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Operaciones de administración sobre la ficha (docs/diseno-api.md sección 5.3). */
class StudentControllerIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private StudentTestData data;

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
    void admin_crea_un_estudiante_y_recibe_201_con_location() {
        String location = client.post().uri("/api/v1/students")
                .cookie("SESSION", admin.sessionCookie())
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("firstName", "Danylo", "lastName", "Kovalenko", "nationality", "UA",
                        "birthDate", "2011-04-22"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ACTIVE")
                .jsonPath("$.nationality").isEqualTo("UA")
                .jsonPath("$.createdAt").exists()
                .returnResult()
                .getResponseHeaders().getLocation().getPath();

        client.get().uri(location)
                .cookie("SESSION", admin.sessionCookie())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.firstName").isEqualTo("Danylo");
    }

    @Test
    void crear_estudiante_sin_nombre_o_con_nacionalidad_invalida_devuelve_422() {
        client.post().uri("/api/v1/students")
                .cookie("SESSION", admin.sessionCookie())
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("lastName", "Kovalenko", "nationality", "Ucrania"))
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectBody()
                .jsonPath("$.errors[?(@.field == 'firstName')]").exists()
                .jsonPath("$.errors[?(@.field == 'nationality')]").exists();
    }

    @Test
    void patch_solo_cambia_los_campos_enviados() {
        UUID id = data.student("Lucía", "Martín");

        client.patch().uri("/api/v1/students/" + id)
                .cookie("SESSION", admin.sessionCookie())
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", "INACTIVE", "city", "València"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.firstName").isEqualTo("Lucía")
                .jsonPath("$.lastName").isEqualTo("Martín")
                .jsonPath("$.status").isEqualTo("INACTIVE")
                .jsonPath("$.contact.address.city").isEqualTo("València");
    }

    /** PUT es reemplazo total: lo que no se envía en la segunda llamada queda vacío. */
    @Test
    void put_del_bloque_deportivo_reemplaza_el_bloque_entero() {
        UUID id = data.student("Danylo", "Kovalenko");

        putJson("/api/v1/students/" + id + "/sports-profile",
                Map.of("level", "Nacional sub-14", "coachNotes", "Revés cortado"))
                .jsonPath("$.coachNotes").isEqualTo("Revés cortado");

        putJson("/api/v1/students/" + id + "/sports-profile", Map.of("level", "Nacional sub-16"))
                .jsonPath("$.level").isEqualTo("Nacional sub-16")
                .jsonPath("$.coachNotes").doesNotExist();
    }

    @Test
    void put_de_educacion_y_alojamiento_aparecen_en_la_ficha_de_administrador() {
        UUID id = data.student("Sofiia", "Kovalenko");

        putJson("/api/v1/students/" + id + "/education", Map.of("schoolName", "IES Ejemplo", "grade", "2º ESO"));
        putJson("/api/v1/students/" + id + "/housing", Map.of("addressLine", "Residencia", "notes", "Interna"));

        client.get().uri("/api/v1/students/" + id)
                .cookie("SESSION", admin.sessionCookie())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.education.grade").isEqualTo("2º ESO")
                .jsonPath("$.housing.notes").isEqualTo("Interna")
                .jsonPath("$.sportsProfile").doesNotExist();
    }

    @Test
    void put_de_bloque_sobre_estudiante_inexistente_devuelve_404() {
        client.put().uri("/api/v1/students/" + UUID.randomUUID() + "/education")
                .cookie("SESSION", admin.sessionCookie())
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("grade", "1º ESO"))
                .exchange()
                .expectStatus().isNotFound();
    }

    /**
     * Borrado lógico con {@code @SQLRestriction}: el estudiante desaparece para todos,
     * administrador incluido (docs/PROGRESO.md, decisión a revisar en el corte de documentos).
     */
    @Test
    void estudiante_borrado_deja_de_existir_incluso_para_el_admin() {
        UUID id = data.student("Borrado", StudentTestData.unique("Apellido"));

        client.delete().uri("/api/v1/students/" + id)
                .cookie("SESSION", admin.sessionCookie())
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .exchange()
                .expectStatus().isNoContent();

        client.get().uri("/api/v1/students/" + id)
                .cookie("SESSION", admin.sessionCookie())
                .exchange()
                .expectStatus().isNotFound();
        client.put().uri("/api/v1/students/" + id + "/housing")
                .cookie("SESSION", admin.sessionCookie())
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("notes", "x"))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void estudiante_borrado_desaparece_tambien_del_listado_de_su_familia() {
        StudentTestData.Family familia = data.family(true);
        client.delete().uri("/api/v1/students/" + familia.studentId())
                .cookie("SESSION", admin.sessionCookie())
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .exchange()
                .expectStatus().isNoContent();
        SesionAutenticada tutor = AuthTestSupport.login(client, csrf, familia.email(), StudentTestData.PASSWORD);

        client.get().uri("/api/v1/students")
                .cookie("SESSION", tutor.sessionCookie())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.page.totalElements").isEqualTo(0);
    }

    @Test
    void listado_de_admin_filtra_por_nombre_y_estado() {
        String apellido = StudentTestData.unique("Filtro");
        data.student("Ana", apellido);
        UUID inactivo = data.student("Bea", apellido);
        client.patch().uri("/api/v1/students/" + inactivo)
                .cookie("SESSION", admin.sessionCookie())
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", "INACTIVE"))
                .exchange()
                .expectStatus().isOk();

        client.get().uri("/api/v1/students?q=" + apellido.toUpperCase() + "&status=INACTIVE")
                .cookie("SESSION", admin.sessionCookie())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.page.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].id").isEqualTo(inactivo.toString())
                // DTO de listado: sin bloques ni contacto (docs/diseno-api.md sección 4.3).
                .jsonPath("$.content[0].contact").doesNotExist();
    }

    @Test
    void comodines_de_like_en_q_se_tratan_como_texto() {
        client.get().uri("/api/v1/students?q=%25")
                .cookie("SESSION", admin.sessionCookie())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.page.totalElements").isEqualTo(0);
    }

    @Test
    void el_tamano_de_pagina_se_topa_en_100() {
        client.get().uri("/api/v1/students?size=1000")
                .cookie("SESSION", admin.sessionCookie())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.page.size").isEqualTo(100);
    }

    @Test
    void contactos_de_emergencia_alta_edicion_y_borrado() {
        UUID id = data.student("Danylo", "Kovalenko");

        String location = client.post().uri("/api/v1/students/" + id + "/emergency-contacts")
                .cookie("SESSION", admin.sessionCookie())
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Iryna", "phone", "+34 600 000 010", "notes", "Solo por la tarde"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.priority").isEqualTo(1)
                .returnResult()
                .getResponseHeaders().getLocation().getPath();
        assertThat(location).startsWith("/api/v1/emergency-contacts/");

        Map<String, Object> cambios = new HashMap<>();
        cambios.put("priority", 2);
        client.patch().uri(location)
                .cookie("SESSION", admin.sessionCookie())
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .body(cambios)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.priority").isEqualTo(2)
                .jsonPath("$.name").isEqualTo("Iryna");

        client.get().uri("/api/v1/students/" + id + "/emergency-contacts")
                .cookie("SESSION", admin.sessionCookie())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].notes").isEqualTo("Solo por la tarde")
                .jsonPath("$.page.totalElements").isEqualTo(1);

        client.delete().uri(location)
                .cookie("SESSION", admin.sessionCookie())
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .exchange()
                .expectStatus().isNoContent();
        client.delete().uri(location)
                .cookie("SESSION", admin.sessionCookie())
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .exchange()
                .expectStatus().isNotFound();
    }

    private RestTestClient.BodyContentSpec putJson(String uri, Map<String, ?> body) {
        return client.put().uri(uri)
                .cookie("SESSION", admin.sessionCookie())
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody();
    }
}
