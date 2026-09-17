package com.academia.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.academia.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifica de extremo a extremo que un método anotado con {@link Audited} deja su rastro en
 * `audit_log`, incluyendo que la escritura sobrevive en una transacción propia
 * (docs/modelo-datos.md sección 6).
 */
@Import(AuditAspectIT.Config.class)
class AuditAspectIT extends AbstractIntegrationTest {

    @Autowired
    private ServicioDePruebaAuditado servicio;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void un_metodo_anotado_registra_una_fila_en_audit_log() {
        UUID studentId = UUID.randomUUID();

        servicio.accionSobreEstudiante(studentId);

        Integer filas = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = ? AND entity_type = ? AND student_id = ?",
                Integer.class, "TEST_ACTION", "TEST_ENTITY", studentId);

        assertThat(filas).isEqualTo(1);
    }

    @Test
    void un_metodo_que_lanza_excepcion_no_deja_rastro() {
        UUID studentId = UUID.randomUUID();

        try {
            servicio.accionQueFalla(studentId);
        } catch (IllegalStateException expected) {
            // ignorado a propósito: lo que interesa es que no se audite
        }

        Integer filas = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE student_id = ?", Integer.class, studentId);

        assertThat(filas).isZero();
    }

    static class ServicioDePruebaAuditado {

        @Audited(action = "TEST_ACTION", entity = "TEST_ENTITY", studentIdParam = "studentId")
        void accionSobreEstudiante(UUID studentId) {
        }

        @Audited(action = "TEST_ACTION_FALLIDA", entity = "TEST_ENTITY", studentIdParam = "studentId")
        void accionQueFalla(UUID studentId) {
            throw new IllegalStateException("fallo esperado en el test");
        }
    }

    @TestConfiguration
    static class Config {

        @Bean
        ServicioDePruebaAuditado servicioDePruebaAuditado() {
            return new ServicioDePruebaAuditado();
        }
    }
}
