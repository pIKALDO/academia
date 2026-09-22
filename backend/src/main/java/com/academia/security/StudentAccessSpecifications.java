package com.academia.security;

import com.academia.guardians.StudentGuardianEntity;
import com.academia.students.StudentEntity;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Definición única de "qué estudiantes ve un tutor" (docs/diseno-api.md sección 3.2):
 *
 * <pre>
 * EXISTS (SELECT 1 FROM student_guardians sg JOIN guardians g ON g.id = sg.guardian_id
 *         WHERE sg.student_id = s.id AND g.user_id = :usuario AND sg.has_access)
 * </pre>
 *
 * La usan las dos preguntas de autorización: "¿ve este estudiante?"
 * ({@link JpaStudentAccessRepository}, que alimenta {@code @PreAuthorize}) y "¿qué
 * estudiantes ve?" (el listado, vía {@link AccessService#visibleStudents()}). Con un solo
 * predicado, las dos respuestas no pueden divergir. Los estudiantes borrados quedan fuera
 * por la {@code @SQLRestriction} de {@link StudentEntity}, sin repetirlo aquí.
 */
public final class StudentAccessSpecifications {

    private StudentAccessSpecifications() {
    }

    public static Specification<StudentEntity> visibleToGuardian(UUID guardianUserId) {
        return (root, query, cb) -> {
            Subquery<Integer> link = query.subquery(Integer.class);
            Root<StudentGuardianEntity> sg = link.from(StudentGuardianEntity.class);
            link.select(cb.literal(1)).where(
                    cb.equal(sg.get("id").get("studentId"), root.get("id")),
                    cb.equal(sg.get("guardian").get("userId"), guardianUserId),
                    cb.isTrue(sg.get("hasAccess")));
            return cb.exists(link);
        };
    }

    static Specification<StudentEntity> hasId(UUID studentId) {
        return (root, query, cb) -> cb.equal(root.get("id"), studentId);
    }

    /** Ningún estudiante: para roles sin acceso al listado, cerrar en vez de abrir. */
    static Specification<StudentEntity> none() {
        return (root, query, cb) -> cb.disjunction();
    }
}
