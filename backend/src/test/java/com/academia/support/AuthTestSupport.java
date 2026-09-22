package com.academia.support;

import com.academia.users.dto.LoginRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Simula lo que hace un navegador con la cookie CSRF y la cookie de sesión
 * (docs/diseno-api.md sección 2.1): {@link RestTestClient} no arrastra cookies entre
 * llamadas por su cuenta, así que hay que leerlas y reenviarlas a mano, igual que lo haría
 * un cliente HTTP real.
 *
 * <p>Cada llamada a {@link #obtenerTokenCsrf} necesita un {@link RestTestClient} recién
 * creado para esa petición: si el cliente ya trae consigo una cookie {@code XSRF-TOKEN} de
 * una petición anterior (propia o de otro test), el servidor no vuelve a enviar
 * {@code Set-Cookie} y este método no tiene de dónde leer el token.
 */
public final class AuthTestSupport {

    private AuthTestSupport() {
    }

    public static String obtenerTokenCsrf(RestTestClient client) {
        ExchangeResult result = client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .returnResult();
        var cookie = result.getResponseCookies().getFirst("XSRF-TOKEN");
        if (cookie == null) {
            throw new IllegalStateException(
                    "No se recibió la cookie XSRF-TOKEN: ¿el cliente ya traía una de una petición anterior?");
        }
        return cookie.getValue();
    }

    public static SesionAutenticada login(RestTestClient client, String csrfToken, String email, String password) {
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

    public record SesionAutenticada(String csrfToken, String sessionCookie) {
    }
}
