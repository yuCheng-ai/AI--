#!/usr/bin/env bash
set +x
set -euo pipefail

: "${DRAMA_DB_APP_PASSWORD:?DRAMA_DB_APP_PASSWORD must be set}"
# psql reads the secret from its environment and quotes it as an SQL literal.
# Never expand the password into shell text, command arguments, or logs.
psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --no-psqlrc --set ON_ERROR_STOP=1 <<'SQL'
\getenv app_password DRAMA_DB_APP_PASSWORD
\getenv database_name POSTGRES_DB
CREATE ROLE drama_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT PASSWORD :'app_password';
GRANT CONNECT ON DATABASE :"database_name" TO drama_app;
SQL
