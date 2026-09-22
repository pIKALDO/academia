package com.academia.guardians;

import com.academia.common.audit.Audited;
import com.academia.common.error.ConflictException;
import com.academia.common.error.NotFoundException;
import com.academia.common.error.UnprocessableEntityException;
import com.academia.common.web.PagedResponse;
import com.academia.guardians.dto.CreateGuardianRequest;
import com.academia.guardians.dto.GuardianDto;
import com.academia.guardians.dto.UpdateGuardianRequest;
import com.academia.users.UserRole;
import com.academia.users.UserService;
import com.academia.users.dto.UserDetailDto;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tutores (docs/diseno-api.md sección 5.4). Solo ADMIN. */
@Service
public class GuardianService {

    private final GuardianRepository guardianRepository;
    private final UserService userService;

    GuardianService(GuardianRepository guardianRepository, UserService userService) {
        this.guardianRepository = guardianRepository;
        this.userService = userService;
    }

    @PreAuthorize("@access.canManageGuardians()")
    @Transactional(readOnly = true)
    public PagedResponse<GuardianDto> list(Pageable pageable) {
        return PagedResponse.from(guardianRepository.findAll(pageable), GuardianDto::from);
    }

    @PreAuthorize("@access.canManageGuardians()")
    @Audited(action = "GUARDIAN_CREATED", entity = "GUARDIAN")
    @Transactional
    public GuardianDto create(CreateGuardianRequest request) {
        GuardianEntity guardian = new GuardianEntity(request.firstName(), request.lastName());
        guardian.changePhone(request.phone());
        guardian.changeEmail(request.email());
        if (request.userId() != null) {
            requireAssignableAccount(request.userId());
            guardian.changeUserId(request.userId());
        }
        return GuardianDto.from(guardianRepository.saveAndFlush(guardian));
    }

    @PreAuthorize("@access.canManageGuardians()")
    @Transactional(readOnly = true)
    public GuardianDto get(UUID id) {
        return GuardianDto.from(getEntityOrThrow(id));
    }

    @PreAuthorize("@access.canManageGuardians()")
    @Audited(action = "GUARDIAN_UPDATED", entity = "GUARDIAN")
    @Transactional
    public GuardianDto update(UUID id, UpdateGuardianRequest request) {
        GuardianEntity guardian = getEntityOrThrow(id);
        if (request.userId() != null && !Objects.equals(request.userId(), guardian.getUserId())) {
            requireAssignableAccount(request.userId());
            guardian.changeUserId(request.userId());
        }
        if (request.firstName() != null) {
            guardian.changeFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            guardian.changeLastName(request.lastName());
        }
        if (request.phone() != null) {
            guardian.changePhone(request.phone());
        }
        if (request.email() != null) {
            guardian.changeEmail(request.email());
        }
        return GuardianDto.from(guardianRepository.saveAndFlush(guardian));
    }

    GuardianEntity getEntityOrThrow(UUID id) {
        return guardianRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Tutor no encontrado."));
    }

    /**
     * La cuenta enlazada decide qué estudiantes ve ese usuario ({@code StudentAccessSpecifications}):
     * tiene que existir, tener rol GUARDIAN y no estar ya enlazada a otro tutor. Enlazar una
     * cuenta ADMIN no daría más acceso del que ya tiene, pero sí dejaría el modelo en un estado
     * sin sentido.
     */
    private void requireAssignableAccount(UUID userId) {
        UserDetailDto user;
        try {
            user = userService.get(userId);
        } catch (NotFoundException e) {
            throw new UnprocessableEntityException("userId no corresponde a ninguna cuenta.");
        }
        if (user.role() != UserRole.GUARDIAN) {
            throw new UnprocessableEntityException("La cuenta enlazada a un tutor debe tener rol GUARDIAN.");
        }
        if (guardianRepository.existsByUserId(userId)) {
            throw new ConflictException("Esa cuenta ya está enlazada a otro tutor.");
        }
    }
}
