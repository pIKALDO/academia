package com.academia.students;

import com.academia.common.web.PageRequestFactory;
import com.academia.common.web.PagedResponse;
import com.academia.students.dto.CreateEmergencyContactRequest;
import com.academia.students.dto.EmergencyContactAdminDto;
import com.academia.students.dto.EmergencyContactDto;
import com.academia.students.dto.UpdateEmergencyContactRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Contactos de emergencia (docs/diseno-api.md sección 5.5): anidados para listar y crear,
 * planos para modificar y borrar (sección 1.2).
 */
@Tag(name = "Contactos de emergencia")
@RestController
@RequestMapping("/api/v1")
class EmergencyContactController {

    private final EmergencyContactService emergencyContactService;

    EmergencyContactController(EmergencyContactService emergencyContactService) {
        this.emergencyContactService = emergencyContactService;
    }

    @GetMapping("/students/{studentId}/emergency-contacts")
    PagedResponse<EmergencyContactDto> listEmergencyContacts(
            @PathVariable UUID studentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Sort sort = Sort.by("priority").ascending().and(Sort.by("name").ascending());
        return emergencyContactService.list(studentId, PageRequestFactory.of(page, size, sort));
    }

    @PostMapping("/students/{studentId}/emergency-contacts")
    ResponseEntity<EmergencyContactAdminDto> createEmergencyContact(@PathVariable UUID studentId,
            @Valid @RequestBody CreateEmergencyContactRequest request, UriComponentsBuilder uriBuilder) {
        EmergencyContactAdminDto created = emergencyContactService.create(studentId, request);
        // El recurso creado vive en la URL plana, no bajo el estudiante (sección 1.2).
        // uriBuilder solo trae esquema, host y puerto: la ruta completa va explícita.
        URI location = uriBuilder.path("/api/v1/emergency-contacts/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PatchMapping("/emergency-contacts/{id}")
    EmergencyContactAdminDto updateEmergencyContact(@PathVariable UUID id, @Valid @RequestBody UpdateEmergencyContactRequest request) {
        return emergencyContactService.update(id, request);
    }

    @DeleteMapping("/emergency-contacts/{id}")
    ResponseEntity<Void> deleteEmergencyContact(@PathVariable UUID id) {
        emergencyContactService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
