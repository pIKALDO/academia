package com.academia.documents.dto;

import com.academia.documents.DocumentCategory;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** {@code PATCH /documents/{id}}: un campo en {@code null} significa "no lo toques". */
public record UpdateDocumentRequest(
        @Nullable DocumentCategory category,
        @Nullable String name,
        @Nullable LocalDate issuedAt,
        @Nullable LocalDate expiresAt) {
}
