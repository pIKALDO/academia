package com.academia.common.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Envoltorio propio para toda colección paginada de la API (regla no negociable nº9).
 *
 * Nunca se serializa {@link Page} de Spring Data directamente: su JSON incluye campos
 * internos ({@code pageable}, {@code sort}, {@code first}, {@code last},
 * {@code numberOfElements}) que atan el contrato de la API a la versión de Spring Data.
 */
public record PagedResponse<T>(List<T> content, PageInfo page) {

    public record PageInfo(int number, int size, long totalElements, int totalPages) {

        static PageInfo from(Page<?> page) {
            return new PageInfo(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }

    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(page.getContent(), PageInfo.from(page));
    }

    public static <S, T> PagedResponse<T> from(Page<S> page, Function<S, T> mapper) {
        return new PagedResponse<>(page.getContent().stream().map(mapper).toList(), PageInfo.from(page));
    }
}
