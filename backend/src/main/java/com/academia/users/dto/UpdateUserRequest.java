package com.academia.users.dto;

import com.academia.users.UserRole;
import jakarta.validation.constraints.Size;

/**
 * PATCH parcial: un campo en {@code null} significa "no lo toques", no "ponlo a null" (ni
 * {@code displayName} ni {@code role} son nulables en la entidad). No incluye {@code email}:
 * cambiarlo no está entre los casos de uso pedidos para este corte.
 */
public record UpdateUserRequest(
        @Size(max = 150) String displayName,
        UserRole role) {
}
