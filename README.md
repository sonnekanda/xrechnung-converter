# senapsa eRechnung-Konverter & Validator

PDF und/oder CSV rein → geprüfte, gültige **XRechnung (UBL-XML)** raus.
White-Label-SaaS in drei Schritten: **Hochladen → Prüfen → Herunterladen**.
Ein Docker-Compose-Stack pro Kunde, cloud- und on-premise-identisch.

> Ältere Abas-ERP-Versionen erzeugen kein XML – nur PDF/CSV. B2G verlangt die
> e-Rechnung zwingend, B2B zunehmend. Dieser Konverter schließt die Lücke.

---

## Inhalt

- [Architektur](#architektur)
- [Schnellstart](#schnellstart)
- [Der Weg einer Rechnung](#der-weg-einer-rechnung)
- [Internes Rechnungsmodell & Ampellogik](#internes-rechnungsmodell--ampellogik)
- [Abas-Datenpfade (PDF & CSV)](#abas-datenpfade-pdf--csv)
- [Konfiguration](#konfiguration)
- [Tests & Verifikation](#tests--verifikation)
- [Weitere Dokumentation](#weitere-dokumentation)

---

## Architektur

Sechs Dienste in einem internen Netz `sic-net`; nur der Port der Webanwendung
ist nach außen exponiert.

| Dienst | Technik | Aufgabe |
|---|---|---|
| `webapp` | Spring Boot + React (SPA) | UI + API `/api/v1`, Auth/Rollen, Statusmaschine, Stammdaten, Export-Freigabe |
| `extraction-worker` | Python | PDF-Textextraktion (pdfplumber), versioniertes Abas-CSV-Mapping, PDF↔CSV-Gegenprüfung |
| `ocr-worker` | Python + Tesseract/ocrmypdf | OCR für Scans ohne Textebene (immer prüfpflichtig) |
| `invoice-engine` | Java + Mustangproject | Generierung XRechnung-UBL + amtliche Validierung (XSD + Schematron) |
| `postgres` | PostgreSQL 17 | Benutzer, Stammdaten, Rechnungsmodell, Audit-Log |
| `redis` | Redis 7 | Job-Queue für die asynchrone Extraktion/OCR |

```
Upload ─▶ webapp ─▶ Redis-Queue ─▶ extraction-worker ─┬─▶ (Scan) ocr-worker
                                                       │
   internes Rechnungsmodell (+ Stammdaten-Autofill) ◀─┘
        │
        ▼
   Fachprüfung ─▶ Prüfbildschirm (Ampel, Korrektur) ─▶ invoice-engine
        │                                                 (Mustangproject)
        ▼                                                 Generierung + Validierung
   Export XML + Bericht  ◀── nur wenn "validiert" ────────┘
```

Prinzipien (senapsa-Standard): Container-First, eine zentrale `.env`, keine
Secrets im Image, non-root, Healthchecks, strukturierte Logs, Audit-Log,
White-Label (keine OSS-Namen für den Kunden sichtbar).

---

## Schnellstart

Voraussetzung: Docker + Docker Compose.

```bash
cp .env.example .env      # Werte anpassen (Passwörter, JWT_SECRET, Admin-Konto)
docker compose up -d      # Stack bauen und starten
```

- Anwendung: `http://localhost:8080` (bzw. `WEBAPP_PORT`)
- API-Dokumentation (OpenAPI/Swagger): `http://localhost:8080/swagger-ui.html`
- Erst-Login: `ADMIN_EMAIL` / `ADMIN_PASSWORD` aus der `.env`

Beispieldateien zum Ausprobieren liegen unter `aufgabe/Musterrechnung *`.

---

## Der Weg einer Rechnung

1. **Hochladen** – PDF und/oder CSV per Drag-and-drop oder Auswahl. Erlaubte
   Typen und Größenlimit über `.env` (`MAX_UPLOAD_MB`).
2. **Auslesen** – Der `extraction-worker` liest die PDF-Textebene (vorrangig)
   bzw. die CSV. Fehlt die Textebene (Scan), springt der `ocr-worker` ein.
3. **Autofill** – Fehlende Pflichtfelder werden automatisch ergänzt: Verkäufer
   und Zahlungsweg (IBAN, Kontakt) aus der **eigenen Firma**, Empfänger-Adresse
   und Leitweg-ID aus den **Empfänger-Stammdaten**.
4. **Prüfen** – Der Prüfbildschirm zeigt links das Original-PDF, rechts die
   erkannten Daten mit Ampel je Feld. Unsichere/fehlende Werte werden korrigiert
   bzw. bestätigt.
5. **Erzeugen & Validieren** – „XRechnung erzeugen" ruft die `invoice-engine`
   (Mustangproject) auf: UBL-Generierung + amtliche Validierung.
6. **Herunterladen** – Nur im Status `validiert` möglich (sonst HTTP 409). Es
   werden XML und Validierungsbericht bereitgestellt.

Statusmaschine: `hochgeladen → in_verarbeitung → prüfen → validiert → exportiert`.
Bei Validierungsfehler geht es mit feldbezogener Fehlerliste zurück zu `prüfen`.

---

## Internes Rechnungsmodell & Ampellogik

Alle Motoren (PDF, OCR, CSV) liefern **ausschließlich** in ein internes
JSON-Modell; generiert wird **ausschließlich** daraus. Jedes Feld trägt Wert,
`herkunft` (`pdf`/`csv`/`ocr`/`stammdaten`/`manuell`/`berechnet`), `status` und
optional `confidence`. Schema: [`schema/internal-invoice-model.schema.json`](schema/internal-invoice-model.schema.json).

- 🟢 **Grün** – sicher erkannt oder aus Stammdaten übernommen
- 🟡 **Gelb** – unsicher (niedrige Confidence, OCR oder PDF↔CSV-Abweichung) → bestätigen
- 🔴 **Rot** – Pflichtfeld fehlt → ergänzen

**Kein Export ohne Grün auf allen Pflichtfeldern.** Diese Sperre sitzt im
Backend (Fachprüfung + Statusmaschine) und ist technisch nicht umgehbar.

---

## Abas-Datenpfade (PDF & CSV)

- **PDF führt.** Liegen PDF und CSV vor, ist das PDF maßgeblich; die CSV dient
  der Gegenprüfung – Abweichungen werden gelb markiert.
- **CSV ist vollwertig.** Liegt nur eine CSV vor, wird die Rechnung vollständig
  daraus verarbeitet (versioniertes Mapping `abas-v1`).

Das Abas-CSV-Format hat Eigenheiten, die im Mapping abgebildet sind – u.a.
**Summen-Pseudozeilen** (Artikel 9/6/1 = Netto/Steuer/Brutto), fehlende
Kopfzeilen und CP1252-Kodierung. Details:
[`docs/mapping-abas.md`](docs/mapping-abas.md).

Gutschriften/Stornos werden erkannt und (gemäß Scope „Später") nicht exportiert.

---

## Konfiguration

Der gesamte Stack wird über eine einzige `.env` gesteuert; eine kommentierte
Vorlage liegt als [`.env.example`](.env.example) bei. Wichtige Schlüssel:
`WEBAPP_PORT`, `MAX_UPLOAD_MB`, `JWT_SECRET`, `ADMIN_EMAIL`/`ADMIN_PASSWORD`,
`ROUNDING_TOLERANCE`, `KOSIT_ENABLED`, `WATCH_*`, Branding (`BRAND_*`).

Die **eigene Firma** (Verkäufer: Name, Adresse, USt-IdNr, Kontakt, IBAN) kann
per `COMPANY_*` vorbelegt und jederzeit im Admin-Bereich unter „Eigene Firma"
gepflegt werden. Sie wird automatisch in jede Rechnung eingefüllt.

---

## Tests & Verifikation

- **extraction-worker** (Python): `cd extraction-worker && pytest` – prüft das
  CSV-Mapping und die PDF-Extraktion gegen die realen Abas-Muster (10 Tests).
- **invoice-engine** (Java): `cd invoice-engine && mvn verify`.
- **webapp** (Java): `cd webapp && mvn verify`.

Ein durchgängiger Lauf mit der Musterrechnung 400009 erzeugt eine XRechnung, die
der Mustang-Validator als **gültig** meldet (EN 16931, 54 Schematron-Regeln,
0 Fehler). Ein Beispiel-Export liegt unter
[`docs/beispiel-rechnung-400009.ubl.xml`](docs/beispiel-rechnung-400009.ubl.xml).

Der aktuelle Umsetzungsstand (verifiziert vs. Ausbaustufe) ist in
[`docs/status.md`](docs/status.md) dokumentiert.

---

## Weitere Dokumentation

| Dokument | Inhalt |
|---|---|
| [`docs/mapping-abas.md`](docs/mapping-abas.md) | Versioniertes Abas-CSV-Mapping, Feldabgleich |
| [`docs/status.md`](docs/status.md) | Umsetzungsstand, verifizierte Teile, offene Punkte |
| [`docs/betrieb.md`](docs/betrieb.md) | Betrieb, Backup/Restore, Health-Checks |
| [`docs/oss-lizenzen.md`](docs/oss-lizenzen.md) | OSS-Komponenten & Lizenzen (SBOM-Hinweis) |
| `swagger-ui.html` (zur Laufzeit) | API-Referenz (OpenAPI) |
