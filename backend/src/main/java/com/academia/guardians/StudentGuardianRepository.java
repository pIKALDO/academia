package com.academia.guardians;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Pública porque la ficha del estudiante ({@code students}) lista los tutores vinculados. La
 * consulta de autorización no pasa por aquí: vive en {@code StudentAccessSpecifications}.
 */
public interface StudentGuardianRepository extends JpaRepository<StudentGuardianEntity, StudentGuardianId> {

    /** {@code JOIN FETCH}: la ficha de administrador lee nombre y contacto de cada tutor. */
    @Query("""
            SELECT l FROM StudentGuardianEntity l JOIN FETCH l.guardian g
            WHERE l.id.studentId = :studentId
            ORDER BY l.primary DESC, g.lastName, g.firstName
            """)
    List<StudentGuardianEntity> findWithGuardianByStudentId(@Param("studentId") UUID studentId);
}
