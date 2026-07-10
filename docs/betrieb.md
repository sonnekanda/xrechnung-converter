# Betrieb, Backup & Wartung

## Start / Stop

```bash
docker compose up -d        # starten
docker compose ps           # Status je Dienst
docker compose logs -f webapp
docker compose down         # stoppen (Daten bleiben in Volumes)
```

Alle Dienste haben Health-Checks; `webapp` startet erst, wenn `postgres`,
`redis` und `invoice-engine` gesund sind (`depends_on: service_healthy`).

## Konfiguration

Ausschließlich über `.env` (Vorlage: `.example.env`). Nach Änderungen:
`docker compose up -d` (betroffene Container werden neu erstellt). Secrets
niemals ins Image/Repo – nur in die `.env` der Instanz.

## Datenhaltung / Volumes

| Volume | Inhalt |
|---|---|
| `pg-data` | PostgreSQL (Benutzer, Rechnungen, Stammdaten, Audit-Log) |
| `redis-data` | Job-Queue (persistiert via AOF) |
| `app-data` | Uploads und Arbeitsdaten unter `/data` |

## Backup / Restore

```bash
# Backup Datenbank
docker compose exec postgres pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" > backup.sql

# Backup Dateien
docker run --rm -v senapsa-erechnung_app-data:/data -v "$PWD":/backup alpine \
  tar czf /backup/app-data.tgz -C /data .

# Restore Datenbank
cat backup.sql | docker compose exec -T postgres psql -U "$POSTGRES_USER" "$POSTGRES_DB"
```

Ein regelmäßiger, verschlüsselter Off-Site-Backup-Job ist im Produktivbetrieb
vorzusehen (Scope „Später": vollständiges Backup/Restore-Konzept).

## Schema-Migrationen

Das DB-Schema wird über Flyway versioniert (`webapp/src/main/resources/db/migration`).
Neue Migrationen als `V2__...sql`, `V3__...sql` hinzufügen; sie laufen beim Start
der `webapp` automatisch.

## Beobachtbarkeit

- Strukturierte JSON-Logs nach stdout (`LOGLEVEL` aus `.env`), keine Secrets.
- Audit-Log je Rechnung (Tabelle `audit_event`) über alle Schritte.
- Health-Endpunkte: `webapp`/`invoice-engine` unter `/actuator/health`,
  Worker unter `/health`.

## Lasttest (Auslegung 200 Rechnungen/Tag, 5 parallele Nutzer)

Die asynchrone Extraktion über die Redis-Queue entkoppelt Upload und
Verarbeitung. Der beiliegende Lasttest simuliert parallele Nutzer:

```bash
python scripts/loadtest.py --count 200 --concurrency 5
```

Er lädt Rechnungen mit der konfigurierten Parallelität hoch und misst, bis die
Extraktion je Rechnung abgeschlossen ist (Durchsatz + Latenz p95).

**Messung** (30 Rechnungen, 5 parallele Nutzer, lokaler Stack): 30/30 erfolgreich,
0 Fehler, Durchsatz ~186 Rechnungen/Minute (~11 000/Stunde), Latenz median 1,6 s /
p95 1,8 s. Das Auslegungsziel von **200 Rechnungen pro Tag** wird damit um
Größenordnungen erfüllt. Bei Bedarf sind `extraction-worker`/`ocr-worker`
horizontal skalierbar (mehrere Instanzen konsumieren dieselbe Queue).
