package com.academia.common.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Único punto donde se construye el {@link Pageable} de cualquier endpoint de listado, para
 * que el tope de tamaño de página (regla no negociable nº9) se aplique siempre igual y no se
 * reimplemente en cada controlador.
 */
public final class PageRequestFactory {

    public static final int MAX_SIZE = 100;
    public static final int DEFAULT_SIZE = 20;

    private PageRequestFactory() {
    }

    public static Pageable of(int page, int size, Sort sort) {
        int safePage = Math.max(page, 0);
        int requestedSize = size <= 0 ? DEFAULT_SIZE : size;
        int safeSize = Math.min(requestedSize, MAX_SIZE);
        return PageRequest.of(safePage, safeSize, sort);
    }

    public static Pageable of(int page, int size) {
        return of(page, size, Sort.unsorted());
    }
}
