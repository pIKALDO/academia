package com.academia.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.academia.support.AbstractIntegrationTest;
import com.academia.support.AuthTestSupport;
import com.academia.support.AuthTestSupport.SesionAutenticada;
import com.academia.support.StudentTestData;
import com.academia.support.StudentTestData.Family;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.MultiValueMap;

/**
 * Documentación (docs/diseno-api.md sección 5.6). Cubre validación por contenido (415/413),
 * la regla del 404 aplicada a la subida y la excepción de revisión (403, sección 5.6), y la
 * idempotencia del aviso diario de caducidad.
 */
class DocumentControllerIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private StudentTestData data;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DocumentExpiryNotificationService notificationService;

    @MockitoBean
    private JavaMailSender mailSender;

    private RestTestClient client;
    private String csrf;
    private SesionAutenticada admin;

    @BeforeEach
    void preparar() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        csrf = AuthTestSupport.obtenerTokenCsrf(client);
        String email = StudentTestData.unique("admin") + "@example.com";
        data.user(email, "ADMIN");
        admin = AuthTestSupport.login(client, csrf, email, StudentTestData.PASSWORD);

        // El mock de JavaMailSender necesita un MimeMessage real para que
        // MimeMessageHelper pueda construirse; sin esto, DocumentExpiryMailService lanzaría
        // una NullPointerException que ni siquiera atraparía su propio try/catch.
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
    }

    @Test
    void subir_documento_de_tipo_no_permitido_devuelve_415() {
        UUID studentId = data.student("Danylo", StudentTestData.unique("Kovalenko"));
        byte[] contenidoFalso = "esto no es ni un pdf, ni un jpeg, ni un png".getBytes(StandardCharsets.UTF_8);

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", contenidoFalso).filename("pasaporte.pdf").contentType(MediaType.APPLICATION_PDF);
        builder.part("category", "PASSPORT");
        builder.part("name", "Pasaporte falso.pdf");

        client.post().uri("/api/v1/students/" + studentId + "/documents")
                .cookie("SESSION", admin.sessionCookie())
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(builder.build())
                .exchange()
                .expectStatus().isEqualTo(415);
    }

    @Test
    void subir_documento_demasiado_grande_devuelve_413() {
        UUID studentId = data.student("Danylo", StudentTestData.unique("Kovalenko"));
        byte[] contenidoEnorme = new byte[11 * 1024 * 1024];

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", contenidoEnorme).filename("pasaporte.pdf").contentType(MediaType.APPLICATION_PDF);
        builder.part("category", "PASSPORT");
        builder.part("name", "Pasaporte enorme.pdf");

        client.post().uri("/api/v1/students/" + studentId + "/documents")
                .cookie("SESSION", admin.sessionCookie())
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(builder.build())
                .exchange()
                .expectStatus().isEqualTo(413);
    }

    /**
     * {@code AccessService.canUploadDocument} es una regla de visibilidad (delegada en
     * {@code canViewStudent}), no de operación: un estudiante ajeno debe dar 404, igual que al
     * leerlo (regla no negociable nº1).
     */
    @Test
    void familia_subiendo_documento_de_hijo_ajeno_devuelve_404() {
        Family familiaObjetivo = data.family(true);
        Family familiaSubiendo = data.family(true);
        SesionAutenticada tutor = AuthTestSupport.login(client, csrf, familiaSubiendo.email(), StudentTestData.PASSWORD);

        client.post().uri("/api/v1/students/" + familiaObjetivo.studentId() + "/documents")
                .cookie("SESSION", tutor.sessionCookie())
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(pdfUploadBody())
                .exchange()
                .expectStatus().isNotFound();
    }

    /**
     * Un 404 con un cuerpo distinto al de un id inexistente delataría, igual que un 403, que
     * el estudiante existe (regla no negociable nº1; mismo test que
     * {@code StudentAccessIT.estudiante_ajeno_e_inexistente_devuelven_exactamente_la_misma_respuesta}
     * pero para la subida de documentos).
     */
    @Test
    void subida_a_estudiante_ajeno_e_inexistente_devuelve_exactamente_la_misma_respuesta() {
        Family familiaObjetivo = data.family(true);
        Family familiaSubiendo = data.family(true);
        SesionAutenticada tutor = AuthTestSupport.login(client, csrf, familiaSubiendo.email(), StudentTestData.PASSWORD);
        UUID inexistente = UUID.randomUUID();

        var ajeno = postDocumentProblem(tutor, familiaObjetivo.studentId());
        var noExiste = postDocumentProblem(tutor, inexistente);

        assertThat(ajeno.getResponseHeaders().getContentType()).isEqualTo(noExiste.getResponseHeaders().getContentType());

        Map<String, Object> cuerpoAjeno = new HashMap<>(ajeno.getResponseBody());
        Map<String, Object> cuerpoNoExiste = new HashMap<>(noExiste.getResponseBody());
        assertThat(cuerpoAjeno.remove("instance"))
                .isEqualTo("/api/v1/students/" + familiaObjetivo.studentId() + "/documents");
        assertThat(cuerpoNoExiste.remove("instance")).isEqualTo("/api/v1/students/" + inexistente + "/documents");
        assertThat(cuerpoAjeno.remove("timestamp")).isNotNull();
        assertThat(cuerpoNoExiste.remove("timestamp")).isNotNull();

        assertThat(cuerpoAjeno).isEqualTo(cuerpoNoExiste);
        assertThat(cuerpoAjeno).containsEntry("status", 404);
    }

    @Test
    void familia_intentando_revisar_documento_de_su_hijo_devuelve_403() {
        Family familia = data.family(true);
        UUID documentId = data.document(familia.studentId(), "PASSPORT", "RECEIVED", LocalDate.now().plusYears(1));
        SesionAutenticada tutor = AuthTestSupport.login(client, csrf, familia.email(), StudentTestData.PASSWORD);

        client.post().uri("/api/v1/documents/" + documentId + "/review")
                .cookie("SESSION", tutor.sessionCookie())
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void proceso_de_avisos_no_reenvia_en_segunda_ejecucion() {
        Family familia = data.family(true);
        UUID documentId = data.document(familia.studentId(), "HEALTH_INSURANCE", "RECEIVED",
                LocalDate.now().plusDays(30));

        notificationService.sendExpiryNotifications();
        notificationService.sendExpiryNotifications();

        verify(mailSender, times(1)).createMimeMessage();
        verify(mailSender, times(1)).send(any(MimeMessage.class));

        // StudentTestData.guardian() fija el email de contacto del tutor a "tutor@example.com":
        // es el que lee la consulta de destinatarios (guardians.email), no el email de la
        // cuenta de acceso (familia.email(), guardado en users.email).
        Integer filas = jdbc.queryForObject("""
                SELECT count(*) FROM document_notifications
                WHERE document_id = ? AND kind = 'EXPIRY_30D'::notification_kind AND recipient_email = ?
                """, Integer.class, documentId, "tutor@example.com");
        assertThat(filas).isEqualTo(1);
    }

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {
            };

    private EntityExchangeResult<Map<String, Object>> postDocumentProblem(SesionAutenticada tutor, UUID studentId) {
        return client.post().uri("/api/v1/students/" + studentId + "/documents")
                .cookie("SESSION", tutor.sessionCookie())
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(pdfUploadBody())
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(JSON_OBJECT)
                .returnResult();
    }

    private static MultiValueMap<String, HttpEntity<?>> pdfUploadBody() {
        byte[] pdf = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", pdf).filename("documento.pdf").contentType(MediaType.APPLICATION_PDF);
        builder.part("category", "PASSPORT");
        builder.part("name", "Documento.pdf");
        return builder.build();
    }
}
