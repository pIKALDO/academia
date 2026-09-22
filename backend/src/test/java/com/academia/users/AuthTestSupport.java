package com.academia.users;

import com.academia.users.dto.LoginRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Simula lo que hace un navegador con la cookie CSRF y la cookie de sesión
 * (docs/diseno-api.md sección 2.1): {@link RestTestClient} no arrastra cookies entre
 * llamadas por su cuenta, así que hay que leerlas y reenviarlas a mano, igual que lo haría
 * un cliente HTTP real.
 */
final class AuthTestSupport {

    /**
     * El {@link RestTestClient} autoconfigurado contra un servidor real conserva su propio
     * cookie jar entre peticiones (bean compartido entre métodos de test). Pasada la primera
     * llamada, el servidor deja de reenviar {@code Set-Cookie: XSRF-TOKEN} porque el cliente
     * ya presenta uno válido en la petición; el valor no cambia mientras ese token siga
     * siendo válido, así que basta con cachearlo la primera vez que se ve.
     */
    private static volatile String csrfTokenCache;

    private AuthTestSupport() {
    }

    static String obtenerTokenCsrf(RestTestClient client) {
        ExchangeResult result = client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .returnResult();
        var cookie = result.getResponseCookies().getFirst("XSRF-TOKEN");
        if (cookie != null) {
            csrfTokenCache = cookie.getValue();
        }
        if (csrfTokenCache == null) {
            throw new IllegalStateException("No se recibió la cookie XSRF-TOKEN en ninguna petición todavía.");
        }
        return csrfTokenCache;
    }

    static SesionAutenticada login(RestTestClient client, String csrfToken, String email, String password) {
        ExchangeResult result = client.post().uri("/api/v1/auth/login")
                .cookie("XSRF-TOKEN", csrfToken)
                .header("X-XSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, password))
                .exchange()
                .expectStatus().isNoContent()
                .returnResult();
        String sessionCookie = result.getResponseCookies().getFirst("SESSION").getValue();
        return new SesionAutenticada(csrfToken, sessionCookie);
    }

    record SesionAutenticada(String csrfToken, String sessionCookie) {
    }
}
