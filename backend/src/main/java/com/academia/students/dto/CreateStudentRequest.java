package com.academia.students.dto;

import com.academia.students.StudentStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * {@code POST /students}. Solo la ficha principal: los bloques se rellenan después con su
 * propio {@code PUT}. {@code status} ausente → {@code ACTIVE}, como el valor por defecto de la
 * columna.
 */
public record CreateStudentRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 150) String lastName,
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
