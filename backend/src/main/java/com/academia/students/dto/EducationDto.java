package com.academia.students.dto;

import com.academia.students.EducationInfoEntity;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Bloque académico, vista de administrador (ficha completa y respuesta de su PUT). */
public record EducationDto(
        @Nullable String schoolName,
        @Nullable String grade,
        @Nullable String scheduleNotes,
        @Nullable String notes,
        Instant updatedAt) {

    public static EducationDto from(EducationInfoEntity entity) {
        return new EducationDto(entity.getSchoolName(), entity.getGrade(), entity.getScheduleNotes(),
                entity.getNotes(), entity.getUpdatedAt());
    }
}
