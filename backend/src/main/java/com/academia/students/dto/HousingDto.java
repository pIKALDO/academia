package com.academia.students.dto;

import com.academia.students.HousingInfoEntity;
import java.time.Instant;

/**
 * Bloque de alojamiento. Solo existe vista de administrador: no hay equivalente en
 * {@link StudentGuardianDto} (regla no negociable nº5).
 */
public record HousingDto(
        String addressLine,
        String city,
        String responsibleName,
        String responsiblePhone,
        String notes,
        Instant updatedAt) {

    public static HousingDto from(HousingInfoEntity entity) {
        return new HousingDto(entity.getAddressLine(), entity.getCity(), entity.getResponsibleName(),
                entity.getResponsiblePhone(), entity.getNotes(), entity.getUpdatedAt());
    }
}
