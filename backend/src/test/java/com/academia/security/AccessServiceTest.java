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
 * núcleo del proyecto. Aquí sin base de datos: {@link StudentAccessRepository} es una lambda
 * que dice qué estudiante es "del tutor". Que la consulta real responda lo mismo lo cubre
 * {@code StudentAccessIT}.
 */
class AccessServiceTest {

    private final UUID suHijo = UUID.randomUUID();
    private final UUID ajeno = UUID.randomUUID();
    private final UUID documentoDeSuHijo = UUID.randomUUID();
    private final UUID documentoAjeno = UUID.randomUUID();
    private final UUID documentoInexistente = UUID.randomUUID();

    /** El tutor autenticado solo tiene acceso a {@link #suHijo}. */
    private final AccessService access = new AccessService(
            (studentId, guardianUserId) -> studentId.equals(suHijo),
            documentId -> {
                if (documentId.equals(documentoDeSuHijo)) {
                    return Optional.of(suHijo);
                }
                if (documentId.equals(documentoAjeno)) {
                    return Optional.of(ajeno);
                }
                return Optional.empty();
            });

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
        assertThat(access.canUploadDocument(suHijo)).isEqualTo(StudentAccessDecision.FORBIDDEN);
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
        assertThat(access.canUploadDocument(ajeno)).isEqualTo(StudentAccessDecision.GRANTED);
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

        assertThat(access.canUploadDocument(suHijo)).isEqualTo(StudentAccessDecision.GRANTED);
    }

    /** Ajeno → oculto (404), no prohibido: mismo criterio que ver al estudiante (regla nº1). */
    @Test
    void a_un_tutor_se_le_oculta_la_subida_a_un_estudiante_ajeno() {
        autenticarComo(UserRole.GUARDIAN);

        StudentAccessDecision decision = access.canUploadDocument(ajeno);

        assertThat(decision.isGranted()).isFalse();
        assertThat(decision.isHidden()).isTrue();
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
        assertThat(access.canUploadDocument(suHijo)).isEqualTo(StudentAccessDecision.FORBIDDEN);
        assertThat(access.canReviewDocument(UUID.randomUUID())).isFalse();
        assertThat(access.canManageUsers()).isFalse();
    }

    @Test
    void admin_ve_cualquier_documento_puede_borrarlo_editarlo_y_ver_los_que_caducan() {
        autenticarComo(UserRole.ADMIN);

        assertThat(access.canViewDocument(documentoAjeno)).isEqualTo(StudentAccessDecision.GRANTED);
        assertThat(access.canDeleteDocument(documentoAjeno)).isTrue();
        assertThat(access.canEditDocument(documentoAjeno)).isTrue();
        assertThat(access.canViewExpiringDocuments()).isTrue();
    }

    @Test
    void tutor_ve_el_documento_de_su_hijo_pero_no_puede_borrarlo_ni_editarlo_ni_ver_los_que_caducan() {
        autenticarComo(UserRole.GUARDIAN);

        assertThat(access.canViewDocument(documentoDeSuHijo)).isEqualTo(StudentAccessDecision.GRANTED);
        assertThat(access.canDeleteDocument(documentoDeSuHijo)).isFalse();
        assertThat(access.canEditDocument(documentoDeSuHijo)).isFalse();
        assertThat(access.canViewExpiringDocuments()).isFalse();
    }

    /** Documento de un estudiante ajeno → oculto (404), no prohibido (regla no negociable nº1). */
    @Test
    void a_un_tutor_se_le_oculta_el_documento_de_un_estudiante_ajeno() {
        autenticarComo(UserRole.GUARDIAN);

        StudentAccessDecision decision = access.canViewDocument(documentoAjeno);

        assertThat(decision.isGranted()).isFalse();
        assertThat(decision.isHidden()).isTrue();
    }

    @Test
    void un_documento_inexistente_tambien_se_oculta() {
        autenticarComo(UserRole.GUARDIAN);

        assertThat(access.canViewDocument(documentoInexistente)).isEqualTo(StudentAccessDecision.HIDDEN);
    }

    private void autenticarComo(UserRole role) {
        UserEntity user = new UserEntity(role.name().toLowerCase() + "@example.com", role, "Usuario de prueba");
        AcademiaUserPrincipal principal = AcademiaUserPrincipal.of(user);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
