#!/bin/sh
# Startet den KoSIT-Daemon als non-root Benutzer "app".
set -eu

if [ "$(id -u)" = "0" ]; then
    exec gosu app "$@"
fi
exec "$@"
