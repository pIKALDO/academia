package com.academia.students;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Pública porque {@code security.JpaStudentAccessRepository} resuelve la visibilidad con
 * {@code exists(Specification)} sobre ella: el mismo predicado que filtra el listado.
 */
public interface StudentRepository extends JpaRepository<StudentEntity, UUID>, JpaSpecificationExecutor<StudentEntity> {
}
