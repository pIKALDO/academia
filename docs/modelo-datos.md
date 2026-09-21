# Modelo de datos — Plataforma de gestión de la academia

Diseñado para el alcance completo del proyecto. Solo se **construye** el bloque de fase 1;
el resto queda documentado y con su hueco reservado en el diseño, pero sin tablas creadas.

- Base de datos: PostgreSQL
- Migraciones: Flyway, desde el primer commit
- Nomenclatura: inglés, `snake_case` en base de datos, `camelCase` en la API
- Todas las marcas de tiempo son `TIMESTAMPTZ` (UTC en base de datos, conversión en el front)

---

## 1. Decisiones transversales

### 1.1 Claves primarias: UUID v7

Todas las entidades usan `UUID` como clave primaria, generado con **UUID v7** (ordenado por
tiempo) en lugar de v4.

**Por qué no un `BIGSERIAL`.** Los identificadores aparecen en las URLs de la API
(`/api/v1/students/{id}`). Con enteros secuenciales, cualquier usuario autenticado sabe cuántos
estudiantes hay y puede recorrerlos. Aunque la autorización devuelva 404 (ver 1.4), sigue
filtrando el volumen del sistema.

**Por qué v7 y no v4.** El v4 es completamente aleatorio, lo que fragmenta el índice B-tree en
inserciones: cada fila nueva va a una página distinta. El v7 lleva el timestamp en los bits altos,
así que mantiene la localidad de escritura y se comporta casi como un secuencial en el índice,
conservando la imprevisibilidad de cara al exterior.

### 1.2 Auditoría temporal en todas las tablas

`created_at`, `updated_at` en toda entidad. En JPA con `@CreationTimestamp` / `@UpdateTimestamp`,
no a mano en los servicios.

### 1.3 Borrado lógico solo donde aporta

`deleted_at` únicamente en `documents` y `students`. En el resto, borrado real.

**Por qué no en todas.** El borrado lógico universal obliga a filtrar `deleted_at IS NULL` en cada
consulta; olvidarlo una vez es un fallo silencioso que devuelve datos borrados. Se aplica solo donde
la recuperación tiene valor real (un documento borrado por error) y donde hace falta conservar
trazabilidad.

**Cuidado para el futuro:** cuando el proyecto pase a datos reales, el borrado lógico **no cumple**
una solicitud de supresión del RGPD. Habrá que implementar un borrado físico en cascada. El modelo
lo contempla con `students.left_at` (ver 3.1).

### 1.4 Regla de autorización: 404, no 403

Cuando una familia solicita un estudiante que no es suyo, la API responde **404 Not Found**, no 403.

Un 403 confirma que ese identificador existe y pertenece a alguien. Con 404, el atacante no puede
distinguir entre "no existe" y "no es tuyo". Esta regla se aplica a todos los recursos anidados bajo
un estudiante y se verifica con tests automáticos.

---

## 2. Acceso y usuarios

```sql
CREATE TYPE user_role AS ENUM ('ADMIN', 'GUARDIAN', 'STUDENT');
CREATE TYPE user_status AS ENUM ('ACTIVE', 'DISABLED', 'PENDING_ACTIVATION');

CREATE TABLE users (
    id              UUID PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255),
    role            user_role NOT NULL,
    status          user_status NOT NULL DEFAULT 'PENDING_ACTIVATION',
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_users_email ON users (lower(email));

CREATE TABLE password_reset_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_reset_tokens_user ON password_reset_tokens (user_id);
```

**Un rol por usuario, no una tabla `user_roles`.** Con tres roles excluyentes, una tabla de
relación añade un JOIN a cada comprobación de permisos sin resolver ningún problema real. Si más
adelante hacen falta roles múltiples (un entrenador que además es padre de un alumno), la migración
es directa: crear la tabla, volcar la columna y eliminarla.

Añadir roles nuevos (`COACH`, `COORDINATOR`) es solo extender el enum — `ALTER TYPE ... ADD VALUE`.

**`password_hash` es nulable** porque un usuario recién creado por el administrador todavía no tiene
contraseña: se le envía un enlace de activación. Estado `PENDING_ACTIVATION`.

**`token_hash`, no `token`.** El token de recuperación se guarda hasheado, igual que una contraseña.
Si alguien accede a la base de datos, no puede usar los tokens pendientes para secuestrar cuentas.

**El índice de email es sobre `lower(email)`**, no sobre la columna: evita que `Ana@x.com` y
`ana@x.com` sean dos cuentas distintas.

---

## 3. Personas

### 3.1 Estudiantes

```sql
CREATE TYPE student_status AS ENUM ('PROSPECT', 'ACTIVE', 'INACTIVE');

CREATE TABLE students (
    id              UUID PRIMARY KEY,
    user_id         UUID UNIQUE REFERENCES users(id) ON DELETE SET NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(150) NOT NULL,
    birth_date      DATE,
    nationality     CHAR(2),
    photo_key       VARCHAR(500),
    phone           VARCHAR(30),
    email           VARCHAR(255),
    address_line    VARCHAR(255),
    city            VARCHAR(100),
    postal_code     VARCHAR(20),
    country         CHAR(2),
    status          student_status NOT NULL DEFAULT 'ACTIVE',
    enrolled_at     DATE,
    left_at         DATE,
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_students_status ON students (status) WHERE deleted_at IS NULL;
```

**`user_id` es nulable.** Un estudiante existe en la plataforma aunque no tenga cuenta propia: el
portal del estudiante es fase 2. La entidad persona y la entidad cuenta son cosas distintas y
conviene no mezclarlas.

**`left_at` se construye aunque no se use todavía.** Es la fecha que dispara los plazos de
conservación cuando el proyecto pase a datos reales. Añadirla ahora cuesta una línea; añadirla
después obliga a inventar retroactivamente cuándo se fue cada alumno.

**`nationality` y `country` como ISO 3166-1 alfa-2** (`UA`, `ES`), no texto libre. Evita tener
"Ucrania", "ucraniana" y "UCRANIA" como tres valores distintos.

**`photo_key` no es una URL.** Es la clave del objeto en el almacenamiento privado. La URL se genera
firmada y temporal en cada petición (ver sección 5).

### 3.2 Tutores y su relación con los estudiantes

```sql
CREATE TYPE guardian_relationship AS ENUM ('FATHER', 'MOTHER', 'LEGAL_GUARDIAN', 'OTHER');

CREATE TABLE guardians (
    id          UUID PRIMARY KEY,
    user_id     UUID UNIQUE REFERENCES users(id) ON DELETE SET NULL,
    first_name  VARCHAR(100) NOT NULL,
    last_name   VARCHAR(150) NOT NULL,
    phone       VARCHAR(30),
    email       VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE student_guardians (
    student_id      UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    guardian_id     UUID NOT NULL REFERENCES guardians(id) ON DELETE CASCADE,
    relationship    guardian_relationship NOT NULL,
    is_primary      BOOLEAN NOT NULL DEFAULT false,
    has_access      BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (student_id, guardian_id)
);

CREATE INDEX ix_student_guardians_guardian ON student_guardians (guardian_id);
```

**Relación N:M, no una columna `father_id` / `mother_id` en `students`.** Dos hermanos en la
academia comparten padres: con columnas habría datos duplicados y actualizaciones inconsistentes.
Además hay casos reales que las columnas fijas no soportan: tutor legal que no es progenitor,
familias con un solo tutor, tutores que cambian.

**`has_access` separa el vínculo familiar del permiso de acceso.** Un progenitor puede figurar en la
ficha por ser contacto legal y aun así no tener acceso a la plataforma. Meter esto en un único campo
booleano de la tabla `guardians` sería incorrecto: el permiso es por estudiante, no por persona.

**El índice sobre `guardian_id` es el que sostiene la autorización.** Cada petición de una familia
resuelve "qué estudiantes puede ver este usuario" con esta consulta, así que se ejecuta
constantemente.

### 3.3 Contactos de emergencia

```sql
CREATE TABLE emergency_contacts (
    id              UUID PRIMARY KEY,
    student_id      UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    name            VARCHAR(200) NOT NULL,
    relationship    VARCHAR(100),
    phone           VARCHAR(30) NOT NULL,
    notes           VARCHAR(500),
    priority        SMALLINT NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_emergency_contacts_student ON emergency_contacts (student_id);
```

Tabla propia y no columnas en `students` porque son varios por estudiante y con orden de prioridad.
Nombre, relación y teléfono son datos personales ordinarios, no datos de salud.

---

## 4. Bloques de la ficha

Tres tablas en relación 1:1 con `students`, con la clave primaria compartida.

```sql
CREATE TYPE dominant_hand AS ENUM ('RIGHT', 'LEFT', 'AMBIDEXTROUS');

CREATE TABLE sports_profiles (
    student_id      UUID PRIMARY KEY REFERENCES students(id) ON DELETE CASCADE,
    level           VARCHAR(50),
    dominant_hand   dominant_hand,
    ranking         VARCHAR(50),
    previous_club   VARCHAR(200),
    history         TEXT,
    goals           TEXT,
    coach_notes     TEXT,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE education_info (
    student_id      UUID PRIMARY KEY REFERENCES students(id) ON DELETE CASCADE,
    school_name     VARCHAR(200),
    grade           VARCHAR(100),
    schedule_notes  TEXT,
    notes           TEXT,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE housing_info (
    student_id          UUID PRIMARY KEY REFERENCES students(id) ON DELETE CASCADE,
    address_line        VARCHAR(255),
    city                VARCHAR(100),
    responsible_name    VARCHAR(200),
    responsible_phone   VARCHAR(30),
    notes               TEXT,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**Por qué tablas separadas y no columnas en `students`.** Tres motivos, en orden de peso:

1. **Los permisos son por bloque, no por estudiante.** `coach_notes` no debe llegar nunca al portal
   de familias; `housing_info` no debe llegar al portal del estudiante. Con bloques separados, el
   control se hace a nivel de recurso y es verificable con un test por bloque. Con 40 columnas en una
   tabla, el filtrado se hace campo a campo al construir el DTO, que es exactamente donde se cuelan
   los fallos.
2. **Establece el patrón para `medical_data`.** Cuando (y si) llegue, entra como una tabla 1:1 más,
   sin tocar nada existente. Ese es el motivo por el que insistí en no meter datos de salud como
   columnas sueltas.
3. **Mantiene `students` legible.** La entidad central se consulta en cada listado; no conviene
   arrastrar campos que casi nunca se usan.

**El coste de esta decisión**, que también hay que saber defender: cargar la ficha completa son
cuatro JOIN en lugar de uno. Con este volumen es irrelevante, y el listado de estudiantes no los
necesita.

**`goals` y `coach_notes` como `TEXT` libre** en fase 1. Cuando llegue el seguimiento deportivo
estructurado (fase 3), pasan a `evaluations` y estos campos quedan como resumen.

---

## 5. Documentos

```sql
CREATE TYPE document_category AS ENUM (
    'PASSPORT', 'ID_CARD', 'VISA', 'HEALTH_INSURANCE',
    'AUTHORIZATION', 'SPORTS', 'ACADEMIC', 'OTHER'
);
CREATE TYPE document_status AS ENUM ('PENDING', 'RECEIVED', 'REVIEWED');

CREATE TABLE documents (
    id              UUID PRIMARY KEY,
    student_id      UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    category        document_category NOT NULL,
    name            VARCHAR(255) NOT NULL,
    storage_key     VARCHAR(500),
    content_type    VARCHAR(100),
    size_bytes      BIGINT,
    checksum_sha256 CHAR(64),
    status          document_status NOT NULL DEFAULT 'PENDING',
    issued_at       DATE,
    expires_at      DATE,
    uploaded_by     UUID REFERENCES users(id) ON DELETE SET NULL,
    uploaded_at     TIMESTAMPTZ,
    reviewed_by     UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at     TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_documents_student ON documents (student_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_documents_expiry ON documents (expires_at)
    WHERE deleted_at IS NULL AND expires_at IS NOT NULL;
```

### 5.1 Corrección al enunciado original: "caducado" no es un estado

La especificación planteaba `Pendiente → Recibido → Revisado → Caducado`. Eso mezcla dos cosas
distintas en un mismo campo:

- **Estado del flujo de trabajo**: lo cambia una persona. Pendiente → Recibido → Revisado.
- **Estado temporal**: lo determina el calendario. Un documento está caducado si
  `expires_at < hoy`.

Si se mezclan, aparecen incoherencias inmediatas: un documento revisado que caduca pierde la
información de que fue revisado, y un documento caducado al que le renuevan la fecha no sabe a qué
estado volver. Además obliga a un proceso que reescriba filas cada noche solo para mantener una
columna que se puede calcular.

**Solución:** `status` guarda solo el flujo; la caducidad se deriva de `expires_at` y se expone en
la API como un campo calculado (`expired`, `daysUntilExpiry`). Es la misma información para el
usuario, sin estado duplicado.

### 5.2 Registro de avisos enviados

```sql
CREATE TYPE notification_kind AS ENUM ('EXPIRY_30D', 'EXPIRY_7D', 'EXPIRED');

CREATE TABLE document_notifications (
    id              UUID PRIMARY KEY,
    document_id     UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    kind            notification_kind NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (document_id, kind, recipient_email)
);
```

**La restricción `UNIQUE` es el mecanismo de idempotencia.** El proceso nocturno consulta los
documentos que caducan pronto e intenta insertar el registro del aviso; si ya existe, la inserción
falla y el correo no se envía. Sin esto, cualquier reinicio del servidor, reintento o doble
ejecución del programador manda el mismo aviso otra vez. Es un fallo habitual y muy visible para el
usuario.

### 5.3 Acceso a los ficheros

Ningún fichero se sirve desde una URL pública. El flujo es:

1. `GET /api/v1/documents/{id}/download`
2. El backend comprueba el permiso sobre el estudiante propietario
3. Genera una URL prefirmada del almacenamiento con caducidad de 60 segundos
4. Responde `302` hacia esa URL

`checksum_sha256` permite detectar duplicados y verificar integridad tras una restauración de copia
de seguridad.

---

## 6. Auditoría

```sql
CREATE TABLE audit_log (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_user_id   UUID,
    actor_role      user_role,
    action          VARCHAR(80) NOT NULL,
    entity_type     VARCHAR(80) NOT NULL,
    entity_id       UUID,
    student_id      UUID,
    ip_address      INET,
    metadata        JSONB
);

CREATE INDEX ix_audit_student_time ON audit_log (student_id, occurred_at DESC);
CREATE INDEX ix_audit_actor_time ON audit_log (actor_user_id, occurred_at DESC);
```

**Sin claves foráneas, a propósito.** Si el registro apuntara a `users(id)` con `ON DELETE CASCADE`,
borrar un usuario borraría su rastro — justo lo contrario de lo que sirve una auditoría. Y con
`RESTRICT` sería imposible borrar a nadie. Se guardan los identificadores desnudos.

**`student_id` denormalizado** para poder responder "todo lo que ha pasado con este estudiante" con
una sola consulta indexada, sin recorrer todas las entidades relacionadas.

**Clave numérica secuencial y no UUID**, porque esta tabla nunca se expone en una URL y crece mucho
más que las demás: aquí el orden de inserción sí importa para el rendimiento.

**Solo inserción.** Se revoca `UPDATE` y `DELETE` sobre la tabla al rol de aplicación de PostgreSQL.
Un registro que se puede modificar no prueba nada.

Se escribe desde un aspecto (`@Around`) sobre los servicios, no llamando al logger en cada método:
así no se olvida ninguno.

---

## 7. Tablas reservadas — no se crean en fase 1

Documentadas aquí para que el diseño las contemple, pero sin migración.

| Tabla | Bloque | Notas de diseño |
|---|---|---|
| `medical_data` | Datos médicos | 1:1 con `students`. Campos cifrados a nivel de aplicación, endpoint propio, auditoría de lectura además de escritura. No entra sin revisión legal previa. |
| `student_needs` | Necesidades | Ojo: las necesidades dietéticas y médicas son datos de salud. Entra con `medical_data`, no antes. |
| `physical_measurements` | Datos físicos | Histórico (`student_id`, `measured_at`, `height_cm`, `weight_kg`), no columnas fijas: el valor está en la evolución. |
| `procedures` + `procedure_documents` | Trámites | Un trámite tiene estado, responsable, fechas y N documentos asociados. `procedure_documents` es N:M contra `documents`. |
| `events` + `event_participants` | Calendario | Entrenamientos, partidos, torneos, viajes. Participantes N:M con `students`. |
| `attendance` | Asistencia | `event_id` + `student_id` + estado. Depende de `events`. |
| `evaluations` + `evaluation_items` | Seguimiento deportivo | Evaluación periódica con ítems puntuables; permite gráficas de evolución. |
| `announcements` + `announcement_reads` | Avisos y comunicaciones | `announcement_reads` registra quién ha leído qué; es lo que permite el "pendiente de leer". |
| `field_permissions` | Permisos por campo | Lo más caro del backlog. Atraviesa toda la capa de servicio, no es un módulo aislado. |

---

## 8. Resumen de lo que se construye en fase 1

```
users ──┬── students ──┬── sports_profiles
        │              ├── education_info
        │              ├── housing_info
        │              ├── emergency_contacts
        │              └── documents ── document_notifications
        │
        └── guardians ──── student_guardians ──> students

password_reset_tokens ──> users
audit_log (sin relaciones)
```

**12 tablas.** Ninguna de ellas habrá que rehacerla para incorporar el backlog.

---

## 9. Migraciones

```
V1__baseline_users.sql
V2__students_and_guardians.sql
V3__profile_blocks.sql
V4__documents.sql
V5__audit_log.sql
```

Una migración por bloque funcional y no una sola inicial gigante: facilita revisar el historial y
permite desplegar el módulo de documentos por separado si hiciera falta.

`spring.jpa.hibernate.ddl-auto=validate` en todos los entornos. Nunca `update`: el esquema lo define
Flyway, y Hibernate solo comprueba que las entidades coinciden con lo que hay.
