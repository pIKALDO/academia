package com.academia.common.audit;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escribe en {@code audit_log} en una transacción propia ({@code REQUIRES_NEW}): una
 * acción se audita aunque la transacción de negocio que la originó acabe revirtiéndose por
 * otra causa, y viceversa, un fallo al auditar no debe deshacer la operación de negocio.
 *
 * Vive en un bean aparte de {@link AuditAspect} a propósito: si {@code @Transactional}
 * estuviera en un método del propio aspecto, una llamada interna ({@code this.escribir(...)})
 * no pasaría por el proxy de Spring y la propagación {@code REQUIRES_NEW} no tendría efecto.
 */
@Component
class AuditLogWriter {

    private final JdbcTemplate jdbcTemplate;

    AuditLogWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void write(String action, String entityType, UUID entityId, UUID studentId, String ipAddress) {
        // actor_user_id queda pendiente de completar cuando el módulo de usuarios exponga
        // el id del principal autenticado (por ahora Spring Security solo conoce el nombre
        // de usuario, no el UUID de la fila en `users`).
        UUID actorUserId = null;
        String actorRole = resolveActorRole();

        jdbcTemplate.update("""
                INSERT INTO audit_log (actor_user_id, actor_role, action, entity_type, entity_id, student_id, ip_address)
                VALUES (?, ?::user_role, ?, ?, ?, ?, ?::inet)
                """,
                actorUserId, actorRole, action, entityType, entityId, studentId, ipAddress);
    }

    private String resolveActorRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.replaceFirst("^ROLE_", ""))
                .orElse(null);
    }
}
