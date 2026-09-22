package com.academia.users.dto;

import com.academia.users.UserEntity;
import com.academia.users.UserRole;
import com.academia.users.UserStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Respuesta de {@code GET /auth/me}: lo que un usuario ve de sí mismo. */
public record UserProfileDto(
        UUID id,
        String email,
        String displayName,
        UserRole role,
        UserStatus status,
        @Nullable Instant lastLoginAt) {

    public static UserProfileDto from(UserEntity user) {
        return new UserProfileDto(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getRole(), user.getStatus(), user.getLastLoginAt());
    }
}
