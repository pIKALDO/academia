package com.academia.students.dto;

import com.academia.students.StudentEntity;
import com.academia.students.StudentStatus;
import java.util.UUID;

/**
 * Fila de {@code GET /students} (docs/diseno-api.md sección 4.3): solo lo necesario para
 * pintar la lista, sin cargar ningún bloque. Igual para ADMIN y GUARDIAN: no contiene nada
 * que la familia no deba ver de sus propios hijos.
 *
 * {@code photoUrl} y {@code documentsSummary} llegan con el corte de documentos (necesitan el
 * almacenamiento y el módulo de documentación); añadirlos entonces no rompe el contrato.
 */
public record StudentListDto(
        UUID id,
        String firstName,
        String lastName,
        StudentStatus status) {

    public static StudentListDto from(StudentEntity entity) {
        return new StudentListDto(entity.getId(), entity.getFirstName(), entity.getLastName(), entity.getStatus());
    }
}
