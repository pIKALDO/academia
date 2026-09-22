package com.academia.users;

import com.academia.common.web.PagedResponse;
import com.academia.common.web.PageRequestFactory;
import com.academia.users.dto.CreateUserRequest;
import com.academia.users.dto.UpdateUserRequest;
import com.academia.users.dto.UserDetailDto;
import com.academia.users.dto.UserSummaryDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
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

/**
 * Administración de usuarios, solo ADMIN (docs/diseno-api.md sección 5.2). La autorización se
 * resuelve en {@code UserService} vía {@code @access.canManageUsers()}, no aquí: este
 * controlador no decide nada de permisos (regla no negociable nº2).
 */
@Tag(name = "Usuarios")
@RestController
@RequestMapping("/api/v1/users")
class UserController {

    private final UserService userService;

    UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    PagedResponse<UserSummaryDto> list(
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequestFactory.of(page, size, Sort.by("email").ascending());
        return userService.list(role, status, pageable);
    }

    @PostMapping
    ResponseEntity<UserDetailDto> create(@Valid @RequestBody CreateUserRequest request,
            UriComponentsBuilder uriBuilder) {
        UserDetailDto created = userService.create(request);
        // El UriComponentsBuilder que inyecta Spring MVC parte del servlet mapping, no de la URL
        // de esta petición: solo trae esquema, host y puerto. La ruta va completa.
        URI location = uriBuilder.path("/api/v1/users/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    UserDetailDto get(@PathVariable UUID id) {
        return userService.get(id);
    }

    @PatchMapping("/{id}")
    UserDetailDto update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @PostMapping("/{id}/disable")
    ResponseEntity<Void> disable(@PathVariable UUID id) {
        userService.disable(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/enable")
    ResponseEntity<Void> enable(@PathVariable UUID id) {
        userService.enable(id);
        return ResponseEntity.noContent().build();
    }
}
