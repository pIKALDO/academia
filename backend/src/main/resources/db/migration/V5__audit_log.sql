-- Registro de auditoría. Ver docs/modelo-datos.md sección 6.

-- Sin claves foráneas, a propósito: si el registro apuntara a users(id) con
-- ON DELETE CASCADE, borrar un usuario borraría su rastro -justo lo contrario de lo que
-- sirve una auditoría-, y con RESTRICT sería imposible borrar a nadie. Se guardan los
-- identificadores desnudos.
--
-- student_id denormalizado para poder responder "todo lo que ha pasado con este
-- estudiante" con una sola consulta indexada, sin recorrer todas las entidades
-- relacionadas.
--
-- Clave numérica secuencial y no UUID: esta tabla nunca se expone en una URL y crece
-- mucho más que las demás, así que aquí el orden de inserción sí importa para el
-- rendimiento.
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

-- Solo inserción: se revoca UPDATE y DELETE sobre la tabla al rol de aplicación de
-- PostgreSQL. Un registro que se puede modificar no prueba nada.
--
-- CAVEAT pendiente de revisar: en este prototipo el rol que ejecuta Flyway es el mismo
-- que usa la aplicación en tiempo de ejecución (un único usuario en docker-compose), y ese
-- rol es el propietario de la tabla. En PostgreSQL el propietario de una tabla puede
-- UPDATE/DELETE siempre, al margen de lo que digan sus propios GRANT/REVOKE: este REVOKE
-- documenta la intención pero no la hace cumplir de verdad. Para que la restricción sea
-- efectiva hace falta un segundo rol de aplicación, sin propiedad sobre la tabla, que sea
-- el único con el que se conecta el backend en tiempo de ejecución (Flyway seguiría usando
-- el rol propietario). Se deja fuera de este corte para no meter una segunda credencial en
-- docker-compose sin que se haya pedido explícitamente.
REVOKE UPDATE, DELETE ON audit_log FROM CURRENT_USER;
