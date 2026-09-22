package com.academia.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * Sesión con cookie, no JWT: se puede revocar al instante desactivando la cuenta
 * (docs/diseno-api.md sección 2.1). CSRF vía cookie legible por JavaScript, pensado para que
 * el interceptor de Angular reenvíe el token en la cabecera de las peticiones que modifican
 * estado.
 *
 * {@code AcademiaUserDetailsService} (módulo {@code users}) sustituye desde el corte 1 al
 * {@code UserDetailsService} en memoria que autoconfigura Spring Boot por defecto.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SessionRegistry sessionRegistry) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        // Sin esto, Spring Security usa por defecto
                        // XorCsrfTokenRequestAttributeHandler, que enmascara el token servido
                        // y espera esa misma máscara de vuelta en la cabecera: el patrón
                        // "lee la cookie, reenvíala tal cual en X-XSRF-TOKEN" que hace el
                        // interceptor de Angular dejaría de validar. Este handler no enmascara.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                // Desde Spring Security 6 el token CSRF se resuelve de forma perezosa: si
                // nada lee el atributo de petición "_csrf", la cookie XSRF-TOKEN no llega a
                // escribirse nunca, y el interceptor de Angular no tendría nada que reenviar.
                // Este filtro fuerza la resolución en cada petición (patrón documentado por
                // Spring Security para SPA).
                .addFilterAfter(new CsrfCookieWritingFilter(), CsrfFilter.class)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(
                                "/actuator/health",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()
                        // Únicos endpoints de auth accesibles sin sesión: el resto (logout,
                        // /auth/me) exige estar ya autenticado (docs/diseno-api.md sección 5.1).
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/password-reset",
                                "/api/v1/auth/password-reset/confirm",
                                "/api/v1/auth/activate")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(ProblemDetailSecurityHandlers.authenticationEntryPoint())
                        .accessDeniedHandler(ProblemDetailSecurityHandlers.accessDeniedHandler()))
                .securityContext(context -> context.securityContextRepository(securityContextRepository()))
                // maximumSessions(-1): sin tope de sesiones concurrentes, pero registradas en
                // sessionRegistry. Es lo que hace real la revocación inmediata prometida en
                // docs/diseno-api.md sección 2.1: al desactivar una cuenta (UserService),
                // expirar sus sesiones ahí las corta en la siguiente petición, sin esperar a
                // que la cookie caduque sola.
                .sessionManagement(session -> session
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry)
                        .expiredSessionStrategy(event ->
                                event.getResponse().setStatus(HttpServletResponse.SC_UNAUTHORIZED)));

        return http.build();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /**
     * Sin esto, sessionRegistry nunca se entera cuando una sesión muere por timeout (solo
     * por invalidación explícita): las entradas de sesiones ya caducadas se quedarían
     * acumulando en memoria indefinidamente.
     */
    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
