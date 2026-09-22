package com.academia.students.dto;

import jakarta.validation.constraints.Size;

/** {@code PUT /students/{id}/housing}: reemplazo total del bloque. */
public record HousingRequest(
        @Size(max = 255) String addressLine,
        @Size(max = 100) String city,
        @Size(max = 200) String responsibleName,
        @Size(max = 30) String responsiblePhone,
        String notes) {
}
