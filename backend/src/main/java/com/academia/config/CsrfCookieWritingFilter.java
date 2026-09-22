package com.academia.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fuerza la resolución del {@link CsrfToken} de la petición en cada llamada. Desde Spring
 * Security 6, {@code CsrfFilter} deja el token en un supplier perezoso: si nada lo lee (no
 * hay vistas server-side que lo rendericen en este proyecto, la API es JSON puro), el
 * {@code CookieCsrfTokenRepository} nunca llega a escribir la cookie {@code XSRF-TOKEN}, y el
 * interceptor de Angular no tendría nada que reenviar en la cabecera (docs/diseno-api.md
 * sección 2.1). Patrón documentado por el propio proyecto Spring Security para SPA.
 */
class CsrfCookieWritingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
