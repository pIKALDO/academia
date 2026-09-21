package com.academia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.academia.support.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

/**
 * Verifica el nivel de privilegio de PostgreSQL en sí, no la capa de aplicación: se conecta
 * directamente con las credenciales del rol de aplicación (nunca con el datasource gestionado
 * por Spring, que en los tests corre como el rol propietario) y comprueba que
 * V6__app_role.sql deja `audit_log` realmente en solo lectura+inserción para ese rol.
 */
class AppRolePrivilegesIT extends AbstractIntegrationTest {

    @Test
    void el_rol_de_aplicacion_no_puede_modificar_audit_log() throws SQLException {
        try (Connection connection = conectarComoRolDeAplicacion()) {
            insertarFilaDePrueba(connection);

            try (Statement statement = connection.createStatement()) {
                assertThatThrownBy(() -> statement.execute("UPDATE audit_log SET action = 'X'"))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("permission denied");
            }

            try (Statement statement = connection.createStatement()) {
                assertThatThrownBy(() -> statement.execute("DELETE FROM audit_log"))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("permission denied");
            }
        }
    }

    @Test
    void el_rol_de_aplicacion_si_puede_leer_e_insertar_en_audit_log() throws SQLException {
        try (Connection connection = conectarComoRolDeAplicacion()) {
            insertarFilaDePrueba(connection);

            try (Statement statement = connection.createStatement()) {
                var resultSet = statement.executeQuery(
                        "SELECT COUNT(*) FROM audit_log WHERE action = 'TEST_PRIVILEGIOS'");
                resultSet.next();
                assertThat(resultSet.getInt(1)).isGreaterThan(0);
            }
        }
    }

    private void insertarFilaDePrueba(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO audit_log (action, entity_type) VALUES ('TEST_PRIVILEGIOS', 'TEST')");
        }
    }

    private Connection conectarComoRolDeAplicacion() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), APP_DB_USER, APP_DB_PASSWORD);
    }
}
