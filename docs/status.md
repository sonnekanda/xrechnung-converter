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

## Ausbaustufe / bewusst vereinfacht

- **KoSIT als zweite Validierung**: Schnittstelle vorhanden (`KOSIT_ENABLED`),
  aktuell übernimmt die Mustang-Validierung. Zweite Instanz nachrüstbar.
- **Ordner-Automatik** (A-16, „Kann"): `.env`-Schalter und Verzeichnisse
  vorgesehen; Watch-Schleife als Ausbaustufe markiert.
- **ZUGFeRD PDF/A-3** (Kann): nach positivem PoC; Schalter `ZUGFERD_ENABLED`.
- **Lasttest** (K-09): Skript/Vorgehen dokumentiert, nicht als Dauerlauf Teil
  dieses Stands.
- **Lizenzcontroller**: Schnittstelle vorgesehen (`LICENSE_*`), Durchsetzung vor
  Produktivgang.

## Umgebung dieser Verifikation

Lokale Toolchain: Python 3.14, Node 24, Docker. Java wird ausschließlich über
Docker gebaut/ausgeführt (kein lokales JDK). Der gesamte Stack startet über
`docker compose up`.
