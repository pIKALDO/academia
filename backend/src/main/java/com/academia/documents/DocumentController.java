package com.academia.documents;

import com.academia.common.web.PageRequestFactory;
import com.academia.common.web.PagedResponse;
import com.academia.documents.dto.DocumentDto;
import com.academia.documents.dto.UpdateDocumentRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Documentación (docs/diseno-api.md sección 5.6): anidada para listar y subir, plana para leer,
 * modificar, descargar, revisar y borrar (sección 1.2).
 */
@Tag(name = "Documentos")
@RestController
@RequestMapping("/api/v1")
class DocumentController {

    private final DocumentService documentService;

    DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/students/{studentId}/documents")
    PagedResponse<DocumentDto> listDocuments(
            @PathVariable UUID studentId,
            @RequestParam(required = false) DocumentCategory category,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) LocalDate expiringBefore,
            @RequestParam(required = false) Boolean expired,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Sort sort = Sort.by("expiresAt").ascending();
        return documentService.list(studentId, category, status, expiringBefore, expired,
                PageRequestFactory.of(page, size, sort));
    }

    @PostMapping(path = "/students/{studentId}/documents", consumes = "multipart/form-data")
    ResponseEntity<DocumentDto> uploadDocument(
            @PathVariable UUID studentId,
            @RequestParam DocumentCategory category,
            @RequestParam String name,
            @RequestParam(required = false) LocalDate issuedAt,
            @RequestParam(required = false) LocalDate expiresAt,
            @RequestPart MultipartFile file,
            UriComponentsBuilder uriBuilder) {
        DocumentDto created = documentService.upload(studentId, category, name, issuedAt, expiresAt, file);
        // El recurso creado vive en la URL plana, no bajo el estudiante (sección 1.2).
        URI location = uriBuilder.path("/api/v1/documents/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/documents/{id}")
    DocumentDto getDocument(@PathVariable UUID id) {
        return documentService.get(id);
    }

    @GetMapping("/documents/{id}/download")
    ResponseEntity<Void> downloadDocument(@PathVariable UUID id) {
        URI presignedUrl = documentService.download(id);
        return ResponseEntity.status(HttpStatus.FOUND).location(presignedUrl).build();
    }

    @PatchMapping("/documents/{id}")
    DocumentDto updateDocument(@PathVariable UUID id, @Valid @RequestBody UpdateDocumentRequest request) {
        return documentService.update(id, request);
    }

    @PostMapping("/documents/{id}/review")
    DocumentDto reviewDocument(@PathVariable UUID id) {
        return documentService.review(id);
    }

    @DeleteMapping("/documents/{id}")
    ResponseEntity<Void> deleteDocument(@PathVariable UUID id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/documents/expiring")
    PagedResponse<DocumentDto> expiringDocuments(
            @RequestParam(required = false) DocumentCategory category,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Sort sort = Sort.by("expiresAt").ascending();
        return documentService.expiring(category, status, PageRequestFactory.of(page, size, sort));
    }
}
