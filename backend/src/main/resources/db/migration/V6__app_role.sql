-- Segundo rol de PostgreSQL: separa quién ejecuta las migraciones (el propietario de las
-- tablas, usado solo por Flyway) de quién usa la aplicación en tiempo de ejecución.
--
-- Es lo que hace real el "solo inserción" de audit_log documentado en V5: el propietario de
-- una tabla puede modificarla al margen de sus propios GRANT/REVOKE, así que esa
-- restricción solo tiene efecto sobre un rol que no sea el propietario.
--
-- El rol en sí no lo crea esta migración: lo crea la infraestructura (el script de
-- inicialización de PostgreSQL en docker-compose.yml para desarrollo local; el proveedor de
-- base de datos en servidor), porque su contraseña es un secreto y no debe pasar por un
-- fichero versionado en git, ni siquiera a través de un placeholder de Flyway. Esta
-- migración asume que el rol "${appDbUser}" ya existe y solo gestiona sus privilegios, que
-- no son secretos.

GRANT USAGE ON SCHEMA public TO "${appDbUser}";

-- Lectura y escritura completas en el resto de tablas: la aplicación necesita poder crear,
-- actualizar (incluidos los borrados lógicos de students/documents) y borrar filas.
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO "${appDbUser}";

-- Las columnas GENERATED ALWAYS AS IDENTITY (audit_log.id) usan una secuencia interna: sin
-- USAGE sobre ella, los INSERT fallarían al no poder avanzar el generador de id.
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO "${appDbUser}";

-- Privilegios por defecto para tablas/secuencias que se creen en migraciones futuras
-- ejecutadas por este mismo rol propietario, para no tener que repetir estos GRANT en cada
-- corte que añada una tabla nueva.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO "${appDbUser}";
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO "${appDbUser}";

-- audit_log es la excepción: el rol de aplicación solo puede leer e insertar. Aquí el
-- REVOKE sí es efectivo, a diferencia del de V5, porque quien lo ejecuta (el propietario)
-- no es el mismo rol al que se le revoca.
REVOKE UPDATE, DELETE ON audit_log FROM "${appDbUser}";
