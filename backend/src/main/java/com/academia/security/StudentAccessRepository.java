package com.academia.security;

import java.util.UUID;

/**
 * Las dos consultas de las que depende {@link AccessService} para resolver el acceso a un
 * estudiante (docs/diseno-api.md sección 3.2). ADMIN no necesita consulta: ve todos.
 *
 * La implementación (JPA o JdbcTemplate contra student_guardians / students) llega en el
 * corte en el que existan las entidades Student y Guardian.
 */
public interface StudentAccessRepository {

    /**
     * GUARDIAN → los de student_guardians donde guardian.user_id = usuario y
     * has_access = true.
     */
    boolean isAccessibleByGuardian(UUID studentId, UUID guardianUserId);

    /**
     * STUDENT → únicamente el suyo (students.user_id = usuario).
     */
    boolean isOwnStudent(UUID studentId, UUID studentUserId);
}
