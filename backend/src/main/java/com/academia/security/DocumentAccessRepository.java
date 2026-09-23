package com.academia.security;

import java.util.Optional;
import java.util.UUID;

/**
 * La consulta de la que depende {@link AccessService#canViewDocument} para resolver a qué
 * estudiante pertenece un documento, y con ello reutilizar {@link AccessService#canViewStudent}
 * (mismo patrón que {@link StudentAccessRepository}).
 */
public interface DocumentAccessRepository {

    /**
     * Vacío si el documento no existe, está borrado, o su estudiante está borrado: un
     * documento de un estudiante borrado debe dar 404 igual que el propio estudiante borrado.
     */
    Optional<UUID> findOwningStudentId(UUID documentId);
}
