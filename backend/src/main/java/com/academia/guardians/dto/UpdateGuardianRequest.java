package com.academia.guardians.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** {@code PATCH /guardians/{id}}: un campo en {@code null} significa "no lo toques". */
public record UpdateGuardianRequest(
        UUID userId,
        @Size(min = 1, max = 100) String firstName,
        @Size(min = 1, max = 150) String lastName,
        @Size(max = 30) String phone,
        @Email @Size(max = 255) String email) {
}
