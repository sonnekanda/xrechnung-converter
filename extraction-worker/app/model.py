"""Bausteine des internen Rechnungsmodells.

Alle Motoren (PDF, OCR, CSV) erzeugen ausschliesslich Felder ueber ``feld()``.
Die Ampel im Pruefbildschirm ist eine direkte Anzeige von ``status`` + ``herkunft``.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any


class Herkunft(str, Enum):
    PDF = "pdf"
    CSV = "csv"
    OCR = "ocr"
    KI = "ki"              # LLM-Fallback (unsicher, immer pruefpflichtig)
    STAMMDATEN = "stammdaten"
    MANUELL = "manuell"
    BERECHNET = "berechnet"


class Status(str, Enum):
    SICHER = "sicher"      # gruen
    UNSICHER = "unsicher"  # gelb  -> bestaetigen
    FEHLT = "fehlt"        # rot   -> ergaenzen


def feld(
    wert: Any,
    herkunft: Herkunft | str,
    status: Status | str,
    confidence: float | None = None,
    hinweis: str | None = None,
) -> dict[str, Any]:
    """Erzeugt ein Feld des internen Modells."""
    h = herkunft.value if isinstance(herkunft, Herkunft) else herkunft
    s = status.value if isinstance(status, Status) else status
    out: dict[str, Any] = {"wert": wert, "herkunft": h, "status": s}
    if confidence is not None:
        out["confidence"] = round(float(confidence), 4)
    if hinweis is not None:
        out["hinweis"] = hinweis
    return out


def fehlend(hinweis: str) -> dict[str, Any]:
    """Rotes Pflichtfeld ohne Wert, mit Handlungsanweisung in Alltagssprache."""
    return feld(None, Herkunft.MANUELL, Status.FEHLT, hinweis=hinweis)


@dataclass
class InvoiceModel:
    """Container fuer das interne Rechnungsmodell (JSON-serialisierbar)."""

    mapping_version: str = "unbekannt"
    dokumentart: str = "unbekannt"
    quelle: dict[str, Any] = field(default_factory=dict)
    kopf: dict[str, dict] = field(default_factory=dict)
    summen: dict[str, dict] = field(default_factory=dict)
    positionen: list[dict[str, dict]] = field(default_factory=list)
    abweichungen: list[dict] = field(default_factory=list)

    def to_dict(self) -> dict[str, Any]:
        return {
            "schema_version": "1.0.0",
            "mapping_version": self.mapping_version,
            "dokumentart": self.dokumentart,
            "quelle": self.quelle,
            "kopf": self.kopf,
            "summen": self.summen,
            "positionen": self.positionen,
            "abweichungen": self.abweichungen,
        }
