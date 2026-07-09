# Umsetzungsstand

Ehrliche Übersicht: was verifiziert läuft, was als Ausbaustufe angelegt ist.
Bezug: Anforderungen A-01…A-16 und Abnahmekriterien K-01…K-11 aus dem
Entwicklungsdokument.

## Verifiziert (ausgeführt, nicht nur geschrieben)

- **CSV-Datenpfad** (A-03/A-04): 10 automatisierte Tests grün gegen die realen
  Abas-Muster – Summen-Pseudozeilen (9/6/1), CP1252-/Header-Erkennung,
  Gutschrift-Erkennung, PDF↔CSV-Gegenprüfung.
- **PDF-Textextraktion** (A-02/A-04): Kopf, Positionen und Summen aus 400009
  korrekt erkannt; Scan (400014) korrekt als `needs_ocr` erkannt.
- **Generierung + Validierung** (A-09/A-10, K-05): Die `invoice-engine`
  kompiliert gegen Mustangproject 2.16.0; ein Lauf mit 400009 erzeugt eine
  **gültige** XRechnung-UBL (EN 16931, 54 Schematron-Regeln, 0 Fehler). Beispiel:
  `docs/beispiel-rechnung-400009.ubl.xml`.
- **Frontend** baut (Vite) und liefert Login, Übersicht, Upload, Prüfbildschirm,
  Stammdaten aus.
- **webapp** (Java) kompiliert vollständig (Security/JWT, JPA, Redis, RestClient,
  Statusmaschine, Fachprüfung).

## Implementiert (im Stack aktiv)

- Upload PDF/CSV, Statusmaschine, internes Modell, Stammdaten-Autofill,
  Fachprüfung mit Exportsperre (kein Bypass), Export nur bei `validiert` (409),
  Rollen Benutzer/Admin, Audit-Log, White-Label-Branding über `.env`,
  strukturierte Logs, Health-Checks, OpenAPI.
- `ocr-worker` (Tesseract/ocrmypdf) als Dienst; Aufruf durch den
  extraction-worker bei fehlender Textebene, Ergebnisse immer „unsicher".
- CI/CD-Pipeline (`.gitlab-ci.yml`) mit Test → Build → Trivy-Scan → Push, SBOM
  je Image.

## Verifiziert (Nachtrag)

- **Doppelte amtliche Validierung** (A-10, K-05): Der KoSIT-Validator läuft als
  eigener Dienst (`kosit-validator`) mit der offiziellen XRechnung-3.0.2-
  Prüfkonfiguration (HTTP-Daemon, isolierter Classpath). Die invoice-engine ruft
  ihn nach der Mustang-Prüfung auf; Export nur, wenn BEIDE bestehen (`KOSIT_ENABLED`).
  Verifiziert: Rechnung 400009 wird von Mustang UND KoSIT als gültig akzeptiert
  (HTTP 200). Dabei wurde sichtbar, dass Mustangs UBL erst XRechnung-konform
  gemacht werden musste (CustomizationID `xeinkauf.de:kosit:xrechnung_3.0`,
  elektronische Adressen BT-34/BT-49) - genau der Mehrwert der zweiten Instanz.

## Ausbaustufe / bewusst vereinfacht
- **Ordner-Automatik** (A-16, „Kann"): implementiert und verifiziert. Der
  Eingangsordner wird im konfigurierbaren Intervall überwacht; neue Dateien
  laufen über denselben Weg wie ein Upload (kein Sonderpfad). Nur vollständig
  gültige Rechnungen werden automatisch als XRechnung + Bericht in den
  Ausgangsordner exportiert, unsichere landen im Prüfbildschirm. Verarbeitete
  Eingangsdateien werden nach `.done`/`.error` verschoben; identische
  Wiederablage wird per Inhalts-Hash abgefangen. Default aus (`WATCH_ENABLED`).
- **ZUGFeRD PDF/A-3** (Kann): nach positivem PoC; Schalter `ZUGFERD_ENABLED`.
- **Lasttest** (K-09): Skript/Vorgehen dokumentiert, nicht als Dauerlauf Teil
  dieses Stands.
- **Lizenzcontroller**: Schnittstelle vorgesehen (`LICENSE_*`), Durchsetzung vor
  Produktivgang.

## Umgebung dieser Verifikation

Lokale Toolchain: Python 3.14, Node 24, Docker. Java wird ausschließlich über
Docker gebaut/ausgeführt (kein lokales JDK). Der gesamte Stack startet über
`docker compose up`.
