package com.academia.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PagedResponseTest {

    @Test
    void envuelve_el_contenido_y_los_metadatos_de_pagina() {
        var page = new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 20), 42);

        PagedResponse<String> response = PagedResponse.from(page);

        assertThat(response.content()).containsExactly("a", "b");
        assertThat(response.page().number()).isZero();
        assertThat(response.page().size()).isEqualTo(20);
        assertThat(response.page().totalElements()).isEqualTo(42);
        assertThat(response.page().totalPages()).isEqualTo(3);
    }

    @Test
    void mapea_cada_elemento_al_dto_indicado() {
        var page = new PageImpl<>(List.of(1, 2, 3), PageRequest.of(0, 20), 3);

        PagedResponse<String> response = PagedResponse.from(page, n -> "n" + n);

        assertThat(response.content()).containsExactly("n1", "n2", "n3");
    }

    @Test
    void el_tope_de_tamano_de_pagina_es_cien() {
        var pageable = PageRequestFactory.of(0, 1_000_000);

        assertThat(pageable.getPageSize()).isEqualTo(PageRequestFactory.MAX_SIZE);
    }

    @Test
    void un_tamano_no_positivo_usa_el_valor_por_defecto() {
        var pageable = PageRequestFactory.of(0, 0);

        assertThat(pageable.getPageSize()).isEqualTo(PageRequestFactory.DEFAULT_SIZE);
    }
}
