package com.academia.students.dto;

import com.academia.students.EducationInfoEntity;
import java.time.Instant;

/** Bloque académico, vista de administrador (ficha completa y respuesta de su PUT). */
public record EducationDto(
        String schoolName,
        String grade,
        String scheduleNotes,
        String notes,
        Instant updatedAt) {

    public static EducationDto from(EducationInfoEntity entity) {
        return new EducationDto(entity.getSchoolName(), entity.getGrade(), entity.getScheduleNotes(),
                entity.getNotes(), entity.getUpdatedAt());
    }
}
