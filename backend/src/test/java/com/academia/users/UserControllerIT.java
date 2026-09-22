package com.academia.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.academia.support.AbstractIntegrationTest;
import com.academia.support.AuthTestSupport;
import com.academia.support.AuthTestSupport.SesionAutenticada;
import com.academia.users.dto.CreateUserRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;

class UserControllerIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    private RestTestClient restTestClient;

    @BeforeEach
    void crearClienteLimpio() {
        restTestClient = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void admin_puede_crear_un_usuario_y_recibe_201_con_location() {
        SesionAutenticada admin = crearYAutenticarAdmin("admin1@example.com");

        restTestClient.post().uri("/api/v1/users")
                .cookie("SESSION", admin.sessionCookie())
                .cookie("XSRF-TOKEN", admin.csrfToken())
                .header("X-XSRF-TOKEN", admin.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateUserRequest("nuevo@example.com", UserRole.GUARDIAN, "Nuevo Usuario"))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().value("Location", location -> assertThat(location)
                        .matches("http://localhost:\\d+/api/v1/users/[0-9a-f-]{36}"))
                .expectBody()
                .jsonPath("$.status").isEqualTo("PENDING_ACTIVATION")
                .jsonPath("$.email").isEqualTo("nuevo@example.com");
    }

    @Test
    void crear_usuario_con_email_duplicado_devuelve_409() {
        SesionAutenticada admin = crearYAutenticarAdmin("admin2@example.com");
        crearUsuarioActivo("duplicado@example.com", UserRole.GUARDIAN, "password-123");

        restTestClient.post().uri("/api/v1/users")
                .cookie("SESSION", admin.sessionCookie())
                .cookie("XSRF-TOKEN", admin.csrfToken())
                .header("X-XSRF-TOKEN", admin.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateUserRequest("duplicado@example.com", UserRole.GUARDIAN, "Otro"))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void usuario_no_admin_no_puede_listar_usuarios() {
        crearUsuarioActivo("familia1@example.com", UserRole.GUARDIAN, "password-123");
        String csrf = AuthTestSupport.obtenerTokenCsrf(restTestClient);
        SesionAutenticada familia = AuthTestSupport.login(restTestClient, csrf, "familia1@example.com", "password-123");

        restTestClient.get().uri("/api/v1/users")
                .cookie("SESSION", familia.sessionCookie())
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void admin_puede_desactivar_y_reactivar_un_usuario() {
        SesionAutenticada admin = crearYAutenticarAdmin("admin4@example.com");
        UserEntity objetivo = crearUsuarioActivo("objetivo@example.com", UserRole.GUARDIAN, "password-123");

        restTestClient.post().uri("/api/v1/users/" + objetivo.getId() + "/disable")
                .cookie("SESSION", admin.sessionCookie())
                .cookie("XSRF-TOKEN", admin.csrfToken())
                .header("X-XSRF-TOKEN", admin.csrfToken())
                .exchange()
                .expectStatus().isNoContent();

        restTestClient.get().uri("/api/v1/users/" + objetivo.getId())
                .cookie("SESSION", admin.sessionCookie())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("DISABLED");

        restTestClient.post().uri("/api/v1/users/" + objetivo.getId() + "/enable")
                .cookie("SESSION", admin.sessionCookie())
                .cookie("XSRF-TOKEN", admin.csrfToken())
                .header("X-XSRF-TOKEN", admin.csrfToken())
                .exchange()
                .expectStatus().isNoContent();

        restTestClient.get().uri("/api/v1/users/" + objetivo.getId())
                .cookie("SESSION", admin.sessionCookie())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ACTIVE");
    }

    /**
     * El argumento de docs/diseno-api.md sección 2.1 para elegir sesión con cookie en lugar
     * de JWT es precisamente este: la revocación es inmediata, no depende de que caduque un
     * token. Si este test no pasara, esa justificación de diseño sería falsa.
     */
    @Test
    void al_desactivar_un_usuario_su_sesion_activa_se_invalida_de_inmediato() {
        SesionAutenticada admin = crearYAutenticarAdmin("admin5@example.com");
        crearUsuarioActivo("familia2@example.com", UserRole.GUARDIAN, "password-123");
        // El token CSRF no es específico de usuario (CookieCsrfTokenRepository no lo liga a la
        // sesión): el mismo cliente puede reutilizar el que ya obtuvo para el admin. Pedir uno
        // nuevo aquí fallaría, porque el cliente ya presenta una cookie XSRF-TOKEN válida y el
        // servidor no vuelve a enviar Set-Cookie para un valor que no ha cambiado.
        SesionAutenticada familia =
                AuthTestSupport.login(restTestClient, admin.csrfToken(), "familia2@example.com", "password-123");

        restTestClient.get().uri("/api/v1/auth/me")
                .cookie("SESSION", familia.sessionCookie())
                .exchange()
                .expectStatus().isOk();

        UserEntity objetivo = userRepository.findByEmailIgnoreCase("familia2@example.com").orElseThrow();
        restTestClient.post().uri("/api/v1/users/" + objetivo.getId() + "/disable")
                .cookie("SESSION", admin.sessionCookie())
                .cookie("XSRF-TOKEN", admin.csrfToken())
                .header("X-XSRF-TOKEN", admin.csrfToken())
                .exchange()
                .expectStatus().isNoContent();

        restTestClient.get().uri("/api/v1/auth/me")
                .cookie("SESSION", familia.sessionCookie())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private SesionAutenticada crearYAutenticarAdmin(String email) {
        crearUsuarioActivo(email, UserRole.ADMIN, "password-123");
        String csrf = AuthTestSupport.obtenerTokenCsrf(restTestClient);
        return AuthTestSupport.login(restTestClient, csrf, email, "password-123");
    }

    private UserEntity crearUsuarioActivo(String email, UserRole role, String password) {
        UserEntity user = new UserEntity(email, role, "Nombre de prueba");
        user.activate(passwordEncoder.encode(password));
        return userRepository.save(user);
    }
}
