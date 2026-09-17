-- Documentación de los estudiantes y registro de avisos de caducidad.
-- Ver docs/modelo-datos.md sección 5.

CREATE TYPE document_category AS ENUM (
    'PASSPORT', 'ID_CARD', 'VISA', 'HEALTH_INSURANCE',
    'AUTHORIZATION', 'SPORTS', 'ACADEMIC', 'OTHER'
);

-- "Caducado" no es un estado: status guarda solo el flujo de trabajo (lo cambia una
-- persona), y la caducidad es un estado temporal que determina el calendario
-- (expires_at < hoy). Mezclarlos rompe en cuanto un documento revisado caduca -perdería
-- la marca de revisado- o un documento caducado se renueva -no sabría a qué estado volver-,
-- y obligaría a un proceso que reescriba filas cada noche solo para mantener una columna
-- calculable. La caducidad se deriva de expires_at y se expone en la API como campo
-- calculado (expired, daysUntilExpiry), nunca como valor de este enum.
CREATE TYPE document_status AS ENUM ('PENDING', 'RECEIVED', 'REVIEWED');

CREATE TABLE documents (
    id              UUID PRIMARY KEY,
    student_id      UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    category        document_category NOT NULL,
    name            VARCHAR(255) NOT NULL,
    storage_key     VARCHAR(500),
    content_type    VARCHAR(100),
    size_bytes      BIGINT,
    -- Permite detectar duplicados y verificar integridad tras una restauración de copia
    -- de seguridad.
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

CREATE TYPE notification_kind AS ENUM ('EXPIRY_30D', 'EXPIRY_7D', 'EXPIRED');

-- La restricción UNIQUE es el mecanismo de idempotencia: el proceso nocturno intenta
-- insertar el registro del aviso, y si ya existe, la inserción falla y el correo no se
-- envía. Sin esto, cualquier reinicio del servidor, reintento o doble ejecución del
-- programador manda el mismo aviso otra vez.
CREATE TABLE document_notifications (
    id              UUID PRIMARY KEY,
    document_id     UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    kind            notification_kind NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (document_id, kind, recipient_email)
);
