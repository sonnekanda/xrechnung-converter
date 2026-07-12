# Senapsa eRechnung-Konverter — Backlog (Epics & Stories)

> **Status-Kopie (Stand 2026-07-12)** — Abgleich der Anforderungen mit dem umgesetzten Stand.
> Original unverändert unter `aufgabe/`. Legende: **[x]** erfüllt · **🟡** teilweise · **❌** offen.


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

- [x] **Docker-Compose-Stack aufsetzen** `Must` `stack, K-08`  *(✅ 7 Services, sic-net, non-root+gosu, Healthchecks, EIN Compose-File, .env/.example.env)*
  - *User Story:* Als Betreiber möchte ich den gesamten Stack mit `docker compose up` aus einer `.env` starten, damit jede Kundeninstanz reproduzierbar in Cloud und on-premise läuft.
  - *Akzeptanzkriterien:* Alle Services (webapp, extraction-worker, ocr-worker, invoice-engine, postgres, redis) im internen Netz `sic-net`; nur App-Port exponiert. Benannte Volumes, gepinnte Images, Healthchecks, `depends_on: service_healthy`. Konfiguration ausschließlich über `.env`; kommentierte `.example.env` eingecheckt. Je Service Multi-Stage-Dockerfile, non-root, `entrypoint.sh` mit `gosu`, Daten unter `/data`.

- [ ] **CI/CD-Pipeline einrichten** `Must` `cicd, K-10`  *(🟡 `.gitlab-ci.yml` vollständig (Tests→Build→Trivy→SBOM→Push), aber mangels Zugang zu git.senapsa.io nie ausgeführt – abgestimmt)*
  - *User Story:* Als Developer möchte ich versionierte, gescannte Images automatisch bauen und pushen, damit jeder Commit nachvollziehbar und `main` jederzeit auslieferbar ist.
  - *Akzeptanzkriterien:* Repository unter `git.senapsa.io` mit `.gitlab-ci.yml`. Pipeline: Tests → Build → Tagging (`:latest`/`:version`/`:vversion`) → Trivy-Scan (CRITICAL, HIGH) → Push nach `registry.senapsa.io`. `main` ist immer auslieferbar (Feature-Branches + Merge Requests).

- [x] **Logging, Audit-Log & Health-Checks** `Must` `observability, K-10`  *(✅ strukturierte ECS-JSON-Logs nach stdout, Audit-Log je Rechnung, /actuator/health je Service)*
  - *User Story:* Als Betreiber möchte ich strukturierte Logs, ein Audit-Log und Health-Checks je Service, damit ich Betrieb und Verarbeitung jederzeit nachvollziehen kann.
  - *Akzeptanzkriterien:* Strukturierte Logs nach stdout (`LOGLEVEL` aus `.env`), keine Secrets im Log, vollständiges Audit-Log über alle Schritte, `/health`-Endpoint je Service.

- [x] **Lizenzcontroller-Schnittstelle vorbereiten** `Kann` `licensing`  *(✅ vorbereitet: LICENSE_* in .env + Statusanzeige; Durchsetzung offen, bis Lizenzpflicht bestätigt ist)*
  - *User Story:* Als Produktverantwortlicher möchte ich die Lizenz-Schnittstelle von Anfang an vorgesehen haben, damit die Durchsetzung vor Produktivgang ohne Umbau möglich ist.
  - *Akzeptanzkriterien:* Schnittstelle zum Lizenzcontroller ab Start vorsehen und vorbereiten; Durchsetzung des erlaubten Funktionsumfangs vor Produktivgang, falls das Produkt lizenzpflichtig ist. (DoD: Lizenzcontroller vor Produktivphase aktiv, falls Lizenzpflicht bestätigt.)

---

## EPIC: Webapp — Upload, Prüfbildschirm, Navigationsleiste

UI-Basisfunktionen: Anmeldung, Übersicht, Upload, Prüfbildschirm mit Ampellogik, Navigationsleiste, verständliche Fehlermeldungen (Kap. 3).

- [x] **Anmeldung / Login** `Must` `auth`  *(✅ JWT, Rollen Benutzer/Admin, Login-Lockout)*
  - *User Story:* Als Benutzer möchte ich mich mit meiner Rolle anmelden, damit ich nur die für mich vorgesehenen Funktionen sehe.
  - *Akzeptanzkriterien:* Login mit den Rollen Benutzer und Admin. (Teil von K-07: normaler Benutzer schafft alle drei Schritte ohne Schulung.)

- [x] **Übersicht / Dashboard** `Must` `dashboard`  *(✅ inkl. Sortierung, Datumsfilter+Monatsnavigation, Mehrfachauswahl, XML/ZUGFeRD/Bericht-Download, Zähler-Badge)*
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich alle Rechnungen mit Status und Ampel auf einen Blick sehen, damit ich sofort weiß, wo etwas zu tun ist.
  - *Akzeptanzkriterien:* Liste aller Rechnungen mit Status und Ampel; Download von XML und Prüfbericht bei validierten Rechnungen; Zähler für offene Prüfungen im Navigationsmenü. (K-07)

- [x] **Upload (A-01)** `Must` `A-01, K-01`  *(✅ Drag&Drop, Mehrfach, MAX_UPLOAD_MB, Typ-/Größenprüfung)*
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich PDF und/oder CSV einfach per Drag-and-drop hochladen, damit ich meine Abas-Rechnungen ohne Technikwissen einreichen kann.
  - *Akzeptanzkriterien:* Upload von PDF und/oder CSV per Drag-and-drop und Dateiauswahl (auch einzeln); Mehrfach-Upload mit Status je Datei. Erlaubte Typen: PDF, CSV; Größenlimit über `.env` (`MAX_UPLOAD_MB`); Prüfung von Typ, Größe, Berechtigung beim Upload.

- [x] **Navigationsleiste** `Must` `navigation`  *(✅ einklappbar (Griff an der Kante), Zustand je Benutzer, rollenabhängig, Einstellungen-Gruppe unten)*
  - *User Story:* Als Benutzer möchte ich über eine aufklappbare Navigationsleiste alle Hauptfunktionen erreichen, damit ich mich ohne Schulung zurechtfinde.
  - *Akzeptanzkriterien:* Aufklappbare Leiste links: eingeklappt Symbole, aufgeklappt Symbole mit Beschriftung, Zustand je Benutzer gespeichert. Menüpunkte: Dashboard/Übersicht, Upload, Stammdaten (nur Admin), Einstellungen (nur Admin), Abmelden. Aktiver Bereich hervorgehoben; Logo über `.env` konfigurierbar.

- [x] **Prüfbildschirm mit Ampellogik (A-08)** `Must` `A-08, K-02, K-04`  *(✅ PDF links/Formular rechts, Ampel je Feld, Korrektur+Bestätigen, feste Aktionsleiste – Button heißt „Prüfen“)*
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich Original und erkannte Daten nebeneinander mit Ampel sehen und direkt korrigieren, damit ich unsichere Werte schnell und sicher bestätigen kann.
  - *Akzeptanzkriterien:* Original-PDF links, Formular mit erkannten Daten rechts. Ampel je Feld (🟢/🟡/🔴), Korrektur direkt im Formular, Bestätigung unsicherer Felder vor Export. Button „XRechnung erzeugen".

- [x] **Fehlermeldungen in Alltagssprache (A-11)** `Must` `A-11, K-06`  *(✅ feld-/positionsbezogen mit Handlungsanweisung, keine Stacktraces)*
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich verständliche Fehlermeldungen mit Handlungsanweisung, damit ich Probleme ohne IT-Hilfe selbst beheben kann.
  - *Akzeptanzkriterien:* Feld- und positionsbezogene Fehlermeldungen mit Handlungsanweisung (z. B. „Leitweg-ID fehlt – bitte ergänzen"); keine technischen Details/Stacktraces.

- [x] **Rollen & Konfiguration (A-15)** `Must` `A-15`  *(✅ .env + Admin-Bereich; Stammdaten/Einstellungen nur Admin)*
  - *User Story:* Als Admin möchte ich Rollen und Konfiguration zentral pflegen, damit die Anwendung ohne Eingriff in Dateien verwaltet werden kann.
  - *Akzeptanzkriterien:* Rollen Benutzer/Admin; Konfiguration über `.env` und Admin-Bereich (inkl. Stammdaten). Stammdaten/Einstellungen nur für Admin sichtbar.

---

## EPIC: Rechnungsmodell & Fachprüfung

Internes Rechnungsmodell als Zwischenschicht, Fachprüfung, Statusmaschine/Audit, Versionierung von Mappings & Regeln (Kap. 7).

- [x] **Internes Rechnungsmodell (A-05)** `Must` `A-05`  *(✅ JSON je Rechnung, Kopf/Summen/Positionen, wert/herkunft/status(/confidence); einzige Zwischenschicht)*
  - *User Story:* Als Developer möchte ich alle erkannten Werte in einem einzigen Modell mit Herkunft, Status und Confidence führen, damit Erkennung und Generierung sauber entkoppelt sind.
  - *Akzeptanzkriterien:* Einzige Zwischenschicht: JSON-Dokument je Rechnung mit Blöcken Kopf, Summen, Positionen. Jedes Feld mit Wert, `herkunft` (pdf, csv, ocr, stammdaten, manuell), `status` (sicher, unsicher, fehlt), ggf. `confidence` (0–1). Alle Motoren liefern ausschließlich in dieses Modell.

- [x] **Fachprüfung (A-07)** `Must` `A-07, K-04`  *(✅ Pflichtfelder, Summen, Steuerlogik, Rundungstoleranz aus .env; zusätzlich Arithmetik-Checks je Position)*
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich, dass Pflichtfelder, Summen und Steuern automatisch geprüft werden, damit keine fehlerhafte Rechnung exportiert werden kann.
  - *Akzeptanzkriterien:* Prüfung von Pflichtfeldern, Netto-/Steuer-/Bruttosummen, Steuerlogik, Rundungstoleranz.

- [x] **Statusmaschine & Audit (A-12)** `Must` `A-12, K-04, K-10`  *(✅ Export nur im Status validiert (sonst 409), lückenloses Audit-Log)*
  - *User Story:* Als Betreiber möchte ich feste Zustände und ein lückenloses Protokoll je Rechnung, damit die Exportsperre technisch nicht umgehbar und alles auditierbar ist.
  - *Akzeptanzkriterien:* Statusmaschine: `hochgeladen → in Verarbeitung → prüfen → validiert → exportiert`. Bei Validierungsfehler zurück zu `prüfen` mit feldbezogener Fehlerliste. Export nur im Zustand `validiert` (sonst API 409). Alles auditierbar protokolliert.

- [x] **Versionierung von Mappings & Regeln (A-13)** `Must` `A-13`  *(✅ Abas-Mapping versioniert (abas-v1, je Rechnung gespeichert); Prüfregeln über gepinnte Mustang-/KoSIT-Versionen)*
  - *User Story:* Als Betreiber möchte ich Mappings und Regeln versioniert austauschen können, damit Spezifikations-Updates ohne Kern-Deployment möglich sind.
  - *Akzeptanzkriterien:* Mappings und Validierungsregeln versionieren.

---

## EPIC: PDF-Extraktion

Vorrangiger Datenpfad: Textebene lesen, Positionen erkennen (Kap. 5 & 6, A-02, A-04).

- [x] **PDF-Extraktion Textebene (A-02)** `Must` `A-02, K-01`  *(✅ pdfplumber-Textpfad, OCR-Fallback; an 400009 verifiziert)*
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich, dass meine PDF-Rechnung automatisch ausgelesen wird, damit ich Daten nicht abtippen muss.
  - *Akzeptanzkriterien:* Vorrangiger Datenpfad: zuerst Textebene lesen (pdfplumber/invoice2data). OCR springt ein, wo nötig; OCR-Ergebnisse immer mit Confidence je Feld und prüfpflichtig.

- [ ] **Positionserkennung aus PDF (A-04, PDF-Teil)** `Must` `A-04, K-02`  *(🟡 Menge/Einheit/Preise/Steuer/Artikelnr./Beschreibung ✅ – **Rabatt nicht modelliert** (in Beispieldaten nicht vorhanden))*
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich, dass alle Rechnungspositionen vollständig erkannt werden, damit die XRechnung inhaltlich der Originalrechnung entspricht.
  - *Akzeptanzkriterien:* Alle Positionen aus dem PDF erkennen (Menge, Einheit, Einzel-/Gesamtpreis, Steuer, Rabatt, Artikelnummer, Beschreibung).

---

## EPIC: CSV-Datenpfad

Zweitrangiger, vollwertiger Datenpfad über versioniertes Abas-Mapping inkl. Gegenprüfung zum PDF (A-03, A-04).

- [x] **CSV-Mapping Abas (A-03)** `Must` `A-03, K-01`  *(✅ vollwertiger Pfad; dokumentierte Abweichung zur Annahme: bei PDF+CSV führt die CSV (strukturierte Daten), PDF/OCR füllt nur Lücken – abgestimmt)*
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich auch eine Abas-CSV allein zur XRechnung verarbeiten können, damit ich nicht auf das PDF angewiesen bin.
  - *Akzeptanzkriterien:* Liegt nur eine CSV vor, wird die Rechnung vollständig daraus verarbeitet. Liegen PDF und CSV vor, führt das PDF; die CSV dient Ergänzung/Gegenprüfung.

- [x] **Positionserkennung aus CSV (A-04, CSV-Teil)** `Must` `A-04, K-02`  *(✅ alles im Export Vorhandene; Abas-CSV enthält keine Menge/Einheit/Einzelpreis/Beschreibung → rot bzw. aus PDF (dokumentierte Datengrenze))*
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich, dass alle Positionen auch aus der CSV vollständig übernommen werden, damit beide Datenpfade gleichwertig funktionieren.
  - *Akzeptanzkriterien:* Alle Positionen aus der CSV erkennen (Menge, Einheit, Preise, Steuer, Rabatt, Artikelnummer, Beschreibung).

- [x] **Gegenprüfung PDF ↔ CSV** `Must` `A-03, K-01`  *(✅ Abweichung = gelb am Feld, Nutzer entscheidet; bewusst nur gegen die PDF-Textebene, nicht gegen OCR)*
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich bei Abweichungen zwischen PDF und CSV eine gelbe Markierung sehen, damit ich bewusst entscheide, welcher Wert stimmt.
  - *Akzeptanzkriterien:* Bei Abweichung wird das betroffene Feld gelb markiert; der Benutzer entscheidet im Prüfbildschirm.

---

## EPIC: OCR

OCR überall dort, wo notwendig — fehlende/unbrauchbare Textebene, unsichere Werte, Bildinhalte; Ergebnisse immer prüfpflichtig (A-02, Rang 5).

- [ ] **OCR-Worker (Tesseract)** `Soll` `ocr, K-01`  *(🟡 Tesseract/ocrmypdf, deutsch, asynchron über Queue ✅ – numerische Confidence je Feld nicht geliefert (stattdessen pauschal „unsicher“))*
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich, dass auch gescannte oder bildhafte Rechnungen erkannt werden, damit ich keine Rechnung manuell erfassen muss.
  - *Akzeptanzkriterien:* Texterkennung für gescannte PDFs ohne Textebene bzw. eingebettete Bildinhalte; deutsche Modelle; Confidence-Wert je Erkennung. Asynchrone Verarbeitung über Job-Queue.

- [x] **OCR-Confidence & Prüfpflicht** `Soll` `ocr, K-01, K-02`  *(✅ OCR-Werte immer gelb/prüfpflichtig; vom Auto-Bestätigen der Ordner-Automatik ausgenommen)*
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich OCR-Werte immer als unsicher markiert sehen, damit nichts Ungeprüftes in die XRechnung gelangt.
  - *Akzeptanzkriterien:* OCR-Ergebnisse grundsätzlich als „unsicher" (gelb) markiert und müssen im Prüfbildschirm bestätigt werden — unabhängig von der Confidence.

---

## EPIC: Generierung & Validierung

XRechnung-Generierung (UBL/ZUGFeRD) und doppelte amtliche Validierung (Mustangproject + KoSIT) (A-09, A-10).

- [x] **XRechnung-Generierung UBL (A-09)** `Must` `A-09, K-05`  *(✅ Mustang CII→UBL, XRechnung-3.0-CustomizationID, EndpointID BT-34/49)*
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich per Klick eine XRechnung in UBL erhalten, damit ich die gesetzlichen Anforderungen meiner Empfänger erfülle.
  - *Akzeptanzkriterien:* Erzeugung der XRechnung-XML in UBL-Syntax aus dem internen Rechnungsmodell (Mustangproject als invoice-engine-Service). ZUGFeRD EN 16931, sofern PDF/A-3-PoC positiv.

- [x] **Doppelte Validierung Mustangproject + KoSIT (A-10)** `Must` `A-10, K-04, K-05, K-06`  *(✅ Mustang in-process + KoSIT-Daemon; Export nur wenn BEIDE grün; Bericht je Datei (+ lesbarer Dialog))*
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich, dass jede Datei zweifach amtlich geprüft wird, damit „validiert" wirklich validiert heißt.
  - *Akzeptanzkriterien:* Jede Zieldatei wird gegen zwei unabhängige Instanzen validiert (XSD + Schematron + Business Rules): Mustangproject und KoSIT-Validator. Export nur bei Erfolg beider Prüfungen; Bericht je Datei.

- [ ] **ZUGFeRD PoC** `Kann` `zugferd, K-05`  *(🟡 technisch über PoC hinaus umgesetzt (Hybrid-PDF für Rechnungen mit Original-PDF); offen: strikte PDF/A-3-Konformität (Best-Effort) + Format-Abstimmung mit 2–3 Empfängern)*
  - *User Story:* Als Produktverantwortlicher möchte ich per PoC klären, ob ZUGFeRD PDF/A-3 machbar und gefordert ist, damit wir nur bauen, was Empfänger wirklich brauchen.
  - *Akzeptanzkriterien:* PoC für ZUGFeRD-PDF/A-3 im Profil EN 16931; vorab Abstimmung mit 2–3 konkreten Rechnungsempfängern, welches Format tatsächlich gefordert wird.

---

## EPIC: Stammdaten & Admin

Empfänger-Stammdaten, Admin-Bereich, White-Label-Umsetzung (A-06, A-14, A-15 Admin-Teil).

- [x] **Empfänger-Stammdaten (A-06)** `Must` `A-06, K-03`  *(✅ inkl. E-Mail (BT-49) und Autofill; „Empfänger speichern“ direkt aus dem Prüfbildschirm)*
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich Leitweg-ID, Bank, USt-ID und Zahlungsbedingungen je Empfänger hinterlegen, damit fehlende Pflichtfelder automatisch ergänzt werden.
  - *Akzeptanzkriterien:* Stammdaten je Rechnungsempfänger mit automatischem Ausfüllen fehlender Pflichtfelder im Rechnungsmodell.

- [x] **Admin-Bereich (Benutzerverwaltung, Konfiguration)** `Must` `A-15, K-03`  *(✅ Stammdaten, Benutzer, Ordner-Automatik, Konfiguration – nur Admin)*
  - *User Story:* Als Admin möchte ich Benutzer, Stammdaten und Einstellungen an einem Ort pflegen, damit der Betrieb ohne Entwicklerhilfe möglich ist.
  - *Akzeptanzkriterien:* Admin-Bereich zur Pflege der Empfänger-Stammdaten, Benutzerverwaltung und Konfiguration; nur für Rolle Admin sichtbar.

- [x] **White-Label (A-14)** `Must` `A-14, K-07`  *(✅ keine OSS-Namen in UI/Meldungen/Berichten; Motoren intern; docs/oss-lizenzen.md)*
  - *User Story:* Als Produktverantwortlicher möchte ich, dass der Kunde ausschließlich die senapsa-Oberfläche sieht, damit das Produkt als eigenständige senapsa-Lösung auftritt.
  - *Akzeptanzkriterien:* Keine OSS-Bezeichnungen sichtbar (UI, Meldungen, Berichte). Alle Motoren im internen Docker-Netz gekapselt. OSS-Lizenzübersicht (SBOM) im Repository.

---

## EPIC: Ordner-Automatik (Watch-Folder)

Optionale Automatik („Kann", A-16): Eingangsordner aus dem alten Abas-ERP wird in flexibel einstellbarem Intervall verarbeitet; validierte XRechnungen landen im Ausgangsordner. Gleiche Verarbeitung, Prüfungen und Sperren wie beim Upload. Gilt zunächst on-premise (lokales Verzeichnis als Volume) bzw. für einen von der SaaS-Instanz bereitgestellten Übergabeordner (z. B. SFTP); Cloud-Anbindung eines Kunden-Ordners ist ein eigener, späterer Baustein.

- [x] **Folder-Watcher (Intervall & Konfiguration)** `Kann` `A-16, K-11`  *(✅ Intervall/Settle im Admin ohne Neustart; Paarung über Dateinamen; ein Compose-File, Host-Ordner per .env)*
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich, dass Rechnungen aus dem Abas-Eingangsordner automatisch abgeholt werden, damit ich sie nicht mehr manuell hochladen muss.
  - *Akzeptanzkriterien:* Überwachung im konfigurierbaren Intervall (extraction-worker). Config: `WATCH_ENABLED`, `WATCH_INPUT_DIR`, `WATCH_OUTPUT_DIR`, `WATCH_INTERVAL`; Default aus. Zuordnung PDF↔CSV über gleichen Dateinamen.

- [x] **Verarbeitung über den Standard-Ablauf** `Kann` `A-16, K-11`  *(✅ identischer Weg wie Upload, gleiche Statusmaschine/Audit)*
  - *User Story:* Als Produktverantwortlicher möchte ich, dass Ordner-Dateien exakt den gleichen Weg gehen wie Uploads, damit dieselben Prüfungen, Sperren und Protokolle gelten.
  - *Akzeptanzkriterien:* Gleicher interner Ablauf wie Upload: Extraktion, Rechnungsmodell, Stammdaten-Autofill, Fachprüfung, Generierung, doppelte Validierung. Gleiche Statusmaschine/Audit-Log; kein Sonderpfad.

- [x] **Automatischer Export in den Ausgangsordner** `Kann` `A-16, K-11, K-04`  *(✅ nur vollständig gültige; Zielformat wählbar: XRechnung, ZUGFeRD oder beides)*
  - *User Story:* Als Buchhaltungsmitarbeiter möchte ich fertige XRechnungen automatisch im Ausgangsordner finden, damit vollständig korrekte Rechnungen ohne mein Zutun bereitstehen.
  - *Akzeptanzkriterien:* Nur vollständig validierte Rechnungen werden automatisch als XRechnung-UBL (optional ZUGFeRD) samt Bericht abgelegt. Unsichere Fälle → Prüfbildschirm; Exportsperre gilt unverändert.

- [x] **Ablage & Doppelverarbeitungsschutz** `Kann` `A-16, K-11, K-10`  *(✅ .processing/.done/.error, Inhalts-Hash-Dedup; Vermerk wird beim Löschen der Rechnung freigegeben)*
  - *User Story:* Als Betreiber möchte ich, dass verarbeitete Eingangsdateien als erledigt bzw. fehlerhaft abgelegt werden, damit nichts doppelt verarbeitet wird oder verloren geht.
  - *Akzeptanzkriterien:* Verarbeitete Dateien im Eingangsordner als erledigt/fehlerhaft abgelegt. Erneute Ablage identischer Dateien führt nicht zu Doppelverarbeitung. Alle Schritte im Audit-Log.

---

## EPIC: Lasttest & Abnahme

Performance-Nachweis, Gesamtabnahme gegen K-01 bis K-11, Dokumentation (Kap. 8).

- [x] **Lasttest 200 Rechnungen/Tag, 5 parallele Nutzer** `Must` `K-09`  *(✅ gemessen ~186 Rechnungen/min (p95 1,8 s) – Ziel weit übertroffen; Verifikation mit Pilotkunden offen (organisatorisch))*
  - *User Story:* Als Betreiber möchte ich das Auslegungsziel per Lasttest nachweisen, damit die Instanz im Kundenbetrieb zuverlässig durchhält.
  - *Akzeptanzkriterien:* Lasttest zur Verifikation (Queue-Dimensionierung); Verifikation mit dem Pilotkunden.

- [ ] **Abnahmetest Gesamt (K-01 bis K-11)** `Must` `K-01…K-11`  *(🟡 docs/abnahme.md deckt K-01…K-11 mit Nachweisen ab; formale Abnahme offen (Java-Unit-Tests fehlen, CI nie gelaufen))*
  - *User Story:* Als Produktverantwortlicher möchte ich alle Abnahmekriterien formal gegen Beispieldaten und amtliche Prüffälle testen, damit die Abnahme belastbar dokumentiert ist.
  - *Akzeptanzkriterien:* Formaler Abnahmetest gegen alle K-01–K-10 (sowie K-11 falls Ordner-Automatik aktiviert); DoD vollständig abgehakt.

- [x] **Dokumentation** `Must` `docs`  *(✅ README, OpenAPI/Swagger, Benutzer-, Admin-, Mapping-, Betriebs-/Backup-Doku)*
  - *User Story:* Als Betreiber und Benutzer möchte ich vollständige Dokumentation, damit Installation, Bedienung und Wartung ohne Rückfragen möglich sind.
  - *Akzeptanzkriterien:* README, API (OpenAPI), Benutzerhandbuch, Admin-Handbuch, Mapping-Dokumentation, Betrieb/Backup-Anleitung.
