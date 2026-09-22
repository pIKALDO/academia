package com.academia.students.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Elemento de {@code GET /students/{id}/emergency-contacts}: vista de administrador o de
 * familia según el rol, igual que {@link StudentDetailDto}.
 */
@Schema(oneOf = {EmergencyContactAdminDto.class, EmergencyContactGuardianDto.class})
public sealed interface EmergencyContactDto permits EmergencyContactAdminDto, EmergencyContactGuardianDto {
}
