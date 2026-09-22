package com.academia.users.dto;

import com.academia.users.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotNull UserRole role,
        @NotBlank @Size(max = 150) String displayName) {
}
