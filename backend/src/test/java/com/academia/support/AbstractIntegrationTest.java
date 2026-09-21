package com.academia.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Base para los tests de integración: levanta un único PostgreSQL 17 real, compartido por
 * toda la suite, y deja que Flyway aplique las migraciones contra él. No se usa H2: el
 * esquema usa tipos ENUM, JSONB e INET que H2 no reproduce.
 *
 * Contenedor "singleton" gestionado a mano (arranque en bloque estático, sin `@Testcontainers`
 * ni `@Container`): con esas anotaciones, JUnit para el contenedor al terminar la PRIMERA
 * clase de test que lo usa, y las siguientes clases heredan una referencia ya cerrada al
 * mismo campo estático. Ryuk se encarga de pararlo al final de la JVM.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    /**
     * Deben coincidir con los valores por defecto de application.yml
     * ({@code spring.flyway.placeholders.appDbUser}) y de application-local.yml: aquí no hay
     * variables de entorno de por medio, así que si estos literales cambian, el rol que crea
     * este contenedor y el que V6__app_role.sql espera encontrar dejan de coincidir.
     */
    public static final String APP_DB_USER = "academia_app";
    public static final String APP_DB_PASSWORD = "academia_app_dev";

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
            .withEnv("POSTGRES_APP_USER", APP_DB_USER)
            .withEnv("POSTGRES_APP_PASSWORD", APP_DB_PASSWORD)
            // Mismo script que monta docker-compose.yml en local, para no mantener dos
            // copias de la lógica que crea el rol de aplicación.
            .withCopyFileToContainer(
                    MountableFile.forHostPath("db/init/01-create-app-role.sh", 0755),
                    "/docker-entrypoint-initdb.d/01-create-app-role.sh");

    static {
        POSTGRES.start();
    }
}
