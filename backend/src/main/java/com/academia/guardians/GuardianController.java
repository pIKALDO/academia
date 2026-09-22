package com.academia.guardians;

import com.academia.common.web.PageRequestFactory;
import com.academia.common.web.PagedResponse;
import com.academia.guardians.dto.CreateGuardianRequest;
import com.academia.guardians.dto.GuardianDto;
import com.academia.guardians.dto.UpdateGuardianRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** Tutores, solo ADMIN (docs/diseno-api.md sección 5.4). */
@Tag(name = "Tutores")
@RestController
@RequestMapping("/api/v1/guardians")
class GuardianController {

    private final GuardianService guardianService;

    GuardianController(GuardianService guardianService) {
        this.guardianService = guardianService;
    }

    @GetMapping
    PagedResponse<GuardianDto> listGuardians(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return guardianService.list(PageRequestFactory.of(page, size, Sort.by("lastName", "firstName").ascending()));
    }

    @PostMapping
    ResponseEntity<GuardianDto> createGuardian(@Valid @RequestBody CreateGuardianRequest request,
            UriComponentsBuilder uriBuilder) {
        GuardianDto created = guardianService.create(request);
        URI location = uriBuilder.path("/api/v1/guardians/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    GuardianDto getGuardian(@PathVariable UUID id) {
        return guardianService.get(id);
    }

    @PatchMapping("/{id}")
    GuardianDto updateGuardian(@PathVariable UUID id, @Valid @RequestBody UpdateGuardianRequest request) {
        return guardianService.update(id, request);
    }
}
