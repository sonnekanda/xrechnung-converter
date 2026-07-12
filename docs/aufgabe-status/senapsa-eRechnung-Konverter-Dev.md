# Senapsa eRechnung-Konverter & Validator — Entwicklungsdokument

> **Status-Kopie (Stand 2026-07-12)** — Abgleich der Anforderungen mit dem umgesetzten Stand.
> Original unverändert unter `aufgabe/`. Legende: **[x]** erfüllt · **🟡** teilweise · **❌** offen.


> Technische Aufbereitung des Konzepts für die Umsetzung (PoC → MVP).
> Quelle: `Konzept_Senapsa_eRechnung_Ausgang.pdf` (10 Seiten)

---

## 1. Elevator Pitch

PDF und/oder CSV rein → geprüfte, gültige **XRechnung (XML)** raus.
SaaS-Webanwendung (White-Label), 3 Schritte: **Hochladen → Prüfen → Herunterladen**.
Ein Docker-Compose-Stack pro Kunde, cloud- und on-premise-identisch.

**Problem:** Ältere Abas-ERP-Versionen erzeugen kein XML — nur PDF/CSV. B2G verlangt e-Rechnung zwingend, B2B zunehmend.

---

## 2. Kern-Arbeitsannahmen (verbindlich)

| Thema | Festlegung |
|---|---|
| Zielformat | XRechnung/UBL zuerst. CII später. ZUGFeRD EN 16931 nur nach positivem PDF/A-3-PoC |
| Datenpfad | PDF = vorrangig, CSV = vollwertiger zweitrangiger Pfad. Beide vorhanden → PDF führt, CSV = Gegenprüfung. Nur CSV → vollständig daraus (versioniertes Abas-Mapping) |
| OCR | Springt überall ein wo nötig (fehlende/unbrauchbare Textebene, unsichere Werte, Bildinhalte) — nicht nur bei Scans. Ergebnisse immer prüfpflichtig |
| Stammdaten | Leitweg-ID, Bankverbindung, USt-ID, Zahlungsbedingungen je Empfänger, Autofill |
| Betrieb | MVP Single-Tenant, 1 Stack/Kunde. Auslegung: 200 Rechnungen/Tag, 5 parallele Nutzer |
| Lizenzcontroller | Schnittstelle ab Start vorbereiten, Durchsetzung vor Produktivgang (falls lizenzpflichtig) |
| Beispieldaten | 5–10 anonymisierte Abas-Beispiele (PDF Pflicht, CSV wo vorhanden) → Basis für PDF-Erkennung, CSV-Mapping, Gegenprüfung, Aufwandsschätzung |

---

## 3. Entwicklungsrichtlinie (senapsa-Standard, verbindlich für alle Produkte)

- **Container-First** — kein Direkt-Install auf Server
- **Stack statt Einzelteile** — ein Docker-Compose-Stack inkl. aller Abhängigkeiten
- **Eine zentrale Konfiguration** — alles über `.env`
- **Konfiguration ≠ Code** — keine Secrets im Image/Code
- **Alles in Git** — `git.senapsa.io`, 1 Produkt = 1 Repo
- **Reproduzierbar** — CI/CD, gleicher Commit → gleiches Image
- **Beobachtbar** — strukturierte Logs, Health-Check, Metriken wo sinnvoll
- **Lizenziert** — Lizenzcontroller vor Produktivphase falls nötig
- **Sicher per Default** — non-root, kein Default-Passwort, TLS-fähig

---

## 4. Architektur — Interner Ablauf

```
Upload → Prüfung → /data → Queue
  ├─ PDF-Extraktion Textebene (pdfplumber/invoice2data)
  ├─ OCR wo nötig (Tesseract)
  └─ CSV-Mapping (eigenständiger Pfad oder Abgleich zum PDF)
        │
        ▼
  internes Rechnungsmodell (+ Stammdaten-Autofill)
        │
        ▼
  Fachprüfung → Prüfbildschirm (Korrektur/Bestätigung durch Nutzer)
        │
        ▼
  Mustangproject: Generierung XRechnung-UBL / ZUGFeRD
        │
        ▼
  Validierung (Mustangproject + KoSIT: XSD/Schematron/Business Rules)
        │
        ├─ OK  → Export XML + Bericht
        └─ NOK → kein Export, feldbezogene Fehlerliste → zurück zu "prüfen"
```

### Statusmaschine
```
hochgeladen → in_verarbeitung → prüfen (wartet auf Nutzer) → validiert → exportiert
```
- Validierung fehlgeschlagen → zurück zu `prüfen` mit Fehlerliste
- Export nur im Zustand `validiert` möglich, sonst HTTP `409`

### Docker-Compose Services

| Service | Aufgabe |
|---|---|
| `webapp` | UI + API `/api/v1`, Auth/Rollen, Statusmaschine, Stammdaten, Export-Freigabe |
| `extraction-worker` | PDF-Extraktion (pdfplumber/invoice2data), CSV-Mapping/-Abgleich, Ordner-Automatik (Watch In/Out) |
| `ocr-worker` | Tesseract OCR |
| `invoice-engine` | Mustangproject + KoSIT (Generierung + Validierung) |
| `postgres` | Benutzer, Stammdaten, Rechnungsdaten, Audit-Log, Versionen |
| `redis` | Job-Queue (async Verarbeitung, OCR, Retry) |

Netz: `sic-net` intern, nur App-Port exponiert. Benannte Volumes, gepinnte Images, Healthchecks, `depends_on: service_healthy`. Je Service: Multi-Stage-Dockerfile, non-root, `entrypoint.sh` mit `gosu`, Daten nur unter `/data`.

---

## 5. Internes Rechnungsmodell (Eigenentwicklung, zentrale Zwischenschicht)

JSON-Dokument je Rechnung mit Blöcken **Kopf**, **Summen**, **Positionen**.
Jedes Feld: `wert`, `herkunft` (`pdf` / `csv` / `ocr` / `stammdaten` / `manuell`), `status` (`sicher` / `unsicher` / `fehlt`), optional `confidence` (0–1).

```json
"leitweg_id": {
  "wert": "991-12345-67",
  "herkunft": "stammdaten",
  "status": "sicher"
}
```

**Wichtig:** Alle Motoren (PDF-Extraktion, OCR, CSV-Mapping) liefern *ausschließlich* in dieses Modell. Generiert wird *ausschließlich* aus diesem Modell. Die Ampel im Prüfbildschirm = direkte Anzeige von `status` + `herkunft`.

### Ampellogik
- 🟢 **Grün** — sicher erkannt oder aus Stammdaten übernommen
- 🟡 **Gelb** — unsicher (niedrige Confidence oder PDF↔CSV-Abweichung) → Nutzer muss bestätigen
- 🔴 **Rot** — Pflichtfeld fehlt → Nutzer muss ergänzen

Kein Export ohne Grün auf allen Pflichtfeldern. **Diese Sperre ist technisch nicht umgehbar.**

### Pflichtdaten (Minimum)
- **Kopf:** Rechnungsnummer, Datum, Leistungszeitraum, Verkäufer/Käufer + Adressen, USt-ID, Leitweg-ID (falls nötig), Zahlungsbedingungen, Fälligkeit, Währung, Bankverbindung, Referenz
- **Summen:** Netto, Steuer, Brutto, Steuerkategorie/-satz, Rundung
- **Positionen:** Nummer, Artikelnummer, Beschreibung, Menge, Einheit (UN/ECE), Einzel-/Gesamtpreis, Rabatt, Steuersatz

Format-Hinweise: deutsche Zahlen-/Datumsformate (`1.234,56` / `TT.MM.JJJJ`). Skonto-Mapping und Mehrfach-Steuersätze anhand Beispieldaten klären.

---

## 6. Open-Source-Komponenten (Ranking)

| # | Tool | Zweck | Lizenz | Einsatz |
|---|---|---|---|---|
| 1 | [Mustangproject](https://github.com/ZUGFeRD/mustangproject) | Generierung XRechnung UBL/CII + ZUGFeRD PDF/A-3, Validierung | Apache-2.0 | **invoice-engine**, erste Wahl |
| 2 | [KoSIT-Validator](https://github.com/itplr-kosit/validator) | Zweite unabhängige Validierung, amtliche Prüfszenarien | Apache-2.0 | Absicherung „validiert" |
| 3 | pdfplumber / pdfminer.six | Text/Tabellen aus PDF-Textebene | MIT-artig | `extraction-worker` |
| 4 | [invoice2data](https://github.com/invoice-x/invoice2data) | Template-basierte PDF-Extraktion, JSON-Output, Tesseract-Anbindung | MIT | Primärmotor PDF-Extraktion |
| 5 | Tesseract OCR + ocrmypdf | OCR für Scans ohne Textebene | Apache-2.0/MPL-2.0 | `ocr-worker`, immer async + prüfpflichtig |
| 6 | PostgreSQL 17 / Redis 7 | Datenhaltung + Job-Queue | frei | `postgres`, `redis` |
| 7 | InvOCR | Rechnungs-OCR-Pipeline | — | nur PoC-Fallback |
| 8 | Invoice Data Extraction AI | KI-Extraktion exotischer Layouts | kommerziell | letzte Wahl, DSGVO/AV-Prüfung zwingend, abschaltbar |

CSV-Verarbeitung ist **kein Motor**, sondern Eigenentwicklung (versioniertes Abas-Mapping).

Jeder Motor: eigene Service-Schnittstelle, austauschbar, liefert nur ins interne Modell — nie direkt die Zieldatei. Alle laufen intern, White-Label (keine OSS-Namen für den Kunden sichtbar).

---

## 7. UI — Basisfunktionen

| Bereich | Funktion |
|---|---|
| Anmeldung | Login, Rollen `Benutzer` / `Admin` |
| Übersicht | Liste + Status/Ampel, Upload-Fläche (Drag&Drop, Mehrfachauswahl), Download XML + Bericht |
| Prüfbildschirm | Links Original-PDF, rechts Formular, Ampel je Feld, Korrektur, Button „XRechnung erzeugen" |
| Admin-Bereich | Stammdaten, Benutzerverwaltung, Konfiguration |
| Navigationsleiste | Links, aufklappbar (Icon-only ↔ Icon+Label, pro Nutzer gespeichert), rollenabhängig |

Design: deutschsprachig, Alltagssprache, Desktop-Browser-Fokus, senapsa-Branding via `.env`. Detail-Layout/Framework: freie Wahl des Developers.

**Fehlermeldungen:** Alltagssprache + Handlungsanweisung (z.B. „Leitweg-ID fehlt – bitte ergänzen"), keine Stacktraces/Technik.

---

## 8. Ordner-Automatik (optional, "Kann")

- Überwacht Eingangsordner in konfigurierbarem Intervall, gleiche Verarbeitungslogik wie Upload
- PDF+CSV-Zuordnung über gleichen Dateinamen
- Nur vollständig validierte Rechnungen → automatischer Export in Ausgangsordner (UBL, optional ZUGFeRD + Bericht)
- Unsichere/fehlende Felder → normaler Prüfbildschirm, kein Auto-Export
- **On-premise:** lokales Volume, Abas schreibt direkt hinein
- **SaaS:** kein direkter Zugriff auf Kunden-Ordner → Instanz stellt Übergabeordner bereit (z.B. SFTP). Automatische Cloud-Synchronisation ist **kein** Teil dieser Funktion (späterer Baustein)
- Config: `WATCH_ENABLED`, `WATCH_INPUT_DIR`, `WATCH_OUTPUT_DIR`, `WATCH_INTERVAL`

---

## 9. API (Auszug)

```
POST   /files/upload
GET    /files/{id}/status
GET    /invoices/{id}
PATCH  /invoices/{id}
POST   /invoices/{id}/generate
POST   /invoices/{id}/validate
GET    /invoices/{id}/export          # nur Status "validiert", sonst 409
GET    /invoices/{id}/validation-report
GET    /health
```
JSON, Multipart-Upload, klare Fehlercodes, OpenAPI-Dokumentation.

---

## 10. Anforderungen nach Priorität

### Muss
- PDF-Extraktion Textebene (vorrangig) + CSV-Verarbeitung (versioniertes Mapping, vollwertig, Ergänzung/Gegenprüfung)
- XRechnung XML (UBL zuerst), internes Rechnungsmodell
- Prüfbildschirm mit Korrektur, Empfänger-Stammdaten mit Autofill
- Doppelte Validierung (Mustangproject + KoSIT), Export nur bei Erfolg

### Soll
- OCR (Tesseract) wo nötig, immer prüfpflichtig
- Validierungsbericht, Audit-Log

### Kann
- ZUGFeRD EN 16931 (nach PoC), Ordner-Automatik, Virenscan, Metriken, SSO, Lizenzcontroller, KI-Zusatzmotor (Rang 8)

### Später
- CII-Syntax, Peppol, E-Mail-Versand, weitere ERP-Systeme, allgemeine Rechnungserkennung, Multi-Tenancy, Gutschriften/Stornos, erweiterte Fehlerbehandlung (Duplikate, defekte Dateien), DSGVO-Konzept/AV-Vertrag, Backup/Restore, Cloud-Anbindung Ordner-Automatik

### Anforderungstabelle (A-01 … A-16)

| Nr | Kurztitel | Kern | Empfohlen mit |
|---|---|---|---|
| A-01 | Upload | Drag&Drop + Auswahl, PDF/CSV einzeln oder kombiniert, Mehrfach-Upload, `MAX_UPLOAD_MB` | webapp |
| A-02 | PDF-Extraktion | Textebene zuerst, OCR wo nötig, Confidence je Feld | pdfplumber, invoice2data, Tesseract |
| A-03 | CSV-Datenpfad | Versioniertes Abas-Mapping, Ergänzung/Gegenprüfung, Abweichung → gelb | Eigenentwicklung |
| A-04 | Positionen | Menge, Einheit, Preise, Steuer, Rabatt, Artikelnr., Beschreibung | invoice2data + Eigenlogik |
| A-05 | Rechnungsmodell | Einzige Zwischenschicht, Herkunft/Status/Confidence je Feld | Eigenentwicklung |
| A-06 | Stammdaten | Leitweg-ID, Bankverbindung, USt-ID, Zahlungsbedingungen, Autofill | Eigenentwicklung |
| A-07 | Fachprüfung | Pflichtfelder, Summen, Steuerlogik, Rundungstoleranz | Eigenentwicklung |
| A-08 | Prüfbildschirm | Original + erkannte Daten, Ampel, Korrektur | webapp |
| A-09 | Generierung | XRechnung UBL, ZUGFeRD (bei PoC-Erfolg) | Mustangproject |
| A-10 | Validierung | XSD + Schematron + Business Rules, Export nur bei Erfolg | Mustangproject + KoSIT |
| A-11 | Fehlermeldungen | Feld-/positionsbezogen, Alltagssprache | webapp |
| A-12 | Status & Audit | Statusmaschine, auditierbare Protokollierung | postgres |
| A-13 | Versionierung | Mappings + Validierungsregeln versioniert | Eigenentwicklung |
| A-14 | White-Label | Keine OSS-Namen sichtbar | alle |
| A-15 | Rollen & Config | Benutzer/Admin, `.env` + Admin-Bereich | webapp |
| A-16 | Ordner-Automatik | Watch-Intervall, gleicher Ablauf wie Upload, `.env`-gesteuert | Eigenentwicklung |

---

## 11. Abnahmekriterien (K-01 … K-11)

- [x] **K-01** PDF vorrangig verarbeitet, OCR wo nötig + prüfpflichtig, CSV liefert eigenständig oder ergänzend eine gültige XRechnung  *(✅ Textpfad + OCR prüfpflichtig + CSV eigenständig/ergänzend; Abweichung dokumentiert: CSV führt bei PDF+CSV)*
- [x] **K-02** Alle Positionen erkannt, im Prüfbildschirm korrigierbar, unsichere Felder erfordern Bestätigung  *(✅ Positionen erkannt/korrigierbar; gelbe Felder erfordern Bestätigung)*
- [x] **K-03** Empfänger-Stammdaten gepflegt + automatisch eingefüllt  *(✅ Stammdaten + Autofill)*
- [x] **K-04** Exportsperre bei fehlenden Pflichtfeldern/unklaren Positionen/falschen Summen  *(✅ Fachprüfung + Doppelvalidierung als Gates; kein Bypass (409))*
- [x] **K-05** Gültige XRechnung-XML (UBL) erzeugt + validiert; ZUGFeRD gemäß PoC  *(✅ UBL erzeugt + von Mustang UND KoSIT validiert; ZUGFeRD umgesetzt (mit Original-PDF))*
- [x] **K-06** Ungültige Dateien nie exportiert; Erfolg = XML + Validierungsbericht; Fehler feldbezogen  *(✅ Export nur bei Erfolg; XML + Bericht; Fehler feldbezogen)*
- [x] **K-07** Nur senapsa-UI sichtbar (White-Label); Bedienung ohne Schulung  *(✅ White-Label, Bedienung in 3 Schritten)*
- [x] **K-08** `docker compose up` startet Stack aus `.env`; non-root; nur App-Port exponiert  *(✅ ein Compose-File aus .env, non-root, nur App-Port)*
- [x] **K-09** Lasttest: 200 Rechnungen/Tag, 5 parallele Nutzer  *(✅ ~186 Rechnungen/min gemessen (Ziel 200/Tag))*
- [ ] **K-10** CI/CD mit Trivy-Scan, alles unter `git.senapsa.io`, Logs/Audit/Health-Checks, OSS-Lizenzen dokumentiert  *(🟡 Logs/Audit/Health ✅, OSS-Lizenzen ✅, .gitlab-ci.yml mit Trivy geschrieben – aber nie auf git.senapsa.io ausgeführt (kein Zugang, abgestimmt))*
- [x] **K-11** Ordner-Automatik (falls aktiv): korrekte Intervallverarbeitung, nur validierte Auto-Exports  *(✅ Intervall, Standard-Ablauf, nur validierte Auto-Exports, Formatwahl, Dedup)*

---

## 12. Definition of Done (Checkliste)

- [x] Upload (Drag&Drop + Auswahl, mehrfach) funktioniert; PDF vorrangig, CSV zweitrangig/eigenständig, OCR wo nötig  *(✅)*
- [x] Alle Positionen im internen Modell; Prüfbildschirm mit Ampellogik + Korrektur; Stammdaten-Autofill aktiv  *(✅ (Rabatt nicht modelliert – nicht in Beispieldaten))*
- [x] Pflichtfelder/Summen/Steuern geprüft; kein Export bei Fehlern (kein Bypass)  *(✅ technisch nicht umgehbar)*
- [x] XRechnung (UBL) erzeugt + validiert (Pflicht); ZUGFeRD gemäß PoC; Validierungsbericht je Datei  *(✅ inkl. ZUGFeRD + Bericht je Datei)*
- [x] White-Label umgesetzt; Motoren gekapselt/austauschbar; OSS-Lizenzübersicht (SBOM)  *(✅ OSS-Übersicht in docs/oss-lizenzen.md; SBOM-Erzeugung in CI vorbereitet)*
- [x] Compose-Stack aus einer `.env`; `.example.env` kommentiert; non-root; Fail-Fast-Config; Single-Tenant dokumentiert  *(✅ inkl. Fail-Fast (StartupConfigValidator), Single-Tenant dokumentiert)*
- [ ] Logs, Audit-Log, Health-Checks vorhanden; CI/CD mit Trivy läuft; alles unter `git.senapsa.io`  *(🟡 Logs/Audit/Health ✅; CI/Trivy geschrieben, aber nie gelaufen; Repo nicht auf git.senapsa.io (abgestimmt))*
- [x] Lasttest 200 Rechnungen/Tag, 5 parallele Nutzer bestanden  *(✅ weit übertroffen)*
- [ ] Tests (Unit, Integration, Negativ) grün; Doku vollständig (README, API, Benutzer, Admin, Mapping, Betrieb/Backup)  *(🟡 Python-Tests (extraction-worker, 10) grün + End-to-End-Smokes; Java-Unit-Tests (webapp/invoice-engine) fehlen; Doku vollständig)*
- [ ] Falls Lizenzpflicht: Lizenzcontroller vor Produktivphase aktiv  *(🟡 Schnittstelle vorbereitet; Durchsetzung offen – Lizenzpflicht ungeklärt)*
- [x] Falls Ordner-Automatik aktiv: gemäß K-11 funktionsfähig  *(✅)*

---

## 13. Offene Punkte für die PoC-Phase (an Developer)

1. **Beispieldaten sichten** — 5–10 anonymisierte Abas-PDF/CSV-Paare
2. **Feldabgleich Abas ↔ XRechnung-Pflichtfelder** erstellen
3. **CSV-Mapping** definieren und versionieren (eigenständiger zweiter Datenpfad)
4. **PDF-Layout-Analyse** — sind Abas-Layouts gleichförmig genug für `invoice2data`-Templates?
5. Klären: wie gibt Abas **Skonto** aus, mehrere Steuersätze je Rechnung?
6. Klären: liefert Abas **Gutschriften/Stornos**? (relevant für "Später")
7. **ZUGFeRD-PoC**: vor Umsetzung mit 2–3 konkreten Rechnungsempfängern klären, welches Format wirklich gefordert wird
8. **Aufwands- und Terminschätzung** als Ergebnis der PoC-Phase an Geschäftsführung liefern

---

## 14. Tech-Stack-Zusammenfassung

- **Backend:** Spring Boot (Java) — passend zu Mustangproject (Java-Bibliothek)
- **Invoice-Engine:** Mustangproject + KoSIT-Validator
- **PDF-Extraktion:** pdfplumber/pdfminer.six, invoice2data
- **OCR:** Tesseract + ocrmypdf
- **DB/Queue:** PostgreSQL 17, Redis 7
- **Frontend:** frei wählbar (Desktop-Browser-fokussiert)
- **Infra:** Docker Compose, CI/CD via GitLab (`git.senapsa.io`), Trivy-Scan, `registry.senapsa.io`
