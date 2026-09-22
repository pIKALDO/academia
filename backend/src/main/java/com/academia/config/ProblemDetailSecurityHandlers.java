package com.academia.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Construye las respuestas {@code ProblemDetail} para {@code AuthenticationException} y
 * {@code AccessDeniedException}, con dos puntos de entrada distintos según de dónde venga la
 * excepción:
 *
 * <ul>
 *   <li>Si la lanza un filtro (petición sin sesión a un recurso protegido, o
 *       {@code AuthorizationFilter} al final de la cadena), {@code ExceptionTranslationFilter}
 *       la captura y llama a {@link #authenticationEntryPoint()} /
 *       {@link #accessDeniedHandler()}, que escriben directamente en el
 *       {@code HttpServletResponse}: en ese punto la petición nunca llega a Spring MVC.</li>
 *   <li>Si la lanza código que corre dentro de la invocación del controlador —el
 *       {@code authenticationManager.authenticate()} manual de {@code AuthController.login},
 *       o un {@code @PreAuthorize} de un método de servicio— la excepción queda dentro de la
 *       pila de {@code DispatcherServlet}, que la resuelve con sus propios
 *       {@code HandlerExceptionResolver} antes de que se propague de vuelta al filtro. Spring
 *       Security no registra ningún resolver para interceptarla ahí: sin un
 *       {@code @ExceptionHandler} explícito, cae en el catch-all de
 *       {@code GlobalExceptionHandler} y se convierte en un 500 en vez de un 401/403. Por eso
 *       {@code GlobalExceptionHandler} llama a {@link #authenticationProblemDetail} y
 *       {@link #accessDeniedProblemDetail}, que construyen el mismo {@code ProblemDetail} que
 *       el filtro, para que el formato de la respuesta no dependa de por qué camino haya
 *       venido la excepción (docs/diseno-api.md sección 7).</li>
 * </ul>
 */
public final class ProblemDetailSecurityHandlers {

    private static final String ERROR_TYPE_BASE = "https://api.academia.example/errors/";

    /**
     * Instancia propia y no la del contexto de Spring: en este punto de arranque (construcción
     * de {@code SecurityFilterChain}) no hay garantía de que el {@code ObjectMapper}
     * autoconfigurado por Jackson ya esté publicado como bean con un tipo y nombre estables
     * entre versiones de Spring Boot. Para las pocas propiedades de {@link ProblemDetail} que
     * hay que serializar aquí, un {@code ObjectMapper} propio con los módulos estándar
     * (fecha/hora incluido) es más simple y no depende de ese detalle de arranque.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private ProblemDetailSecurityHandlers() {
    }

    /**
     * Un intento de login fallido (credenciales incorrectas, cuenta desactivada o pendiente
     * de activación) recibe el mismo 401 con el mismo mensaje genérico
     * (docs/diseno-api.md sección 2.2). Cualquier otro caso —pedir un recurso protegido sin
     * sesión— es el 401 seco de siempre: sin cuerpo, para no dar pistas sobre si el problema
     * es la sesión o las credenciales.
     */
    public static AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            ProblemDetail problemDetail = authenticationProblemDetail(authException, request);
            if (problemDetail != null) {
                write(response, problemDetail);
            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            }
        };
    }

    public static AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> write(response, accessDeniedProblemDetail(request));
    }

    /**
     * {@code null} cuando la petición simplemente no está autenticada (pedir un recurso
     * protegido sin sesión): ese caso se queda con el 401 seco, sin cuerpo, para no dar
     * pistas sobre si el problema es la sesión o las credenciales.
     */
    public static ProblemDetail authenticationProblemDetail(AuthenticationException ex, HttpServletRequest request) {
        if (!isFailedLoginAttempt(ex)) {
            return null;
        }
        return problemDetail(HttpStatus.UNAUTHORIZED, "unauthorized", "No autenticado",
                "Email o contraseña incorrectos.", request);
    }

    public static ProblemDetail accessDeniedProblemDetail(HttpServletRequest request) {
        return problemDetail(HttpStatus.FORBIDDEN, "forbidden", "Operación no permitida",
                "No tienes permiso para realizar esta operación.", request);
    }

    private static boolean isFailedLoginAttempt(AuthenticationException ex) {
        return ex instanceof BadCredentialsException
                || ex instanceof DisabledException
                || ex instanceof LockedException
                || ex instanceof AccountExpiredException
                || ex instanceof CredentialsExpiredException;
    }

    private static ProblemDetail problemDetail(HttpStatus status, String typeSlug, String title, String detail,
            HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + typeSlug));
        problemDetail.setTitle(title);
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    private static void write(HttpServletResponse response, ProblemDetail problemDetail) throws IOException {
        response.setStatus(problemDetail.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        OBJECT_MAPPER.writeValue(response.getWriter(), problemDetail);
    }
}
