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

- Java 21 (LTS)
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

| Servicio   | URL                            | Credenciales                              |
|------------|---------------------------------|--------------------------------------------|
| PostgreSQL | `localhost:5432`, BD `academia` | propietario: `academia` / `academia_dev`   |
| PostgreSQL | `localhost:5432`, BD `academia` | aplicación: `academia_app` / `academia_app_dev` |
| MinIO      | http://localhost:9001           | `academia` / `academia_dev_minio`          |
| Mailpit    | http://localhost:8025           | (sin autenticación)                        |

Para cambiar estas credenciales sin tocar `docker-compose.yml`, copia `.env.example` a
`.env` en la raíz del repositorio.

**`.env` solo lo lee `docker compose`, no Maven.** El backend no carga `.env` por su cuenta:
las variables tienen que estar exportadas en el entorno del propio proceso que ejecuta
`./mvnw spring-boot:run`. Con los valores por defecto de `docker-compose.yml` esto no se nota
(`application-local.yml` ya trae esos mismos valores como fallback), pero `ADMIN_EMAIL` y
`ADMIN_PASSWORD` (ver siguiente sección) no tienen fallback a propósito, así que hace falta
exportarlas antes de arrancar:

```bash
set -a && source .env && set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Primer ADMIN

Con la base de datos vacía no hay ninguna cuenta con la que entrar, y crear usuarios exige ya
ser ADMIN. En el perfil `local`, si la tabla `users` no tiene ningún ADMIN, el backend crea uno
al arrancar (`LocalAdminBootstrapper`) con el email y la contraseña de `ADMIN_EMAIL` /
`ADMIN_PASSWORD` (ver `.env.example`) — nunca con un valor fijo en el código. Solo pasa una vez:
si ya existe un ADMIN, no hace nada. No corre fuera del perfil `local`.

## Dos roles de base de datos

PostgreSQL tiene dos roles, no uno:

- **`academia`** (propietario): el único que ejecuta las migraciones de Flyway. Es dueño de
  todas las tablas.
- **`academia_app`** (aplicación): el único con el que se conecta el backend en tiempo de
  ejecución (`spring.datasource.*`). No tiene propiedad sobre ninguna tabla, y
  `V6__app_role.sql` le revoca explícitamente `UPDATE`/`DELETE` sobre `audit_log`.

**Por qué dos roles y no uno.** En PostgreSQL, el propietario de una tabla puede modificarla
al margen de sus propios `GRANT`/`REVOKE`: si Flyway y la aplicación compartieran el mismo
rol, el `REVOKE UPDATE, DELETE` sobre `audit_log` sería decorativo, no una restricción real.
Separando ambos roles, la inmutabilidad del registro de auditoría la hace cumplir PostgreSQL
mismo, no la disciplina de quien escriba el código de negocio. `AppRolePrivilegesIT` lo
verifica conectándose directamente como `academia_app`.

**Quién crea el rol de aplicación y quién le da permisos.** Son dos ficheros distintos, a
propósito: `backend/db/init/01-create-app-role.sh` (montado en
`docker-entrypoint-initdb.d`, se ejecuta una sola vez al crear el contenedor) crea el rol con
su contraseña; `V6__app_role.sql` (versionado con Flyway) le concede los privilegios. La
contraseña nunca pasa por una migración: aunque Flyway soporta sustituir placeholders, ese
valor sigue quedando fijado en el fichero `.sql` ejecutado contra la base de datos, y un
secreto no debería depender de que nadie mire el histórico de Flyway para encontrarlo.

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
