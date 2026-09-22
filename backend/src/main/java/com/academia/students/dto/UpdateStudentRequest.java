package com.academia.students.dto;

import com.academia.students.StudentStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * {@code PATCH /students/{id}} (docs/diseno-api.md sección 5.3: la ficha principal se edita
 * por secciones). Misma convención que {@code UpdateUserRequest}: un campo en {@code null}
 * significa "no lo toques".
 *
 * Limitación conocida de esa convención: por PATCH no se puede vaciar un campo opcional
 * (teléfono, email...). Es justo la ambigüedad que el documento de diseño atribuye a PATCH y
 * que evita en los bloques usando PUT.
 */
public record UpdateStudentRequest(
        @Size(min = 1, max = 100) String firstName,
        @Size(min = 1, max = 150) String lastName,
        LocalDate birthDate,
        @Pattern(regexp = "[A-Z]{2}", message = "debe ser un código ISO 3166-1 alfa-2") String nationality,
        StudentStatus status,
        LocalDate enrolledAt,
        @Size(max = 30) String phone,
        @Email @Size(max = 255) String email,
        @Size(max = 255) String addressLine,
        @Size(max = 100) String city,
        @Size(max = 20) String postalCode,
        @Pattern(regexp = "[A-Z]{2}", message = "debe ser un código ISO 3166-1 alfa-2") String country) {
}
