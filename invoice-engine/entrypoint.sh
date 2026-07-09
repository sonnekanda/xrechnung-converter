#!/bin/sh
# Startet die invoice-engine als non-root Benutzer "app". Daten unter /data.
set -eu

mkdir -p /data
if [ "$(id -u)" = "0" ]; then
    chown -R app:app /data 2>/dev/null || true
    exec gosu app "$@"
fi
exec "$@"
