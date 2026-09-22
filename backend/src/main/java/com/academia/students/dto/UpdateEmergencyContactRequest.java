package com.academia.students.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** {@code PATCH /emergency-contacts/{id}}: un campo en {@code null} significa "no lo toques". */
public record UpdateEmergencyContactRequest(
        @Size(min = 1, max = 200) String name,
        @Size(max = 100) String relationship,
        @Size(min = 1, max = 30) String phone,
        @Size(max = 500) String notes,
        @Min(1) @Max(99) Integer priority) {
}
