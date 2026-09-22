package com.academia.users;

import com.academia.common.error.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * Cuenta de acceso a la plataforma (docs/modelo-datos.md sección 2). No confundir con
 * {@code Student} o {@code Guardian}: la entidad persona y la entidad cuenta son cosas
 * distintas (un estudiante puede existir sin cuenta propia, fase 2).
 *
 * {@code passwordHash} nulable: un usuario recién creado por el administrador no tiene
 * contraseña hasta que activa la cuenta con el enlace enviado por correo.
 */
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "user_role")
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "user_status")
    private UserStatus status;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserEntity() {
        // JPA
    }

    public UserEntity(String email, UserRole role, String displayName) {
        this.email = email;
        this.role = role;
        this.displayName = displayName;
        this.status = UserStatus.PENDING_ACTIVATION;
    }

    /** Fija la contraseña y activa la cuenta. Solo válido mientras espera activación. */
    public void activate(String newPasswordHash) {
        if (status != UserStatus.PENDING_ACTIVATION) {
            throw new ConflictException("El usuario ya está activado.");
        }
        this.passwordHash = newPasswordHash;
        this.status = UserStatus.ACTIVE;
    }

    /** Recuperación de contraseña: no cambia el estado, solo el hash. */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void changeDisplayName(String newDisplayName) {
        this.displayName = newDisplayName;
    }

    public void changeRole(UserRole newRole) {
        this.role = newRole;
    }

    /**
     * Idempotente a propósito: un administrador puede pulsar "desactivar" dos veces sin que
     * la segunda llamada falle. Válido desde ACTIVE o desde PENDING_ACTIVATION (revocar una
     * invitación aún no aceptada).
     */
    public void disable() {
        this.status = UserStatus.DISABLED;
    }

    /**
     * Solo tiene sentido reactivar una cuenta que estaba DISABLED. Reactivar una
     * PENDING_ACTIVATION la dejaría en ACTIVE sin contraseña, un estado inconsistente que
     * bloquearía el login para siempre.
     */
    public void enable() {
        if (status != UserStatus.DISABLED) {
            throw new ConflictException("El usuario no está desactivado.");
        }
        this.status = UserStatus.ACTIVE;
    }

    public void recordLogin(Instant when) {
        this.lastLoginAt = when;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
