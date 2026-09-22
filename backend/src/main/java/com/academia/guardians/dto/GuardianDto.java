package com.academia.guardians.dto;

import com.academia.guardians.GuardianEntity;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Respuesta de {@code /guardians} (docs/diseno-api.md sección 5.4). Solo ADMIN accede a este
 * recurso, así que hay una única vista; lo que una familia ve de un tutor está en
 * {@code StudentGuardianDto.LinkedGuardian}.
 */
public record GuardianDto(
        UUID id,
        @Nullable UUID userId,
        String firstName,
        String lastName,
        @Nullable String phone,
        @Nullable String email,
        Instant createdAt,
        Instant updatedAt) {

    public static GuardianDto from(GuardianEntity entity) {
        return new GuardianDto(entity.getId(), entity.getUserId(), entity.getFirstName(), entity.getLastName(),
                entity.getPhone(), entity.getEmail(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
