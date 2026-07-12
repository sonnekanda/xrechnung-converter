# Entwicklungsrichtlinie für eigene Softwareprodukte

> **Status-Kopie (Stand 2026-07-12)** — Abgleich der Anforderungen mit dem umgesetzten Stand.
> Original unverändert unter `aufgabe/`. Legende: **[x]** erfüllt · **🟡** teilweise · **❌** offen.


> **Zweck dieses Dokuments**
> Diese Richtlinie definiert verbindlich, wie bei uns eigene Softwareprodukte aufgebaut, ausgeliefert und betrieben werden. Sie gilt unabhängig von der Programmiersprache (Java, Python, …). Jeder Entwickler entwickelt neue Produkte **nach diesen Vorgaben**.
>
> Als **Referenzimplementierung** dient unser Produkt **BFTS** (Better Full Text Search). Wo es hilft, wird auf die konkrete BFTS-Umsetzung verwiesen.

---

## 0. Leitprinzipien (das „Warum")

| Prinzip | Bedeutung |
|---|---|
| **Container-First** | Jede Anwendung läuft als Docker-Container. Keine Installation „direkt auf dem Server". |
| **Stack statt Einzelteile** | Das Produkt inkl. aller Abhängigkeiten (DB, Suchindex, …) wird als **ein zusammenhängender Stack** (Docker Compose) ausgeliefert. |
| **Eine zentrale Konfiguration** | Die gesamte Konfiguration eines Stacks läuft über **eine `.env`-Datei**. Kein Konfig-Wirrwarr über viele Dateien. |
| **Konfiguration ≠ Code** | Geheimnisse und umgebungsspezifische Werte stehen **nie** im Code oder im Image, sondern ausschließlich in der `.env`. |
| **Alles in Git** | Quellcode, Beschreibung, Dokumentation **und** Docker-Images liegen in unserem selbstgehosteten **GitLab (`git.senapsa.io`)**. Ein Produkt = ein Repository als zentraler Wahrheitsort. |
| **Reproduzierbar** | Build und Versionierung sind automatisiert (CI/CD). Gleicher Commit → gleiches Image → gleiche Version. |
| **Beobachtbar** | Jede Anwendung protokolliert strukturiert und stellt einen Health-Check sowie (wo sinnvoll) Metriken bereit. |
| **Lizenziert (falls lizenzpflichtig)** | Vor der finalen Produktivphase muss ein **Lizenzcontroller** den erlaubten Funktionsumfang durchsetzen. |
| **Sicher per Default** | Non-root-Container, keine Default-Passwörter im Auslieferungszustand, TLS-fähig. |

---

## 1. Containerisierung (Pflicht)

### 1.1 Jede Anwendung ist ein Container
Die Produktanwendung **muss** als Docker-Image baubar und startbar sein. Es gibt kein „läuft nur auf meinem Rechner / nur mit lokal installiertem JDK".

### 1.2 Dockerfile-Vorgaben
Das `Dockerfile` liegt im jeweiligen Anwendungsverzeichnis (bei BFTS: [`webapp/Dockerfile`](../webapp/Dockerfile)).

Verbindliche Regeln:

1. **Multi-Stage-Build.** Build-Werkzeuge (z. B. Maven/Gradle, Node) landen **nicht** im finalen Image. Es wird nur das Lauf-Artefakt kopiert.
2. **Schlankes, gepflegtes Base-Image.** Offizielle, schlanke Images verwenden (`*-slim`, `eclipse-temurin:21-jre`, …). Beim Build `apt-get upgrade` für Sicherheits-Patches.
3. **Non-root-Container.** Es wird ein dedizierter Benutzer mit fester UID/GID angelegt; die Anwendung läuft als dieser Benutzer, **nicht** als root.
4. **Klarer Entrypoint.** Ein `entrypoint.sh` darf nur das tun, was zur Laufzeit nötig ist (z. B. Volume-Rechte korrigieren) und dann via `gosu`/`exec` auf den App-User wechseln.
5. **Schreibbare Daten nur in Volumes** (z. B. `/data`), nie ins Image-Dateisystem.

**Java-Beispiel** (analog zum BFTS-Muster):

```dockerfile
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src/ ./src/
RUN mvn -B clean package -DskipTests

# Stage 2: Runtime (schlank, kein Maven/JDK-Build im finalen Image)
FROM eclipse-temurin:21-jre
ARG APP_UID=10001
ARG APP_GID=10001
RUN groupadd -g ${APP_GID} appgroup && \
    useradd -u ${APP_UID} -g appgroup -m -s /bin/sh appuser
WORKDIR /app
COPY --from=build /build/target/app.jar /app/app.jar
RUN mkdir -p /data/logs && chown -R appuser:appgroup /app /data
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]
CMD ["java", "-jar", "/app/app.jar"]
```

```sh
#!/bin/sh
# entrypoint.sh – Volume-Rechte zur Laufzeit fixen, dann auf appuser droppen
chown -R appuser:appgroup /data
exec gosu appuser "$@"
```

### 1.3 Health-Check (Pflicht)
Jeder Service definiert einen **Health-Check** (im Dockerfile oder Compose). Andere Services warten per `depends_on … condition: service_healthy` darauf. Eine Java-App stellt dafür z. B. einen `/health`-Endpunkt bereit (Spring Boot Actuator: `/actuator/health`).

---

## 2. Stack & Drittsoftware (Docker Compose)

### 2.1 Das Produkt ist ein Stack
Drittsoftware (Datenbank, Suchindex, Cache, …) wird **nicht** vom Kunden separat installiert, sondern ist Teil des ausgelieferten Stacks. Bei BFTS: OpenSearch + PostgreSQL + die Webapp – siehe [`docker-compose.yml`](../docker-compose.yml).

### 2.2 Verbindliche Compose-Regeln

1. **Eigenes Bridge-Netzwerk.** Alle Services kommunizieren intern über ein benanntes Netzwerk (`<produkt>-net`). Drittsoftware wird **nicht** nach außen exponiert – nur der App-Port wird gemappt.
2. **Benannte Volumes** für persistente Daten (DB, Index). Daten überleben Container-Neustarts/-Updates.
3. **`restart: unless-stopped`** für alle Services.
4. **Pinned Image-Tags.** Drittsoftware mit einer definierten Major-Version pinnen (`postgres:17`, `opensearchproject/opensearch:3`) – **kein** ungepinntes `latest` für Fremdsoftware.
5. **Healthchecks + `depends_on`.** Die App startet erst, wenn ihre Abhängigkeiten gesund sind.
6. **Alle Werte aus der `.env`** mit sinnvollen Defaults: `${VAR:-default}`.

### 2.3 Referenz: BFTS-Stack (Auszug)

```yaml
services:
  opensearch:                                   # Drittsoftware, gepinnt, nicht exponiert
    image: opensearchproject/opensearch:3
    restart: unless-stopped
    environment:
      - discovery.type=single-node
      - "OPENSEARCH_INITIAL_ADMIN_PASSWORD=${OPENSEARCH_INITIAL_ADMIN_PASSWORD}"
    volumes:
      - opensearch-data:/usr/share/opensearch/data
    networks: [bfts-net]
    healthcheck:
      test: ["CMD-SHELL", "curl -s -k https://localhost:9200 | grep -q cluster_name || exit 1"]
      interval: 20s
      timeout: 10s
      retries: 10

  webapp:                                       # Unser Produkt
    image: git.madgeeks.de/senapsa/bfts:latest
    restart: unless-stopped
    ports:
      - "${WEBAPP_PORT:-8550}:8000"             # nur die App wird gemappt
    env_file: [.env]                            # zentrale Konfiguration
    networks: [bfts-net]
    depends_on:
      opensearch:  { condition: service_healthy }
      postgres:    { condition: service_healthy }

volumes:
  opensearch-data:
  postgres-data:

networks:
  bfts-net: { driver: bridge }
```

> **Hinweis zu Varianten:** Wenn ein Produkt austauschbare Drittkomponenten anbietet (BFTS: PostgreSQL **oder** SQLite), wird das über separate Compose-Dateien gelöst (`docker-compose.postgres.yml`, `docker-compose.sqlite.yml`), nicht über if/else im Code.

---

## 3. Zentrale Konfiguration (`.env`)

### 3.1 Eine Datei, ein Wahrheitsort
Die **gesamte** Stack-Konfiguration lebt in **einer** `.env` im Stack-Wurzelverzeichnis. Die App liest sie über `env_file` / Umgebungsvariablen. Es gibt **keine** zweite, parallele Konfigdatei mit Geheimnissen.

### 3.2 Pflichtregeln

1. **`.env` ist niemals im Git.** In `.gitignore` eintragen. Stattdessen wird eine kommentierte Vorlage **`.example.env`** eingecheckt (siehe [`.example.env`](../.example.env)).
2. **Die Vorlage ist die Doku.** Jede Variable wird in `.example.env` mit einem Kommentar erklärt (Zweck, erlaubte Werte, Default, Sicherheitshinweis).
3. **Keine Default-Geheimnisse im Auslieferungszustand.** Passwörter/Secrets haben Platzhalter (`changeme`, `dein-…`), nie produktiv nutzbare Defaults.
4. **Logische Gruppierung** mit Kommentarblöcken: Lizenzierung, Sicherheit, Datenbank, Drittsoftware, Ports, Logging/Performance.
5. **Typisierte, validierte Konfiguration im Code.** Konfiguration wird beim Start in ein typisiertes Objekt geladen und validiert; Pflichtwerte ohne Default lassen den Start fehlschlagen (Fail-Fast).

### 3.3 Referenz BFTS
- Vorlage: [`.example.env`](../.example.env) – gruppiert nach `--- Lizenzierung ---`, `--- Sicherheit ---`, `--- OpenSearch ---`, `--- Datenbank ---`, `--- Logging & Performance ---`.
- Typisierte Validierung: [`webapp/app/config/settings.py`](../webapp/app/config/settings.py) (Pydantic `BaseSettings`). Pflichtfelder ohne Default (`abas_restapi_base_url`) erzwingen einen Wert.

### 3.4 Java-Entsprechung
Bei Spring Boot übernimmt die `.env` → Umgebungsvariablen die Rolle der externen Konfiguration. Im Code wird typisiert gebunden:

```java
@ConfigurationProperties(prefix = "opensearch")
@Validated
public record OpenSearchConfig(
    @NotBlank String host,
    @Min(1) int port,
    @NotBlank String adminPassword   // OPENSEARCH_ADMIN_PASSWORD aus .env
) {}
```
Umgebungsvariablen (`SESSION_SECRET_KEY`) mappen automatisch auf Properties (`session.secret-key`). **Niemals** Secrets in `application.yml` einchecken.

---

## 4. Logging & Protokollierung (Pflicht)

Jede Anwendung **muss** ordnungsgemäß protokollieren.

### 4.1 Pflichtregeln

1. **Zentrales, konfigurierbares Log-Level** über `.env` (`LOGLEVEL=INFO`), nicht hartkodiert.
2. **Strukturiert & einheitlich.** Pro Eintrag: Zeitstempel, Level, Logger/Modul, Nachricht. In containerisierten Umgebungen primär nach **stdout/stderr** (Docker übernimmt das Sammeln). Optional zusätzlich rotierende Datei-Logs unter `/data/logs`.
3. **Named Logger pro Modul** statt globalem print/`System.out` (Java: SLF4J + Logback; `private static final Logger log = LoggerFactory.getLogger(...)`).
4. **Keine Geheimnisse im Log.** Passwörter, Tokens, Lizenz-Secrets, personenbezogene Daten werden nicht (bzw. maskiert) geloggt.
5. **Audit-Log für sicherheitsrelevante Ereignisse.** Login-Erfolg/-Fehler, Lockouts, Admin-Änderungen, Lizenzzustandswechsel werden nachvollziehbar protokolliert.
6. **Sinnvolle Level:** `ERROR` (Aktion nötig), `WARN` (auffällig, aber selbstheilend – z. B. Lizenz-Grace), `INFO` (Geschäftsereignisse), `DEBUG` (nur Diagnose).

### 4.2 Referenz BFTS
Durchgängig `logging.getLogger(__name__)` pro Modul, Level über `settings.loglevel` / `LOGLEVEL`, Log-Verzeichnis `/data/logs` wird im Dockerfile angelegt.

### 4.3 Metriken & Health (empfohlen)
Wo sinnvoll, einen `/metrics`-Endpunkt (Prometheus) bereitstellen – bei sensiblen Deployments per Bearer-Token absicherbar (BFTS: `METRICS_TOKEN`). Health-Endpunkt ist Pflicht (siehe 1.3).

---

## 5. Lizenzcontroller (Pflicht bei lizenzpflichtigen Produkten)

> Gilt **nur**, wenn das Produkt lizenzpflichtig ist. Dann **muss** vor der finalen Produktivphase ein funktionierender Lizenzcontroller integriert sein.

### 5.1 Anforderungen

1. **Lizenzschlüssel kommt aus der `.env`** (BFTS: `SENAPSA_LICENSE_KEY`, `SENAPSA_LICENSING_SERVER_URL`). Format klar definiert (z. B. `demo` oder `SPSA-{Tenant-UUID}-{API-Secret}`).
2. **Zentraler Lizenz-Server.** Der Client validiert periodisch gegen unseren Lizenzdienst und erhält den erlaubten Funktionsumfang (z. B. Dokument-/User-Limits, Feature-Flags wie „KI an/aus").
3. **Durchsetzung der Limits** lokal in der App (z. B. Concurrent-User-Limit beim Login, Mengenlimits beim Import). Überschreitung → harte Ablehnung.
4. **Robustheit gegen Netzwerkausfälle (Grace Period).** Bei vorübergehendem Ausfall des Lizenzservers läuft die App eine definierte Kulanzzeit weiter (BFTS: 24 h ab letzter erfolgreicher Prüfung). Eine **explizite** Ablehnung (403 / Limit überschritten) wirkt dagegen sofort.
5. **Persistenter Lizenzzustand**, damit Neustarts und mehrere Worker-Prozesse denselben Zustand sehen.
6. **Demo-Modus** mit zeitlicher Begrenzung und sichtbarem Banner (BFTS: 14 Tage).
7. **Sauberes Fehlerverhalten.** Bei ungültiger Lizenz: klare Fehlerseite/Meldung statt undefiniertem Verhalten (BFTS: `license_error.html`).

### 5.2 Referenz BFTS
[`webapp/app/src/services/license.py`](../webapp/app/src/services/license.py): `is_active()` mit Demo-Ablauf, `license_valid`, Hard-Reject und 24-h-Grace; Zustand wird persistiert (`license_state`), damit nicht-synchronisierende Web-Worker den Master spiegeln.

### 5.3 Zustands-Logik (verbindliches Muster)

```
aktiv? =
    demo_modus      → jetzt < demo_ablauf
    lizenz_gültig   → true
    hart_abgelehnt  → false            (sofort sperren)
    sonst (Netzfehler) → jetzt < letzter_ok + grace_period   (Kulanz)
```

---

## 6. Quellcodeverwaltung & GitLab (`git.senapsa.io`)

Alles wird mit **Git** verwaltet und in unserem **selbstgehosteten GitLab** unter **`git.senapsa.io`** abgelegt. Es gibt keine Quellen, Doku oder Images außerhalb dieser Plattform.

### 6.1 Ein Repository = ein zentraler Wahrheitsort
Pro Produkt **ein** GitLab-Repository, das **alles** enthält bzw. bereitstellt:

| Inhalt | Wo im GitLab |
|---|---|
| **Quellcode** | Repository (Branches, MRs, Tags) |
| **Produktbeschreibung** | `README.md` im Repo-Root |
| **Dokumentation** | `Dev_Documents/` bzw. GitLab Wiki / Pages |
| **Docker-Images** | GitLab **Container Registry** des Repos (`registry.senapsa.io/<gruppe>/<produkt>`) |
| **CI/CD-Pipeline** | `.gitlab-ci.yml` im Repo-Root |
| **Releases & Changelog** | GitLab Releases, an Git-Tags geknüpft |

### 6.2 Verbindliche Git-Regeln

1. **Alles versioniert.** Quellcode, `docker-compose.yml`, `.example.env`, Dockerfile, CI-Pipeline, Dokumentation – alles liegt im Repo. (Ausnahme: die echte `.env` – siehe 3.2.)
2. **Branching.** `main` ist immer baubar/auslieferbar. Entwicklung in Feature-Branches, Integration über **Merge Requests** mit Review.
3. **Aussagekräftige Commits**, Versions-Bumps und Changelog-Pflege gehören zum Feature, nicht in einen Nachgang.
4. **Tags = Releases.** Jeder Release ist ein Git-Tag `v<version>` (siehe 7.1) und ein GitLab Release-Eintrag.
5. **Keine Geheimnisse im Git.** Secrets ausschließlich über `.env` (lokal) bzw. **GitLab CI/CD Variables** (Pipeline). `.env` steht in `.gitignore`.
6. **Images gehören ins GitLab.** Jedes gebaute Docker-Image wird in die **GitLab Container Registry** des Repos gepusht – nicht in fremde/lokale Registries.

### 6.3 CI/CD läuft in GitLab
Die Pipeline ist `.gitlab-ci.yml` und nutzt GitLab Runner. Sie baut, taggt, scannt und pusht das Image in die GitLab Container Registry (Details siehe 7).

> **Migrationshinweis:** Die BFTS-Referenz nutzt historisch Gitea (`git.madgeeks.de`) mit `.gitea/workflows/*`. Der **verbindliche Standard für neue Produkte** ist GitLab unter `git.senapsa.io` mit `.gitlab-ci.yml`. Die Konzepte (Versions-Ableitung, Dreifach-Tagging, Trivy-Scan) bleiben identisch.

---

## 7. Versionierung & CI/CD

### 7.1 Single Source of Truth für die Version
Es gibt **genau eine** Stelle, an der die Version steht (BFTS: `APP_VERSION` in [`settings.py`](../webapp/app/config/settings.py)). Java: typischerweise `pom.xml` / `build.gradle`.

### 7.2 Automatischer Build nach Push
Push auf `main` → CI baut das Image, leitet die Version aus der Single Source ab und taggt **dreifach**: `:latest`, `:<version>`, `:v<version>`. Referenz-Konzept: [`.gitea/workflows/docker-build.yml`](../.gitea/workflows/docker-build.yml) (bei neuen Produkten als `.gitlab-ci.yml` umgesetzt).

### 7.3 Sicherheits-Scan im Build (Pflicht)
Jedes gebaute Image wird auf Schwachstellen gescannt (BFTS: **Trivy**, Severity `CRITICAL,HIGH`). Funde werden adressiert, nicht ignoriert.

### 7.4 Registry & Promotion
Images landen in der **GitLab Container Registry** (`registry.senapsa.io/<gruppe>/<produkt>`). Trennung von Test/Prod erfolgt über einen Promote-Schritt (BFTS-Referenz: `promote-to-prod.yml`), nicht über manuelles Re-Tagging am Server.

---

## 8. Sicherheit (Querschnitt)

1. **Non-root-Container** (siehe 1.2).
2. **Keine produktiven Default-Secrets**; Pflicht-Secrets erzwingen (Fail-Fast beim Start, wenn leer/Default).
3. **TLS-fähig** ausliefern (Zertifikatspfade über `.env`, optionaler HTTP→HTTPS-Redirect). Bei Reverse-Proxy-Betrieb: vertrauenswürdige Proxys konfigurierbar (`TRUSTED_PROXIES`) gegen `X-Forwarded-For`-Spoofing.
4. **Session-Härtung** (Secret-Key aus `.env`, Inaktivitäts-Timeout, Account-Lockout, Rate-Limiting).
5. **SSO/OIDC-fähig**, wo Kundenumgebungen das erfordern (BFTS: Keycloak via `AUTH_*`).
6. **Geheimnisse nur über `.env`/Secrets**, nie im Image oder Git.

---

## 9. Projektstruktur & Auslieferung (Checkliste für neue Produkte)

Ein neues Produkt-Repo enthält mindestens:

```
<produkt>/
├── docker-compose.yml              # Stack-Definition (App + Drittsoftware)
├── docker-compose.<variante>.yml   # optionale Varianten (z. B. DB-Engine)
├── .example.env                    # kommentierte Konfig-Vorlage (eingecheckt)
├── .gitignore                      # enthält .env
├── LICENSE
├── README.md                       # Produktbeschreibung + Installation/Betrieb
├── .gitlab-ci.yml                  # CI: Build, Versions-Tagging, Trivy-Scan, Push in Registry
├── Dev_Documents/                  # Dokumentation (alternativ GitLab Wiki/Pages)
└── <app>/
    ├── Dockerfile                  # Multi-Stage, non-root, Healthcheck
    ├── entrypoint.sh
    └── <quellcode>/
        ├── config/                 # zentrale, typisierte Konfiguration
        ├── services/license.*      # Lizenzcontroller (falls lizenzpflichtig)
        └── ...
```

### Definition of Done (Akzeptanzkriterien)

- [x] Anwendung startet als Docker-Container, läuft als non-root.  *(✅ alle 5 Eigen-Services non-root (gosu))*
- [x] Kompletter Stack (App + Drittsoftware) startet mit `docker compose up` aus einer `.env`.  *(✅ ein Compose-File, eine .env)*
- [x] Drittsoftware ist gepinnt, im internen Netzwerk, mit Healthcheck; nur der App-Port ist exponiert.  *(✅ postgres:17.2-alpine, redis:7.4-alpine; nur App-Port exponiert)*
- [x] `.example.env` vollständig kommentiert; `.env` ist in `.gitignore`; keine Default-Geheimnisse.  *(✅ gruppiert + kommentiert; .env in .gitignore; Platzhalter statt Default-Secrets)*
- [x] Konfiguration ist im Code typisiert und validiert (Fail-Fast).  *(✅ Fail-Fast beim Start (StartupConfigValidator))*
- [x] Strukturiertes Logging nach stdout, Level über `.env`, keine Secrets im Log, Audit-Log vorhanden.  *(✅ ECS-JSON, LOGLEVEL aus .env, Audit-Log)*
- [ ] Health-Endpunkt vorhanden; Metriken wo sinnvoll.  *(🟡 Health ✅ je Service; Metriken bewusst zurückgestellt (Entscheidung Produktverantwortlicher))*
- [ ] (Falls lizenzpflichtig) Lizenzcontroller mit Limits, Grace Period, Demo-Modus, persistentem Zustand, Fehlerseite – **vor** Produktivphase.  *(🟡 Schnittstelle vorbereitet; Durchsetzung/Grace/Demo offen – Lizenzpflicht ungeklärt)*
- [ ] Quellcode, Beschreibung, Doku **und** Image liegen im GitLab-Repo unter `git.senapsa.io` (Image in der GitLab Container Registry).  *(❌ git.senapsa.io im Take-Home nicht verfügbar – lokales Git-Repo vollständig; Übertragung = reiner Push (abgestimmt))*
- [ ] CI (`.gitlab-ci.yml`) baut automatisch, leitet die Version aus der Single Source ab, taggt `:latest/:version/:vversion`, scannt mit Trivy, pusht in die GitLab Registry.  *(🟡 Pipeline vollständig geschrieben (Version aus pom.xml, 3-fach-Tagging, Trivy, SBOM, Push) – mangels GitLab nie ausgeführt)*
- [x] README beschreibt Installation, Konfiguration und Betrieb.  *(✅)*

---

*Referenzprodukt: BFTS (senapsa). Diese Richtlinie ist verbindlich für alle neuen Eigenentwicklungen.*
