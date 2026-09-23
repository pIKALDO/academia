package com.academia.documents.dto;

import java.util.UUID;

/** Autor de la subida de un documento (docs/diseno-api.md sección 5.6). */
public record UploadedByDto(UUID id, String displayName) {
}
