package com.academia.common.error;

/**
 * Choque con el estado actual del servidor (el email ya existe, el documento ya fue
 * revisado), no con la forma de los datos recibidos. Se traduce en 409.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
