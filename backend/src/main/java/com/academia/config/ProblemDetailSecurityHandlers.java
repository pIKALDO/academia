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
 * {@link org.springframework.web.bind.annotation.RestControllerAdvice} no llega a ver
 * {@code AuthenticationException} ni {@code AccessDeniedException}: Spring Security registra
 * su propio {@code HandlerExceptionResolver}, con prioridad sobre el de Spring MVC, que
 * intercepta esos dos tipos —vengan de un filtro o de un {@code @PreAuthorize} disparado
 * dentro de un controlador o servicio— y los reenvía a {@code ExceptionTranslationFilter}.
 * Por eso esta respuesta se construye aquí, con el mismo formato {@code ProblemDetail} que
 * {@code GlobalExceptionHandler} usa para todo lo demás (docs/diseno-api.md sección 7).
 */
final class ProblemDetailSecurityHandlers {

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
    static AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            if (isFailedLoginAttempt(authException)) {
                write(request, response, HttpStatus.UNAUTHORIZED, "unauthorized",
                        "No autenticado", "Email o contraseña incorrectos.");
            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            }
        };
    }

    static AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> write(request, response,
                HttpStatus.FORBIDDEN, "forbidden", "Operación no permitida",
                "No tienes permiso para realizar esta operación.");
    }

    private static boolean isFailedLoginAttempt(AuthenticationException ex) {
        return ex instanceof BadCredentialsException
                || ex instanceof DisabledException
                || ex instanceof LockedException
                || ex instanceof AccountExpiredException
                || ex instanceof CredentialsExpiredException;
    }

    private static void write(HttpServletRequest request, HttpServletResponse response,
            HttpStatus status, String typeSlug, String title, String detail) throws IOException {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + typeSlug));
        problemDetail.setTitle(title);
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("timestamp", Instant.now());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        OBJECT_MAPPER.writeValue(response.getWriter(), problemDetail);
    }
}
