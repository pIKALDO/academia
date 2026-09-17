package com.academia.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

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

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    static {
        POSTGRES.start();
    }
}
