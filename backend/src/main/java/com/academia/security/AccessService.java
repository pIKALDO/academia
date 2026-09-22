package com.academia.security;

import com.academia.users.AcademiaUserPrincipal;
import com.academia.users.UserRole;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resuelve, para el usuario autenticado, qué operaciones puede realizar sobre un estudiante,
 * su documentación o el módulo de usuarios. Se referencia desde {@code @PreAuthorize} en la
 * capa de servicio, nunca desde el controlador ni desde el front (regla no negociable nº2):
 *
 * <pre>{@code @PreAuthorize("@access.canViewStudent(#studentId)")}</pre>
 *
 * Cada regla de este componente tiene su propio test (docs/diseno-api.md sección 3.2): es
 * el núcleo del proyecto.
 *
 * {@link StudentAccessRepository} todavía no tiene ninguna implementación: llega con el
 * módulo de estudiantes/tutores. Hasta entonces, {@code studentAccessRepository} está vacío
 * y las reglas que dependen de él deniegan por defecto en lugar de lanzar: un fallo en la
 * capa de autorización tiene que cerrar la puerta, no romper la petición.
 */
@Component("access")
public class AccessService {

    private final Optional<StudentAccessRepository> studentAccessRepository;

    public AccessService(Optional<StudentAccessRepository> studentAccessRepository) {
        this.studentAccessRepository = studentAccessRepository;
    }

    public boolean canViewStudent(UUID studentId) {
        return currentPrincipal().map(principal -> switch (principal.role()) {
            case ADMIN -> true;
            case GUARDIAN -> checkGuardianAccess(studentId, principal.userId());
            case STUDENT -> checkStudentAccess(studentId, principal.userId());
        }).orElse(false);
    }

    /** PATCH /students/{id} es ADMIN únicamente (docs/diseno-api.md sección 5.3): resoluble
     *  por completo sin esperar al módulo de estudiantes. */
    public boolean canEditStudent(UUID studentId) {
        return hasRole(UserRole.ADMIN);
    }

    public boolean canUploadDocument(UUID studentId) {
        return currentPrincipal().map(principal -> switch (principal.role()) {
            case ADMIN -> true;
            case GUARDIAN -> checkGuardianAccess(studentId, principal.userId());
            case STUDENT -> false;
        }).orElse(false);
    }

    /** POST /documents/{id}/review es ADMIN únicamente (docs/diseno-api.md sección 5.6). */
    public boolean canReviewDocument(UUID documentId) {
        return hasRole(UserRole.ADMIN);
    }

    /** GET/POST/PATCH /users es ADMIN únicamente (docs/diseno-api.md sección 5.2). */
    public boolean canManageUsers() {
        return hasRole(UserRole.ADMIN);
    }

    private boolean checkGuardianAccess(UUID studentId, UUID guardianUserId) {
        return studentAccessRepository
                .map(repository -> repository.isAccessibleByGuardian(studentId, guardianUserId))
                .orElse(false);
    }

    private boolean checkStudentAccess(UUID studentId, UUID studentUserId) {
        return studentAccessRepository
                .map(repository -> repository.isOwnStudent(studentId, studentUserId))
                .orElse(false);
    }

    private boolean hasRole(UserRole role) {
        return currentPrincipal().map(principal -> principal.role() == role).orElse(false);
    }

    private Optional<AcademiaUserPrincipal> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return authentication.getPrincipal() instanceof AcademiaUserPrincipal principal
                ? Optional.of(principal)
                : Optional.empty();
    }
}
