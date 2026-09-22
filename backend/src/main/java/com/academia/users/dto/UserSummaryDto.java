package com.academia.users.dto;

import com.academia.users.UserEntity;
import com.academia.users.UserRole;
import com.academia.users.UserStatus;
import java.util.UUID;

/** Fila de {@code GET /users}: lo mínimo para un listado administrativo. */
public record UserSummaryDto(
        UUID id,
        String email,
        String displayName,
        UserRole role,
        UserStatus status) {

    public static UserSummaryDto from(UserEntity user) {
        return new UserSummaryDto(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getRole(), user.getStatus());
    }
}
