package com.academia.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.academia.users.AcademiaUserPrincipal;
import com.academia.users.UserEntity;
import com.academia.users.UserRole;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Cada regla de {@link AccessService} tiene su test (docs/diseno-api.md sección 3.2): es el
 * núcleo del proyecto. Este corte cubre en particular que, sin {@link StudentAccessRepository}
 * todavía implementado, las reglas que dependen de él deniegan en lugar de lanzar.
 */
class AccessServiceTest {

    private final UUID studentId = UUID.randomUUID();

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sin_autenticacion_todas_las_reglas_deniegan_sin_lanzar() {
        AccessService access = new AccessService(Optional.empty());

        assertThat(access.canViewStudent(studentId)).isFalse();
        assertThat(access.canEditStudent(studentId)).isFalse();
        assertThat(access.canUploadDocument(studentId)).isFalse();
        assertThat(access.canReviewDocument(studentId)).isFalse();
        assertThat(access.canManageUsers()).isFalse();
    }

    @Test
    void admin_puede_ver_editar_y_gestionar_todo_sin_necesitar_el_repositorio_de_estudiantes() {
        autenticarComo(UserRole.ADMIN);
        AccessService access = new AccessService(Optional.empty());

        assertThat(access.canViewStudent(studentId)).isTrue();
        assertThat(access.canEditStudent(studentId)).isTrue();
        assertThat(access.canUploadDocument(studentId)).isTrue();
        assertThat(access.canReviewDocument(studentId)).isTrue();
        assertThat(access.canManageUsers()).isTrue();
    }

    @Test
    void guardian_no_puede_ver_ni_subir_documentos_de_un_estudiante_si_no_hay_repositorio_implementado() {
        autenticarComo(UserRole.GUARDIAN);
        AccessService access = new AccessService(Optional.empty());

        assertThat(access.canViewStudent(studentId)).isFalse();
        assertThat(access.canUploadDocument(studentId)).isFalse();
    }

    @Test
    void guardian_no_puede_editar_estudiantes_ni_revisar_documentos_ni_gestionar_usuarios() {
        autenticarComo(UserRole.GUARDIAN);
        AccessService access = new AccessService(Optional.empty());

        assertThat(access.canEditStudent(studentId)).isFalse();
        assertThat(access.canReviewDocument(studentId)).isFalse();
        assertThat(access.canManageUsers()).isFalse();
    }

    @Test
    void student_no_puede_subir_documentos_editar_ni_revisar_ni_gestionar_usuarios() {
        autenticarComo(UserRole.STUDENT);
        AccessService access = new AccessService(Optional.empty());

        assertThat(access.canUploadDocument(studentId)).isFalse();
        assertThat(access.canEditStudent(studentId)).isFalse();
        assertThat(access.canReviewDocument(studentId)).isFalse();
        assertThat(access.canManageUsers()).isFalse();
    }

    @Test
    void guardian_puede_ver_el_estudiante_cuando_el_repositorio_dice_que_tiene_acceso() {
        autenticarComo(UserRole.GUARDIAN);
        StudentAccessRepository repositorioConAcceso = new StudentAccessRepository() {
            @Override
            public boolean isAccessibleByGuardian(UUID sid, UUID guardianUserId) {
                return sid.equals(studentId);
            }

            @Override
            public boolean isOwnStudent(UUID sid, UUID studentUserId) {
                return false;
            }
        };
        AccessService access = new AccessService(Optional.of(repositorioConAcceso));

        assertThat(access.canViewStudent(studentId)).isTrue();
        assertThat(access.canViewStudent(UUID.randomUUID())).isFalse();
    }

    @Test
    void student_solo_puede_ver_su_propio_estudiante_cuando_el_repositorio_lo_confirma() {
        autenticarComo(UserRole.STUDENT);
        StudentAccessRepository repositorioPropio = new StudentAccessRepository() {
            @Override
            public boolean isAccessibleByGuardian(UUID sid, UUID guardianUserId) {
                return false;
            }

            @Override
            public boolean isOwnStudent(UUID sid, UUID studentUserId) {
                return sid.equals(studentId);
            }
        };
        AccessService access = new AccessService(Optional.of(repositorioPropio));

        assertThat(access.canViewStudent(studentId)).isTrue();
        assertThat(access.canViewStudent(UUID.randomUUID())).isFalse();
    }

    private void autenticarComo(UserRole role) {
        UserEntity user = new UserEntity(role.name().toLowerCase() + "@example.com", role, "Usuario de prueba");
        AcademiaUserPrincipal principal = AcademiaUserPrincipal.of(user);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
