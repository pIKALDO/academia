package com.academia.documents;

import java.util.Optional;
import java.util.Set;

/**
 * Solo 3 tipos permitidos en fase 1 (PDF, JPEG, PNG), así que un sniffer de cabecera propio es
 * más simple y con menos dependencias que Apache Tika (no presente en {@code pom.xml}) para lo
 * que hace falta: la extensión y el {@code Content-Type} son triviales de falsear
 * (docs/diseno-api.md sección 5.7), así que la comprobación se hace sobre los primeros bytes
 * reales del fichero.
 */
public final class FileTypeSniffer {

    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46}; // %PDF
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    public static final String PDF = "application/pdf";
    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";

    private FileTypeSniffer() {
    }

    /** Detecta entre los tres tipos admitidos en fase 1, sin restringir el conjunto. */
    public static Optional<String> detect(byte[] header) {
        return detect(header, Set.of(PDF, JPEG, PNG));
    }

    /**
     * Detecta solo entre {@code allowed}: la subida de documentos admite PDF/JPEG/PNG, pero
     * una foto de estudiante (docs/diseno-api.md sección 5.3) no tiene sentido como PDF.
     * Pública porque también la usa {@code students.StudentService} para validar la foto.
     */
    public static Optional<String> detect(byte[] header, Set<String> allowed) {
        if (allowed.contains(PDF) && startsWith(header, PDF_MAGIC)) {
            return Optional.of(PDF);
        }
        if (allowed.contains(JPEG) && startsWith(header, JPEG_MAGIC)) {
            return Optional.of(JPEG);
        }
        if (allowed.contains(PNG) && startsWith(header, PNG_MAGIC)) {
            return Optional.of(PNG);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] header, byte[] magic) {
        if (header.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (header[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
