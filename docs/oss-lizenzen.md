# OSS-Komponenten & Lizenzen (SBOM-Hinweis)

Alle Fremdkomponenten laufen intern und sind gekapselt; für den Kunden sind
keine OSS-Namen sichtbar (White-Label, A-14). Die CI erzeugt je Image eine SBOM
(CycloneDX) via Trivy (`sbom-*.json`).

## Kernkomponenten

| Komponente | Zweck | Lizenz |
|---|---|---|
| Mustangproject | XRechnung-UBL/CII-Erzeugung + Validierung | Apache-2.0 |
| KoSIT-Validator (optional) | zweite amtliche Validierung | Apache-2.0 |
| pdfplumber / pdfmin.six | PDF-Textextraktion | MIT |
| Tesseract OCR / ocrmypdf | OCR für Scans | Apache-2.0 / MPL-2.0 |
| Spring Boot | webapp/invoice-engine Framework | Apache-2.0 |
| React / Vite | Frontend | MIT |
| PostgreSQL | Datenhaltung | PostgreSQL License |
| Redis | Job-Queue | BSD-3 / RSALv2/SSPL (v7 prüfen) |
| FastAPI / Uvicorn | Worker-HTTP | MIT / BSD |
| jjwt | JWT | Apache-2.0 |
| Flyway | DB-Migrationen/Versionierung | Apache-2.0 |

## SBOM erzeugen (lokal)

```bash
trivy image --format cyclonedx --output sbom-webapp.json \
  registry.senapsa.io/erechnung/webapp:latest
```

In der Pipeline geschieht dies automatisch je Dienst (Stage `scan`).
Vollständige, versionsgenaue Stücklisten liegen den CI-Artefakten bei.
