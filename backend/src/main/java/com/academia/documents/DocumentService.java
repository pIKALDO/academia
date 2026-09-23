package com.academia.documents;

import com.academia.common.audit.Audited;
import com.academia.common.error.ConflictException;
import com.academia.common.error.UnprocessableEntityException;
import com.academia.common.error.UnsupportedMediaTypeException;
import com.academia.common.web.PagedResponse;
import com.academia.config.StorageProperties;
import com.academia.config.StorageService;
import com.academia.documents.dto.DocumentDto;
import com.academia.documents.dto.UpdateDocumentRequest;
import com.academia.documents.dto.UploadedByDto;
import com.academia.security.HideDocumentWhenNotVisible;
import com.academia.security.HideStudentWhenNotVisible;
import com.academia.students.StudentNotFoundException;
import com.academia.students.StudentRepository;
import com.academia.users.AcademiaUserPrincipal;
import com.academia.users.UserService;
import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.method.HandleAuthorizationDenied;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Documentación de un estudiante (docs/diseno-api.md sección 5.6). Anidado bajo el estudiante
 * para listar y subir; plano ({@code /documents/{id}}) para el resto (sección 1.2).
 */
@Service
public class DocumentService {

    /** Fase 1: PDF, JPEG, PNG (docs/diseno-api.md sección 5.7). */
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of(FileTypeSniffer.PDF, FileTypeSniffer.JPEG, FileTypeSniffer.PNG);

    private final DocumentRepository documentRepository;
    private final StudentRepository studentRepository;
    private final StorageService storageService;
    private final StorageProperties storageProperties;
    private final UserService userService;

    DocumentService(DocumentRepository documentRepository, StudentRepository studentRepository,
            StorageService storageService, StorageProperties storageProperties, UserService userService) {
        this.documentRepository = documentRepository;
        this.studentRepository = studentRepository;
        this.storageService = storageService;
        this.storageProperties = storageProperties;
        this.userService = userService;
    }

    @PreAuthorize("@access.canViewStudent(#studentId)")
    @HandleAuthorizationDenied(handlerClass = HideStudentWhenNotVisible.class)
    @Transactional(readOnly = true)
    public PagedResponse<DocumentDto> list(UUID studentId, DocumentCategory category, DocumentStatus status,
            LocalDate expiringBefore, Boolean expired, Pageable pageable) {
        requireStudent(studentId);
        Specification<DocumentEntity> spec = studentIs(studentId)
                .and(notDeleted())
                .and(categoryIs(category))
                .and(statusIs(status))
                .and(expiringBefore(expiringBefore))
                .and(expiredIs(expired));
        return PagedResponse.from(documentRepository.findAll(spec, pageable), this::toDto);
    }

    /**
     * Regla de visibilidad, no de operación (docs/diseno-api.md sección 5.6 + regla no
     * negociable nº1): un estudiante ajeno da 404, igual que al leerlo. {@link #requireStudent}
     * sigue haciendo falta para el caso ADMIN, cuya decisión de visibilidad no consulta la
     * base de datos (ve "todos" sin comprobar que el id exista).
     */
    @PreAuthorize("@access.canUploadDocument(#studentId)")
    @HandleAuthorizationDenied(handlerClass = HideStudentWhenNotVisible.class)
    @Audited(action = "DOCUMENT_UPLOADED", entity = "DOCUMENT", studentIdParam = "studentId")
    @Transactional
    public DocumentDto upload(UUID studentId, DocumentCategory category, String name, LocalDate issuedAt,
            LocalDate expiresAt, MultipartFile file) {
        requireStudent(studentId);
        if (category == null) {
            throw new UnprocessableEntityException("La categoría del documento es obligatoria.");
        }
        if (name == null || name.isBlank()) {
            throw new UnprocessableEntityException("El nombre del documento es obligatorio.");
        }
        if (issuedAt != null && expiresAt != null && expiresAt.isBefore(issuedAt)) {
            throw new UnprocessableEntityException("La fecha de caducidad no puede ser anterior a la de emisión.");
        }

        byte[] content = readBytes(file);
        // Ficheros de hasta 10 MB (límite global de multipart): cargarlos enteros en memoria
        // es aceptable a este tamaño, sin la complejidad de un streaming a S3.
        String contentType = FileTypeSniffer.detect(content, ALLOWED_CONTENT_TYPES)
                .orElseThrow(() -> new UnsupportedMediaTypeException(
                        "Solo se admiten ficheros PDF, JPEG o PNG, comprobados por contenido."));

        String storageKey = "students/" + studentId + "/" + UUID.randomUUID();
        storageService.upload(storageKey, content, contentType);

        DocumentEntity document = new DocumentEntity(studentId, category, name);
        document.changeMetadata(category, name, issuedAt, expiresAt);
        document.attachFile(storageKey, contentType, content.length, sha256(content), currentUserId(), Instant.now());
        documentRepository.save(document);
        return toDto(document);
    }

    @PreAuthorize("@access.canViewDocument(#id)")
    @HandleAuthorizationDenied(handlerClass = HideDocumentWhenNotVisible.class)
    @Transactional(readOnly = true)
    public DocumentDto get(UUID id) {
        return toDto(getEntityOrThrow(id));
    }

    /** Devuelve la URL prefirmada; el controlador la traduce en un 302 (docs/diseno-api.md sección 5.6). */
    @PreAuthorize("@access.canViewDocument(#id)")
    @HandleAuthorizationDenied(handlerClass = HideDocumentWhenNotVisible.class)
    @Transactional(readOnly = true)
    public URI download(UUID id) {
        DocumentEntity document = getEntityOrThrow(id);
        if (document.getStorageKey() == null) {
            throw new ConflictException("El documento todavía no tiene fichero.");
        }
        return storageService.presignedGetUrl(document.getStorageKey(), storageProperties.presignedUrlDuration());
    }

    @PreAuthorize("@access.canEditDocument(#id)")
    @Audited(action = "DOCUMENT_UPDATED", entity = "DOCUMENT")
    @Transactional
    public DocumentDto update(UUID id, UpdateDocumentRequest request) {
        DocumentEntity document = getEntityOrThrow(id);
        DocumentCategory category = request.category() != null ? request.category() : document.getCategory();
        String name = request.name() != null ? request.name() : document.getName();
        LocalDate issuedAt = request.issuedAt() != null ? request.issuedAt() : document.getIssuedAt();
        LocalDate expiresAt = request.expiresAt() != null ? request.expiresAt() : document.getExpiresAt();
        document.changeMetadata(category, name, issuedAt, expiresAt);
        return toDto(document);
    }

    @PreAuthorize("@access.canReviewDocument(#id)")
    @Audited(action = "DOCUMENT_REVIEWED", entity = "DOCUMENT")
    @Transactional
    public DocumentDto review(UUID id) {
        DocumentEntity document = getEntityOrThrow(id);
        document.review(currentUserId(), Instant.now());
        return toDto(document);
    }

    /** Borrado lógico únicamente (docs/PROGRESO.md): el objeto no se borra del almacenamiento. */
    @PreAuthorize("@access.canDeleteDocument(#id)")
    @Audited(action = "DOCUMENT_DELETED", entity = "DOCUMENT")
    @Transactional
    public void delete(UUID id) {
        getEntityOrThrow(id).markDeleted(Instant.now());
    }

    @PreAuthorize("@access.canViewExpiringDocuments()")
    @Transactional(readOnly = true)
    public PagedResponse<DocumentDto> expiring(DocumentCategory category, DocumentStatus status, Pageable pageable) {
        Specification<DocumentEntity> spec = notDeleted()
                .and((root, query, cb) -> cb.isNotNull(root.get("expiresAt")))
                .and(categoryIs(category))
                .and(statusIs(status));
        return PagedResponse.from(documentRepository.findAll(spec, pageable), this::toDto);
    }

    private DocumentDto toDto(DocumentEntity document) {
        UploadedByDto uploadedBy = document.getUploadedBy() == null ? null
                : userService.findSummary(document.getUploadedBy())
                        .map(summary -> new UploadedByDto(summary.id(), summary.displayName()))
                        .orElse(null);
        return DocumentDto.from(document, uploadedBy, LocalDate.now());
    }

    /**
     * La fila del documento sobrevive al borrado lógico de su estudiante: sin comprobar que el
     * estudiante sigue existiendo, se podría seguir consultando documentación de un borrado
     * (mismo patrón que {@code EmergencyContactService.getEntityOrThrow}).
     */
    private DocumentEntity getEntityOrThrow(UUID id) {
        return documentRepository.findById(id)
                .filter(document -> document.getDeletedAt() == null)
                .filter(document -> studentRepository.existsById(document.getStudentId()))
                .orElseThrow(DocumentNotFoundException::new);
    }

    private void requireStudent(UUID studentId) {
        if (!studentRepository.existsById(studentId)) {
            throw new StudentNotFoundException();
        }
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UnprocessableEntityException("No se pudo leer el fichero recibido.");
        }
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM.", e);
        }
    }

    private static @Nullable UUID currentUserId() {
        return SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof AcademiaUserPrincipal principal
                ? principal.userId()
                : null;
    }

    private static Specification<DocumentEntity> studentIs(UUID studentId) {
        return (root, query, cb) -> cb.equal(root.get("studentId"), studentId);
    }

    private static Specification<DocumentEntity> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    private static Specification<DocumentEntity> categoryIs(DocumentCategory category) {
        return (root, query, cb) -> category == null ? null : cb.equal(root.get("category"), category);
    }

    private static Specification<DocumentEntity> statusIs(DocumentStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    private static Specification<DocumentEntity> expiringBefore(LocalDate expiringBefore) {
        return (root, query, cb) -> expiringBefore == null ? null
                : cb.and(cb.isNotNull(root.get("expiresAt")), cb.lessThan(root.get("expiresAt"), expiringBefore));
    }

    private static Specification<DocumentEntity> expiredIs(Boolean expired) {
        return (root, query, cb) -> {
            if (expired == null) {
                return null;
            }
            var notNullAndPast = cb.and(cb.isNotNull(root.get("expiresAt")),
                    cb.lessThan(root.get("expiresAt"), LocalDate.now()));
            return expired ? notNullAndPast : cb.not(notNullAndPast);
        };
    }
}
