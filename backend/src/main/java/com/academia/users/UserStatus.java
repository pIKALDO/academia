package com.academia.users;

/**
 * Refleja el tipo {@code user_status} de PostgreSQL (V1__baseline_users.sql).
 */
public enum UserStatus {
    ACTIVE,
    DISABLED,
    PENDING_ACTIVATION
}
