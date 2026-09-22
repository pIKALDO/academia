package com.academia.security;

import java.util.UUID;

/**
 * La consulta de la que depende {@link AccessService} para resolver el acceso de un tutor a
 * un estudiante (docs/diseno-api.md sección 3.2). ADMIN no necesita consulta: ve todos.
 *
 * Interfaz y no uso directo de {@link JpaStudentAccessRepository}: {@code AccessServiceTest}
 * prueba cada regla sin base de datos, con una implementación en memoria.
 *
 * La regla de STUDENT ("únicamente el suyo", {@code students.user_id}) no está: en fase 1 el
 * rol STUDENT no tiene acceso al módulo de estudiantes (portal del estudiante, fase 2).
 */
public interface StudentAccessRepository {

    /**
     * GUARDIAN → los de student_guardians donde guardian.user_id = usuario y
     * has_access = true.
     */
    boolean isAccessibleByGuardian(UUID studentId, UUID guardianUserId);
}
