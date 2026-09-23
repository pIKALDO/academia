package com.academia.users;

import com.academia.common.audit.Audited;
import com.academia.common.error.ConflictException;
import com.academia.common.error.NotFoundException;
import com.academia.common.web.PagedResponse;
import com.academia.users.dto.CreateUserRequest;
import com.academia.users.dto.UpdateUserRequest;
import com.academia.users.dto.UserDetailDto;
import com.academia.users.dto.UserProfileDto;
import com.academia.users.dto.UserSummaryDto;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administración de usuarios (docs/diseno-api.md sección 5.2). Sin {@code delete}: borrar un
 * usuario dejaría huérfanos los registros de auditoría y la autoría de documentos; se
 * desactiva, que es lo que realmente hace falta.
 */
@Service
public class UserService {

    private static final Duration ACTIVATION_TOKEN_TTL = Duration.ofDays(7);

    private final UserRepository userRepository;
    private final TokenIssuer tokenIssuer;
    private final AuthMailService mailService;
    private final SessionRegistry sessionRegistry;

    UserService(UserRepository userRepository, TokenIssuer tokenIssuer, AuthMailService mailService,
            SessionRegistry sessionRegistry) {
        this.userRepository = userRepository;
        this.tokenIssuer = tokenIssuer;
        this.mailService = mailService;
        this.sessionRegistry = sessionRegistry;
    }

    @PreAuthorize("@access.canManageUsers()")
    @Audited(action = "USER_CREATED", entity = "USER")
    @Transactional
    public UserDetailDto create(CreateUserRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("Ya existe un usuario con ese email.");
        }

        UserEntity user = new UserEntity(request.email(), request.role(), request.displayName());
        userRepository.save(user);

        String rawToken = tokenIssuer.issue(user.getId(), ACTIVATION_TOKEN_TTL);
        mailService.sendActivationEmail(user, rawToken);

        return UserDetailDto.from(user);
    }

    @PreAuthorize("@access.canManageUsers()")
    @Transactional(readOnly = true)
    public PagedResponse<UserSummaryDto> list(UserRole role, UserStatus status, Pageable pageable) {
        return PagedResponse.from(userRepository.search(role, status, pageable), UserSummaryDto::from);
    }

    @PreAuthorize("@access.canManageUsers()")
    @Transactional(readOnly = true)
    public UserDetailDto get(UUID id) {
        return UserDetailDto.from(getEntityOrThrow(id));
    }

    @PreAuthorize("@access.canManageUsers()")
    @Audited(action = "USER_UPDATED", entity = "USER")
    @Transactional
    public UserDetailDto update(UUID id, UpdateUserRequest request) {
        UserEntity user = getEntityOrThrow(id);
        if (request.displayName() != null) {
            user.changeDisplayName(request.displayName());
        }
        if (request.role() != null) {
            user.changeRole(request.role());
        }
        return UserDetailDto.from(user);
    }

    @PreAuthorize("@access.canManageUsers()")
    @Audited(action = "USER_DISABLED", entity = "USER")
    @Transactional
    public UserDetailDto disable(UUID id) {
        UserEntity user = getEntityOrThrow(id);
        user.disable();
        // La sesión con cookie existe precisamente para poder revocar el acceso al instante
        // (docs/diseno-api.md sección 2.1): sin esto, una familia desactivada seguiría dentro
        // hasta que su sesión expirase sola.
        expireActiveSessions(user.getEmail());
        return UserDetailDto.from(user);
    }

    @PreAuthorize("@access.canManageUsers()")
    @Audited(action = "USER_ENABLED", entity = "USER")
    @Transactional
    public UserDetailDto enable(UUID id) {
        UserEntity user = getEntityOrThrow(id);
        user.enable();
        return UserDetailDto.from(user);
    }

    @Transactional
    void recordSuccessfulLogin(UUID id) {
        userRepository.findById(id).ifPresent(user -> user.recordLogin(Instant.now()));
    }

    @Transactional(readOnly = true)
    UserProfileDto getProfile(UUID id) {
        return UserProfileDto.from(getEntityOrThrow(id));
    }

    /**
     * Usado por {@code documents} para resolver {@code uploadedBy}/{@code reviewedBy}
     * (docs/diseno-api.md sección 5.6): solo lo mínimo que necesita esa vista, sin exponer
     * {@link UserRepository} —package-private— fuera de este módulo.
     */
    @Transactional(readOnly = true)
    public Optional<UserSummary> findSummary(UUID id) {
        return userRepository.findById(id).map(user -> new UserSummary(user.getId(), user.getDisplayName()));
    }

    public record UserSummary(UUID id, String displayName) {
    }

    private UserEntity getEntityOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado."));
    }

    private void expireActiveSessions(String email) {
        sessionRegistry.getAllPrincipals().stream()
                .filter(principal -> principal instanceof AcademiaUserPrincipal p
                        && p.getUsername().equalsIgnoreCase(email))
                .flatMap(principal -> sessionRegistry.getAllSessions(principal, false).stream())
                .forEach(SessionInformation::expireNow);
    }
}
