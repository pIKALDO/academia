-- Cuentas de acceso a la plataforma y recuperación de contraseña.
-- Ver docs/modelo-datos.md sección 2.

CREATE TYPE user_role AS ENUM ('ADMIN', 'GUARDIAN', 'STUDENT');
CREATE TYPE user_status AS ENUM ('ACTIVE', 'DISABLED', 'PENDING_ACTIVATION');

-- Un rol por usuario, no una tabla user_roles: con tres roles excluyentes, una tabla de
-- relación añade un JOIN a cada comprobación de permisos sin resolver ningún problema real.
-- Añadir roles nuevos (COACH, COORDINATOR) es solo extender el enum.
CREATE TABLE users (
    id              UUID PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    -- Nulable: un usuario recién creado por el administrador aún no tiene contraseña,
    -- se le envía un enlace de activación (estado PENDING_ACTIVATION).
    password_hash   VARCHAR(255),
    role            user_role NOT NULL,
    status          user_status NOT NULL DEFAULT 'PENDING_ACTIVATION',
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Índice sobre lower(email): evita que Ana@x.com y ana@x.com sean dos cuentas distintas.
CREATE UNIQUE INDEX ux_users_email ON users (lower(email));

CREATE TABLE password_reset_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- token_hash, no token: se guarda hasheado igual que una contraseña. Si alguien accede
    -- a la base de datos, no puede usar los tokens pendientes para secuestrar cuentas.
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_reset_tokens_user ON password_reset_tokens (user_id);
