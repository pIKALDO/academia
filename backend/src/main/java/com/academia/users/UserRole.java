package com.academia.users;

/**
 * Refleja el tipo {@code user_role} de PostgreSQL (V1__baseline_users.sql). Un rol por
 * usuario, no una tabla de relación: ver la justificación en docs/modelo-datos.md sección 2.
 */
public enum UserRole {
    ADMIN,
    GUARDIAN,
    STUDENT
}
