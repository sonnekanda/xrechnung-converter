# Senapsa eRechnung-Konverter — Backlog (Epics & Stories)

> Quelle: `senapsa_erechnung_ausgang_epics_tickets.xlsx`
> Querverweis: `senapsa-eRechnung-Konverter-Dev.md` (A-01…A-16, K-01…K-11)
> Checkboxen dienen als Fortschritts-Tracking direkt im Repo.

---

## Übersicht

| Epic | Tickets | Must / Soll / Kann |
|---|---|---|
| Stack & CI/CD | 4 | 3 / 0 / 1 |
| Webapp: Upload, Prüfbildschirm, Navigationsleiste | 7 | 7 / 0 / 0 |
| Rechnungsmodell & Fachprüfung | 4 | 4 / 0 / 0 |
| PDF-Extraktion | 2 | 2 / 0 / 0 |
| CSV-Datenpfad | 3 | 3 / 0 / 0 |
| OCR | 2 | 0 / 2 / 0 |
| Generierung & Validierung | 3 | 2 / 0 / 1 |
| Stammdaten & Admin | 3 | 3 / 0 / 0 |
| Ordner-Automatik (Watch-Folder) | 4 | 0 / 0 / 4 |
| Lasttest & Abnahme | 3 | 3 / 0 / 0 |
| **Gesamt** | **35** | **27 / 2 / 6** |

---

## EPIC: Stack & CI/CD

Docker-Compose-Stack, CI/CD-Pipeline, Logging/Audit/Health-Checks, Lizenzcontroller-Schnittstelle gemäß Entwicklungsrichtlinie (Kap. 1 & 7).

- [ ] **Docker-Compose-Stack aufsetzen** `Must` `stack, K-08`
  - *User Story:* Als Betreiber möchte ich den gesamten Stack mit `docker compose up` aus einer `.env` starten, damit jede Kundeninstanz reproduzierbar in Cloud und on-premise läuft.
  - *Akzeptanzkriterien:* Alle Services (webapp, extraction-worker, ocr-worker, invoice-engine, postgres, redis) im internen Netz `sic-net`; nur App-Port exponiert. Benannte Volumes, gepinnte Images, Healthchecks, `depends_on: service_healthy`. Konfiguration ausschließlich über `.env`; kommentierte `.example.env` eingecheckt. Je Service Multi-Stage-Dockerfile, non-root, `entrypoint.sh` mit `gosu`, Daten unter `/data`.

- [ ] **CI/CD-Pipeline einrichten** `Must` `cicd, K-10`
  - *User Story:* Als Developer möchte ich versionierte, gescannte Images automatisch bauen und pushen, damit jeder Commit nachvollziehbar und `main` jederzeit auslieferbar ist.
  - *Akzeptanzkriterien:* Repository unter `git.senapsa.io` mit `.gitlab-ci.yml`. Pipeline: Tests → Build → Tagging (`:latest`/`:version`/`:vversion`) → Trivy-Scan (CRITICAL, HIGH) → Push nach `registry.senapsa.io`. `main` ist immer auslieferbar (Feature-Branches + Merge Requests).

- [ ] **Logging, Audit-Log & Health-Checks** `Must` `observability, K-10`
  - *User Story:* Als Betreiber möchte ich strukturierte Logs, ein Audit-Log und Health-Checks je Service, damit ich Betrieb und Verarbeitung jederzeit nachvollziehen kann.
  - *Akzeptanzkriterien:* Strukturierte Logs nach stdout (`LOGLEVEL` aus `.env`), keine Secrets im Log, vollständiges Audit-Log über alle Schritte, `/health`-Endpoint je Service.

- [ ] **Lizenzcontroller-Schnittstelle vorbereiten** `Kann` `licensing`
  - *User Story:* Als Produktverantwortlicher möchte ich die Lizenz-Schnittstelle von Anfang an vorgesehen haben, damit die Durchsetzung vor Produktivgang ohne Umbau möglich ist.
  - *Akzeptanzkriterien:* Schnittstelle zum Lizenzcontroller ab Start vorsehen und vorbereiten; Durchsetzung des erlaubten Funktionsumfangs vor Produktivgang, falls das Produkt lizenzpflichtig ist. (DoD: Lizenzcontroller vor Produktivphase aktiv, falls Lizenzpflicht bestätigt.)

---

## EPIC: Webapp — Upload, Prüfbildschirm, Navigationsleiste

UI-Basisfunktionen: Anmeldung, Übersicht, Upload, Prüfbildschirm mit Ampellogik, Navigationsleiste, verständliche Fehlermeldungen (Kap. 3).

- [ ] **Anmeldung / Login** `Must` `auth`
  - *User Story:* Als Benutzer möchte ich mich mit meiner Rolle anmelden, damit ich nur die für mich vorgesehenen Funktionen sehe.
  - *Akzeptanzkriterien:* Login mit den Rollen Benutzer und Admin. (Teil von K-07: normaler Benutzer schafft alle drei Schritte ohne Schulung.)

- [ ] **Übersicht / Dashboard** `Must` `dashboard`
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich alle Rechnungen mit Status und Ampel auf einen Blick sehen, damit ich sofort weiß, wo etwas zu tun ist.
  - *Akzeptanzkriterien:* Liste aller Rechnungen mit Status und Ampel; Download von XML und Prüfbericht bei validierten Rechnungen; Zähler für offene Prüfungen im Navigationsmenü. (K-07)

- [ ] **Upload (A-01)** `Must` `A-01, K-01`
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich PDF und/oder CSV einfach per Drag-and-drop hochladen, damit ich meine Abas-Rechnungen ohne Technikwissen einreichen kann.
  - *Akzeptanzkriterien:* Upload von PDF und/oder CSV per Drag-and-drop und Dateiauswahl (auch einzeln); Mehrfach-Upload mit Status je Datei. Erlaubte Typen: PDF, CSV; Größenlimit über `.env` (`MAX_UPLOAD_MB`); Prüfung von Typ, Größe, Berechtigung beim Upload.

- [ ] **Navigationsleiste** `Must` `navigation`
  - *User Story:* Als Benutzer möchte ich über eine aufklappbare Navigationsleiste alle Hauptfunktionen erreichen, damit ich mich ohne Schulung zurechtfinde.
  - *Akzeptanzkriterien:* Aufklappbare Leiste links: eingeklappt Symbole, aufgeklappt Symbole mit Beschriftung, Zustand je Benutzer gespeichert. Menüpunkte: Dashboard/Übersicht, Upload, Stammdaten (nur Admin), Einstellungen (nur Admin), Abmelden. Aktiver Bereich hervorgehoben; Logo über `.env` konfigurierbar.

- [ ] **Prüfbildschirm mit Ampellogik (A-08)** `Must` `A-08, K-02, K-04`
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich Original und erkannte Daten nebeneinander mit Ampel sehen und direkt korrigieren, damit ich unsichere Werte schnell und sicher bestätigen kann.
  - *Akzeptanzkriterien:* Original-PDF links, Formular mit erkannten Daten rechts. Ampel je Feld (🟢/🟡/🔴), Korrektur direkt im Formular, Bestätigung unsicherer Felder vor Export. Button „XRechnung erzeugen".

- [ ] **Fehlermeldungen in Alltagssprache (A-11)** `Must` `A-11, K-06`
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich verständliche Fehlermeldungen mit Handlungsanweisung, damit ich Probleme ohne IT-Hilfe selbst beheben kann.
  - *Akzeptanzkriterien:* Feld- und positionsbezogene Fehlermeldungen mit Handlungsanweisung (z. B. „Leitweg-ID fehlt – bitte ergänzen"); keine technischen Details/Stacktraces.

- [ ] **Rollen & Konfiguration (A-15)** `Must` `A-15`
  - *User Story:* Als Admin möchte ich Rollen und Konfiguration zentral pflegen, damit die Anwendung ohne Eingriff in Dateien verwaltet werden kann.
  - *Akzeptanzkriterien:* Rollen Benutzer/Admin; Konfiguration über `.env` und Admin-Bereich (inkl. Stammdaten). Stammdaten/Einstellungen nur für Admin sichtbar.

---

## EPIC: Rechnungsmodell & Fachprüfung

Internes Rechnungsmodell als Zwischenschicht, Fachprüfung, Statusmaschine/Audit, Versionierung von Mappings & Regeln (Kap. 7).

- [ ] **Internes Rechnungsmodell (A-05)** `Must` `A-05`
  - *User Story:* Als Developer möchte ich alle erkannten Werte in einem einzigen Modell mit Herkunft, Status und Confidence führen, damit Erkennung und Generierung sauber entkoppelt sind.
  - *Akzeptanzkriterien:* Einzige Zwischenschicht: JSON-Dokument je Rechnung mit Blöcken Kopf, Summen, Positionen. Jedes Feld mit Wert, `herkunft` (pdf, csv, ocr, stammdaten, manuell), `status` (sicher, unsicher, fehlt), ggf. `confidence` (0–1). Alle Motoren liefern ausschließlich in dieses Modell.

- [ ] **Fachprüfung (A-07)** `Must` `A-07, K-04`
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich, dass Pflichtfelder, Summen und Steuern automatisch geprüft werden, damit keine fehlerhafte Rechnung exportiert werden kann.
  - *Akzeptanzkriterien:* Prüfung von Pflichtfeldern, Netto-/Steuer-/Bruttosummen, Steuerlogik, Rundungstoleranz.

- [ ] **Statusmaschine & Audit (A-12)** `Must` `A-12, K-04, K-10`
  - *User Story:* Als Betreiber möchte ich feste Zustände und ein lückenloses Protokoll je Rechnung, damit die Exportsperre technisch nicht umgehbar und alles auditierbar ist.
  - *Akzeptanzkriterien:* Statusmaschine: `hochgeladen → in Verarbeitung → prüfen → validiert → exportiert`. Bei Validierungsfehler zurück zu `prüfen` mit feldbezogener Fehlerliste. Export nur im Zustand `validiert` (sonst API 409). Alles auditierbar protokolliert.

- [ ] **Versionierung von Mappings & Regeln (A-13)** `Must` `A-13`
  - *User Story:* Als Betreiber möchte ich Mappings und Regeln versioniert austauschen können, damit Spezifikations-Updates ohne Kern-Deployment möglich sind.
  - *Akzeptanzkriterien:* Mappings und Validierungsregeln versionieren.

---

## EPIC: PDF-Extraktion

Vorrangiger Datenpfad: Textebene lesen, Positionen erkennen (Kap. 5 & 6, A-02, A-04).

- [ ] **PDF-Extraktion Textebene (A-02)** `Must` `A-02, K-01`
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich, dass meine PDF-Rechnung automatisch ausgelesen wird, damit ich Daten nicht abtippen muss.
  - *Akzeptanzkriterien:* Vorrangiger Datenpfad: zuerst Textebene lesen (pdfplumber/invoice2data). OCR springt ein, wo nötig; OCR-Ergebnisse immer mit Confidence je Feld und prüfpflichtig.

- [ ] **Positionserkennung aus PDF (A-04, PDF-Teil)** `Must` `A-04, K-02`
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich, dass alle Rechnungspositionen vollständig erkannt werden, damit die XRechnung inhaltlich der Originalrechnung entspricht.
  - *Akzeptanzkriterien:* Alle Positionen aus dem PDF erkennen (Menge, Einheit, Einzel-/Gesamtpreis, Steuer, Rabatt, Artikelnummer, Beschreibung).

---

## EPIC: CSV-Datenpfad

Zweitrangiger, vollwertiger Datenpfad über versioniertes Abas-Mapping inkl. Gegenprüfung zum PDF (A-03, A-04).

- [ ] **CSV-Mapping Abas (A-03)** `Must` `A-03, K-01`
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich auch eine Abas-CSV allein zur XRechnung verarbeiten können, damit ich nicht auf das PDF angewiesen bin.
  - *Akzeptanzkriterien:* Liegt nur eine CSV vor, wird die Rechnung vollständig daraus verarbeitet. Liegen PDF und CSV vor, führt das PDF; die CSV dient Ergänzung/Gegenprüfung.

- [ ] **Positionserkennung aus CSV (A-04, CSV-Teil)** `Must` `A-04, K-02`
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich, dass alle Positionen auch aus der CSV vollständig übernommen werden, damit beide Datenpfade gleichwertig funktionieren.
  - *Akzeptanzkriterien:* Alle Positionen aus der CSV erkennen (Menge, Einheit, Preise, Steuer, Rabatt, Artikelnummer, Beschreibung).

- [ ] **Gegenprüfung PDF ↔ CSV** `Must` `A-03, K-01`
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich bei Abweichungen zwischen PDF und CSV eine gelbe Markierung sehen, damit ich bewusst entscheide, welcher Wert stimmt.
  - *Akzeptanzkriterien:* Bei Abweichung wird das betroffene Feld gelb markiert; der Benutzer entscheidet im Prüfbildschirm.

---

## EPIC: OCR

OCR überall dort, wo notwendig — fehlende/unbrauchbare Textebene, unsichere Werte, Bildinhalte; Ergebnisse immer prüfpflichtig (A-02, Rang 5).

- [ ] **OCR-Worker (Tesseract)** `Soll` `ocr, K-01`
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich, dass auch gescannte oder bildhafte Rechnungen erkannt werden, damit ich keine Rechnung manuell erfassen muss.
  - *Akzeptanzkriterien:* Texterkennung für gescannte PDFs ohne Textebene bzw. eingebettete Bildinhalte; deutsche Modelle; Confidence-Wert je Erkennung. Asynchrone Verarbeitung über Job-Queue.

- [ ] **OCR-Confidence & Prüfpflicht** `Soll` `ocr, K-01, K-02`
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich OCR-Werte immer als unsicher markiert sehen, damit nichts Ungeprüftes in die XRechnung gelangt.
  - *Akzeptanzkriterien:* OCR-Ergebnisse grundsätzlich als „unsicher" (gelb) markiert und müssen im Prüfbildschirm bestätigt werden — unabhängig von der Confidence.

---

## EPIC: Generierung & Validierung

XRechnung-Generierung (UBL/ZUGFeRD) und doppelte amtliche Validierung (Mustangproject + KoSIT) (A-09, A-10).

- [ ] **XRechnung-Generierung UBL (A-09)** `Must` `A-09, K-05`
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich per Klick eine XRechnung in UBL erhalten, damit ich die gesetzlichen Anforderungen meiner Empfänger erfülle.
  - *Akzeptanzkriterien:* Erzeugung der XRechnung-XML in UBL-Syntax aus dem internen Rechnungsmodell (Mustangproject als invoice-engine-Service). ZUGFeRD EN 16931, sofern PDF/A-3-PoC positiv.

- [ ] **Doppelte Validierung Mustangproject + KoSIT (A-10)** `Must` `A-10, K-04, K-05, K-06`
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich, dass jede Datei zweifach amtlich geprüft wird, damit „validiert" wirklich validiert heißt.
  - *Akzeptanzkriterien:* Jede Zieldatei wird gegen zwei unabhängige Instanzen validiert (XSD + Schematron + Business Rules): Mustangproject und KoSIT-Validator. Export nur bei Erfolg beider Prüfungen; Bericht je Datei.

- [ ] **ZUGFeRD PoC** `Kann` `zugferd, K-05`
  - *User Story:* Als Produktverantwortlicher möchte ich per PoC klären, ob ZUGFeRD PDF/A-3 machbar und gefordert ist, damit wir nur bauen, was Empfänger wirklich brauchen.
  - *Akzeptanzkriterien:* PoC für ZUGFeRD-PDF/A-3 im Profil EN 16931; vorab Abstimmung mit 2–3 konkreten Rechnungsempfängern, welches Format tatsächlich gefordert wird.

---

## EPIC: Stammdaten & Admin

Empfänger-Stammdaten, Admin-Bereich, White-Label-Umsetzung (A-06, A-14, A-15 Admin-Teil).

- [ ] **Empfänger-Stammdaten (A-06)** `Must` `A-06, K-03`
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich Leitweg-ID, Bank, USt-ID und Zahlungsbedingungen je Empfänger hinterlegen, damit fehlende Pflichtfelder automatisch ergänzt werden.
  - *Akzeptanzkriterien:* Stammdaten je Rechnungsempfänger mit automatischem Ausfüllen fehlender Pflichtfelder im Rechnungsmodell.

- [ ] **Admin-Bereich (Benutzerverwaltung, Konfiguration)** `Must` `A-15, K-03`
  - *User Story:* Als Admin möchte ich Benutzer, Stammdaten und Einstellungen an einem Ort pflegen, damit der Betrieb ohne Entwicklerhilfe möglich ist.
  - *Akzeptanzkriterien:* Admin-Bereich zur Pflege der Empfänger-Stammdaten, Benutzerverwaltung und Konfiguration; nur für Rolle Admin sichtbar.

- [ ] **White-Label (A-14)** `Must` `A-14, K-07`
  - *User Story:* Als Produktverantwortlicher möchte ich, dass der Kunde ausschließlich die senapsa-Oberfläche sieht, damit das Produkt als eigenständige senapsa-Lösung auftritt.
  - *Akzeptanzkriterien:* Keine OSS-Bezeichnungen sichtbar (UI, Meldungen, Berichte). Alle Motoren im internen Docker-Netz gekapselt. OSS-Lizenzübersicht (SBOM) im Repository.

---

## EPIC: Ordner-Automatik (Watch-Folder)

Optionale Automatik („Kann", A-16): Eingangsordner aus dem alten Abas-ERP wird in flexibel einstellbarem Intervall verarbeitet; validierte XRechnungen landen im Ausgangsordner. Gleiche Verarbeitung, Prüfungen und Sperren wie beim Upload. Gilt zunächst on-premise (lokales Verzeichnis als Volume) bzw. für einen von der SaaS-Instanz bereitgestellten Übergabeordner (z. B. SFTP); Cloud-Anbindung eines Kunden-Ordners ist ein eigener, späterer Baustein.

- [ ] **Folder-Watcher (Intervall & Konfiguration)** `Kann` `A-16, K-11`
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich, dass Rechnungen aus dem Abas-Eingangsordner automatisch abgeholt werden, damit ich sie nicht mehr manuell hochladen muss.
  - *Akzeptanzkriterien:* Überwachung im konfigurierbaren Intervall (extraction-worker). Config: `WATCH_ENABLED`, `WATCH_INPUT_DIR`, `WATCH_OUTPUT_DIR`, `WATCH_INTERVAL`; Default aus. Zuordnung PDF↔CSV über gleichen Dateinamen.

- [ ] **Verarbeitung über den Standard-Ablauf** `Kann` `A-16, K-11`
  - *User Story:* Als Produktverantwortlicher möchte ich, dass Ordner-Dateien exakt den gleichen Weg gehen wie Uploads, damit dieselben Prüfungen, Sperren und Protokolle gelten.
  - *Akzeptanzkriterien:* Gleicher interner Ablauf wie Upload: Extraktion, Rechnungsmodell, Stammdaten-Autofill, Fachprüfung, Generierung, doppelte Validierung. Gleiche Statusmaschine/Audit-Log; kein Sonderpfad.

- [ ] **Automatischer Export in den Ausgangsordner** `Kann` `A-16, K-11, K-04`
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich fertige XRechnungen automatisch im Ausgangsordner finden, damit vollständig korrekte Rechnungen ohne mein Zutun bereitstehen.
  - *Akzeptanzkriterien:* Nur vollständig validierte Rechnungen werden automatisch als XRechnung-UBL (optional ZUGFeRD) samt Bericht abgelegt. Unsichere Fälle → Prüfbildschirm; Exportsperre gilt unverändert.

- [ ] **Ablage & Doppelverarbeitungsschutz** `Kann` `A-16, K-11, K-10`
  - *User Story:* Als Betreiber möchte ich, dass verarbeitete Eingangsdateien als erledigt bzw. fehlerhaft abgelegt werden, damit nichts doppelt verarbeitet wird oder verloren geht.
  - *Akzeptanzkriterien:* Verarbeitete Dateien im Eingangsordner als erledigt/fehlerhaft abgelegt. Erneute Ablage identischer Dateien führt nicht zu Doppelverarbeitung. Alle Schritte im Audit-Log.

---

## EPIC: Lasttest & Abnahme

Performance-Nachweis, Gesamtabnahme gegen K-01 bis K-11, Dokumentation (Kap. 8).

- [ ] **Lasttest 200 Rechnungen/Tag, 5 parallele Nutzer** `Must` `K-09`
  - *User Story:* Als Betreiber möchte ich das Auslegungsziel per Lasttest nachweisen, damit die Instanz im Kundenbetrieb zuverlässig durchhält.
  - *Akzeptanzkriterien:* Lasttest zur Verifikation (Queue-Dimensionierung); Verifikation mit dem Pilotkunden.

- [ ] **Abnahmetest Gesamt (K-01 bis K-11)** `Must` `K-01…K-11`
  - *User Story:* Als Produktverantwortlicher möchte ich alle Abnahmekriterien formal gegen Beispieldaten und amtliche Prüffälle testen, damit die Abnahme belastbar dokumentiert ist.
  - *Akzeptanzkriterien:* Formaler Abnahmetest gegen alle K-01–K-10 (sowie K-11 falls Ordner-Automatik aktiviert); DoD vollständig abgehakt.

- [ ] **Dokumentation** `Must` `docs`
  - *User Story:* Als Betreiber und Benutzer möchte ich vollständige Dokumentation, damit Installation, Bedienung und Wartung ohne Rückfragen möglich sind.
  - *Akzeptanzkriterien:* README, API (OpenAPI), Benutzerhandbuch, Admin-Handbuch, Mapping-Dokumentation, Betrieb/Backup-Anleitung.
