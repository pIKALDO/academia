package com.academia.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.academia.users.AcademiaUserPrincipal;
import com.academia.users.UserEntity;
import com.academia.users.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Cada regla de {@link AccessService} tiene su test (docs/diseno-api.md sección 3.2): es el
 * núcleo del proyecto. Aquí sin base de datos: {@link StudentAccessRepository} es una lambda
 * que dice qué estudiante es "del tutor". Que la consulta real responda lo mismo lo cubre
 * {@code StudentAccessIT}.
 */
class AccessServiceTest {

    private final UUID suHijo = UUID.randomUUID();
    private final UUID ajeno = UUID.randomUUID();

    /** El tutor autenticado solo tiene acceso a {@link #suHijo}. */
    private final AccessService access =
            new AccessService((studentId, guardianUserId) -> studentId.equals(suHijo));

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sin_autenticacion_todas_las_reglas_deniegan_sin_lanzar() {
        assertThat(access.canViewStudent(suHijo)).isEqualTo(StudentAccessDecision.FORBIDDEN);
        assertThat(access.canListStudents()).isFalse();
        assertThat(access.canSeeInternalStudentData()).isFalse();
        assertThat(access.canCreateStudent()).isFalse();
        assertThat(access.canEditStudent(suHijo)).isFalse();
        assertThat(access.canEditEmergencyContact(UUID.randomUUID())).isFalse();
        assertThat(access.canManageGuardians()).isFalse();
        assertThat(access.canUploadDocument(suHijo)).isFalse();
        assertThat(access.canReviewDocument(suHijo)).isFalse();
        assertThat(access.canManageUsers()).isFalse();
    }

    @Test
    void admin_ve_cualquier_estudiante_sin_consultar_vinculos() {
        autenticarComo(UserRole.ADMIN);

        assertThat(access.canViewStudent(ajeno)).isEqualTo(StudentAccessDecision.GRANTED);
        assertThat(access.canListStudents()).isTrue();
    }

    @Test
    void admin_recibe_la_vista_interna_y_puede_gestionarlo_todo() {
        autenticarComo(UserRole.ADMIN);

        assertThat(access.canSeeInternalStudentData()).isTrue();
        assertThat(access.canCreateStudent()).isTrue();
        assertThat(access.canEditStudent(ajeno)).isTrue();
        assertThat(access.canEditEmergencyContact(UUID.randomUUID())).isTrue();
        assertThat(access.canManageGuardians()).isTrue();
        assertThat(access.canUploadDocument(ajeno)).isTrue();
        assertThat(access.canReviewDocument(UUID.randomUUID())).isTrue();
        assertThat(access.canManageUsers()).isTrue();
    }

    @Test
    void tutor_ve_a_su_hijo() {
        autenticarComo(UserRole.GUARDIAN);

        assertThat(access.canViewStudent(suHijo)).isEqualTo(StudentAccessDecision.GRANTED);
    }

    /** Oculto, no prohibido: se traduce en 404, no en 403 (regla no negociable nº1). */
    @Test
    void a_un_tutor_se_le_oculta_el_estudiante_ajeno() {
        autenticarComo(UserRole.GUARDIAN);

        StudentAccessDecision decision = access.canViewStudent(ajeno);

        assertThat(decision.isGranted()).isFalse();
        assertThat(decision.isHidden()).isTrue();
    }

    @Test
    void tutor_puede_listar_pero_no_recibe_la_vista_interna() {
        autenticarComo(UserRole.GUARDIAN);

        assertThat(access.canListStudents()).isTrue();
        assertThat(access.canSeeInternalStudentData()).isFalse();
    }

    @Test
    void tutor_no_puede_crear_ni_editar_ni_siquiera_a_su_propio_hijo() {
        autenticarComo(UserRole.GUARDIAN);

        assertThat(access.canCreateStudent()).isFalse();
        assertThat(access.canEditStudent(suHijo)).isFalse();
        assertThat(access.canEditEmergencyContact(UUID.randomUUID())).isFalse();
        assertThat(access.canManageGuardians()).isFalse();
        assertThat(access.canReviewDocument(UUID.randomUUID())).isFalse();
        assertThat(access.canManageUsers()).isFalse();
    }

    @Test
    void tutor_solo_puede_subir_documentos_de_su_hijo() {
        autenticarComo(UserRole.GUARDIAN);

        assertThat(access.canUploadDocument(suHijo)).isTrue();
        assertThat(access.canUploadDocument(ajeno)).isFalse();
    }

    /**
     * STUDENT no tiene acceso al módulo en fase 1: prohibido (403), no oculto, y sin mirar
     * el id. Por eso la respuesta es la misma con cualquier id y no revela nada.
     */
    @Test
    void estudiante_tiene_prohibido_el_modulo_sea_cual_sea_el_id() {
        autenticarComo(UserRole.STUDENT);

        assertThat(access.canViewStudent(suHijo)).isEqualTo(StudentAccessDecision.FORBIDDEN);
        assertThat(access.canViewStudent(ajeno)).isEqualTo(StudentAccessDecision.FORBIDDEN);
        assertThat(access.canListStudents()).isFalse();
        assertThat(access.canSeeInternalStudentData()).isFalse();
    }

    @Test
    void estudiante_no_puede_crear_editar_subir_revisar_ni_gestionar() {
        autenticarComo(UserRole.STUDENT);

        assertThat(access.canCreateStudent()).isFalse();
        assertThat(access.canEditStudent(suHijo)).isFalse();
        assertThat(access.canEditEmergencyContact(UUID.randomUUID())).isFalse();
        assertThat(access.canManageGuardians()).isFalse();
        assertThat(access.canUploadDocument(suHijo)).isFalse();
        assertThat(access.canReviewDocument(UUID.randomUUID())).isFalse();
        assertThat(access.canManageUsers()).isFalse();
    }

    private void autenticarComo(UserRole role) {
        UserEntity user = new UserEntity(role.name().toLowerCase() + "@example.com", role, "Usuario de prueba");
        AcademiaUserPrincipal principal = AcademiaUserPrincipal.of(user);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
