package com.academia.students.dto;

import com.academia.students.EmergencyContactEntity;
import org.jspecify.annotations.Nullable;

/**
 * Contacto de emergencia, vista de familia (docs/diseno-api.md sección 4.2): nombre, relación
 * y teléfono. Sin {@code notes}, que son de gestión interna, ni {@code id}, porque la familia
 * no puede operar sobre el contacto.
 */
public record EmergencyContactGuardianDto(
        String name,
        @Nullable String relationship,
        String phone) implements EmergencyContactDto {

    public static EmergencyContactGuardianDto from(EmergencyContactEntity entity) {
        return new EmergencyContactGuardianDto(entity.getName(), entity.getRelationship(), entity.getPhone());
    }
}
