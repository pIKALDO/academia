package com.academia.students.dto;

import jakarta.validation.constraints.Size;

/** {@code PUT /students/{id}/education}: reemplazo total del bloque. */
public record EducationRequest(
        @Size(max = 200) String schoolName,
        @Size(max = 100) String grade,
        String scheduleNotes,
        String notes) {
}
