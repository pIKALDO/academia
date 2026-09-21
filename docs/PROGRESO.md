# Progreso

Estado del proyecto al retomarlo en una máquina nueva (2026-09-21).

## Cortes terminados

### Corte 0 — esqueleto, seguridad, migraciones, auditoría

Incluye:

- Esqueleto Spring Boot (`AcademiaApplication`), configuración común (`application.yml`,
  `application-local.yml`).
- Seguridad base: `SecurityConfig`, cookie de sesión `SESSION` (`HttpOnly`, `SameSite=Strict`,
  `secure` condicionado por perfil).
- Manejo de errores centralizado: `GlobalExceptionHandler` con `ProblemDetail`
  (`NotFoundException`, `ConflictException`).
- Paginación propia: `PagedResponse<T>`, `PageRequestFactory`.
- Auditoría por aspecto: `AuditAspect` + `AuditLogWriter`, anotación `@Audited`.
- `AccessService` / `StudentAccessRepository` (base de la resolución de permisos; aún sin las
  reglas de negocio de corte 1 en adelante).
- **6 migraciones Flyway**, no 5:
  - `V1__baseline_users.sql`
  - `V2__students_and_guardians.sql`
  - `V3__profile_blocks.sql`
  - `V4__documents.sql`
  - `V5__audit_log.sql`
  - `V6__app_role.sql` — separación del rol de aplicación (`academia_app`) del propietario
    (`academia`). Ver "Decisiones tomadas" más abajo.
- Dos roles de PostgreSQL en desarrollo local: el propietario ejecuta Flyway, el rol de
  aplicación (sin propiedad sobre las tablas) es el único con el que corre el backend en
  tiempo de ejecución. El rol se crea en `backend/db/init/01-create-app-role.sh` (montado
  tanto en `docker-compose.yml` como en Testcontainers); sus privilegios los gestiona `V6`.
- Tests: `AcademiaApplicationTests`, `AppRolePrivilegesIT`, `FlywayMigrationIT`,
  `AuditAspectIT`, `GlobalExceptionHandlerTest`, `PagedResponseTest`, `SecurityConfigIT`.

**Verificado en esta máquina:** `./mvnw verify` pasa en verde completo. Los tests `*IT` usan
Testcontainers (PostgreSQL efímero propio), así que no dependen de `docker-compose.yml` ni de
`.env` para pasar — solo necesitan Docker corriendo.

## Corte actual y siguiente paso

**Corte actual:** ninguno en marcha. Corte 0 cerrado.

**Siguiente:** Corte 1 — usuarios y autenticación. No se ha empezado (ni entidades `User`, ni
`AuthController`, ni lógica de login/sesión más allá del esqueleto de `SecurityConfig`).

Primer paso concreto cuando se arranque: implementar `POST /auth/login` +
`GET /auth/me` sobre la tabla `users` ya migrada en `V1`, con `AuthenticationManager` propio
sustituyendo el `UserDetailsService` en memoria que genera Spring Boot por defecto (visible en
los logs de arranque: "Using generated security password...").

## Decisiones tomadas que no están en los documentos de diseño

- **`docs/modelo-datos.md` sección 9 lista solo `V1`–`V5`.** El repositorio ya tiene `V6`
  (separación de rol de aplicación con `ALTER DEFAULT PRIVILEGES`). El documento de diseño no
  se ha actualizado para reflejar esta migración adicional — no contradice ninguna decisión,
  simplemente es posterior a la última revisión del documento.
- **Separación de roles de PostgreSQL no estaba en el modelo de datos original.** Se añadió
  para que el `REVOKE UPDATE, DELETE` sobre `audit_log` (documentado en `V5`/sección 6 de
  `modelo-datos.md`) tenga efecto real: un propietario de tabla puede saltarse sus propios
  `GRANT`/`REVOKE`, así que la restricción de solo-inserción solo es efectiva si el backend
  corre con un rol distinto al propietario.
- El rol de aplicación lo crea la infraestructura (script de init de Docker en local; el
  proveedor de base de datos en servidor), nunca una migración de Flyway, porque su contraseña
  es secreto y no debe pasar por un fichero versionado.

## Pendientes conocidos

- **Ya resuelto, no pendiente:** `ALTER DEFAULT PRIVILEGES` para `academia_app` — está en
  `V6__app_role.sql` (tablas y secuencias), contra lo que podría sugerir el contexto previo de
  la conversación.
- **`.env` real pendiente de traer de la otra máquina.** No se ha generado uno en esta sesión
  a petición explícita. Queda por comprobar en esta máquina, una vez esté el `.env`:
  - `docker compose up -d` con las credenciales reales (se probó una vez con los valores por
    defecto de `.env.example` y funcionó correctamente: los tres servicios — postgres, minio,
    mailpit — arrancan sanos y Flyway valida las 6 migraciones contra ellos).
  - `mvn spring-boot:run -Dspring-boot.run.profiles=local` arrancando contra esos servicios
    (a diferencia de `verify`, esto sí depende del `.env`).
- Perfil de servidor (mencionado como "aún no creado" en el comentario de
  `application.yml`) no existe todavía — se añadirá cuando toque desplegar contra
  Cloudflare R2 y la base de datos de producción.
- `frontend/` no existe todavía en el repositorio (no incluido en el alcance del corte 0).
