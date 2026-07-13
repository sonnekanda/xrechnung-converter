#!/usr/bin/env python3
"""Erzeugt Demo-Rechnungen (PDF) zum Vorfuehren des KI-Assistenten.

Hintergrund: Die echten Abas-Musterrechnungen sind so vollstaendig, dass die
deterministischen Pfade (CSV/PDF-Layout) alle Felder sicher fuellen - der
KI-Fallback kommt nie zum Zug. Diese Demo-PDFs sind bewusst in einem freien,
NICHT-Abas-Layout gehalten: der Layout-Extractor erkennt die Kaeufer-/Kopf-
felder nicht, sie bleiben "fehlt", und der KI-Assistent schlaegt sie aus dem
Freitext vor (herkunft=ki, gelb, immer pruefpflichtig).

Ohne externe Libs: minimaler, gueltiger 1-Seiten-PDF mit Textebene
(WinAnsiEncoding -> Umlaute werden korrekt dargestellt UND wieder extrahiert).

Aufruf:  python scripts/ki-demo-rechnungen.py [ziel-ordner]
Default-Ziel: docs/ki-demo/
"""
from __future__ import annotations

import sys
from pathlib import Path


def _pdf(lines: list[str]) -> bytes:
    parts = ["BT", "/F1 12 Tf", "50 800 Td", "16 TL"]
    for ln in lines:
        esc = ln.replace("\\", r"\\").replace("(", r"\(").replace(")", r"\)")
        parts.append(f"({esc}) Tj")
        parts.append("T*")
    parts.append("ET")
    stream = "\n".join(parts).encode("latin-1")

    objs = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
        b"/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
        b"<< /Length " + str(len(stream)).encode() + b" >>\nstream\n" + stream + b"\nendstream",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
    ]
    out = bytearray(b"%PDF-1.4\n")
    offsets = []
    for i, body in enumerate(objs, start=1):
        offsets.append(len(out))
        out += f"{i} 0 obj\n".encode() + body + b"\nendobj\n"
    xref = len(out)
    out += f"xref\n0 {len(objs)+1}\n".encode() + b"0000000000 65535 f \n"
    for off in offsets:
        out += f"{off:010d} 00000 n \n".encode()
    out += (f"trailer\n<< /Size {len(objs)+1} /Root 1 0 R >>\n"
            f"startxref\n{xref}\n%%EOF\n").encode()
    return bytes(out)


# Jede Rechnung nennt Rechnungsnummer/-datum + Kaeufer im Freitext. Was im Text
# steht, schlaegt die KI vor (gelb); was fehlt, bleibt rot -> zeigt schoen, dass
# die KI nichts erfindet. Der VERKAEUFER wird nie aus dem Text genommen (immer
# eigene Firma aus den Stammdaten) - hier nur als Briefkopf, ohne Wirkung.
RECHNUNGEN = [
    # --- Set A: Kaeufer vollstaendig im Text -> KI fuellt alle Kaeufer-Felder ---
    ("set-a_rechnung-1.pdf", [
        "Senapsa Test GmbH  .  Teststrasse 1  .  65868 Testhausen",
        "",
        "R E C H N U N G",
        "",
        "Rechnungsnummer: RE-2026-0815",
        "Rechnungsdatum:  15.06.2026",
        "Zahlbar bis:     29.06.2026",
        "",
        "Rechnungsempfaenger:",
        "Mueller Fahrzeugteile GmbH",
        "Industriestrasse 42",
        "40474 Duesseldorf",
        "USt-IdNr. des Empfaengers: DE811234567",
        "",
        "Leistung: Ersatzteile laut Bestellung. Rechnungsbetrag 2.380,00 EUR inkl. USt.",
    ]),
    ("set-a_rechnung-2.pdf", [
        "Senapsa Test GmbH  .  Teststrasse 1  .  65868 Testhausen",
        "",
        "Rechnung 2026/0442",
        "",
        "Datum der Rechnung: 20.06.2026",
        "Faelligkeit: 04.07.2026",
        "",
        "An:",
        "Nordlicht Handels AG",
        "Am Hafen 7",
        "20457 Hamburg",
        "Umsatzsteuer-Identifikationsnummer: DE245998877",
        "",
        "Beratungsleistung Juni 2026. Summe 1.190,00 EUR brutto.",
    ]),
    # --- Set B: absichtlich unvollstaendig -> KI fuellt nur das Vorhandene ---
    ("set-b_rechnung-1_ohne-ustid.pdf", [
        "Senapsa Test GmbH  .  Teststrasse 1  .  65868 Testhausen",
        "",
        "RECHNUNG Nr. R-9910",
        "Rechnungsdatum: 28.06.2026",
        "",
        "Kunde:",
        "Baeckerei Sonnenschein e.K.",
        "Dorfplatz 3",
        "82041 Oberhaching",
        "",
        "(keine USt-IdNr angegeben, kein Faelligkeitsdatum)",
        "Warenlieferung. Betrag 476,00 EUR.",
    ]),
    ("set-b_rechnung-2.pdf", [
        "Senapsa Test GmbH  .  Teststrasse 1  .  65868 Testhausen",
        "",
        "R e c h n u n g",
        "",
        "Beleg-Nr.: 2026-KW26-118",
        "Ausgestellt am: 25.06.2026, faellig am 09.07.2026",
        "",
        "Rechnung an:",
        "Gruenwald Consulting GmbH",
        "Maximilianstrasse 13",
        "80539 Muenchen",
        "USt-IdNr: DE320445566",
        "",
        "Projektpauschale. 5.950,00 EUR inklusive Mehrwertsteuer.",
    ]),
]


def main() -> None:
    ziel = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent / "docs" / "ki-demo"
    ziel.mkdir(parents=True, exist_ok=True)
    for name, lines in RECHNUNGEN:
        (ziel / name).write_bytes(_pdf(lines))
        print("geschrieben:", ziel / name)
    print(f"\n{len(RECHNUNGEN)} Demo-Rechnungen in {ziel}")


if __name__ == "__main__":
    main()
