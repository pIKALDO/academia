package com.academia.students.dto;

import com.academia.students.EmergencyContactEntity;
import java.util.UUID;

/**
 * Contacto de emergencia, vista de administrador. Lleva {@code id} (lo necesita para
 * {@code PATCH/DELETE /emergency-contacts/{id}}), {@code notes} y {@code priority}.
 */
public record EmergencyContactAdminDto(
        UUID id,
        String name,
        String relationship,
        String phone,
        String notes,
        int priority) implements EmergencyContactDto {

    public static EmergencyContactAdminDto from(EmergencyContactEntity entity) {
        return new EmergencyContactAdminDto(entity.getId(), entity.getName(), entity.getRelationship(),
                entity.getPhone(), entity.getNotes(), entity.getPriority());
    }
}
