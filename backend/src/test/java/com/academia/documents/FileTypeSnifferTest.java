package com.academia.documents;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@link FileTypeSniffer} comprueba el tipo por los primeros bytes, no por la extensión ni el
 * {@code Content-Type} declarado (docs/diseno-api.md sección 5.7).
 */
class FileTypeSnifferTest {

    @Test
    void reconoce_pdf_por_su_cabecera() {
        byte[] header = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};

        assertThat(FileTypeSniffer.detect(header)).contains(FileTypeSniffer.PDF);
    }

    @Test
    void reconoce_jpeg_por_su_cabecera() {
        byte[] header = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

        assertThat(FileTypeSniffer.detect(header)).contains(FileTypeSniffer.JPEG);
    }

    @Test
    void reconoce_png_por_su_cabecera() {
        byte[] header = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

        assertThat(FileTypeSniffer.detect(header)).contains(FileTypeSniffer.PNG);
    }

    @Test
    void un_fichero_de_texto_disfrazado_de_pdf_no_se_reconoce() {
        byte[] header = "Esto no es un PDF de verdad".getBytes();

        assertThat(FileTypeSniffer.detect(header)).isEmpty();
    }

    @Test
    void una_cabecera_mas_corta_que_la_firma_no_lanza_ni_reconoce() {
        byte[] header = {0x25, 0x50};

        assertThat(FileTypeSniffer.detect(header)).isEmpty();
    }

    @Test
    void restringe_al_conjunto_permitido_aunque_la_cabecera_sea_valida() {
        byte[] pngHeader = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

        Optional<String> detected = FileTypeSniffer.detect(pngHeader, Set.of(FileTypeSniffer.PDF));

        assertThat(detected).isEmpty();
    }
}
