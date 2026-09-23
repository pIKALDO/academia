package com.academia.documents;

import com.academia.common.error.ConflictException;
import com.academia.common.error.UnprocessableEntityException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * Documentación de un estudiante (docs/modelo-datos.md sección 5). Sin
 * {@code @SQLRestriction("deleted_at IS NULL")}: a diferencia de {@code StudentEntity}, aquí el
 * filtro de borrados vive en las consultas del repositorio, explícito, para no repetir el
 * problema ya detectado de esconder filas incluso al administrador sin ningún camino de
 * restauración (decisión registrada en docs/PROGRESO.md, no se toca en este corte).
 */
@Entity
@Table(name = "documents")
public class DocumentEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "student_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "document_category")
    private DocumentCategory category;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "storage_key", length = 500)
    private String storageKey;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "document_status")
    private DocumentStatus status;

    @Column(name = "issued_at")
    private LocalDate issuedAt;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Column(name = "uploaded_by", columnDefinition = "uuid")
    private UUID uploadedBy;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    @Column(name = "reviewed_by", columnDefinition = "uuid")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DocumentEntity() {
        // JPA
    }

    public DocumentEntity(UUID studentId, DocumentCategory category, String name) {
        this.studentId = studentId;
        this.category = category;
        this.name = name;
        this.status = DocumentStatus.PENDING;
    }

    /**
     * Subir el fichero es una transición natural, no revisable: de {@code PENDING} pasa a
     * {@code RECEIVED} sin más (docs/diseno-api.md sección 1.1). Revisar sí necesita su propio
     * endpoint, {@link #review}, porque tiene efectos de auditoría propios.
     */
    public void attachFile(String storageKey, String contentType, long sizeBytes, String checksumSha256,
            UUID uploadedBy, Instant uploadedAt) {
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.checksumSha256 = checksumSha256;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
        if (this.status == DocumentStatus.PENDING) {
            this.status = DocumentStatus.RECEIVED;
        }
    }

    /** POST /documents/{id}/review: solo desde RECEIVED (docs/diseno-api.md sección 5.6). */
    public void review(UUID reviewedBy, Instant reviewedAt) {
        if (status == DocumentStatus.PENDING) {
            throw new ConflictException("El documento todavía no tiene fichero que revisar.");
        }
        if (status == DocumentStatus.REVIEWED) {
            throw new ConflictException("El documento ya está revisado.");
        }
        this.status = DocumentStatus.REVIEWED;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
    }

    /** PATCH /documents/{id}: metadatos, nunca el fichero ni el estado. */
    public void changeMetadata(DocumentCategory category, String name, LocalDate issuedAt, LocalDate expiresAt) {
        if (issuedAt != null && expiresAt != null && expiresAt.isBefore(issuedAt)) {
            throw new UnprocessableEntityException("La fecha de caducidad no puede ser anterior a la de emisión.");
        }
        this.category = category;
        this.name = name;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public void markDeleted(Instant when) {
        this.deletedAt = when;
    }

    /** Campo calculado, no persistido (regla no negociable nº11). */
    public boolean isExpired(LocalDate today) {
        return expiresAt != null && expiresAt.isBefore(today);
    }

    /** Campo calculado, no persistido; con signo, positivo o negativo, si ya ha caducado. */
    public Long daysUntilExpiry(LocalDate today) {
        return expiresAt == null ? null : ChronoUnit.DAYS.between(today, expiresAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public DocumentCategory getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public LocalDate getIssuedAt() {
        return issuedAt;
    }

    public LocalDate getExpiresAt() {
        return expiresAt;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
