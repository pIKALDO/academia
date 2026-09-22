package com.academia.security;

import static com.academia.security.StudentAccessSpecifications.hasId;
import static com.academia.security.StudentAccessSpecifications.visibleToGuardian;

import com.academia.students.StudentRepository;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Resuelve {@link StudentAccessRepository} con el mismo predicado que filtra el listado
 * ({@link StudentAccessSpecifications#visibleToGuardian}), acotado a un id: un
 * {@code SELECT ... WHERE EXISTS} que no carga la entidad.
 */
@Repository
class JpaStudentAccessRepository implements StudentAccessRepository {

    private final StudentRepository studentRepository;

    JpaStudentAccessRepository(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Override
    public boolean isAccessibleByGuardian(UUID studentId, UUID guardianUserId) {
        return studentRepository.exists(visibleToGuardian(guardianUserId).and(hasId(studentId)));
    }
}
