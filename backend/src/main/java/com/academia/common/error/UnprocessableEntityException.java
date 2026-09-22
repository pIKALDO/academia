package com.academia.common.error;

/**
 * Sintaxis correcta, contenido inválido (docs/diseno-api.md sección 6): un token de
 * activación o recuperación caducado, ya usado o inexistente es el caso de uso que motiva
 * esta excepción. Se trata como un único caso genérico a propósito: distinguir "caducado" de
 * "ya usado" de "no existe" en la respuesta permitiría a un atacante sondear el estado de un
 * token ajeno.
 */
public class UnprocessableEntityException extends RuntimeException {

    public UnprocessableEntityException(String message) {
        super(message);
    }
}
