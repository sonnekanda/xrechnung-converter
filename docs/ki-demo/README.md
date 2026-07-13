# KI-Assistent – Demo-Rechnungen

Zum Vorführen des **KI-Assistenten** in der Präsentation.

**Warum eigene Demo-Dateien?** Die echten Abas-Musterrechnungen sind so
vollständig, dass die deterministischen Pfade (CSV-Mapping / PDF-Layout) alle
Felder sicher füllen – der KI-Fallback kommt dort nie zum Zug. Diese PDFs sind
bewusst in einem **freien, Nicht-Abas-Layout** gehalten: Der Layout-Extractor
erkennt die Kopf-/Käufer-Felder nicht, sie bleiben `fehlt`, und der
KI-Assistent schlägt sie aus dem Freitext vor – immer `herkunft=ki`, gelb,
**prüfpflichtig** (nie Auto-Export).

## Die Sets

| Datei | Zweck |
|---|---|
| `set-a_rechnung-1.pdf` | Käufer + Metadaten vollständig im Text → KI füllt **alle 8** Kandidatenfelder (Rechnungsnr., -datum, Fälligkeit, Käufer: Name/Straße/PLZ/Ort/USt-IdNr). |
| `set-a_rechnung-2.pdf` | wie oben, anderer Käufer (Nordlicht Handels AG). |
| `set-b_rechnung-1_ohne-ustid.pdf` | Bewusst unvollständig: **keine** USt-IdNr, **kein** Fälligkeitsdatum im Text. KI füllt nur das Vorhandene, `faelligkeit` und `kaeufer_ustid` bleiben rot → zeigt schön, dass die KI **nichts erfindet**. |
| `set-b_rechnung-2.pdf` | Käufer vollständig (Grünwald Consulting, München). |

## Vorführen

1. KI-Assistent aktiv? Admin-Bereich **KI-Assistent** (`/admin/ki-assistent`)
   bzw. `.env`: `KI_ENABLED=true`, gültiges `OPENROUTER_API_KEY`, ein
   funktionierendes `KI_MODEL`. Nach `.env`-Änderung:
   `docker compose up -d extraction-worker`.
2. **Hochladen** → eine der PDFs wählen.
3. Im **Prüfbildschirm**: die von der KI vorgeschlagenen Käufer-/Kopffelder
   erscheinen **gelb** mit Hinweis „Von der KI vorgeschlagen – bitte prüfen und
   bestätigen." Der Verkäufer bleibt unangetastet (eigene Firma aus Stammdaten).

## Grenzen (bewusst)

Diese Belege dienen **nur** der KI-Vorführung. Da sie kein Abas-Layout haben,
werden **Positionen und Summen nicht erkannt** – die Rechnungen sind also nicht
export-fertig (Positionen/Summen bleiben rot). Für den vollständigen
Happy-Path (bis zur gültigen XRechnung) die echten Abas-Muster verwenden.

## Neu erzeugen

```
python scripts/ki-demo-rechnungen.py [ziel-ordner]
```

Ohne Argument landet die Ausgabe wieder in `docs/ki-demo/`. Der Generator
braucht keine externen Bibliotheken.
