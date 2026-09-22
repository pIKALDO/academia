package com.academia.students.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Respuesta de {@code GET /students/{id}}: una de las dos vistas por rol (docs/diseno-api.md
 * sección 4). Interfaz sellada y no una clase con campos anulables (regla no negociable nº4):
 * cada vista es un record completo e independiente, y un campo nuevo en
 * {@link StudentAdminDto} no aparece en {@link StudentGuardianDto} hasta que alguien lo añade
 * a propósito.
 *
 * Sin discriminador en el JSON: la vista la determina el rol de la sesión, que el front ya
 * conoce por {@code GET /auth/me}.
 */
@Schema(oneOf = {StudentAdminDto.class, StudentGuardianDto.class})
public sealed interface StudentDetailDto permits StudentAdminDto, StudentGuardianDto {
}
