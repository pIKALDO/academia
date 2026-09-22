package com.academia.students;

import static org.assertj.core.api.Assertions.assertThat;

import com.academia.support.AbstractIntegrationTest;
import com.academia.support.AuthTestSupport;
import com.academia.support.AuthTestSupport.SesionAutenticada;
import com.academia.support.StudentTestData;
import com.academia.support.StudentTestData.Family;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * El corazón del proyecto (docs/diseno-api.md sección 3.2): qué ve cada familia, y que lo que
 * no ve sea indistinguible de lo que no existe. Contra la API real, con sesión real y
 * PostgreSQL real: prueba a la vez {@code AccessService}, la consulta de
 * {@code StudentAccessSpecifications}, {@code HideStudentWhenNotVisible} y los DTOs por rol.
 */
class StudentAccessIT extends AbstractIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {
            };

    @LocalServerPort
    private int port;

    @Autowired
    private StudentTestData data;

    private RestTestClient client;
    private String csrf;

    @BeforeEach
    void crearClienteLimpio() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        csrf = AuthTestSupport.obtenerTokenCsrf(client);
    }

    @Test
    void familia_no_puede_ver_estudiante_de_otra_familia() {
        Family familiaA = data.family(true);
        Family familiaB = data.family(true);
        SesionAutenticada b = login(familiaB.email());

        client.get().uri("/api/v1/students/" + familiaA.studentId())
                .cookie("SESSION", b.sessionCookie())
                .exchange()
                .expectStatus().isNotFound();
    }

    /**
     * Un 404 con un {@code detail}, un {@code title} o un {@code type} distinto al de un id
     * inexistente delataría igual que un 403 que el estudiante existe (regla no negociable
     * nº1). Se compara el cuerpo entero, salvo lo que por naturaleza varía entre dos
     * peticiones: {@code timestamp}, e {@code instance}, que es la propia URL pedida.
     */
    @Test
    void estudiante_ajeno_e_inexistente_devuelven_exactamente_la_misma_respuesta() {
        Family familiaA = data.family(true);
        Family familiaB = data.family(true);
        SesionAutenticada b = login(familiaB.email());
        UUID inexistente = UUID.randomUUID();

        EntityExchangeResult<Map<String, Object>> ajeno = getProblem(b, familiaA.studentId());
        EntityExchangeResult<Map<String, Object>> noExiste = getProblem(b, inexistente);

        assertThat(ajeno.getResponseHeaders().getContentType())
                .isEqualTo(noExiste.getResponseHeaders().getContentType());

        Map<String, Object> cuerpoAjeno = new HashMap<>(ajeno.getResponseBody());
        Map<String, Object> cuerpoNoExiste = new HashMap<>(noExiste.getResponseBody());
        assertThat(cuerpoAjeno.remove("instance")).isEqualTo("/api/v1/students/" + familiaA.studentId());
        assertThat(cuerpoNoExiste.remove("instance")).isEqualTo("/api/v1/students/" + inexistente);
        assertThat(cuerpoAjeno.remove("timestamp")).isNotNull();
        assertThat(cuerpoNoExiste.remove("timestamp")).isNotNull();

        assertThat(cuerpoAjeno).isEqualTo(cuerpoNoExiste);
        assertThat(cuerpoAjeno).containsEntry("status", 404);
    }

    @Test
    void familia_puede_ver_a_su_propio_hijo() {
        Family familia = data.family(true);
        SesionAutenticada sesion = login(familia.email());

        client.get().uri("/api/v1/students/" + familia.studentId())
                .cookie("SESSION", sesion.sessionCookie())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(familia.studentId().toString())
                .jsonPath("$.guardians[0].relationship").isEqualTo("MOTHER")
                .jsonPath("$.guardians[0].isPrimary").isEqualTo(true);
    }

    @Test
    void familia_no_recibe_las_notas_del_entrenador() {
        Family familia = data.family(true);
        data.sportsProfile(familia.studentId(), "Le falta actitud");
        SesionAutenticada sesion = login(familia.email());

        String cuerpo = getBodyAsString(sesion, "/api/v1/students/" + familia.studentId());

        // Ni el campo ni su contenido: comprobar solo la clave dejaría pasar el texto bajo
        // otro nombre.
        assertThat(cuerpo).doesNotContain("coachNotes").doesNotContain("Le falta actitud");
        assertThat(cuerpo).contains("\"level\":\"Nacional sub-14\"");
    }

    @Test
    void familia_no_recibe_el_bloque_de_alojamiento() {
        Family familia = data.family(true);
        data.housing(familia.studentId(), "Incidencia en la residencia");
        SesionAutenticada sesion = login(familia.email());

        String cuerpo = getBodyAsString(sesion, "/api/v1/students/" + familia.studentId());

        assertThat(cuerpo).doesNotContain("housing").doesNotContain("Incidencia en la residencia")
                .doesNotContain("Residencia, hab. 1");
    }

    @Test
    void familia_no_recibe_el_contacto_de_los_tutores_ni_los_metadatos_de_gestion() {
        Family familia = data.family(true);
        SesionAutenticada sesion = login(familia.email());

        String cuerpo = getBodyAsString(sesion, "/api/v1/students/" + familia.studentId());

        assertThat(cuerpo).doesNotContain("+34 600 111 222").doesNotContain("tutor@example.com")
                .doesNotContain("hasAccess").doesNotContain("createdAt").doesNotContain("enrolledAt");
    }

    @Test
    void admin_si_recibe_notas_del_entrenador_y_alojamiento() {
        Family familia = data.family(true);
        data.sportsProfile(familia.studentId(), "Le falta actitud");
        data.housing(familia.studentId(), "Incidencia en la residencia");
        SesionAutenticada admin = loginNuevo("ADMIN");

        client.get().uri("/api/v1/students/" + familia.studentId())
                .cookie("SESSION", admin.sessionCookie())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.sportsProfile.coachNotes").isEqualTo("Le falta actitud")
                .jsonPath("$.housing.notes").isEqualTo("Incidencia en la residencia")
                .jsonPath("$.guardians[0].phone").isEqualTo("+34 600 111 222")
                .jsonPath("$.guardians[0].hasAccess").isEqualTo(true);
    }

    @Test
    void listado_de_familia_solo_devuelve_sus_hijos() {
        Family familia = data.family(true);
        UUID hermano = data.student("Hermano", StudentTestData.unique("Apellido"));
        data.link(hermano, familia.guardianId(), true);
        data.family(true); // otra familia, con su propio hijo
        SesionAutenticada sesion = login(familia.email());

        client.get().uri("/api/v1/students")
                .cookie("SESSION", sesion.sessionCookie())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.page.totalElements").isEqualTo(2)
                .jsonPath("$.content[*].id").value(ids -> assertThat((Iterable<Object>) ids)
                        .containsExactlyInAnyOrder(familia.studentId().toString(), hermano.toString()));
    }

    @Test
    void tutor_sin_has_access_no_ve_al_estudiante() {
        Family familia = data.family(false);
        SesionAutenticada sesion = login(familia.email());

        client.get().uri("/api/v1/students/" + familia.studentId())
                .cookie("SESSION", sesion.sessionCookie())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void tutor_sin_has_access_no_ve_al_estudiante_en_el_listado() {
        Family familia = data.family(false);
        SesionAutenticada sesion = login(familia.email());

        client.get().uri("/api/v1/students")
                .cookie("SESSION", sesion.sessionCookie())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.page.totalElements").isEqualTo(0);
    }

    @Test
    void familia_no_puede_ver_contactos_de_emergencia_de_otra_familia() {
        Family familiaA = data.family(true);
        Family familiaB = data.family(true);
        SesionAutenticada b = login(familiaB.email());

        client.get().uri("/api/v1/students/" + familiaA.studentId() + "/emergency-contacts")
                .cookie("SESSION", b.sessionCookie())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void familia_ve_los_contactos_de_emergencia_de_su_hijo_sin_notas_internas() {
        Family familia = data.family(true);
        data.emergencyContact(familia.studentId(), "Abuela Ana", "No llamar antes de las 9");
        SesionAutenticada sesion = login(familia.email());

        String cuerpo = getBodyAsString(sesion, "/api/v1/students/" + familia.studentId() + "/emergency-contacts");

        assertThat(cuerpo).contains("Abuela Ana").doesNotContain("No llamar antes de las 9")
                .doesNotContain("\"id\"").doesNotContain("priority");
    }

    /**
     * 403 y no 404: la familia ya sabe que su hijo existe, y editar es una operación que su
     * rol no tiene (docs/diseno-api.md sección 3.1).
     */
    @Test
    void familia_no_puede_editar_a_su_propio_hijo() {
        Family familia = data.family(true);
        SesionAutenticada sesion = login(familia.email());

        client.patch().uri("/api/v1/students/" + familia.studentId())
                .cookie("SESSION", sesion.sessionCookie())
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("firstName", "Otro"))
                .exchange()
                .expectStatus().isForbidden();
    }

    /**
     * Las escrituras solo miran el rol: la familia recibe el mismo 403 con el id de un
     * estudiante ajeno que con uno inexistente, así que tampoco aquí se filtra nada.
     */
    @Test
    void familia_recibe_el_mismo_403_al_editar_un_estudiante_ajeno_o_inexistente() {
        Family familiaA = data.family(true);
        Family familiaB = data.family(true);
        SesionAutenticada b = login(familiaB.email());

        for (UUID id : new UUID[] {familiaA.studentId(), UUID.randomUUID()}) {
            client.put().uri("/api/v1/students/" + id + "/housing")
                    .cookie("SESSION", b.sessionCookie())
                    .cookie("XSRF-TOKEN", csrf)
                    .header("X-XSRF-TOKEN", csrf)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("notes", "x"))
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    /** STUDENT no tiene acceso al módulo en fase 1: 403 sin mirar el id. */
    @Test
    void cuenta_de_estudiante_recibe_403_en_todo_el_modulo() {
        Family familia = data.family(true);
        SesionAutenticada estudiante = loginNuevo("STUDENT");

        client.get().uri("/api/v1/students")
                .cookie("SESSION", estudiante.sessionCookie())
                .exchange()
                .expectStatus().isForbidden();
        client.get().uri("/api/v1/students/" + familia.studentId())
                .cookie("SESSION", estudiante.sessionCookie())
                .exchange()
                .expectStatus().isForbidden();
        client.get().uri("/api/v1/students/" + UUID.randomUUID())
                .cookie("SESSION", estudiante.sessionCookie())
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void sin_sesion_el_modulo_devuelve_401() {
        client.get().uri("/api/v1/students")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private SesionAutenticada login(String email) {
        return AuthTestSupport.login(client, csrf, email, StudentTestData.PASSWORD);
    }

    private SesionAutenticada loginNuevo(String role) {
        String email = StudentTestData.unique(role.toLowerCase()) + "@example.com";
        data.user(email, role);
        return login(email);
    }

    private EntityExchangeResult<Map<String, Object>> getProblem(SesionAutenticada sesion, UUID studentId) {
        return client.get().uri("/api/v1/students/" + studentId)
                .cookie("SESSION", sesion.sessionCookie())
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(JSON_OBJECT)
                .returnResult();
    }

    private String getBodyAsString(SesionAutenticada sesion, String uri) {
        return client.get().uri(uri)
                .cookie("SESSION", sesion.sessionCookie())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }
}
