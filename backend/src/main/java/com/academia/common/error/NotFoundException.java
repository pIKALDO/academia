package com.academia.common.error;

/**
 * Recurso inexistente o que no pertenece al usuario autenticado (regla no negociable nº1):
 * ambos casos son indistinguibles desde fuera y se traducen siempre en 404, nunca en 403.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
