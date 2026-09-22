package com.academia.users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * Token de un solo uso para fijar contraseña: sirve tanto al enlace de recuperación
 * (docs/diseno-api.md sección 2.2, {@code /auth/password-reset/confirm}) como al de
 * activación de cuenta ({@code /auth/activate}). Ambos flujos son, en el fondo, "fijar una
 * contraseña nueva dado un token válido"; la única diferencia de negocio es que activar
 * además exige que el usuario esté en PENDING_ACTIVATION (ver {@link UserEntity#activate}).
 * No se modela una tabla de activación aparte: el modelo de datos solo define
 * {@code password_reset_tokens} (docs/modelo-datos.md sección 2).
 *
 * {@code userId} como columna simple, no {@code @ManyToOne}: no hace falta navegar la
 * asociación en ningún flujo, y así se evita el coste de un proxy perezoso para una fila que
 * se usa una vez y se descarta.
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetTokenEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PasswordResetTokenEntity() {
        // JPA
    }

    public PasswordResetTokenEntity(UUID userId, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public void markUsed(Instant when) {
        this.usedAt = when;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
