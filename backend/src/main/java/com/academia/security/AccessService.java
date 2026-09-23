package com.academia.security;

import com.academia.students.StudentEntity;
import com.academia.users.AcademiaUserPrincipal;
import com.academia.users.UserRole;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
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
 * Dos tipos de regla, según qué deba pasar al denegar (docs/diseno-api.md sección 3.1):
 * <ul>
 *   <li>Visibilidad ({@link #canViewStudent}): devuelve {@link StudentAccessDecision}, que
 *       distingue "ocultar" (404) de "prohibir" (403). Va acompañada de
 *       {@code @HandleAuthorizationDenied(handlerClass = HideStudentWhenNotVisible.class)}.</li>
 *   <li>Operación ({@link #canEditStudent}, {@link #canManageGuardians}...): {@code boolean},
 *       403 al denegar. Solo dependen del rol, así que la respuesta es la misma con cualquier
 *       id —propio, ajeno o inventado— y no revela si existe.</li>
 * </ul>
 *
 * Cada regla de este componente tiene su propio test (docs/diseno-api.md sección 3.2): es
 * el núcleo del proyecto.
 */
@Component("access")
public class AccessService {

    private final StudentAccessRepository studentAccessRepository;
    private final DocumentAccessRepository documentAccessRepository;

    public AccessService(StudentAccessRepository studentAccessRepository,
            DocumentAccessRepository documentAccessRepository) {
        this.studentAccessRepository = studentAccessRepository;
        this.documentAccessRepository = documentAccessRepository;
    }

    /**
     * ADMIN ve todos; GUARDIAN solo los vinculados con {@code has_access}, y el resto se le
     * oculta; STUDENT no tiene acceso al módulo en fase 1 (portal del estudiante, fase 2), y
     * se le prohíbe sin consultar el id.
     */
    public StudentAccessDecision canViewStudent(UUID studentId) {
        return currentPrincipal().map(principal -> switch (principal.role()) {
            case ADMIN -> StudentAccessDecision.GRANTED;
            case GUARDIAN -> studentAccessRepository.isAccessibleByGuardian(studentId, principal.userId())
                    ? StudentAccessDecision.GRANTED
                    : StudentAccessDecision.HIDDEN;
            case STUDENT -> StudentAccessDecision.FORBIDDEN;
        }).orElse(StudentAccessDecision.FORBIDDEN);
    }

    /** GET /students: ADMIN y GUARDIAN; qué filas ve cada uno lo decide {@link #visibleStudents()}. */
    public boolean canListStudents() {
        return hasRole(UserRole.ADMIN) || hasRole(UserRole.GUARDIAN);
    }

    /**
     * Filtro del listado para el usuario autenticado: el mismo predicado que
     * {@link #canViewStudent} ({@link StudentAccessSpecifications#visibleToGuardian}), para
     * que una familia no pueda encontrar en el listado lo que no puede abrir, ni al revés.
     */
    public Specification<StudentEntity> visibleStudents() {
        return currentPrincipal().map(principal -> switch (principal.role()) {
            case ADMIN -> Specification.<StudentEntity>unrestricted();
            case GUARDIAN -> StudentAccessSpecifications.visibleToGuardian(principal.userId());
            case STUDENT -> StudentAccessSpecifications.none();
        }).orElse(StudentAccessSpecifications.none());
    }

    /**
     * Qué representación de la ficha recibe el usuario: la de administración (con
     * {@code coachNotes}, {@code housing}, contacto de los tutores y metadatos) solo ADMIN.
     * Cualquier otro rol que llegue a ver la ficha recibe {@code StudentGuardianDto}, que
     * no tiene esos campos (regla no negociable nº5).
     */
    public boolean canSeeInternalStudentData() {
        return hasRole(UserRole.ADMIN);
    }

    /**
     * PATCH/DELETE /students/{id}, PUT de bloques, alta y edición de contactos de emergencia:
     * ADMIN únicamente (docs/diseno-api.md secciones 5.3 y 5.5).
     */
    public boolean canEditStudent(UUID studentId) {
        return hasRole(UserRole.ADMIN);
    }

    /** PATCH/DELETE /emergency-contacts/{id}: ADMIN únicamente (docs/diseno-api.md sección 5.5). */
    public boolean canEditEmergencyContact(UUID emergencyContactId) {
        return hasRole(UserRole.ADMIN);
    }

    /** POST /students: ADMIN únicamente. */
    public boolean canCreateStudent() {
        return hasRole(UserRole.ADMIN);
    }

    /** /guardians y el vínculo estudiante-tutor: ADMIN únicamente (docs/diseno-api.md sección 5.4). */
    public boolean canManageGuardians() {
        return hasRole(UserRole.ADMIN);
    }

    /**
     * Regla de visibilidad, no de operación: subir a un estudiante ajeno debe dar 404, igual
     * que verlo (regla no negociable nº1). Hoy "puedo ver" y "puedo subir" coinciden para
     * cualquier rol, así que se apoya directamente en {@link #canViewStudent}; si algún día
     * hiciera falta una restricción de subida distinta de la de visibilidad (p. ej. un rol que
     * ve pero no puede subir), se añadiría aquí por encima, sin tocar la decisión de
     * visibilidad en sí.
     */
    public StudentAccessDecision canUploadDocument(UUID studentId) {
        return canViewStudent(studentId);
    }

    /** POST /documents/{id}/review es ADMIN únicamente (docs/diseno-api.md sección 5.6). */
    public boolean canReviewDocument(UUID documentId) {
        return hasRole(UserRole.ADMIN);
    }

    /**
     * Documento de un estudiante ajeno → 404 (docs/diseno-api.md sección 3.1), igual que
     * {@link #canViewStudent}: se resuelve el estudiante propietario y se reutiliza la misma
     * regla, en vez de duplicar el switch por rol.
     */
    public StudentAccessDecision canViewDocument(UUID documentId) {
        return documentAccessRepository.findOwningStudentId(documentId)
                .map(this::canViewStudent)
                .orElse(StudentAccessDecision.HIDDEN);
    }

    /** DELETE /documents/{id}: ADMIN únicamente (docs/diseno-api.md sección 5.6). */
    public boolean canDeleteDocument(UUID documentId) {
        return hasRole(UserRole.ADMIN);
    }

    /** PATCH /documents/{id}: ADMIN únicamente. */
    public boolean canEditDocument(UUID documentId) {
        return hasRole(UserRole.ADMIN);
    }

    /** GET /documents/expiring: ADMIN únicamente — vista de gestión de todos los estudiantes. */
    public boolean canViewExpiringDocuments() {
        return hasRole(UserRole.ADMIN);
    }

    /** GET/POST/PATCH /users es ADMIN únicamente (docs/diseno-api.md sección 5.2). */
    public boolean canManageUsers() {
        return hasRole(UserRole.ADMIN);
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
