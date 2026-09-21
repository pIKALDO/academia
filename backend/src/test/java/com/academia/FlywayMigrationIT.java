package com.academia;

import static org.assertj.core.api.Assertions.assertThat;

import com.academia.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifica que Flyway aplica limpiamente las seis migraciones contra un PostgreSQL 17 real
 * y que el esquema resultante contiene exactamente las tablas de fase 1
 * (docs/modelo-datos.md sección 8). La sexta (V6) no añade tablas: crea los privilegios del
 * rol de aplicación (ver AppRolePrivilegesIT).
 */
class FlywayMigrationIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void aplica_las_seis_migraciones_sin_fallos() {
        List<String> versionesAplicadas = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank",
                String.class);

        assertThat(versionesAplicadas).containsExactly("1", "2", "3", "4", "5", "6");
    }

    @Test
    void crea_las_tablas_del_alcance_de_fase_1() {
        List<String> tablas = jdbcTemplate.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
                ORDER BY table_name
                """, String.class);

        assertThat(tablas).containsExactlyInAnyOrder(
                "users", "password_reset_tokens",
                "students", "guardians", "student_guardians", "emergency_contacts",
                "sports_profiles", "education_info", "housing_info",
                "documents", "document_notifications",
                "audit_log");
    }
}
