package com.academia.documents.dto;

import com.academia.documents.DocumentCategory;
import com.academia.documents.DocumentEntity;
import com.academia.documents.DocumentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Representación de un documento (docs/diseno-api.md sección 5.6). Igual para ADMIN y GUARDIAN:
 * el JSON de ejemplo del contrato es único para los dos roles, a diferencia de la ficha del
 * estudiante. {@code reviewedBy} no aparece a propósito: el contrato solo expone
 * {@code reviewedAt}.
 */
public record DocumentDto(
        UUID id,
        UUID studentId,
        DocumentCategory category,
        String name,
        @Nullable String contentType,
        @Nullable Long sizeBytes,
        DocumentStatus status,
        @Nullable LocalDate issuedAt,
        @Nullable LocalDate expiresAt,
        boolean expired,
        @Nullable Long daysUntilExpiry,
        @Nullable UploadedByDto uploadedBy,
        @Nullable Instant uploadedAt,
        @Nullable Instant reviewedAt) {

    public static DocumentDto from(DocumentEntity entity, @Nullable UploadedByDto uploadedBy, LocalDate today) {
        return new DocumentDto(
                entity.getId(),
                entity.getStudentId(),
                entity.getCategory(),
                entity.getName(),
                entity.getContentType(),
                entity.getSizeBytes(),
                entity.getStatus(),
                entity.getIssuedAt(),
                entity.getExpiresAt(),
                entity.isExpired(today),
                entity.daysUntilExpiry(today),
                uploadedBy,
                entity.getUploadedAt(),
                entity.getReviewedAt());
    }
}
