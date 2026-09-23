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

    /**
     * Destinatarios de los avisos de caducidad (corte 3): los tutores vinculados con
     * {@code has_access = true}, sin importar si son el principal. Un tutor sin
     * {@code hasAccess} no entra en la plataforma, así que tampoco tiene sentido avisarlo por
     * correo de algo que no puede consultar.
     */
    @Query("""
            SELECT new com.academia.guardians.GuardianRecipient(g.email, g.firstName)
            FROM StudentGuardianEntity l JOIN l.guardian g
            WHERE l.id.studentId = :studentId AND l.hasAccess = true AND g.email IS NOT NULL
            """)
    List<GuardianRecipient> findAccessibleGuardianRecipients(@Param("studentId") UUID studentId);
}
