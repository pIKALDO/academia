package com.academia.guardians;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Vínculo estudiante-tutor (docs/modelo-datos.md sección 3). {@code hasAccess} separa el
 * vínculo familiar del permiso: un progenitor puede figurar en la ficha sin ver nada en la
 * plataforma. Es la columna que decide la visibilidad en {@code StudentAccessSpecifications}.
 *
 * <p>Solo el lado del tutor está mapeado como asociación: la ficha de administrador necesita
 * sus datos de contacto, y la autorización necesita su {@code userId}. El lado del estudiante
 * se queda en el identificador, para que {@code guardians} no dependa de la entidad de
 * {@code students}.
 */
@Entity
@Table(name = "student_guardians")
public class StudentGuardianEntity {

    @EmbeddedId
    private StudentGuardianId id;

    @MapsId("guardianId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guardian_id")
    private GuardianEntity guardian;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "guardian_relationship")
    private GuardianRelationship relationship;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "has_access", nullable = false)
    private boolean hasAccess;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StudentGuardianEntity() {
        // JPA
    }

    StudentGuardianEntity(UUID studentId, GuardianEntity guardian) {
        this.id = new StudentGuardianId(studentId, guardian.getId());
        this.guardian = guardian;
    }

    /** PUT sobre el vínculo: reemplazo total de sus tres atributos. */
    void replace(GuardianRelationship relationship, boolean primary, boolean hasAccess) {
        this.relationship = relationship;
        this.primary = primary;
        this.hasAccess = hasAccess;
    }

    public UUID getStudentId() {
        return id.studentId();
    }

    public GuardianEntity getGuardian() {
        return guardian;
    }

    public GuardianRelationship getRelationship() {
        return relationship;
    }

    public boolean isPrimary() {
        return primary;
    }

    public boolean hasAccess() {
        return hasAccess;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
