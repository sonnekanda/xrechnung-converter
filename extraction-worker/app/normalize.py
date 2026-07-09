"""Normalisierung deutscher Zahlen- und Datumsformate.

Werte werden kanonisch abgelegt: Betraege als Dezimal-String mit Punkt
(z.B. ``"118059.90"``), Datum als ISO-8601 (``"2026-07-02"``). So bleibt das
interne Modell frei von Locale-Mehrdeutigkeiten.
"""
from __future__ import annotations

import re
from datetime import date, datetime
from decimal import Decimal, InvalidOperation

_NUM_RE = re.compile(r"[-+]?\d[\d.  ]*(?:,\d+)?")


def parse_german_decimal(raw: str | None) -> Decimal | None:
    """``"1.234,56"`` / ``"-58.976,40"`` / ``"0"`` -> Decimal. None bei leer/ungueltig."""
    if raw is None:
        return None
    s = str(raw).strip().replace(" ", "").replace(" ", "")
    if not s:
        return None
    # Tausenderpunkt entfernen, Dezimalkomma -> Punkt.
    s = s.replace(".", "").replace(",", ".")
    try:
        return Decimal(s)
    except InvalidOperation:
        return None


def format_decimal(value: Decimal | int | float | None) -> str | None:
    """Kanonische Betragsdarstellung mit 2 Nachkommastellen (Punkt)."""
    if value is None:
        return None
    if not isinstance(value, Decimal):
        value = Decimal(str(value))
    return f"{value:.2f}"


def parse_german_date(raw: str | None) -> str | None:
    """``"08.07.2026"`` -> ``"2026-07-08"`` (ISO). None bei leer/ungueltig."""
    if raw is None:
        return None
    s = str(raw).strip()
    if not s:
        return None
    for fmt in ("%d.%m.%Y", "%d.%m.%y", "%Y-%m-%d"):
        try:
            return datetime.strptime(s, fmt).date().isoformat()
        except ValueError:
            continue
    return None


def format_german_from_iso(iso: str | None) -> str | None:
    """ISO-Datum -> deutsche Anzeige (fuer Berichte/UI)."""
    if not iso:
        return None
    try:
        return date.fromisoformat(iso).strftime("%d.%m.%Y")
    except ValueError:
        return None


def find_amount(text: str) -> Decimal | None:
    """Erster plausibler Betrag in einem Textfragment (OCR/Freitext)."""
    m = _NUM_RE.search(text or "")
    return parse_german_decimal(m.group(0)) if m else None
