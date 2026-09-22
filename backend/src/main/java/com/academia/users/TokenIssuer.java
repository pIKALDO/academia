package com.academia.users;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Genera y persiste un token de un solo uso (activación o recuperación, ver
 * {@link PasswordResetTokenEntity}), compartido entre {@link UserService} (alta de usuario) y
 * {@link AuthService} (solicitud de recuperación) para no duplicar la lógica de generación.
 */
@Component
class TokenIssuer {

    private final PasswordResetTokenRepository repository;

    TokenIssuer(PasswordResetTokenRepository repository) {
        this.repository = repository;
    }

    /** Devuelve el token en claro: es lo único que viaja al correo, nunca se persiste así. */
    String issue(UUID userId, Duration timeToLive) {
        String rawToken = TokenGenerator.newRawToken();
        PasswordResetTokenEntity entity = new PasswordResetTokenEntity(
                userId, TokenGenerator.hash(rawToken), Instant.now().plus(timeToLive));
        repository.save(entity);
        return rawToken;
    }
}
