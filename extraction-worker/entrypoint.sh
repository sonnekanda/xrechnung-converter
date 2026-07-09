#!/bin/sh
# Startet den Dienst als non-root Benutzer "app". Daten ausschliesslich unter /data.
set -eu

mkdir -p /data
# Falls Volume als root gemountet wurde: Eigentuemer korrigieren, dann Rechte abgeben.
if [ "$(id -u)" = "0" ]; then
    chown -R app:app /data 2>/dev/null || true
    exec gosu app "$@"
fi
exec "$@"
