package com.academia.guardians.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * {@code POST /guardians}. {@code userId} opcional: la cuenta con rol GUARDIAN con la que este
 * tutor entra en el portal de familias. Sin ella, el tutor figura en la ficha pero no ve nada.
 */
public record CreateGuardianRequest(
        UUID userId,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 150) String lastName,
        @Size(max = 30) String phone,
        @Email @Size(max = 255) String email) {
}
