package com.academia.guardians;

import com.academia.guardians.dto.StudentGuardianLinkDto;
import com.academia.guardians.dto.StudentGuardianLinkRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vínculo estudiante-tutor como recurso propio, direccionado por la pareja de ids
 * (docs/diseno-api.md sección 5.4). Vive en {@code guardians} aunque la URL cuelgue de
 * {@code /students}: el módulo es dueño de los vínculos (CLAUDE.md, "tutores y vínculos").
 */
@Tag(name = "Tutores")
@RestController
@RequestMapping("/api/v1/students/{studentId}/guardians/{guardianId}")
class StudentGuardianController {

    private final StudentGuardianService studentGuardianService;

    StudentGuardianController(StudentGuardianService studentGuardianService) {
        this.studentGuardianService = studentGuardianService;
    }

    @PutMapping
    StudentGuardianLinkDto linkStudentGuardian(@PathVariable UUID studentId, @PathVariable UUID guardianId,
            @Valid @RequestBody StudentGuardianLinkRequest request) {
        return studentGuardianService.link(studentId, guardianId, request);
    }

    @DeleteMapping
    ResponseEntity<Void> unlinkStudentGuardian(@PathVariable UUID studentId, @PathVariable UUID guardianId) {
        studentGuardianService.unlink(studentId, guardianId);
        return ResponseEntity.noContent().build();
    }
}
