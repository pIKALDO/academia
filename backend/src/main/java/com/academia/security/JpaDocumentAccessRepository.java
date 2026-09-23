package com.academia.security;

import com.academia.documents.DocumentRepository;
import com.academia.students.StudentRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Resuelve {@link DocumentAccessRepository} sobre {@link DocumentRepository}. Comprueba también
 * que el estudiante propietario siga existiendo (no borrado): mismo criterio que
 * {@code EmergencyContactService.getEntityOrThrow}, para que un documento de un estudiante
 * borrado no sea visible aunque el documento en sí no lo esté.
 */
@Repository
class JpaDocumentAccessRepository implements DocumentAccessRepository {

    private final DocumentRepository documentRepository;
    private final StudentRepository studentRepository;

    JpaDocumentAccessRepository(DocumentRepository documentRepository, StudentRepository studentRepository) {
        this.documentRepository = documentRepository;
        this.studentRepository = studentRepository;
    }

    @Override
    public Optional<UUID> findOwningStudentId(UUID documentId) {
        return documentRepository.findStudentIdByIdAndDeletedAtIsNull(documentId)
                .filter(studentRepository::existsById);
    }
}
