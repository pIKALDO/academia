# Progreso

Estado del proyecto al retomarlo en una máquina nueva (actualizado 2026-09-22).

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

### Corte 2 — estudiantes y tutores (2026-09-22)

Implementa docs/diseno-api.md secciones 4, 5.3, 5.4 y 5.5.

Incluye:

- **`students`**: `StudentEntity` (borrado lógico, ver decisión abajo), los tres bloques 1:1
  (`SportsProfileEntity`, `EducationInfoEntity`, `HousingInfoEntity`) y
  `EmergencyContactEntity`. Endpoints: listado paginado con filtros `status` y `q`, alta,
  ficha, `PATCH` de la ficha principal, `DELETE` lógico, `PUT` de cada bloque (reemplazo
  total) y contactos de emergencia (anidados para listar/crear, planos para editar/borrar).
- **DTOs por rol**, clases separadas: `StudentAdminDto` y `StudentGuardianDto` (también sus
  bloques anidados), `StudentListDto` igual para ambos roles, y
  `EmergencyContactAdminDto`/`EmergencyContactGuardianDto`. `GET /students/{id}` devuelve la
  interfaz sellada `StudentDetailDto` (`oneOf` en OpenAPI, sin discriminador: el front elige
  el tipo por el rol de la sesión). Los dos mapeos leen del mismo agregado, `StudentSheet`.
- **`guardians`**: `GuardianEntity`, `StudentGuardianEntity` (clave compuesta
  `StudentGuardianId`). `/guardians` (listado, alta, ficha, `PATCH`) y el vínculo
  `PUT/DELETE /students/{sid}/guardians/{gid}`: `PUT` idempotente sobre la pareja de ids, con
  `relationship`, `isPrimary` y `hasAccess` obligatorios. Un tutor puede enlazarse a una
  cuenta (`userId`) que debe existir, tener rol GUARDIAN (422 si no) y no estar enlazada ya a
  otro tutor (409).
- **Autorización** (el núcleo del corte):
  - `@PreAuthorize` en todos los métodos de servicio. Las lecturas de un estudiante añaden
    `@HandleAuthorizationDenied(handlerClass = HideStudentWhenNotVisible.class)`.
  - `AccessService.canViewStudent` devuelve `StudentAccessDecision` (`GRANTED`/`HIDDEN`/
    `FORBIDDEN`) en vez de `boolean`: Spring Security 7 admite que la expresión SpEL devuelva un
    `AuthorizationResult`, y el manejador lo recibe al denegar. `HIDDEN` (estudiante ajeno) →
    `StudentNotFoundException`, la misma que un id inexistente → 404 idéntico. `FORBIDDEN`
    (rol STUDENT, sin mirar el id) → 403. La decisión se toma en `AccessService`; el manejador
    solo traduce.
  - Las escrituras solo miran el rol → 403 con cualquier id (propio, ajeno o inexistente), así
    que no revelan existencia.
  - `StudentAccessSpecifications.visibleToGuardian` es el **único** predicado de visibilidad
    (`EXISTS` sobre `student_guardians` con `has_access` y `guardians.user_id`). Lo usan
    `JpaStudentAccessRepository` (acotado a un id, para `@PreAuthorize`) y el listado (vía
    `AccessService.visibleStudents()`).
  - Rol STUDENT: 403 en todo el módulo (portal del estudiante, fase 2). Se retiró
    `StudentAccessRepository.isOwnStudent`, que ya no tenía uso.
- **Seed local**: `devdata/LocalDemoDataSeeder` (`@Profile("local")`) con dos familias y tres
  estudiantes inventados. Incluye un padre vinculado **sin** `has_access` para probar ese caso
  a mano (el Javadoc de la clase tiene la tabla). SQL directo con `JdbcTemplate`, para no abrir
  la visibilidad de los repositorios de cada módulo. Se activa con `SEED_FAMILY_PASSWORD`
  (nueva en `.env.example`); si no está definida, se omite sin fallar. Solo carga si
  `students` está vacía.
- **Tests**: `StudentAccessIT` (16: los seis obligatorios, la comparación del cuerpo completo
  del 404 ajeno frente al inexistente —salvo `timestamp` e `instance`, que varían por
  naturaleza—, sin `has_access` en el listado, contactos de emergencia, 403 de escritura
  idéntico con id ajeno o inexistente, STUDENT, 401), `StudentControllerIT` (12),
  `GuardianControllerIT` (8) y `AccessServiceTest` reescrito (10, una regla por test).
  `AuthTestSupport` se movió a `support` (público) para compartirlo; `StudentTestData`
  prepara escenarios con SQL.
- **`docs/openapi.json`** regenerado: solo añade las 18 operaciones y los esquemas nuevos;
  ninguna operación ni esquema existente cambia.
- **Orden estable del contrato** (`springdoc.writer-with-order-by-keys: true`): springdoc
  emitía los paths en orden de registro de los controladores, así que añadir uno reordenaba
  los demás y el diff mezclaba cambios con movimientos. Ahora todas las claves salen en orden
  alfabético: paths, esquemas y también las propiedades de cada esquema, que dejan de seguir
  el orden del record. Verificado: dos `verify` seguidos generan un fichero idéntico
  (mismo SHA-256).
- **`operationId` sin sufijos numéricos.** springdoc usa el nombre del método Java y numera
  los duplicados (`list_1`, `list_2`) por orden de registro: un controlador nuevo podía
  renumerar operaciones ajenas. Los métodos de controlador llevan nombres únicos
  (`listStudents`, `createGuardian`...), y `OpenApiSpecificationIT` falla si aparece un sufijo
  `_N`. Los de `users` (`list`, `create`...) no se renombran, para no cambiar su contrato.
- **Diff del contrato en local:** con el algoritmo Myers (el de git por defecto), añadir
  bloques a un JSON muy repetitivo aparenta líneas borradas que no lo son. En el commit de
  este corte, Myers muestra 92 "borradas"; `--diff-algorithm=histogram` muestra 1424
  añadidas y 0 borradas. Recomendado: `git config diff.algorithm histogram`.
- **Rutas inexistentes → 404** (antes 500: `NoResourceFoundException` caía en el catch-all
  de `GlobalExceptionHandler`), con `GlobalExceptionHandlerIT` sobre el enrutado real.
- **`/test/protected` fuera del contrato**: `ProtectedTestController` (solo existe en el
  classpath de test) lleva `@Hidden`. Antes aparecía en `docs/openapi.json` porque el fichero
  se exporta desde un test de integración.
- **Seed solo en `local`**, verificado por `LocalDemoDataSeederIT` (en el perfil por defecto el
  bean no existe y no hay cuentas `@familia.local`) y `LocalDemoDataSeederTest` (anotación
  `@Profile("local")`, sin contraseña no toca la base de datos, con estudiantes no inserta).

**Bug previo corregido: cabecera `Location` de `POST /users`.** El `UriComponentsBuilder` que
inyecta Spring MVC parte del *servlet mapping*, no de la URL de la petición, así que
`uriBuilder.path("/{id}")` producía `http://host/{id}`, sin `/api/v1/users`. `UserControllerIT`
solo comprobaba que la cabecera existiera. Corregido, y los tests de todos los `201` validan
ahora la ruta completa.

**Verificado en esta máquina:** `./mvnw verify` en verde (92 tests). `npm run build` del
frontend compila con los tipos regenerados. Con `spring-boot:run -Dspring-boot.run.profiles=local`,
el seed carga, y por `curl`: Olena ve a Danylo y Sofiia, y Lucía le da 404; Taras ve a Danylo,
y Sofiia (vinculado sin acceso) y Lucía le dan 404; Marta solo ve a Lucía. La vista de familia
no trae `coachNotes` ni `housing`, y la de admin sí.

**Verificado también a mano en Swagger UI** (2026-09-22), con las cuentas del seed: Olena →
Danylo 200 sin `coachNotes` ni `housing`; Olena → Lucía 404; Taras → Sofiia 404 y Danylo 200.
Para que funcione "Try it out" hizo falta `springdoc.swagger-ui.csrf.enabled: true`: sin ella,
Swagger UI no envía `X-XSRF-TOKEN` y toda petición que modifica estado (el login incluido)
responde 403.

### Campos obligatorios en el contrato de la API (2026-09-22)

Implementa la propuesta que quedó pendiente en el corte 2 (ver más abajo, ahora resuelta).

Incluye:

- **`ResponseSchemaModelConverter`** (`com.academia.config`, registrado como `@Bean` en
  `OpenApiConfig`): recorre solo records de respuesta (no `*Request`) y marca `required`
  todas sus componentes; las anotadas con `@org.jspecify.annotations.Nullable` en el propio
  DTO añaden `"null"` a su `type`. Un campo `$ref` (un bloque anidado como `sportsProfile`,
  `education`, `housing`) no admite `type` como palabra clave hermana en OpenAPI 3.1 —el
  esquema referenciado ya fija el suyo—, así que esos casos se envuelven en
  `anyOf: [{$ref}, {type: "null"}]` en vez de tocar el `$ref` directamente.
  - **Detalle no obvio de swagger-core**: para un tipo con nombre, `chain.next()` no devuelve
    el esquema con sus `properties`, sino un `$ref` a él; el esquema real vive en
    `context.getDefinedModels()`, bajo el nombre del `$ref` (que puede venir de
    `@Schema(name = ...)` y no coincidir con el nombre simple de la clase Java, como pasa con
    los records anidados de `StudentAdminDto`/`StudentGuardianDto`). El conversor resuelve el
    nombre a partir del propio `$ref`, no de la clase.
  - Otro detalle: `@Nullable` de JSpecify solo admite `ElementType.TYPE_USE`, así que no
    aparece en `RecordComponent.getAnnotations()` (anotaciones de declaración); hace falta
    `RecordComponent.getAnnotatedType().isAnnotationPresent(...)`.
  - Se anotó `@Nullable` cada componente nulable según la migración real (columna sin
    `NOT NULL`), no por conveniencia: `StudentAdminDto`/`StudentGuardianDto` y sus bloques
    anidados, `EducationDto`, `HousingDto`, `SportsProfileDto`,
    `EmergencyContactAdminDto.relationship`/`notes`, `EmergencyContactGuardianDto.relationship`,
    `GuardianDto.userId`/`phone`/`email`, `UserDetailDto.lastLoginAt`,
    `UserProfileDto.lastLoginAt`.
- **`operationId` de `users` renombrados** a la misma convención que el resto
  (`listUsers`, `createUser`, `getUser`, `updateUser`, `disableUser`, `enableUser`): el único
  consumidor del contrato es el frontend propio, así que el cambio no rompe nada fuera del
  repositorio.
- **Test**: `ResponseSchemaModelConverterIT` (`com.academia.config`), cuatro casos concretos
  contra `/v3/api-docs`: un campo obligatorio y no nulable (`UserDetailDto.email`), uno
  nulable (`UserDetailDto.lastLoginAt`), uno nulable que referencia otro esquema
  (`StudentAdminDto.sportsProfile`, envuelto en `anyOf`) y un `*Request`
  (`UpdateUserRequest`) sin `required` añadido.
- **`docs/openapi.json` regenerado**: el diff (`histogram`) toca solo `required` nuevo,
  `type: [X, "null"]`/`anyOf` en las componentes nulables y los seis `operationId` de
  `users`. Ningún esquema de petición (`*Request`) cambia.
- **Frontend**: `npm run build` y `npm test` regeneran `schema.d.ts` y compilan en verde sin
  tocar código. Ninguna pantalla existente usa todavía los campos que pasan a nulables
  (`lastLoginAt` no se lee en ningún sitio), así que no hizo falta ajustar nada.

**Verificado en esta máquina:** `./mvnw verify` en verde (69 tests). `npm run build` y
`npm test` del frontend compilan y pasan en verde con los tipos regenerados.

### Corte web 2 — pantallas de estudiantes (2026-09-22)

Implementa las cinco pantallas de `students`/`guardians` sobre docs/diseno-api.md secciones 4,
5.3, 5.4 y 5.5.

Incluye:

- **Modelos y servicios** (`core/models/{student,guardian,emergency-contact}.model.ts`,
  `core/services/{students,guardians,emergency-contacts,users}.service.ts`), todos derivados de
  `components['schemas'][...]` del `schema.d.ts` generado — ningún tipo escrito a mano.
  `core/models/paged-response.model.ts` define un `PagedResponse<T>` genérico (`content` + el
  `PageInfo` generado) para no repetir el envoltorio por cada `PagedResponse*Dto`.
- **`StudentDetailDto`/`EmergencyContactDto` son un `oneOf` sin discriminador** (decisión ya
  tomada en el corte 2 de backend): el frontend los trata como unión de tipos y distingue la
  vista por la **presencia de la clave**, no por el rol de sesión — `isAdminStudent` comprueba
  `'housing' in student`, `isAdminGuardianLink` comprueba `'id' in guardian`,
  `isAdminEmergencyContact` comprueba `'id' in contact`. Es la regla no negociable nº 3 aplicada
  literalmente: la pestaña "Alojamiento", los campos `coachNotes`/`ranking`/`history` de
  deportiva y `notes`/`scheduleNotes` de estudios no se pintan porque la clave no llega en la
  respuesta de un `GUARDIAN`, nunca porque el componente compruebe `session.role`.
- **`shared/shell/`**: layout autenticado (topbar + navegación + `router-outlet`) que envuelve
  todas las rutas tras el login; sustituye a `features/home` (eliminado). El enlace "Tutores"
  solo se pinta si `session.currentUser()?.role === 'ADMIN'` — ocultar una acción de navegación
  por rol no es lo mismo que filtrar datos ya recibidos, y el backend igualmente devuelve 403 a
  `GET /guardians` si un `GUARDIAN` fuerza la ruta.
- **Pantallas**:
  - `features/students/students-list/` — listado (pantalla de inicio tras login), con buscador
    (`q`, debounce 300ms) y filtro por `status`. Tabla en desktop, tarjetas en mobile
    (`< 720px`), controlado solo por CSS (`.desktop-only`/`.mobile-only`), sin duplicar la
    petición. Paginación con los campos de `PageInfo`. Botón "Añadir estudiante" solo para
    `ADMIN`.
  - `features/students/student-create/` — alta (`POST /students`), formulario plano (los
    bloques no existen hasta que se crean desde la ficha).
  - `features/students/student-detail/` — ficha con pestañas (`Personales`, `Familia`,
    `Deportiva`, `Estudios`, `Alojamiento` — esta última solo si `isAdminStudent`). Cada pestaña
    es un componente propio bajo `tabs/`, recibe `student`/`isAdmin` y emite `saved`; el
    contenedor father hace `reload()` (recarga completa de `GET /students/{id}`) tras cualquier
    escritura en vez de fusionar estado local — más simple y evita divergencias entre lo que
    devuelve cada `PUT`/`PATCH` y el agregado completo.
  - `tabs/family-tab/` — la más grande: tutores vinculados (editar vínculo, desvincular,
    vincular un tutor ya existente o crear uno nuevo e vincularlo en el mismo flujo, con
    selector de cuenta de acceso vía `GET /users?role=GUARDIAN`) y contactos de emergencia
    (alta/edición/borrado). Ambos bloques usan los arrays embebidos en `StudentDetailDto`
    (`guardians`, `emergencyContacts`), no una petición paginada aparte — ya vienen completos en
    la ficha.
  - `features/guardians/guardians-list/` — gestión global de tutores (`GET/POST/PATCH
    /guardians`), independiente de cualquier estudiante concreto; ruta `/guardians`,
    `roleGuard(['ADMIN'])`.
- **Sin `photoUrl` todavía.** `PUT /students/{id}/photo` está aplazado al corte de documentos
  (ver más abajo). El listado y la ficha usan `shared/avatar/` (iniciales sobre un círculo de
  color), pensado para sustituirse por una `<img>` sin tocar la maquetación cuando `photoUrl`
  llegue al contrato.
- **CSS compartido** (`shared/styles/{buttons,forms,panel,data-table}.css`), importado con
  `@import` desde cada componente (mismo patrón que `auth-screen.css` del corte web 1): botones
  primario/secundario/ghost/destructivo, campos de formulario, paneles con cabecera y toda la
  tabla de datos (cabecera `#F8FAFC`, fila 36px, hover, radio recto), tal como los describe
  `frontend/DESIGN.md`.

**Verificado en esta máquina** con el backend real (`spring-boot:run -Dspring-boot.run.profiles=local`)
y `ng serve` a través del proxy: `npm run build` y `npm test` compilan y pasan en verde. Los
flujos de escritura (alta de estudiante, contacto de emergencia, vínculo con tutor existente,
`PUT` de ficha deportiva, borrado) verificados por `curl` contra `localhost:4200` reproduciendo
exactamente las peticiones que hace el frontend (cookie de sesión + `X-XSRF-TOKEN`): todas
devuelven los códigos esperados y el agregado final coincide con lo que pintan los componentes.
Confirmado con las cuentas del seed que Olena, como `GUARDIAN`, recibe la ficha de Danylo sin
`housing` ni `coachNotes`/`ranking`/`history`/`notes`/`scheduleNotes`, sin datos de contacto de
los otros tutores ni de los contactos de emergencia, `GET /students/{id}` de un estudiante ajeno
(Lucía) devuelve 404, y `GET /guardians` devuelve 403.

**Pendiente para quien continúe: verificación visual en un navegador real.** Este entorno no
tiene una herramienta de navegador disponible (mismo límite que en el corte web 1), así que el
render de las cinco pantallas, la maquetación mobile (tarjetas vs. tabla) y el comportamiento de
los formularios no se han visto, solo verificado a nivel de API/contrato. Abrir
`http://localhost:4200`, entrar como `admin@academia.local` y como `olena.kovalenko@familia.local`
(contraseñas en `.env`) y repetir a ojo lo que este corte verificó por `curl`.

## Corte actual y siguiente paso

**Corte actual:** ninguno en marcha. Corte web 2 cerrado (arriba).

**Siguiente:** corte de documentos (`documents`). Además del módulo, trae lo que se aplazó del
corte 2: almacenamiento S3/MinIO, `PUT /students/{id}/photo` + `photoUrl` prefirmada,
`documentsSummary` en la ficha y el listado, y la revisión de `@SQLRestriction` (ver
"Decisiones a revisar"). En el frontend, sustituir `shared/avatar/` por la foto real en listado
y ficha cuando `photoUrl` llegue al contrato.

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
- **STUDENT sin acceso al módulo de estudiantes en fase 1** (403 sin mirar el id). El
  documento de diseño (sección 3.2) enumera la regla "STUDENT → únicamente el suyo"; queda
  para el portal del estudiante (fase 2), porque hoy no existe DTO para esa vista.
- **`GET /students/{id}/emergency-contacts` paginado**, aunque la sección 5.5 no lo marca: se
  aplica la regla no negociable nº9.
- **Sin discriminador en `StudentDetailDto`/`EmergencyContactDto`.** La vista depende del
  rol, y el front ya lo conoce por `/auth/me`. Un campo discriminador sería redundante con la
  sesión, y además podría contradecirla.

## Decisiones a revisar

- **`@SQLRestriction("deleted_at IS NULL")` en `StudentEntity`** (aceptado solo para el corte
  2). Tiene dos consecuencias que revisar **en el corte de documentos**:
  1. Un estudiante borrado es invisible para siempre, **también para el administrador**: no hay
     forma de consultarlo ni de restaurarlo por la API, y el borrado lógico existe justo para
     poder recuperar (docs/modelo-datos.md sección 1.3).
  2. La restricción no se propaga: los documentos del estudiante (y sus contactos de
     emergencia, vínculos y bloques) siguen existiendo. Hoy, cada ruta que parte de un
     id de contacto comprueba a mano que el estudiante exista
     (`EmergencyContactService.getEntityOrThrow`). `GET /documents/{id}` necesitará lo
     mismo, o un documento de un estudiante borrado seguiría accesible por su URL plana.

  Alternativas a valorar entonces: filtrar `deleted_at` en las consultas del repositorio en
  lugar de en la entidad, con una ruta explícita de administrador para ver y restaurar
  borrados; o un `@Filter` de Hibernate que se active por defecto y se desactive solo en esa
  ruta.

## Pendientes conocidos

- **Ya resuelto, no pendiente:** `required` en los DTOs de respuesta — ver "Campos
  obligatorios en el contrato de la API" arriba.
- **`@ApiResponse` por código de error** (docs/diseno-api.md sección 10): ningún controlador
  los tiene todavía; el contrato solo documenta el caso feliz.
- **`PATCH` no puede vaciar un campo opcional** (convención heredada de `UpdateUserRequest`:
  `null` = "no tocar"). Si hace falta, habrá que distinguir "ausente" de `null` en la petición.
- **Varios tutores `isPrimary` por estudiante**: nada impide marcar dos como principales (ni
  el esquema ni el servicio). No se ha pedido; decidir si debe ser único.

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
  Cloudflare R2 y la base de datos de producción. **Al crearlo, el de producción (datos
  reales) debe llevar `springdoc.api-docs.enabled: false` y `springdoc.swagger-ui.enabled:
  false`**, las dos: apagar solo la interfaz deja `/v3/api-docs` sirviendo el contrato. Un
  despliegue de demostración con datos inventados puede mantener Swagger (README, "Swagger
  según el entorno"; docs/diseno-api.md sección 10). Conviene un test que lo verifique con
  el perfil de producción activo: `/v3/api-docs` y `/swagger-ui.html` → 404.
- Verificación en navegador real del flujo de login/recarga/logout del corte web 1 (ver
  arriba) — solo probado por `curl` en esta máquina.
- `frontend/` no tiene todavía pantallas de `students` ni `documents`: fuera de alcance del
  corte web 1 a propósito, su API aún no existe.
