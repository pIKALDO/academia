# Academia — plataforma de gestión

Plataforma privada de gestión para una academia de tenis: fichas de estudiantes,
documentación con control de caducidades y acceso diferenciado para administración y
familias.

**Prototipo con datos inventados.** No contiene ni debe contener datos reales de ninguna
persona.

## Documentación

- [`CLAUDE.md`](CLAUDE.md) — organización del repositorio, stack, reglas no negociables y
  convenciones
- [`docs/modelo-datos.md`](docs/modelo-datos.md) — esquema completo de base de datos, con la
  justificación de cada decisión
- [`docs/diseno-api.md`](docs/diseno-api.md) — contrato de la API: endpoints, DTOs por rol,
  códigos de estado

Estos documentos son la fuente de verdad del proyecto.

## Requisitos

- Java 21 (ver "Decisiones pendientes de revisar" más abajo)
- Docker y Docker Compose
- Node.js (para `frontend/`, todavía no creado)

No hace falta tener Maven instalado: el proyecto usa Maven Wrapper (`./mvnw`).

## Arrancar en local

```bash
# 1. Infraestructura: PostgreSQL, MinIO y Mailpit
docker compose up -d

# 2. Backend
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

La API queda escuchando en `http://localhost:8080`. Documentación interactiva en
`http://localhost:8080/swagger-ui.html`.

Consolas de la infraestructura local:

| Servicio   | URL                            | Credenciales                        |
|------------|---------------------------------|--------------------------------------|
| PostgreSQL | `localhost:5432`, BD `academia` | `academia` / `academia_dev`          |
| MinIO      | http://localhost:9001           | `academia` / `academia_dev_minio`    |
| Mailpit    | http://localhost:8025           | (sin autenticación)                  |

Para cambiar estas credenciales sin tocar `docker-compose.yml`, copia `.env.example` a
`.env` en la raíz del repositorio.

## Tests

```bash
cd backend
./mvnw test      # unitarios (*Test)
./mvnw verify    # unitarios + integración con Testcontainers (*IT), requiere Docker
```

## Estado del proyecto

Construido en cortes verticales; cada uno se cierra con tests en verde antes de empezar el
siguiente.

- [x] Corte 0 — esqueleto ejecutable: infraestructura, migraciones, seguridad base,
      manejo de errores, paginación y auditoría.

### Decisiones pendientes de revisar

- **Java 21, no Java 25.** `CLAUDE.md` fija Java 25, pero la máquina de desarrollo solo
  tiene JDK 21 instalado. Se decidió seguir con 21 por ahora; hay que revisar el pom.xml
  (`java.version`) y este workflow de CI en cuanto se instale el JDK 25.
- **Inmutabilidad de `audit_log` no reforzada de verdad.** La migración `V5` revoca
  `UPDATE`/`DELETE`, pero el rol que ejecuta Flyway es también el propietario de la tabla, y
  un propietario puede modificarla al margen de sus propios `GRANT`/`REVOKE`. Hace falta un
  segundo rol de aplicación, sin propiedad sobre las tablas, para que la restricción sea
  real (detalle en el comentario de `V5__audit_log.sql`).
