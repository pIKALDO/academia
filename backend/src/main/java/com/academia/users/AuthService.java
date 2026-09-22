package com.academia.users;

import com.academia.common.error.UnprocessableEntityException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los tres flujos de auto-servicio de docs/diseno-api.md sección 2.2 que dependen de un token
 * de un solo uso: pedir recuperación, confirmarla, y activar cuenta.
 */
@Service
class AuthService {

    private static final Duration RESET_TOKEN_TTL = Duration.ofHours(1);
    private static final String GENERIC_TOKEN_ERROR = "El enlace no es válido o ha caducado.";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final TokenIssuer tokenIssuer;
    private final AuthMailService mailService;
    private final PasswordEncoder passwordEncoder;

    AuthService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
            TokenIssuer tokenIssuer, AuthMailService mailService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.tokenIssuer = tokenIssuer;
        this.mailService = mailService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Siempre en silencio: el controlador responde 202 exista o no la cuenta
     * (docs/diseno-api.md sección 2.2), así que aquí no hay nada que lanzar ni que devolver.
     * Solo se envía correo a cuentas ACTIVE: una PENDING_ACTIVATION necesita el enlace de
     * activación, no uno de recuperación, y una DISABLED no debería poder entrar de ningún modo.
     */
    @Transactional
    void requestPasswordReset(String email) {
        userRepository.findByEmailIgnoreCase(email)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .ifPresent(user -> {
                    String rawToken = tokenIssuer.issue(user.getId(), RESET_TOKEN_TTL);
                    mailService.sendPasswordResetEmail(user, rawToken);
                });
    }

    @Transactional
    void confirmPasswordReset(String rawToken, String newPassword) {
        PasswordResetTokenEntity token = findValidTokenOrThrow(rawToken);
        UserEntity user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new UnprocessableEntityException(GENERIC_TOKEN_ERROR));

        user.changePassword(passwordEncoder.encode(newPassword));
        token.markUsed(Instant.now());
    }

    @Transactional
    void activate(String rawToken, String newPassword) {
        PasswordResetTokenEntity token = findValidTokenOrThrow(rawToken);
        UserEntity user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new UnprocessableEntityException(GENERIC_TOKEN_ERROR));

        if (user.getStatus() != UserStatus.PENDING_ACTIVATION) {
            // Mismo mensaje genérico que un token caducado o inexistente: no hay motivo para
            // que quien reenvía un enlace de activación viejo sepa que la cuenta ya se activó.
            throw new UnprocessableEntityException(GENERIC_TOKEN_ERROR);
        }

        user.activate(passwordEncoder.encode(newPassword));
        token.markUsed(Instant.now());
    }

    private PasswordResetTokenEntity findValidTokenOrThrow(String rawToken) {
        PasswordResetTokenEntity token = tokenRepository.findByTokenHash(TokenGenerator.hash(rawToken))
                .orElseThrow(() -> new UnprocessableEntityException(GENERIC_TOKEN_ERROR));

        if (token.isUsed() || token.isExpired(Instant.now())) {
            throw new UnprocessableEntityException(GENERIC_TOKEN_ERROR);
        }
        return token;
    }
}
