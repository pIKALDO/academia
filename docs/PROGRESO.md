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

### Corte 1 — usuarios y autenticación

Incluye:

- `UserEntity`/`UserRepository`/`UserService`, con `UserRole` y `UserStatus`
  (`PENDING_ACTIVATION`, `ACTIVE`, `DISABLED`). `AcademiaUserDetailsService` sustituye al
  `UserDetailsService` en memoria de Spring Boot.
- `AuthController`: `POST /auth/login` con `AuthenticationManager` propio (no `formLogin`, para
  que la respuesta sea JSON normal en vez de una redirección), `GET /auth/me`, `POST /auth/logout`,
  alta/confirmación de restablecimiento de contraseña y activación de cuenta con
  `PasswordResetTokenEntity` (token de un solo uso, hash guardado, no el token en claro).
- `UserController`: alta de usuarios (admin), listado, activar/desactivar.
- `AuthMailService`: correos de activación y recuperación por Thymeleaf; un fallo de envío se
  registra y no tumba la petición (el token ya quedó persistido).
- Revocación inmediata de sesión: `SessionRegistry` + `maximumSessions(-1)`, para que desactivar
  una cuenta expulse su sesión activa en la siguiente petición sin esperar a que caduque la
  cookie (verificado por
  `UserControllerIT.al_desactivar_un_usuario_su_sesion_activa_se_invalida_de_inmediato`).

**Verificado en esta máquina:** `./mvnw verify` pasa en verde de forma estable — 3 ejecuciones
consecutivas sin fallos (2026-09-22). Los `WARNING`/`ERROR` de Mailpit en el log de estas
ejecuciones (`Connection refused: connect, port 1025`) son ruido esperado y no fallos: Mailpit
no está levantado en esta sesión y `AuthMailService` ya está diseñado para no propagar ese
fallo.

#### Bugs de seguridad corregidos durante el corte 1

Dos bugs de fondo en la base de seguridad heredada de corte 0, encontrados al llevar
`AuthController`/`UserController` a tests de integración reales (no aparecían en
`SecurityConfigIT`, que solo prueba rutas sin cuerpo de petición):

1. **CSRF.** `CookieCsrfTokenRepository` necesitaba un filtro adicional
   (`CsrfCookieWritingFilter`) para forzar la resolución perezosa del `CsrfToken` en cada
   petición: sin leer el atributo, Spring Security nunca escribe la cookie `XSRF-TOKEN`, así
   que el interceptor de Angular no tendría nada que reenviar. También se sustituyó el
   `CsrfTokenRequestHandler` por defecto (`XorCsrfTokenRequestAttributeHandler`, que enmascara
   el token) por `CsrfTokenRequestAttributeHandler` sin enmascarar, que es el patrón
   "lee la cookie, reenvíala tal cual en `X-XSRF-TOKEN`" que hace un cliente SPA.
2. **`AuthenticationException`/`AccessDeniedException` que no llegaban traducidas.** El
   `AuthenticationEntryPoint`/`AccessDeniedHandler` de `SecurityConfig`
   (`ProblemDetailSecurityHandlers`) solo intercepta estas excepciones cuando las lanza un
   filtro (una petición sin sesión, o `AuthorizationFilter` al final de la cadena). Las que
   lanza código que corre **dentro** de la invocación del controlador —el
   `authenticationManager.authenticate()` manual de `AuthController.login`, o un
   `@PreAuthorize` de un método de servicio llamado desde un controlador— quedan dentro de la
   pila de `DispatcherServlet`, que las resuelve con sus propios `HandlerExceptionResolver`
   antes de que puedan propagarse de vuelta al filtro. Sin un `@ExceptionHandler` explícito
   para esos dos tipos, caían en el catch-all de `GlobalExceptionHandler` y salían como **500**
   en vez de 401/403 — un login con credenciales incorrectas, o un usuario sin el rol
   necesario, recibían "error interno" en lugar del código correcto. Se añadieron
   `@ExceptionHandler(AuthenticationException.class)` y `@ExceptionHandler(AccessDeniedException.class)`
   a `GlobalExceptionHandler`, reutilizando la misma construcción de `ProblemDetail` que ya
   usaba `ProblemDetailSecurityHandlers` (ahora expuesta como métodos estáticos públicos), para
   que el formato de la respuesta sea idéntico venga la excepción del filtro o del
   controlador/servicio. Este bug afectaba potencialmente a cualquier `@PreAuthorize` de
   `AccessService`, no solo al login: es el más serio de los dos.

También se corrigió la causa de la intermitencia en `AuthControllerIT`/`UserControllerIT`
(no es un bug de producción, sino de los propios tests): `AuthTestSupport` cacheaba el token
CSRF en un campo `static`, y el bean `RestTestClient` autoconfigurado con
`@AutoConfigureRestTestClient` es un singleton que conserva su cookie jar entre peticiones —y,
al estar el contexto de Spring cacheado entre clases de test, entre clases enteras. La cookie
`XSRF-TOKEN` de un test se colaba en el siguiente; la caché estática enmascaraba el síntoma en
vez de arreglarlo, así que a veces el test seguía pasando con un token de otro test y a veces
no. Se eliminó la caché estática y ambos `IT` ahora construyen un `RestTestClient` nuevo
(`RestTestClient.bindToServer().baseUrl(...)`) en un `@BeforeEach`, sin depender del bean
autoconfigurado: cada test parte de un cliente sin cookies. Dentro de un mismo test, el token
CSRF obtenido una vez se reutiliza para todas las peticiones (incluida la de un segundo usuario,
como en el test de revocación de sesión): el token no está ligado a la sesión ni al usuario, así
que no hace falta pedir uno nuevo, y pedirlo fallaría de todos modos porque el cliente ya trae
una cookie `XSRF-TOKEN` válida y el servidor no reenvía `Set-Cookie` para un valor que no ha
cambiado.

### Corte web 1 — esqueleto del frontend y autenticación

Incluye:

- Proyecto Angular 22 creado en `frontend/` (standalone, sin NgModules, PWA vía
  `@angular/pwa`/`ngsw-config.json`), sin tocar `DESIGN.md` ni `design/` ya existentes.
- `frontend/src/styles/tokens.css`: tokens de `DESIGN.md` trasladados a variables CSS
  (color, tipografía, espaciado, radios, elevación, componentes recurrentes). Importado desde
  `src/styles.css`, que además fija fondo, tipografía base y `box-sizing` globales. Fuente
  Inter cargada desde Google Fonts en `index.html`.
- `proxy.conf.json`: `/api` → `http://localhost:8080`, sin `changeOrigin`, para que la cookie
  de sesión funcione en el mismo origen desde el punto de vista del navegador. Registrado en
  `angular.json` (`serve.options.proxyConfig`).
- `core/interceptors/csrf.interceptor.ts`: lee la cookie `XSRF-TOKEN` (la que escribe
  `CookieCsrfTokenRepository.withHttpOnlyFalse()` en el backend) y la reenvía en
  `X-XSRF-TOKEN` para toda petición a `/api/**` que no sea `GET/HEAD/OPTIONS/TRACE`.
- `core/interceptors/error.interceptor.ts`: traduce `ProblemDetail` a un mensaje mostrado en
  `shared/error-banner` (señal global en `NotificationService`). Usa `detail` cuando el
  backend lo manda (ya en español) y una tabla de mensajes de reserva por código de estado en
  caso contrario. Silencia el 401 de `/auth/login` (credenciales incorrectas, ya lo maneja el
  propio formulario) y el de `/auth/me` en el arranque (sesión inexistente, no es un error).
- `core/services/session.service.ts`: señal `currentUser` (`UserProfile | null`), `login`,
  `logout`, `loadProfile` (`GET /auth/me`). `loadProfile` se ejecuta una vez con
  `provideAppInitializer` en `app.config.ts`, antes de que el router resuelva la primera
  ruta: así el guard de autenticación conoce el estado real ya en la primera navegación,
  incluida una recarga de página.
- `core/services/auth.service.ts`: `requestPasswordReset`, `confirmPasswordReset`, `activate`
  (los tres endpoints de `/auth` que no dependen de sesión).
- `core/guards/auth.guard.ts` (exige sesión), `core/guards/guest.guard.ts` (redirige fuera de
  login/activación/recuperación si ya hay sesión), `core/guards/role.guard.ts` (fábrica
  `roleGuard(['ADMIN'])`, sin usar todavía — no hay ninguna pantalla restringida por rol en
  este corte, pero la queda lista para `students`/`guardians`). Los guards solo deciden
  navegación; la autorización real sigue viviendo en `AccessService` (regla no negociable
  nº2), y el front nunca oculta un campo que la API sí devuelve (regla nº3).
- Pantallas: `features/auth/login`, `features/auth/password-reset` (petición y confirmación,
  como dos componentes porque son dos formularios y dos estados distintos, no una pantalla con
  ramas), `features/auth/activate`, `features/home` (nombre y rol del usuario, botón de
  cerrar sesión). Todas standalone, `ReactiveFormsModule`, señales para el estado local.
  CSS compartido de las pantallas de autenticación en `shared/styles/auth-screen.css`
  (`@import` desde cada componente: mismo layout, sin repetir declaraciones).
- `.github/workflows/frontend.yml`: filtrado por `frontend/**`, `npm ci` + `npm test` (Vitest,
  vía el builder `@angular/build:unit-test`, no necesita `--watch=false`: ya corre una sola
  vez por defecto) + `npm run build`.

**Verificado en esta máquina** con el backend real arrancado (`docker compose up -d` +
`mvn spring-boot:run -Dspring-boot.run.profiles=local`) y el proxy de `ng serve`: login con
el admin de `LocalAdminBootstrapper` devuelve `204` y las cookies `SESSION`/`XSRF-TOKEN`
correctas; `GET /auth/me` a través del proxy devuelve el perfil mientras la cookie de sesión
sigue viva (la sesión sobrevive a una recarga porque `loadProfile` se repite en cada arranque
de la app); `POST /auth/logout` seguido de `GET /auth/me` devuelve `401`. Verificado por
`curl` contra `localhost:4200` (que es el proxy), no en un navegador real: este entorno no
tiene una herramienta de navegador disponible. **Pendiente para quien continúe:** abrir
`http://localhost:4200` en un navegador y repetir el flujo (login → recargar → logout) antes
de dar el corte por completamente cerrado.

**Build y tests:** `ng build --configuration development` y `ng test` pasan en verde.

### Contrato de la API — exportación de OpenAPI y generación de tipos (previo al corte 2)

Incluye:

- **`docs/openapi.json`** ahora se exporta como parte del build del backend:
  `OpenApiSpecificationIT` (nuevo, en `com.academia.config`) extiende
  `AbstractIntegrationTest` (mismo PostgreSQL de Testcontainers que el resto de la suite),
  pide `/v3/api-docs` con `RestTestClient` y escribe la respuesta formateada en
  `docs/openapi.json`. Se prefirió esto a `springdoc-openapi-maven-plugin` (que arrancaría la
  app de verdad, contra una base de datos real, solo para generar el fichero) o a un test
  unitario con contexto mínimo (no serviría: `ddl-auto: validate` exige un esquema real
  aplicado por Flyway). Reutiliza infraestructura que ya existía para los tests de integración,
  sin añadir un plugin ni un segundo mecanismo de arranque.
  - `OpenApiConfig` fija `servers` a `[{ "url": "/" }]`: sin esto, springdoc infiere la URL del
    puerto aleatorio de `RANDOM_PORT`, que cambia en cada ejecución y rompería la comparación de
    CI aunque el contrato no hubiera cambiado. Verificado determinista: dos ejecuciones
    consecutivas de `mvn verify` producen un `docs/openapi.json` idéntico byte a byte.
- **`.github/workflows/backend.yml`**: paso nuevo tras `mvn verify` que hace
  `git diff --exit-code -- docs/openapi.json`. Como el test de integración ya sobrescribió el
  fichero durante `verify`, si el commiteado no coincide con el recién generado el paso falla y
  el diff queda visible en los logs — cualquier ruptura del contrato aparece en la pull request.
- **`frontend/package.json`**: script `generate:api-types` (`openapi-typescript
  ../docs/openapi.json -o src/app/core/api/schema.d.ts`), enganchado con
  `prestart`/`prebuild`/`pretest` para que `npm start`, `npm run build` y `npm test` regeneren
  los tipos antes de arrancar/compilar — el frontend nunca sirve ni compila contra un contrato
  desactualizado. `schema.d.ts` es generado, no se commitea (añadido a `.gitignore`).
  - **`frontend/.npmrc`** con `legacy-peer-deps=true`: `openapi-typescript@7.13.0` declara
    `peerDependencies.typescript: ^5.x` y el proyecto usa TypeScript 6.0.x. Sin esto, `npm ci`
    falla con `ERESOLVE` en CI. Es una dependencia de desarrollo que solo parsea el JSON y
    genera `.d.ts` (no se mete en la cadena de compilación de la app), así que el desajuste de
    peer dependency es ruido, no un riesgo real — a vigilar en la próxima actualización de
    `openapi-typescript` por si ya declara soporte para TS 6, momento en el que este `.npmrc`
    podría retirarse.
- `core/models/user.model.ts` (`UserProfile`) y los cuerpos de petición de `auth.service.ts` /
  `session.service.ts` (`LoginRequest`, `PasswordResetRequest`, `PasswordResetConfirmRequest`,
  `ActivateAccountRequest`) ahora son alias de `components['schemas'][...]` del fichero
  generado, no interfaces escritas a mano.
- **`.github/workflows/frontend.yml`**: `docs/openapi.json` añadido a los `paths` de disparo
  (push y pull_request), para que un cambio de contrato sin tocar `frontend/` también dispare
  esta CI y falle si rompe la compilación.

**Verificado en esta máquina:** `mvn verify` (con Docker corriendo) genera y dos ejecuciones
seguidas producen el mismo `docs/openapi.json`; `npm run build` y `npm test` regeneran
`schema.d.ts` y compilan en verde con los tipos generados.

## Corte actual y siguiente paso

**Corte actual:** ninguno en marcha. Corte web 1 y el contrato de API (arriba) cerrados, con la
verificación en navegador real del corte web 1 todavía pendiente (ver arriba).

**Siguiente:** corte 2, por decidir el dominio. Candidatos naturales según CLAUDE.md: `students`
(fichas de estudiantes y bloques de ficha) o `guardians` (tutores y vínculos), ambos ya con
migraciones en `V2`/`V3`. El mecanismo de contrato (`docs/openapi.json` + tipos generados) ya
está listo para ese dominio: basta con implementar el endpoint y volver a generar.

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
- **Ya resuelto, no pendiente:** el `.env` ya existe en esta máquina (copia sin cambios de
  `.env.example`, es un prototipo sin secretos reales). Verificado con `docker compose up -d`
  (los tres servicios arrancan sanos) y `mvn spring-boot:run -Dspring-boot.run.profiles=local`
  contra ellos: Flyway valida las 7 migraciones y `/actuator/health` responde `UP`.
- **Alta del primer ADMIN.** Con la base de datos vacía no hay ninguna cuenta con la que
  entrar (crear usuarios exige ya ser ADMIN, `@PreAuthorize("@access.canManageUsers()")`).
  `LocalAdminBootstrapper` (`users`, `@Profile("local")`) crea uno al arrancar si
  `existsByRole(ADMIN)` es falso, con `ADMIN_EMAIL`/`ADMIN_PASSWORD` de `.env` — sin valor por
  defecto en `application-local.yml` a propósito, para que el arranque falle si no están
  exportadas en vez de crear un admin con una contraseña fija conocida por cualquiera que lea
  el repositorio. Va directo a `UserRepository`, no a `UserService.create`: ese método deja al
  usuario en `PENDING_ACTIVATION` a la espera de un correo de activación, que no tiene sentido
  para la primera cuenta. Verificado de extremo a extremo: arranque con `.env` exportado →
  log de creación → `POST /auth/login` con esas credenciales devuelve `204`; un segundo
  arranque no vuelve a crear el admin (idempotente).
  - **Recordatorio para quien arranque en otra máquina:** `.env` solo lo lee
    `docker compose`, no Maven — para que `ADMIN_EMAIL`/`ADMIN_PASSWORD` lleguen al backend
    hace falta exportarlas antes de `./mvnw spring-boot:run` (`set -a && source .env && set +a`
    en bash). Sin exportar, el arranque en local falla al no poder resolver el placeholder
    (fallo explícito, no un admin con contraseña adivinable).
- Perfil de servidor (mencionado como "aún no creado" en el comentario de
  `application.yml`) no existe todavía — se añadirá cuando toque desplegar contra
  Cloudflare R2 y la base de datos de producción.
- Verificación en navegador real del flujo de login/recarga/logout del corte web 1 (ver
  arriba) — solo probado por `curl` en esta máquina.
- `frontend/` no tiene todavía pantallas de `students` ni `documents`: fuera de alcance del
  corte web 1 a propósito, su API aún no existe.
