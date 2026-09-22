package com.academia.users;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Principal de Spring Security para la sesión autenticada. Es una foto de
 * {@link UserEntity} tomada en el momento del login, no la entidad gestionada por JPA: vive
 * en la {@code HttpSession} durante toda la sesión (docs/diseno-api.md sección 2.1), y una
 * entidad JPA detached ahí sería una fuente de bugs sutiles (lazy-loading fuera de
 * transacción, cambios en base de datos que no se reflejan sin releer).
 *
 * {@code AccessService} y {@code AuditLogWriter} leen {@link #userId()} y {@link #role()}
 * directamente desde este principal en {@code SecurityContextHolder}, sin volver a la base
 * de datos.
 */
public final class AcademiaUserPrincipal implements UserDetails {

    /**
     * Hash BCrypt válido de una contraseña aleatoria, generado una vez al cargar la clase: no
     * puede coincidir con ninguna contraseña real. Evita que {@code PasswordEncoder.matches}
     * reciba {@code null} (lanzaría) cuando el usuario aún no tiene contraseña
     * (PENDING_ACTIVATION), sin depender de un literal hardcodeado cuyo formato haya que
     * acertar a mano.
     */
    private static final String UNUSABLE_PASSWORD_HASH =
            new BCryptPasswordEncoder().encode(UUID.randomUUID().toString());

    private final UUID userId;
    private final String email;
    private final String passwordHash;
    private final UserRole role;
    private final UserStatus status;
    private final String displayName;

    private AcademiaUserPrincipal(UUID userId, String email, String passwordHash, UserRole role,
            UserStatus status, String displayName) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = status;
        this.displayName = displayName;
    }

    public static AcademiaUserPrincipal of(UserEntity user) {
        String hash = user.getPasswordHash() != null ? user.getPasswordHash() : UNUSABLE_PASSWORD_HASH;
        return new AcademiaUserPrincipal(user.getId(), user.getEmail(), hash, user.getRole(),
                user.getStatus(), user.getDisplayName());
    }

    public UUID userId() {
        return userId;
    }

    public UserRole role() {
        return role;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }
}
