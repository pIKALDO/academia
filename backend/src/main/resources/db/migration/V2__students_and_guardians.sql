-- Estudiantes, tutores y contactos de emergencia.
-- Ver docs/modelo-datos.md sección 3.

CREATE TYPE student_status AS ENUM ('PROSPECT', 'ACTIVE', 'INACTIVE');

CREATE TABLE students (
    id              UUID PRIMARY KEY,
    -- Nulable: un estudiante existe en la plataforma aunque no tenga cuenta propia (el
    -- portal del estudiante es fase 2). La entidad persona y la entidad cuenta son cosas
    -- distintas y conviene no mezclarlas.
    user_id         UUID UNIQUE REFERENCES users(id) ON DELETE SET NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(150) NOT NULL,
    birth_date      DATE,
    -- ISO 3166-1 alfa-2 (UA, ES), no texto libre: evita "Ucrania", "ucraniana" y "UCRANIA"
    -- como tres valores distintos.
    nationality     CHAR(2),
    -- Clave del objeto en el almacenamiento privado, no una URL: se firma y expira en cada
    -- petición (ver V4__documents.sql).
    photo_key       VARCHAR(500),
    phone           VARCHAR(30),
    email           VARCHAR(255),
    address_line    VARCHAR(255),
    city            VARCHAR(100),
    postal_code     VARCHAR(20),
    country         CHAR(2),
    status          student_status NOT NULL DEFAULT 'ACTIVE',
    enrolled_at     DATE,
    -- Se construye aunque no se use todavía: dispara los plazos de conservación cuando el
    -- proyecto pase a datos reales. Añadirla ahora cuesta una línea; añadirla después obliga
    -- a inventar retroactivamente cuándo se fue cada alumno.
    left_at         DATE,
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_students_status ON students (status) WHERE deleted_at IS NULL;

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

-- Relación N:M, no una columna father_id / mother_id en students: dos hermanos comparten
-- padres, y hay casos que las columnas fijas no soportan (tutor legal que no es progenitor,
-- un solo tutor, tutores que cambian).
CREATE TABLE student_guardians (
    student_id      UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    guardian_id     UUID NOT NULL REFERENCES guardians(id) ON DELETE CASCADE,
    relationship    guardian_relationship NOT NULL,
    is_primary      BOOLEAN NOT NULL DEFAULT false,
    -- has_access separa el vínculo familiar del permiso de acceso: un progenitor puede
    -- figurar en la ficha por ser contacto legal y aun así no tener acceso a la plataforma.
    -- El permiso es por estudiante, no por persona, así que no puede ir en guardians.
    has_access      BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (student_id, guardian_id)
);

-- El índice sobre guardian_id es el que sostiene la autorización: cada petición de una
-- familia resuelve "qué estudiantes puede ver este usuario" con esta consulta.
CREATE INDEX ix_student_guardians_guardian ON student_guardians (guardian_id);

-- Tabla propia y no columnas en students porque son varios contactos por estudiante y con
-- orden de prioridad. Nombre, relación y teléfono son datos personales ordinarios, no datos
-- de salud.
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
