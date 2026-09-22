package com.academia.users;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Sustituye al {@code UserDetailsService} en memoria que autoconfigura Spring Boot cuando no
 * hay ninguno propio (SecurityConfig, corte 0).
 *
 * Lanzar {@link UsernameNotFoundException} aquí es seguro: {@code DaoAuthenticationProvider}
 * la oculta por defecto (`hideUserNotFoundExceptions=true`) y la convierte en la misma
 * {@code BadCredentialsException} que una contraseña incorrecta, así que el 401 de
 * {@code /auth/login} es indistinguible entre "no existe" y "contraseña errónea"
 * (docs/diseno-api.md sección 2.2).
 */
@Service
class AcademiaUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    AcademiaUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .map(AcademiaUserPrincipal::of)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales no válidas."));
    }
}
