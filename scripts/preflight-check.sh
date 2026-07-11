#!/usr/bin/env sh
# =============================================================================
# Preflight für die Ordner-Automatik.
#
# Prüft, dass als Host-Ordner konfigurierte Verzeichnisse (WATCH_HOST_INPUT_DIR /
# WATCH_HOST_OUTPUT_DIR aus der .env) tatsächlich existieren – BEVOR
# `docker compose up` läuft. Sonst legt Docker beim Bind-Mount stillschweigend
# einen leeren Ordner an (ggf. an falscher Stelle) und die Automatik "sieht"
# die Dateien nie. Leere Werte sind ok (dann internes Docker-Volume).
#
# Nutzung:
#   sh scripts/preflight-check.sh && docker compose up -d
# =============================================================================
set -eu

ENV_FILE="${1:-.env}"

# Wert eines Keys aus der .env lesen (ohne die Datei auszuführen -> sicher).
read_env() {
  [ -f "$ENV_FILE" ] || return 0
  # letzte Zuweisung gewinnt; Anführungszeichen entfernen
  grep -E "^[[:space:]]*$1=" "$ENV_FILE" 2>/dev/null | tail -1 \
    | sed -E "s/^[[:space:]]*$1=//; s/^[\"']//; s/[\"'][[:space:]]*$//"
}

fail=0
check() {
  name="$1"
  val="$(read_env "$name")"
  [ -z "$val" ] && return 0            # nicht gesetzt -> internes Volume, ok
  case "$val" in
    */*|*\\*|?:*) ;;                    # sieht nach Pfad aus -> prüfen
    *) return 0 ;;                      # bloßer Name -> kein Host-Pfad, ok
  esac
  if [ ! -d "$val" ]; then
    echo "  FEHLER: $name=\"$val\" existiert nicht." >&2
    echo "          Ordner anlegen oder Wert in $ENV_FILE korrigieren." >&2
    fail=1
  else
    echo "  ok: $name -> $val"
  fi
}

echo "Preflight Ordner-Automatik ($ENV_FILE):"
check WATCH_HOST_INPUT_DIR
check WATCH_HOST_OUTPUT_DIR

if [ "$fail" -ne 0 ]; then
  echo "Preflight fehlgeschlagen – Start abgebrochen." >&2
  exit 1
fi
echo "Preflight ok."
