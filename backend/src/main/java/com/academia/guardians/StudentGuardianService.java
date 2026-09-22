package com.academia.guardians;

import com.academia.common.audit.Audited;
import com.academia.common.error.NotFoundException;
import com.academia.guardians.dto.StudentGuardianLinkDto;
import com.academia.guardians.dto.StudentGuardianLinkRequest;
import com.academia.students.StudentNotFoundException;
import com.academia.students.StudentRepository;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vínculo estudiante-tutor (docs/diseno-api.md sección 5.4). Es el recurso que concede o
 * retira a una familia el acceso a un estudiante ({@code hasAccess}), así que cada cambio
 * queda auditado.
 */
@Service
public class StudentGuardianService {

    private final StudentGuardianRepository studentGuardianRepository;
    private final StudentRepository studentRepository;
    private final GuardianService guardianService;

    StudentGuardianService(StudentGuardianRepository studentGuardianRepository, StudentRepository studentRepository,
            GuardianService guardianService) {
        this.studentGuardianRepository = studentGuardianRepository;
        this.studentRepository = studentRepository;
        this.guardianService = guardianService;
    }

    /**
     * Idempotente: el vínculo se identifica por la pareja de ids de la URL, así que repetir la
     * llamada reescribe el mismo vínculo en lugar de crear otro. Con {@code POST /links} dos
     * clics seguidos darían dos filas —o un 409 por la clave primaria—.
     */
    @PreAuthorize("@access.canManageGuardians()")
    @Audited(action = "STUDENT_GUARDIAN_LINKED", entity = "STUDENT_GUARDIAN", studentIdParam = "studentId")
    @Transactional
    public StudentGuardianLinkDto link(UUID studentId, UUID guardianId, StudentGuardianLinkRequest request) {
        requireStudent(studentId);
        GuardianEntity guardian = guardianService.getEntityOrThrow(guardianId);
        StudentGuardianEntity link = studentGuardianRepository.findById(new StudentGuardianId(studentId, guardianId))
                .orElseGet(() -> new StudentGuardianEntity(studentId, guardian));
        link.replace(request.relationship(), request.isPrimary(), request.hasAccess());
        return StudentGuardianLinkDto.from(studentGuardianRepository.saveAndFlush(link));
    }

    @PreAuthorize("@access.canManageGuardians()")
    @Audited(action = "STUDENT_GUARDIAN_UNLINKED", entity = "STUDENT_GUARDIAN", studentIdParam = "studentId")
    @Transactional
    public void unlink(UUID studentId, UUID guardianId) {
        requireStudent(studentId);
        StudentGuardianEntity link = studentGuardianRepository.findById(new StudentGuardianId(studentId, guardianId))
                .orElseThrow(() -> new NotFoundException("Vínculo no encontrado."));
        studentGuardianRepository.delete(link);
    }

    private void requireStudent(UUID studentId) {
        if (!studentRepository.existsById(studentId)) {
            throw new StudentNotFoundException();
        }
    }
}
