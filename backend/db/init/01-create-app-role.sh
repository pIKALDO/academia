#!/bin/sh
# Crea el rol de aplicación, separado del rol propietario que ejecuta las migraciones de
# Flyway. Se ejecuta una única vez, en la primera inicialización del contenedor de
# PostgreSQL (docker-entrypoint-initdb.d solo corre sobre un data dir vacío).
#
# Este mismo fichero se monta tanto en docker-compose.yml (desarrollo local) como en el
# contenedor de Testcontainers (AbstractIntegrationTest), para no mantener dos copias.
#
# Los privilegios del rol se gestionan en V6__app_role.sql, versionado con Flyway. Aquí solo
# se crea el rol: su contraseña es un secreto y no debe pasar por un fichero de migración
# versionado en git, ni siquiera a través de un placeholder de Flyway.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE ROLE "$POSTGRES_APP_USER" LOGIN PASSWORD '$POSTGRES_APP_PASSWORD';
EOSQL
