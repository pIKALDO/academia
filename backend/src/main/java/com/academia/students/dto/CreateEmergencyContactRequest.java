package com.academia.students.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /students/{id}/emergency-contacts}. {@code priority} ausente → 1. */
public record CreateEmergencyContactRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 100) String relationship,
        @NotBlank @Size(max = 30) String phone,
        @Size(max = 500) String notes,
        @Min(1) @Max(99) Integer priority) {
}
