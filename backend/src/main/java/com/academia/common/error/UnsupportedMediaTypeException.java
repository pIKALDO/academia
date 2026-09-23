package com.academia.common.error;

/**
 * Tipo de fichero no admitido, detectado por contenido (docs/diseno-api.md sección 5.7):
 * ni la extensión ni el {@code Content-Type} declarado por el cliente deciden esto, ambos son
 * triviales de falsear. Se traduce en 415.
 */
public class UnsupportedMediaTypeException extends RuntimeException {

    public UnsupportedMediaTypeException(String message) {
        super(message);
    }
}
