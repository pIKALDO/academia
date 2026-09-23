package com.academia.students.dto;

import com.academia.documents.dto.DocumentsSummaryDto;
import com.academia.students.StudentEntity;
import com.academia.students.StudentStatus;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Fila de {@code GET /students} (docs/diseno-api.md sección 4.3): solo lo necesario para
 * pintar la lista, sin cargar ningún bloque. Igual para ADMIN y GUARDIAN: no contiene nada
 * que la familia no deba ver de sus propios hijos.
 */
public record StudentListDto(
        UUID id,
        String firstName,
        String lastName,
        @Nullable String photoUrl,
        StudentStatus status,
        DocumentsSummaryDto documentsSummary) {

    public static StudentListDto from(StudentEntity entity, @Nullable String photoUrl,
            DocumentsSummaryDto documentsSummary) {
        return new StudentListDto(entity.getId(), entity.getFirstName(), entity.getLastName(), photoUrl,
                entity.getStatus(), documentsSummary);
    }
}
