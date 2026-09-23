package com.academia.documents.dto;

/**
 * Resumen de documentación de un estudiante, incluido en {@code StudentAdminDto},
 * {@code StudentGuardianDto} y {@code StudentListDto} (docs/diseno-api.md secciones 4.1-4.3):
 * la misma información para los dos roles, a diferencia del resto de la ficha.
 *
 * <ul>
 *   <li>{@code total}: documentos no borrados del estudiante.</li>
 *   <li>{@code pending}: {@code status = PENDING}.</li>
 *   <li>{@code expiringSoon}: {@code expiresAt} entre hoy y hoy + 30 días, ambos inclusive
 *       (mismo horizonte que el primer aviso de caducidad, {@code EXPIRY_30D}).</li>
 *   <li>{@code expired}: {@code expiresAt} anterior a hoy.</li>
 * </ul>
 */
public record DocumentsSummaryDto(int total, int pending, int expiringSoon, int expired) {

    public static final DocumentsSummaryDto EMPTY = new DocumentsSummaryDto(0, 0, 0, 0);
}
