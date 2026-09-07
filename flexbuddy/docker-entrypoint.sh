#!/bin/sh
set -eu

case "${DATABASE_URL:-}" in
    "")
        ;;
    postgresql://*|postgres://*)
        : "${DB_NAME:?DB_NAME is required when DATABASE_URL is set}"
        database_address="${DATABASE_URL#*://}"
        database_address="${database_address#*@}"
        database_host_port="${database_address%%/*}"
        export DB_URL="jdbc:postgresql://${database_host_port}/${DB_NAME}"
        ;;
    *)
        echo "DATABASE_URL must begin with postgresql:// or postgres://" >&2
        exit 1
        ;;
esac

exec java -jar /app/app.jar
