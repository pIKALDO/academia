package com.academia.users;

import com.academia.users.dto.ActivateAccountRequest;
import com.academia.users.dto.LoginRequest;
import com.academia.users.dto.PasswordResetConfirmRequest;
import com.academia.users.dto.PasswordResetRequest;
import com.academia.users.dto.UserProfileDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de sesión (docs/diseno-api.md sección 2). Login con {@link AuthenticationManager}
 * propio y persistencia explícita del {@link SecurityContext} en el repositorio, no
 * {@code formLogin}: así el 204/401 de {@code /auth/login} es una respuesta JSON normal, no
 * una redirección.
 */
@Tag(name = "Autenticación")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionRegistry sessionRegistry;
    private final UserService userService;
    private final AuthService authService;

    AuthController(AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository, SessionRegistry sessionRegistry,
            UserService userService, AuthService authService) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionRegistry = sessionRegistry;
        this.userService = userService;
        this.authService = authService;
    }

    @PostMapping("/login")
    ResponseEntity<Void> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        // El login pasa por este controlador, no por el filtro estándar de autenticación de
        // Spring Security: sin este registro manual, sessionRegistry nunca se entera de la
        // sesión y la revocación inmediata al desactivar una cuenta (UserService.disable) no
        // tendría ninguna sesión que expirar.
        sessionRegistry.registerNewSession(httpRequest.getSession(false).getId(), authentication.getPrincipal());

        userService.recordSuccessfulLogin(((AcademiaUserPrincipal) authentication.getPrincipal()).userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    UserProfileDto me(@AuthenticationPrincipal AcademiaUserPrincipal principal) {
        return userService.getProfile(principal.userId());
    }

    @PostMapping("/password-reset")
    ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        authService.requestPasswordReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-reset/confirm")
    ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.confirmPasswordReset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/activate")
    ResponseEntity<Void> activate(@Valid @RequestBody ActivateAccountRequest request) {
        authService.activate(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
