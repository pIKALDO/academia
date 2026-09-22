package com.academia.guardians.dto;

import com.academia.guardians.GuardianRelationship;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de {@code PUT /students/{sid}/guardians/{gid}} (docs/diseno-api.md sección 5.4).
 * Los tres campos obligatorios: es un PUT, reemplazo total del vínculo. Un
 * {@code hasAccess} implícito sería justo el tipo de valor por defecto que acaba dando acceso a
 * quien no debía tenerlo.
 */
public record StudentGuardianLinkRequest(
        @NotNull GuardianRelationship relationship,
        @NotNull Boolean isPrimary,
        @NotNull Boolean hasAccess) {
}
