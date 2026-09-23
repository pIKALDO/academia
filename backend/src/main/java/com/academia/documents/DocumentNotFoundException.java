package com.academia.documents;

import com.academia.common.error.NotFoundException;

/**
 * Única forma de decir "este documento no está", igual que {@code StudentNotFoundException}:
 * no existe, o su estudiante está borrado, o no es del usuario
 * ({@code security.HideDocumentWhenNotVisible}). Mensaje fijo a propósito (regla no negociable
 * nº1).
 */
public class DocumentNotFoundException extends NotFoundException {

    public DocumentNotFoundException() {
        super("Documento no encontrado.");
    }
}
