package com.academia.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Sesión con cookie, no JWT: se puede revocar al instante desactivando la cuenta
 * (docs/diseno-api.md sección 2.1). CSRF vía cookie legible por JavaScript, pensado para que
 * el interceptor de Angular reenvíe el token en la cabecera de las peticiones que modifican
 * estado.
 *
 * Sin {@code UserDetailsService} propio todavía: hasta que exista el módulo de usuarios,
 * Spring Boot autoconfigura uno en memoria con una contraseña generada en el log de arranque.
 * Es un artefacto esperado de este corte, no un problema de seguridad: no hay endpoints que
 * lo usen todavía.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(
                                "/actuator/health",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()
                        .anyRequest().authenticated())
                // 401 seco: sin cuerpo ni redirección. No confirma ni niega nada sobre la
                // ruta pedida, y el front distingue "no autenticado" de cualquier otro error
                // por el propio código de estado, sin necesidad de leer el cuerpo.
                .exceptionHandling(handling -> handling.authenticationEntryPoint(
                        (request, response, authException) -> response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)));

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
