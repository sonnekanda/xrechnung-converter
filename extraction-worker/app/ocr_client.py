"""Optionaler Aufruf des ocr-worker fuer gescannte PDFs ohne Textebene.

Nur aktiv, wenn ``OCR_URL`` gesetzt ist. OCR-Ergebnisse werden im Modell
grundsaetzlich als ``ocr``/``unsicher`` markiert (prueffpflichtig).
"""
from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

from .model import Herkunft, InvoiceModel, Status
from .pdf_path import extract_pdf_text


@dataclass
class OcrResult:
    """OCR-Ergebnis: das (unsichere) Modell und der rohe OCR-Text. Der Text wird
    zusaetzlich fuer den optionalen KI-Fallback gebraucht (Felder, die die
    regelbasierte OCR-Auswertung nicht erkannt hat)."""
    model: InvoiceModel
    text: str


def ocr_available() -> bool:
    return bool(os.getenv("OCR_URL"))


def run_ocr(pdf_path: str | Path) -> OcrResult | None:
    """Sendet das PDF an den ocr-worker und baut ein OCR-Modell (alles unsicher)."""
    url = os.getenv("OCR_URL")
    if not url:
        return None
    import requests  # nur im Container benoetigt

    p = Path(pdf_path)
    with p.open("rb") as fh:
        resp = requests.post(f"{url}/ocr", files={"pdf": (p.name, fh, "application/pdf")}, timeout=360)
    if resp.status_code != 200:
        return None
    text = resp.json().get("text", "")
    if not text.strip():
        return None

    model = extract_pdf_text(text, p.name)
    model.quelle = {"pdf_datei": p.name, "pdf_hat_textebene": False, "ocr_verwendet": True}
    _tag_ocr(model)
    return OcrResult(model=model, text=text)


def _tag_ocr(model: InvoiceModel) -> None:
    """Alle erkannten Felder auf herkunft=ocr, status=unsicher setzen."""
    def retag(field: dict) -> None:
        if field.get("wert") is not None:
            field["herkunft"] = Herkunft.OCR.value
            field["status"] = Status.UNSICHER.value
            field.setdefault("hinweis", "Aus Texterkennung (OCR) - bitte bestaetigen.")

    for block in (model.kopf, model.summen):
        for f in block.values():
            retag(f)
    for pos in model.positionen:
        for f in pos.values():
            retag(f)
