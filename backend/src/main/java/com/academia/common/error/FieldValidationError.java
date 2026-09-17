package com.academia.common.error;

/**
 * Un fallo de validación de un campo concreto, tal y como se documenta en la extensión
 * {@code errors} de {@code ProblemDetail} (docs/diseno-api.md sección 7).
 */
public record FieldValidationError(String field, String message) {
}
