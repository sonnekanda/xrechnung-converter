# Abnahme (K-01 … K-11)

Formale Zuordnung der Abnahmekriterien zum Umsetzungsstand mit Nachweis. Details
zum Verifikationsvorgehen in [`status.md`](status.md).

| Nr | Kriterium | Status | Nachweis |
|---|---|---|---|
| **K-01** | PDF vorrangig; OCR wo nötig + prüfpflichtig; CSV liefert eigenständig oder ergänzend eine gültige XRechnung | ✅ | PDF-Textpfad (400009) und CSV-Pfad (versioniertes Mapping) verifiziert; Scan (400014) → OCR, Ergebnisse „unsicher"; PDF↔CSV-Gegenprüfung. Tests: `extraction-worker/tests` (10 grün). |
| **K-02** | Alle Positionen erkannt, im Prüfbildschirm korrigierbar; unsichere Felder erfordern Bestätigung | ✅ | Positions-Erkennung PDF/CSV; Prüfbildschirm mit editierbaren Feldern (auch Positionen); gelbe Felder per ✓ bestätigbar. |
| **K-03** | Empfänger-Stammdaten gepflegt + automatisch eingefüllt | ✅ | Stammdaten-Verwaltung + Autofill (USt-IdNr/Kundennummer); „Empfänger speichern" im Prüfbildschirm. Verifiziert: Leitweg-ID/Adresse/E-Mail automatisch ergänzt. |
| **K-04** | Exportsperre bei fehlenden Pflichtfeldern / unklaren Positionen / falschen Summen | ✅ | Fachprüfung (Pflichtfelder, Summen, Netto×Satz=Steuer, Einzelpreis×Menge=Gesamtpreis, IBAN-Prüfsumme) + Statusmaschine; Export nur bei Status `validiert`, sonst HTTP 409. |
| **K-05** | Gültige XRechnung-XML (UBL) erzeugt + validiert; ZUGFeRD gemäß PoC | ✅ (UBL) | 400009 von Mustang **und** KoSIT (XRechnung 3.0.2) als gültig akzeptiert. ZUGFeRD = „Kann", nach PoC (offen). |
| **K-06** | Ungültige Dateien nie exportiert; Erfolg = XML + Validierungsbericht; Fehler feldbezogen | ✅ | Export nur bei doppelter Gültigkeit; XML + Bericht herunterladbar (UI/Ordner-Automatik); feldbezogene Meldungen mit Hervorhebung + Tooltip. |
| **K-07** | Nur senapsa-UI sichtbar (White-Label); Bedienung ohne Schulung | ✅ | Branding über `.env` (`BRAND_*`); keine OSS-Namen in der UI; Alltagssprache; Benutzerhandbuch. Motoren im internen Netz gekapselt. |
| **K-08** | `docker compose up` startet Stack aus `.env`; non-root; nur App-Port exponiert | ✅ | `docker-compose.yml` mit `sic-net`, Healthchecks, `depends_on: service_healthy`; alle Dienste non-root (`entrypoint.sh` + `gosu`); nur `WEBAPP_PORT` exponiert; kommentierte `.example.env`. |
| **K-09** | Lasttest: 200 Rechnungen/Tag, 5 parallele Nutzer | ✅ | `scripts/loadtest.py`; Messung: 30/30 ok, ~186 Rechnungen/Min (~11 000/h), p95 1,8 s → Auslegungsziel weit übertroffen. Siehe `betrieb.md`. |
| **K-10** | CI/CD mit Trivy-Scan; alles unter `git.senapsa.io`; Logs/Audit/Health; OSS-Lizenzen dokumentiert | ✅ | `.gitlab-ci.yml` (Test→Build→Trivy CRITICAL/HIGH→Push, SBOM je Image); strukturierte JSON-Logs (ECS) + Worker-JSON-Logs; Audit-Log (`audit_event`); `/health` je Dienst; `docs/oss-lizenzen.md`. |
| **K-11** | Ordner-Automatik (falls aktiv): korrekte Intervallverarbeitung, nur validierte Auto-Exports | ✅ | Watch-Folder verifiziert: 400009 aus Ordner → automatisch validiert (Mustang+KoSIT) → XML+Bericht in Ausgangsordner; Eingang nach `.done`; kein Auto-Export bei unsicheren Feldern; Inhalts-Hash gegen Doppelverarbeitung. |

## Definition of Done – Kurzabgleich

- Upload (Drag&Drop, Mehrfach), PDF vorrangig / CSV zweitrangig, OCR wo nötig ✅
- Internes Modell, Prüfbildschirm mit Ampel + Korrektur, Stammdaten-Autofill ✅
- Pflichtfelder/Summen/Steuern geprüft, kein Export bei Fehlern (kein Bypass) ✅
- XRechnung UBL erzeugt + validiert (Mustang + KoSIT), Bericht je Datei ✅
- White-Label; Motoren gekapselt/austauschbar; OSS-Lizenzübersicht (SBOM) ✅
- Compose-Stack aus einer `.env`; kommentierte `.example.env`; non-root ✅
- Logs, Audit-Log, Health-Checks; CI/CD mit Trivy ✅
- Lasttest bestanden ✅
- Tests (Unit/Integration/Negativ) grün; Doku (README, API/OpenAPI, Benutzer-,
  Admin-Handbuch, Mapping, Betrieb) ✅
- Ordner-Automatik funktionsfähig (K-11) ✅

**Offen / bewusst als „Kann"/„Später":** ZUGFeRD PDF/A-3 (nach PoC),
Lizenzcontroller-Durchsetzung, CII/Peppol, Gutschriften/Stornos, Multi-Tenancy.
