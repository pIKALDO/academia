package com.academia.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.academia.support.AbstractIntegrationTest;
import com.academia.support.AuthTestSupport;
import com.academia.support.AuthTestSupport.SesionAutenticada;
import com.academia.users.dto.ActivateAccountRequest;
import com.academia.users.dto.LoginRequest;
import com.academia.users.dto.PasswordResetConfirmRequest;
import com.academia.users.dto.PasswordResetRequest;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;

class AuthControllerIT extends AbstractIntegrationTest {

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
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void login_correcto_establece_sesion_y_permite_acceder_a_auth_me() {
        crearUsuarioActivo("ana@example.com", UserRole.ADMIN, "password-123");
        String csrf = AuthTestSupport.obtenerTokenCsrf(restTestClient);

        SesionAutenticada sesion = AuthTestSupport.login(restTestClient, csrf, "ana@example.com", "password-123");

        restTestClient.get().uri("/api/v1/auth/me")
                .cookie("SESSION", sesion.sessionCookie())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo("ana@example.com");
    }

    @Test
    void login_con_contrasena_incorrecta_devuelve_401_generico() {
        crearUsuarioActivo("bruno@example.com", UserRole.ADMIN, "password-123");
        String csrf = AuthTestSupport.obtenerTokenCsrf(restTestClient);

        restTestClient.post().uri("/api/v1/auth/login")
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest("bruno@example.com", "contraseña-incorrecta"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Email o contraseña incorrectos.");
    }

    @Test
    void login_con_usuario_inexistente_devuelve_el_mismo_401_que_contrasena_incorrecta() {
        String csrf = AuthTestSupport.obtenerTokenCsrf(restTestClient);

        restTestClient.post().uri("/api/v1/auth/login")
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest("no-existe@example.com", "cualquier-cosa"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Email o contraseña incorrectos.");
    }

    @Test
    void login_con_cuenta_desactivada_devuelve_401() {
        UserEntity usuario = crearUsuarioActivo("carla@example.com", UserRole.ADMIN, "password-123");
        usuario.disable();
        userRepository.save(usuario);
        String csrf = AuthTestSupport.obtenerTokenCsrf(restTestClient);

        restTestClient.post().uri("/api/v1/auth/login")
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest("carla@example.com", "password-123"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void auth_me_sin_sesion_devuelve_401() {
        restTestClient.get().uri("/api/v1/auth/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().isEmpty();
    }

    @Test
    void logout_invalida_la_sesion() {
        crearUsuarioActivo("diego@example.com", UserRole.ADMIN, "password-123");
        String csrf = AuthTestSupport.obtenerTokenCsrf(restTestClient);
        SesionAutenticada sesion = AuthTestSupport.login(restTestClient, csrf, "diego@example.com", "password-123");

        restTestClient.post().uri("/api/v1/auth/logout")
                .cookie("SESSION", sesion.sessionCookie())
                .cookie("XSRF-TOKEN", sesion.csrfToken())
                .header("X-XSRF-TOKEN", sesion.csrfToken())
                .exchange()
                .expectStatus().isNoContent();

        restTestClient.get().uri("/api/v1/auth/me")
                .cookie("SESSION", sesion.sessionCookie())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void password_reset_devuelve_202_exista_o_no_el_email() {
        crearUsuarioActivo("elena@example.com", UserRole.ADMIN, "password-123");
        String csrf = AuthTestSupport.obtenerTokenCsrf(restTestClient);

        restTestClient.post().uri("/api/v1/auth/password-reset")
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PasswordResetRequest("elena@example.com"))
                .exchange()
                .expectStatus().isAccepted();

        restTestClient.post().uri("/api/v1/auth/password-reset")
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PasswordResetRequest("no-existe@example.com"))
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void password_reset_confirm_con_token_valido_cambia_la_contrasena() {
        UserEntity usuario = crearUsuarioActivo("fabio@example.com", UserRole.ADMIN, "password-vieja");
        String rawToken = emitirToken(usuario, Duration.ofHours(1));
        String csrf = AuthTestSupport.obtenerTokenCsrf(restTestClient);

        restTestClient.post().uri("/api/v1/auth/password-reset/confirm")
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PasswordResetConfirmRequest(rawToken, "password-nueva-123"))
                .exchange()
                .expectStatus().isNoContent();

        AuthTestSupport.login(restTestClient, csrf, "fabio@example.com", "password-nueva-123");
    }

    @Test
    void password_reset_confirm_con_token_caducado_devuelve_422() {
        UserEntity usuario = crearUsuarioActivo("gustavo@example.com", UserRole.ADMIN, "password-vieja");
        String rawToken = emitirToken(usuario, Duration.ofMinutes(-1));
        String csrf = AuthTestSupport.obtenerTokenCsrf(restTestClient);

        restTestClient.post().uri("/api/v1/auth/password-reset/confirm")
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PasswordResetConfirmRequest(rawToken, "password-nueva-123"))
                .exchange()
                .expectStatus().isEqualTo(422);
    }

    @Test
    void password_reset_confirm_con_token_ya_usado_devuelve_422() {
        UserEntity usuario = crearUsuarioActivo("hugo@example.com", UserRole.ADMIN, "password-vieja");
        String rawToken = TokenGenerator.newRawToken();
        PasswordResetTokenEntity token = new PasswordResetTokenEntity(
                usuario.getId(), TokenGenerator.hash(rawToken), Instant.now().plus(Duration.ofHours(1)));
        token.markUsed(Instant.now());
        tokenRepository.save(token);
        String csrf = AuthTestSupport.obtenerTokenCsrf(restTestClient);

        restTestClient.post().uri("/api/v1/auth/password-reset/confirm")
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PasswordResetConfirmRequest(rawToken, "password-nueva-123"))
                .exchange()
                .expectStatus().isEqualTo(422);
    }

    @Test
    void activate_con_token_valido_activa_la_cuenta_pendiente() {
        UserEntity usuario = userRepository.save(new UserEntity("irene@example.com", UserRole.GUARDIAN, "Irene"));
        assertThat(usuario.getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        String rawToken = emitirToken(usuario, Duration.ofDays(1));
        String csrf = AuthTestSupport.obtenerTokenCsrf(restTestClient);

        restTestClient.post().uri("/api/v1/auth/activate")
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ActivateAccountRequest(rawToken, "password-nueva-123"))
                .exchange()
                .expectStatus().isNoContent();

        AuthTestSupport.login(restTestClient, csrf, "irene@example.com", "password-nueva-123");
    }

    @Test
    void activate_con_token_ya_usado_devuelve_422() {
        UserEntity usuario = userRepository.save(new UserEntity("julia@example.com", UserRole.GUARDIAN, "Julia"));
        String rawToken = TokenGenerator.newRawToken();
        PasswordResetTokenEntity token = new PasswordResetTokenEntity(
                usuario.getId(), TokenGenerator.hash(rawToken), Instant.now().plus(Duration.ofDays(1)));
        token.markUsed(Instant.now());
        tokenRepository.save(token);
        String csrf = AuthTestSupport.obtenerTokenCsrf(restTestClient);

        restTestClient.post().uri("/api/v1/auth/activate")
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ActivateAccountRequest(rawToken, "password-nueva-123"))
                .exchange()
                .expectStatus().isEqualTo(422);
    }

    private UserEntity crearUsuarioActivo(String email, UserRole role, String password) {
        UserEntity user = new UserEntity(email, role, "Nombre de prueba");
        user.activate(passwordEncoder.encode(password));
        return userRepository.save(user);
    }

    private String emitirToken(UserEntity user, Duration timeToLive) {
        String rawToken = TokenGenerator.newRawToken();
        tokenRepository.save(new PasswordResetTokenEntity(
                user.getId(), TokenGenerator.hash(rawToken), Instant.now().plus(timeToLive)));
        return rawToken;
    }
}
