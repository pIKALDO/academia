package com.academia.users.dto;

import com.academia.users.UserEntity;
import com.academia.users.UserRole;
import com.academia.users.UserStatus;
import java.time.Instant;
import java.util.UUID;

/** Respuesta de {@code GET/POST/PATCH /users/{id}}: ficha completa para administración. */
public record UserDetailDto(
        UUID id,
        String email,
        String displayName,
        UserRole role,
        UserStatus status,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt) {

    public static UserDetailDto from(UserEntity user) {
        return new UserDetailDto(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(),
                user.getStatus(), user.getLastLoginAt(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
