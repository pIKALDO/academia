package com.academia.security;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resuelve, para el usuario autenticado, qué operaciones puede realizar sobre un estudiante
 * o su documentación. Se referencia desde {@code @PreAuthorize} en la capa de servicio,
 * nunca desde el controlador ni desde el front (regla no negociable nº2):
 *
 * <pre>{@code @PreAuthorize("@access.canViewStudent(#studentId)")}</pre>
 *
 * Cada regla de este componente tiene su propio test (docs/diseno-api.md sección 3.2): es
 * el núcleo del proyecto.
 *
 * La lógica real llega en el corte de estudiantes/documentos, cuando existan las entidades
 * y {@link StudentAccessRepository} tenga una implementación contra la que resolver estas
 * comprobaciones.
 */
@Component("access")
public class AccessService {

    public boolean canViewStudent(UUID studentId) {
        throw pending();
    }

    public boolean canEditStudent(UUID studentId) {
        throw pending();
    }

    public boolean canUploadDocument(UUID studentId) {
        throw pending();
    }

    public boolean canReviewDocument(UUID documentId) {
        throw pending();
    }

    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException(
                "AccessService todavía no tiene implementación: llega con las entidades Student y Document.");
    }
}
