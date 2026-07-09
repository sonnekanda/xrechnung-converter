# PostgreSQL Init

Dieses Verzeichnis wird beim ersten Start nach
`/docker-entrypoint-initdb.d` gemountet. Das Anwendungsschema wird jedoch
**über Flyway** aus der `webapp` verwaltet
(`webapp/src/main/resources/db/migration`), damit Schema-Änderungen versioniert
und reproduzierbar sind.

Zusätzliche Init-Skripte (z.B. Extensions) können hier als `*.sql` abgelegt
werden; sie laufen nur bei einem **leeren** Datenverzeichnis.
