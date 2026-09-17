# Diseño de la API — Fase 1

Contrato de la API REST de la plataforma. Se cierra **antes** de escribir controladores: el front y
el backend se construyen contra este documento.

- Base: `https://<host>/api/v1`
- Formato: JSON (`application/json`), UTF-8
- Fechas: ISO 8601. `LocalDate` para fechas (`2026-03-14`), `Instant` UTC para marcas de tiempo
  (`2026-03-14T09:12:00Z`)
- Documentación: springdoc-openapi en `/v3/api-docs`, interfaz en `/swagger-ui.html`

---

## 1. Principios de diseño

### 1.1 Recursos, no acciones

Las URLs nombran cosas, no operaciones. El verbo HTTP dice qué se hace con ellas.

```
GET    /students/{id}          en lugar de  GET  /getStudent?id=...
POST   /students               en lugar de  POST /createStudent
DELETE /documents/{id}         en lugar de  POST /deleteDocument
```

**Por qué importa.** Con recursos, el cliente puede razonar sobre la API sin leer documentación:
si sabe que existe `/students/{id}`, deduce que `GET` lo lee, `PUT` lo reemplaza y `DELETE` lo borra.
Con acciones, cada endpoint es un caso aparte que hay que aprender de memoria.

**La excepción razonable** son las transiciones de estado que no son un cambio de campo. Marcar un
documento como revisado no es "editar el documento": es una transición con reglas propias y efectos
laterales (quién revisa, cuándo, registro de auditoría). Para eso sí se usa un sub-recurso de acción:

```
POST /documents/{id}/review
```

Esto es preferible a `PATCH /documents/{id}` con `{"status": "REVIEWED"}`, porque el PATCH permitiría
saltar estados o fijar `reviewed_by` a mano.

### 1.2 Jerarquía solo donde hay pertenencia real

```
GET  /students/{studentId}/documents      ← listar los documentos de un estudiante
GET  /documents/{id}                      ← acceder a un documento concreto
```

Un documento pertenece a un estudiante, así que la colección cuelga de él. Pero el recurso
individual tiene su propia URL plana: anidar siempre (`/students/{sid}/documents/{did}`) obliga al
cliente a llevar dos identificadores para algo que solo necesita uno, y permite construir URLs
incoherentes donde el documento no pertenece al estudiante indicado.

**Regla práctica:** anidar para crear y listar, plano para leer, modificar y borrar.

### 1.3 Nunca más de dos niveles de anidamiento

`/students/{id}/documents` sí. `/students/{id}/documents/{id}/notifications` no — eso sería
`/documents/{id}/notifications`.

---

## 2. Autenticación

### 2.1 Sesión con cookie, no JWT en `localStorage`

```
POST /auth/login
{ "email": "ana@example.com", "password": "..." }

→ 204 No Content
   Set-Cookie: SESSION=...; HttpOnly; Secure; SameSite=Strict; Path=/
```

**Por qué no JWT en `localStorage`.** Cualquier JavaScript de la página puede leer `localStorage`:
una vulnerabilidad XSS, una dependencia de npm comprometida, y el token se va. Una cookie `HttpOnly`
no es accesible desde JavaScript, así que el mismo XSS no puede robarla.

**Por qué no JWT en cookie tampoco.** El argumento a favor del JWT es que no requiere estado en el
servidor. Pero eso es justamente su problema: **no se puede revocar**. Si el administrador desactiva
la cuenta de una familia, con JWT esa familia sigue entrando hasta que el token caduque. Con sesión
en servidor, se invalida al instante. En una plataforma con datos de menores, la revocación
inmediata vale más que el ahorro de una consulta.

**CSRF.** La cookie viaja automáticamente en cada petición, así que hace falta protección. Con
`SameSite=Strict` el navegador no la envía en peticiones originadas en otro sitio, lo que cubre el
caso principal. Se complementa con el token CSRF de Spring Security para las peticiones que
modifican estado.

### 2.2 Resto de endpoints de sesión

```
POST   /auth/logout                 → 204
GET    /auth/me                     → 200  perfil del usuario autenticado
POST   /auth/password-reset         → 202  solicitar enlace de recuperación
POST   /auth/password-reset/confirm → 204  fijar nueva contraseña con el token
POST   /auth/activate               → 204  primera contraseña tras el alta
```

**`POST /auth/password-reset` devuelve siempre 202**, exista o no el correo.

Si devolviera 404 para un email inexistente, cualquiera podría comprobar qué direcciones tienen
cuenta en la plataforma. Es enumeración de usuarios. La respuesta es idéntica en ambos casos y el
correo solo se envía si la cuenta existe.

Lo mismo en el login: contraseña incorrecta y usuario inexistente devuelven el mismo `401` con el
mismo mensaje.

### 2.3 Limitación de intentos

`POST /auth/login` y `POST /auth/password-reset` limitados por IP y por cuenta. Al superarse:

```
429 Too Many Requests
Retry-After: 300
```

---

## 3. Autorización

### 3.1 La regla del 404

Cuando un usuario pide un recurso que existe pero no le corresponde, la respuesta es **404**, no 403.

```
GET /students/8f3a...   (familia B pidiendo el estudiante de la familia A)
→ 404 Not Found
```

**Por qué.** Un 403 confirma que ese identificador existe y pertenece a alguien. Recorriendo
identificadores, un usuario autenticado podría mapear cuántos estudiantes hay en el sistema. Con
404, "no existe" y "no es tuyo" son indistinguibles.

**Cuándo sí se usa 403:** cuando el usuario *puede ver* el recurso pero no realizar esa operación
concreta. Una familia que intenta `DELETE /documents/{id}` sobre un documento de su propio hijo
recibe 403 — ya sabe que ese documento existe, no se filtra nada nuevo.

Resumiendo:

| Situación | Código |
|---|---|
| No autenticado | 401 |
| Recurso de otro usuario | 404 |
| Recurso propio, operación no permitida para su rol | 403 |
| Recurso inexistente | 404 |

### 3.2 Dónde se aplica

En la capa de servicio, no en el controlador y desde luego no en el front.

```java
@PreAuthorize("@access.canViewStudent(#studentId)")
public StudentDetailDto get(UUID studentId) { ... }
```

El componente `access` resuelve, para el usuario autenticado, qué estudiantes le corresponden:

- `ADMIN` → todos
- `GUARDIAN` → los de `student_guardians` donde `guardian.user_id = usuario` y `has_access = true`
- `STUDENT` → únicamente el suyo

**Cada una de estas reglas tiene un test.** El más importante: la familia B pide el estudiante de la
familia A y recibe 404. Ese test es el corazón del proyecto.

---

## 4. DTOs por rol

El mismo recurso devuelve representaciones distintas según quién pregunte. **El filtrado ocurre en
el servidor**: el front nunca recibe un campo que luego oculta.

### 4.1 Estudiante — vista de administrador

`GET /students/{id}` con rol `ADMIN`:

```json
{
  "id": "018f...",
  "firstName": "Danylo",
  "lastName": "Kovalenko",
  "birthDate": "2011-04-22",
  "nationality": "UA",
  "photoUrl": "https://.../signed?...",
  "status": "ACTIVE",
  "enrolledAt": "2026-01-15",
  "contact": {
    "phone": "+34...",
    "email": "...",
    "address": { "line": "...", "city": "València", "postalCode": "46001", "country": "ES" }
  },
  "guardians": [
    { "id": "018f...", "firstName": "Olena", "lastName": "Kovalenko",
      "relationship": "MOTHER", "isPrimary": true, "hasAccess": true,
      "phone": "+34...", "email": "..." }
  ],
  "emergencyContacts": [ { "name": "...", "relationship": "...", "phone": "...", "priority": 1 } ],
  "sportsProfile": {
    "level": "Nacional sub-14", "dominantHand": "RIGHT", "ranking": "...",
    "previousClub": "...", "history": "...", "goals": "...",
    "coachNotes": "Necesita trabajar el revés cortado"
  },
  "education": { "schoolName": "...", "grade": "2º ESO", "scheduleNotes": "..." },
  "housing": { "addressLine": "...", "responsibleName": "...", "responsiblePhone": "...", "notes": "..." },
  "documentsSummary": { "total": 8, "pending": 2, "expiringSoon": 1, "expired": 0 },
  "createdAt": "2026-01-15T10:02:00Z",
  "updatedAt": "2026-03-01T17:40:00Z"
}
```

### 4.2 Estudiante — vista de familia

`GET /students/{id}` con rol `GUARDIAN`, para su propio hijo:

```json
{
  "id": "018f...",
  "firstName": "Danylo",
  "lastName": "Kovalenko",
  "birthDate": "2011-04-22",
  "nationality": "UA",
  "photoUrl": "https://.../signed?...",
  "status": "ACTIVE",
  "contact": { "phone": "+34...", "email": "..." },
  "guardians": [ { "firstName": "Olena", "lastName": "Kovalenko",
                   "relationship": "MOTHER", "isPrimary": true } ],
  "emergencyContacts": [ { "name": "...", "relationship": "...", "phone": "..." } ],
  "sportsProfile": {
    "level": "Nacional sub-14", "dominantHand": "RIGHT",
    "previousClub": "...", "goals": "..."
  },
  "education": { "schoolName": "...", "grade": "2º ESO" },
  "documentsSummary": { "total": 8, "pending": 2, "expiringSoon": 1, "expired": 0 }
}
```

**Diferencias, y por qué:**

- **`coachNotes` desaparece.** Son notas internas del entrenador. Que el padre lea "le falta
  actitud" es exactamente el tipo de filtración que rompe la confianza en la herramienta.
- **`housing` desaparece.** La familia ya sabe dónde vive su hijo; el bloque contiene datos internos
  de gestión e incidencias.
- **Los datos de contacto de los tutores desaparecen** del listado de tutores. Un tutor no necesita
  la API para saber su propio teléfono, y si hay dos tutores separados, la plataforma no debe ser el
  canal que le da a uno el contacto del otro.
- **`createdAt`, `updatedAt`, `enrolledAt` desaparecen.** Metadatos de gestión.

**Implementación:** dos clases DTO distintas (`StudentAdminDto`, `StudentGuardianDto`) y dos métodos
de mapeo. No una clase con campos nulos y `@JsonInclude(NON_NULL)`.

**Por qué clases separadas.** Con una sola clase y campos anulables, la seguridad depende de que
alguien recuerde poner el campo a `null` en cada ruta de código. Un campo nuevo añadido dentro de
seis meses se filtra por defecto. Con clases separadas, un campo nuevo **no existe** en el DTO de
familia hasta que alguien lo añade deliberadamente. El diseño falla del lado seguro.

### 4.3 Estudiante — vista de listado

`GET /students` no devuelve la ficha completa. Devuelve un resumen:

```json
{
  "id": "018f...",
  "firstName": "Danylo",
  "lastName": "Kovalenko",
  "photoUrl": "...",
  "status": "ACTIVE",
  "documentsSummary": { "pending": 2, "expiringSoon": 1, "expired": 0 }
}
```

**Por qué un DTO de listado aparte.** Si el listado devolviera fichas completas, cargar la pantalla
principal traería todos los bloques de todos los estudiantes: cuatro JOIN por fila y un payload
enorme para pintar una lista de nombres y fotos. Es el error de rendimiento más común en APIs CRUD.

---

## 5. Catálogo de endpoints

### 5.1 Autenticación

| Método | Ruta | Rol | Respuesta |
|---|---|---|---|
| POST | `/auth/login` | — | 204 + cookie |
| POST | `/auth/logout` | autenticado | 204 |
| GET | `/auth/me` | autenticado | 200 |
| POST | `/auth/password-reset` | — | 202 |
| POST | `/auth/password-reset/confirm` | — | 204 |
| POST | `/auth/activate` | — | 204 |

### 5.2 Usuarios

| Método | Ruta | Rol | Respuesta |
|---|---|---|---|
| GET | `/users` | ADMIN | 200 (paginado) |
| POST | `/users` | ADMIN | 201 + `Location` |
| GET | `/users/{id}` | ADMIN | 200 |
| PATCH | `/users/{id}` | ADMIN | 200 |
| POST | `/users/{id}/disable` | ADMIN | 204 |
| POST | `/users/{id}/enable` | ADMIN | 204 |

**No hay `DELETE /users/{id}`.** Borrar un usuario dejaría huérfanos los registros de auditoría y la
autoría de documentos. Se desactiva, que es lo que realmente se quiere: impedir el acceso.

### 5.3 Estudiantes

| Método | Ruta | Rol | Respuesta |
|---|---|---|---|
| GET | `/students` | ADMIN, GUARDIAN | 200 (paginado; la familia ve solo los suyos) |
| POST | `/students` | ADMIN | 201 + `Location` |
| GET | `/students/{id}` | ADMIN, GUARDIAN | 200 |
| PATCH | `/students/{id}` | ADMIN | 200 |
| DELETE | `/students/{id}` | ADMIN | 204 |
| PUT | `/students/{id}/sports-profile` | ADMIN | 200 |
| PUT | `/students/{id}/education` | ADMIN | 200 |
| PUT | `/students/{id}/housing` | ADMIN | 200 |
| PUT | `/students/{id}/photo` | ADMIN | 200 |

**`GET /students` con rol `GUARDIAN` no es un endpoint distinto.** Es el mismo, filtrado por el
servicio. Crear `/my-students` duplicaría lógica y dejaría dos sitios donde equivocarse.

**Los bloques usan `PUT`, no `PATCH`.** Son formularios completos que el administrador guarda de una
vez; el cliente envía siempre todos los campos del bloque. `PUT` con reemplazo total es más simple de
razonar y evita la ambigüedad de PATCH entre "no envío el campo" y "quiero ponerlo a null".

**La ficha principal usa `PATCH`**, porque se edita por secciones y raramente entera.

**`PUT /students/{id}/photo`** es `multipart/form-data`, no JSON.

### 5.4 Tutores

| Método | Ruta | Rol | Respuesta |
|---|---|---|---|
| GET | `/guardians` | ADMIN | 200 (paginado) |
| POST | `/guardians` | ADMIN | 201 + `Location` |
| GET | `/guardians/{id}` | ADMIN | 200 |
| PATCH | `/guardians/{id}` | ADMIN | 200 |
| PUT | `/students/{sid}/guardians/{gid}` | ADMIN | 200 (vincular o actualizar vínculo) |
| DELETE | `/students/{sid}/guardians/{gid}` | ADMIN | 204 (desvincular) |

**El vínculo es un recurso con `PUT` sobre la pareja de identificadores.** El cuerpo lleva
`relationship`, `isPrimary` y `hasAccess`. Usar `PUT` y no `POST` lo hace idempotente: repetir la
llamada no crea vínculos duplicados.

### 5.5 Contactos de emergencia

| Método | Ruta | Rol | Respuesta |
|---|---|---|---|
| GET | `/students/{id}/emergency-contacts` | ADMIN, GUARDIAN | 200 |
| POST | `/students/{id}/emergency-contacts` | ADMIN | 201 + `Location` |
| PATCH | `/emergency-contacts/{id}` | ADMIN | 200 |
| DELETE | `/emergency-contacts/{id}` | ADMIN | 204 |

### 5.6 Documentos

| Método | Ruta | Rol | Respuesta |
|---|---|---|---|
| GET | `/students/{id}/documents` | ADMIN, GUARDIAN | 200 (paginado) |
| POST | `/students/{id}/documents` | ADMIN, GUARDIAN | 201 + `Location` |
| GET | `/documents/{id}` | ADMIN, GUARDIAN | 200 (metadatos) |
| GET | `/documents/{id}/download` | ADMIN, GUARDIAN | 302 → URL prefirmada |
| PATCH | `/documents/{id}` | ADMIN | 200 |
| POST | `/documents/{id}/review` | ADMIN | 200 |
| DELETE | `/documents/{id}` | ADMIN | 204 |
| GET | `/documents/expiring` | ADMIN | 200 (los que caducan pronto, todos los estudiantes) |

**Representación de un documento:**

```json
{
  "id": "018f...",
  "studentId": "018f...",
  "category": "PASSPORT",
  "name": "Pasaporte Danylo.pdf",
  "contentType": "application/pdf",
  "sizeBytes": 482113,
  "status": "REVIEWED",
  "issuedAt": "2024-06-01",
  "expiresAt": "2029-06-01",
  "expired": false,
  "daysUntilExpiry": 1172,
  "uploadedBy": { "id": "018f...", "displayName": "Olena K." },
  "uploadedAt": "2026-02-10T09:00:00Z",
  "reviewedAt": "2026-02-11T12:30:00Z"
}
```

**`expired` y `daysUntilExpiry` son campos calculados**, no columnas. Coherente con la decisión del
modelo de datos de no guardar un estado que el calendario determina.

**La descarga devuelve 302, no el fichero.** El backend valida el permiso y redirige a una URL
prefirmada del almacenamiento válida 60 segundos. Así el fichero no atraviesa el servidor de
aplicación: menos memoria, menos ancho de banda y descargas más rápidas.

**Una familia puede subir documentos pero no revisarlos ni borrarlos.** Revisar es un acto de
gestión. Si una familia intenta `POST /documents/{id}/review` sobre un documento de su propio hijo,
recibe **403** (no 404): ya sabe que existe.

### 5.7 Subida de ficheros

```
POST /students/{id}/documents
Content-Type: multipart/form-data

file:      (binario)
category:  PASSPORT
name:      Pasaporte Danylo.pdf
issuedAt:  2024-06-01
expiresAt: 2029-06-01
```

Validaciones en servidor, nunca solo en el front:

- Tipos permitidos: PDF, JPEG, PNG. **Comprobados por contenido**, no por la extensión ni por el
  `Content-Type` que envía el cliente, que es trivial de falsear.
- Tamaño máximo 10 MB → si se supera, `413 Payload Too Large`
- Tipo no permitido → `415 Unsupported Media Type`
- El nombre original nunca se usa como clave de almacenamiento. La clave es
  `students/{studentId}/{uuid}`, y el nombre queda como metadato. Un fichero llamado
  `../../etc/passwd` no debe poder decidir dónde se guarda.

---

## 6. Códigos de estado

| Código | Cuándo | Ejemplo en este proyecto |
|---|---|---|
| 200 | Lectura o modificación con cuerpo de respuesta | `GET /students/{id}` |
| 201 | Recurso creado. **Siempre con cabecera `Location`** | `POST /students` |
| 202 | Aceptado pero aún no procesado | `POST /auth/password-reset` |
| 204 | Éxito sin cuerpo | `DELETE`, `logout` |
| 302 | Redirección a la URL prefirmada | `GET /documents/{id}/download` |
| 400 | Petición mal formada (JSON inválido, tipo incorrecto) | body no parseable |
| 401 | No autenticado o sesión caducada | cookie ausente |
| 403 | Autenticado, recurso visible, operación prohibida | familia intentando revisar |
| 404 | No existe, o no es suyo | estudiante de otra familia |
| 409 | Conflicto con el estado actual | email ya registrado |
| 413 | Fichero demasiado grande | subida > 10 MB |
| 415 | Tipo de fichero no admitido | subida de `.exe` |
| 422 | Sintaxis correcta, contenido inválido | `expiresAt` anterior a `issuedAt` |
| 429 | Demasiados intentos | fuerza bruta en login |
| 500 | Error no controlado | — |

**400 frente a 422.** Es la distinción que más se equivoca. 400 es "no entiendo lo que me mandas"
(JSON roto, un texto donde espero un número). 422 es "te entiendo perfectamente, pero lo que pides
no tiene sentido" (una fecha de caducidad anterior a la de emisión, un estudiante sin nombre).
Separarlos permite al front distinguir un error de programación de un error del usuario.

**409 frente a 422.** El 409 es específicamente un choque con el estado *actual* del servidor: el
email ya existe, el documento ya fue revisado. El 422 es un problema con los datos en sí, sin
depender de qué más haya en la base de datos.

**201 sin `Location` es un error.** El cliente acaba de crear algo y necesita saber dónde está.
Devolver solo el cuerpo obliga a extraer el id y construir la URL a mano.

---

## 7. Formato de errores

`ProblemDetail` (RFC 7807), que Spring Boot 3 trae de serie.

```json
{
  "type": "https://api.academia.example/errors/validation",
  "title": "Datos de entrada no válidos",
  "status": 422,
  "detail": "La fecha de caducidad no puede ser anterior a la de emisión",
  "instance": "/api/v1/students/018f.../documents",
  "timestamp": "2026-03-14T09:12:00Z",
  "errors": [
    { "field": "expiresAt", "message": "debe ser posterior a issuedAt" }
  ]
}
```

**Por qué un estándar y no un formato propio.** Dos razones prácticas: el front escribe un solo
manejador de errores en lugar de uno por endpoint, y cualquiera que se incorpore al proyecto no
tiene que aprender un formato inventado. Además `ProblemDetail` ya está integrado en Spring, así que
no hay que mantener clases propias.

**`errors` es una extensión** del estándar para los fallos de validación campo a campo, que es lo
que el front necesita para marcar los inputs en rojo.

Todo se centraliza en un `@RestControllerAdvice`. Regla firme: **ningún mensaje de error expone
detalles internos**. Nada de trazas de excepción, nombres de tabla ni consultas SQL. El detalle
técnico va al log con un identificador de correlación; la respuesta lleva ese identificador para
poder rastrearlo.

---

## 8. Paginación, orden y filtros

```
GET /students?page=0&size=20&sort=lastName,asc&status=ACTIVE&q=kova
```

Respuesta:

```json
{
  "content": [ ... ],
  "page": { "number": 0, "size": 20, "totalElements": 10, "totalPages": 1 }
}
```

**Toda colección se pagina, incluso con 10 estudiantes.** Añadir paginación después es un cambio que
rompe el contrato: el cliente que esperaba un array recibe un objeto. Con 10 registros no se nota;
con 200 es un rediseño.

**No se expone `Page` de Spring Data directamente.** Su serialización incluye campos internos
(`pageable`, `sort`, `first`, `last`, `numberOfElements`) que atan la API a la versión de Spring
Data: una actualización puede cambiar el JSON sin que nadie lo decida. Se envuelve en un
`PagedResponse<T>` propio.

**Límite máximo de `size`.** Se topa en 100 en el servidor. Sin límite, `?size=1000000` es una
denegación de servicio de un solo carácter.

Filtros de fase 1:

| Recurso | Filtros |
|---|---|
| `/students` | `status`, `q` (nombre o apellido) |
| `/documents` | `category`, `status`, `expiringBefore`, `expired` |
| `/users` | `role`, `status` |

---

## 9. Versionado

Versión en la ruta: `/api/v1/...`.

**Por qué en la ruta y no en una cabecera.** La cabecera (`Accept: application/vnd.academia.v2+json`)
es más purista, pero la ruta es visible en el navegador, en los logs, en las capturas y en Swagger.
Para un proyecto de este tamaño, la claridad gana a la pureza.

**Qué rompe el contrato y obliga a `v2`:**

- Eliminar o renombrar un campo de una respuesta
- Cambiar el tipo de un campo (`string` → `number`)
- Añadir un campo obligatorio en una petición
- Cambiar el significado de un código de estado
- Endurecer una validación existente

**Qué no lo rompe:**

- Añadir un campo nuevo a una respuesta
- Añadir un campo opcional a una petición
- Añadir un endpoint nuevo
- Añadir un valor nuevo a un enum **solo si** el cliente lo trata como desconocido sin romperse

**Ese último punto es una trampa clásica.** Añadir `COACH` al enum de roles rompe cualquier cliente
que haga un `switch` exhaustivo sin caso por defecto. Por eso el front debe tratar siempre los
valores de enum desconocidos con una rama por defecto.

En fase 1 no habrá `v2`. Pero el prefijo existe desde el primer día, porque añadirlo después
significa cambiar todas las URLs de golpe.

---

## 10. Documentación con OpenAPI

Dependencia: `springdoc-openapi-starter-webmvc-ui`.

```
/v3/api-docs        especificación JSON
/swagger-ui.html    interfaz navegable
```

Configuración mínima que marca la diferencia:

```java
@Bean
OpenAPI apiInfo() {
    return new OpenAPI()
        .info(new Info()
            .title("API de gestión de la academia")
            .version("1.0")
            .description("API privada de gestión de estudiantes, familias y documentación."))
        .components(new Components()
            .addSecuritySchemes("session",
                new SecurityScheme().type(APIKEY).in(COOKIE).name("SESSION")));
}
```

Prácticas concretas:

- **Anotar los códigos de error, no solo el caso feliz.** Un `@ApiResponse` por cada código que el
  endpoint puede devolver. Una API documentada que solo describe el 200 no sirve para escribir un
  cliente.
- **`@Schema(description = ...)` en los DTOs**, no en los controladores. La descripción vive junto al
  campo que describe.
- **Ejemplos reales en las peticiones**, con `@Schema(example = "...")`. Swagger con ejemplos es una
  herramienta usable; sin ellos, es una lista de nombres de campo.
- **Agrupar por `@Tag`** coherente con los recursos: `Autenticación`, `Estudiantes`, `Tutores`,
  `Documentos`.
- **Exportar el JSON en el CI** y guardarlo en el repositorio. Así cualquier cambio del contrato
  aparece en el diff de una pull request, que es la única forma de detectar una ruptura accidental
  antes de desplegarla.

**Para el portfolio:** un enlace a Swagger desplegado y funcionando en el README vale más que
cualquier descripción escrita. Es lo primero que abre un revisor técnico.

---

## 11. Resumen: los cinco puntos defendibles en entrevista

1. **404 en lugar de 403 para recursos ajenos**, y por qué la distinción importa.
2. **DTOs separados por rol** en lugar de una clase con campos anulables: el diseño falla del lado
   seguro cuando alguien añade un campo.
3. **Sesión con cookie en lugar de JWT**, por revocación inmediata, no por comodidad.
4. **Estado de flujo separado del estado temporal** en los documentos, corrigiendo la especificación
   original.
5. **Descarga por redirección a URL prefirmada**, para que los ficheros no atraviesen el servidor de
   aplicación.
