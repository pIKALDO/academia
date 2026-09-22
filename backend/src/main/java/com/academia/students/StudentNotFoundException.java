package com.academia.students;

import com.academia.common.error.NotFoundException;

/**
 * Única forma de decir "este estudiante no está" en todo el backend, se deba a que no existe
 * o a que no es del usuario ({@code security.HideStudentWhenNotVisible}). Mensaje fijo a
 * propósito: si las dos rutas construyeran su propio mensaje, bastaría una diferencia de
 * redacción para distinguirlas desde fuera (regla no negociable nº1).
 */
public class StudentNotFoundException extends NotFoundException {

    public StudentNotFoundException() {
        super("Estudiante no encontrado.");
    }
}
