"""PDF-Datenpfad (vorrangig): Abas-PDF-Textebene -> internes Rechnungsmodell.

Zuerst wird die Textebene gelesen (pdfplumber). Fehlt sie oder ist sie
unbrauchbar (Scan/Bild), meldet ``extract_pdf`` ``needs_ocr=True``; die
Orchestrierung uebergibt dann an den ocr-worker. Das Layout der Abas-Ausgangs-
rechnungen ist gleichfoermig genug fuer eine regelbasierte Erkennung
(vgl. invoice2data-Templates).
"""
from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

from .model import Herkunft, InvoiceModel, Status, fehlend, feld
from .normalize import format_decimal, parse_german_date, parse_german_decimal

# Menge + Einheit + Einzelpreis + optional % + Gesamtpreis in einer Positionszeile.
_POS_RE = re.compile(
    r"^(?P<artikel>\d{4,})\s+"
    r"(?P<menge>[\d.]+(?:,\d+)?)\s+(?P<einheit>\S+)\s+"
    r"(?P<einzel>[\d.]+,\d{2})\s+"
    r"(?:(?P<satz>\d{1,2},\d{2})\s+)?"
    r"(?P<gesamt>[\d.]+,\d{2})\s*$"
)


@dataclass
class PdfResult:
    model: InvoiceModel | None
    needs_ocr: bool
    raw_text: str


def _search(pattern: str, text: str, flags=0) -> str | None:
    m = re.search(pattern, text, flags)
    return m.group(1).strip() if m else None


def extract_pdf_text(raw_text: str, quelle_name: str) -> InvoiceModel:
    """Baut das interne Modell aus bereits extrahiertem PDF-Text (testbar ohne pdfplumber)."""
    model = InvoiceModel(mapping_version="abas-pdf-v1", dokumentart="rechnung")
    model.quelle = {"pdf_datei": quelle_name, "pdf_hat_textebene": True}
    k = model.kopf

    nummer = _search(r"Rechnung\s+Nr\.?\s*(\d+)", raw_text)
    k["rechnungsnummer"] = feld(nummer, Herkunft.PDF, Status.SICHER if nummer else Status.FEHLT,
                                hinweis=None if nummer else "Rechnungsnummer nicht erkannt - bitte ergaenzen.")

    datum = parse_german_date(_search(r"vom\s+(\d{2}\.\d{2}\.\d{4})", raw_text))
    k["rechnungsdatum"] = feld(datum, Herkunft.PDF, Status.SICHER if datum else Status.FEHLT,
                               hinweis=None if datum else "Rechnungsdatum nicht erkannt - bitte ergaenzen.")

    faellig = parse_german_date(_search(r"F[aä]lligkeit\s+(\d{2}\.\d{2}\.\d{4})", raw_text))
    k["faelligkeit"] = feld(faellig, Herkunft.PDF, Status.SICHER if faellig else Status.FEHLT,
                            hinweis=None if faellig else "Faelligkeitsdatum nicht erkannt - bitte ergaenzen.")

    k["kaeufer_kundennummer"] = feld(_search(r"Kunde\s+(\d+)", raw_text), Herkunft.PDF, Status.SICHER)
    seller = _search(r"Unsere\s+USt-IdNr\s+(\S+)", raw_text)
    k["verkaeufer_ustid"] = feld(seller, Herkunft.PDF, Status.SICHER if seller else Status.FEHLT)
    buyer = _search(r"Ihre\s+USt-IdNr\s+(\S+)", raw_text)
    k["kaeufer_ustid"] = feld(buyer, Herkunft.PDF, Status.SICHER if buyer else Status.FEHLT)

    # Waehrung: Abas-Rechnungen in EUR (Spaltenkopf "Einzelpreis EUR").
    waehrung = "EUR" if re.search(r"\bEUR\b", raw_text) else None
    k["waehrung"] = feld(waehrung or "EUR", Herkunft.PDF, Status.SICHER)

    _extract_kaeufer(model, raw_text)
    _extract_positions(model, raw_text)
    _extract_summen(model, raw_text)
    _backfill_positions_steuersatz(model)
    return model


def _backfill_positions_steuersatz(model: InvoiceModel) -> None:
    """Fehlt der Steuersatz je Position, den (einzigen) Dokument-Steuersatz
    uebernehmen. Bei genau einem Dokumentsatz ist die Ableitung eindeutig und
    wird von der Fachpruefung (Netto x Satz = Steuer) abgesichert -> sicher."""
    doc_satz = model.summen.get("steuersatz", {}).get("wert")
    if not doc_satz:
        return
    for pos in model.positionen:
        s = pos.get("steuersatz")
        if not s or s.get("wert") in (None, ""):
            pos["steuersatz"] = feld(doc_satz, Herkunft.BERECHNET, Status.SICHER)


# Absenderzeile im Adressfenster (klein gesetzt, mit "*"-Trennern), z.B.
# "Senapsa Test * Teststraße * 65868 Testhausen".
_ABSENDER_RE = re.compile(r".+\*.+\*.+\d{5}.+")
_PLZ_ORT_RE = re.compile(r"^(\d{5})\s+(.+)$")


def _extract_kaeufer(model: InvoiceModel, text: str) -> None:
    """Kaeufer-Adressblock: die Zeilen zwischen Absenderzeile und 'Rechnung Nr.'."""
    lines = [ln.strip() for ln in text.splitlines()]
    start = next((i for i, ln in enumerate(lines) if _ABSENDER_RE.match(ln)), None)
    if start is None:
        return
    block: list[str] = []
    for ln in lines[start + 1:]:
        if not ln:
            if block:
                break
            continue
        if ln.startswith("Rechnung"):
            break
        block.append(ln)
        if len(block) >= 4:
            break
    if not block:
        return

    k = model.kopf
    k["kaeufer_name"] = feld(block[0], Herkunft.PDF, Status.SICHER)
    if len(block) >= 2:
        k["kaeufer_strasse"] = feld(block[1], Herkunft.PDF, Status.SICHER)
    plz_ort = next((_PLZ_ORT_RE.match(b) for b in block[2:] if _PLZ_ORT_RE.match(b)), None)
    if plz_ort:
        k["kaeufer_plz"] = feld(plz_ort.group(1), Herkunft.PDF, Status.SICHER)
        k["kaeufer_ort"] = feld(plz_ort.group(2).strip(), Herkunft.PDF, Status.SICHER)


def _extract_positions(model: InvoiceModel, text: str) -> None:
    lines = [ln.strip() for ln in text.splitlines()]
    for i, line in enumerate(lines):
        mm = _POS_RE.match(line)
        if not mm:
            continue
        # Beschreibung steht i.d.R. in der Folgezeile (vor "Kundenartikelnummer").
        beschreibung = None
        if i + 1 < len(lines) and lines[i + 1] and not lines[i + 1].startswith("Kundenartikel"):
            beschreibung = lines[i + 1]
        gesamt = parse_german_decimal(mm.group("gesamt"))
        einzel = parse_german_decimal(mm.group("einzel"))
        menge = parse_german_decimal(mm.group("menge"))
        idx = len(model.positionen) + 1
        model.positionen.append({
            "position": feld(idx, Herkunft.PDF, Status.SICHER),
            "artikelnummer": feld(mm.group("artikel"), Herkunft.PDF, Status.SICHER),
            "beschreibung": feld(beschreibung, Herkunft.PDF,
                                 Status.SICHER if beschreibung else Status.FEHLT),
            "menge": feld(format_decimal(menge), Herkunft.PDF, Status.SICHER),
            "einheit": feld(mm.group("einheit"), Herkunft.PDF, Status.SICHER),
            "einzelpreis": feld(format_decimal(einzel), Herkunft.PDF, Status.SICHER),
            "gesamtpreis": feld(format_decimal(gesamt), Herkunft.PDF, Status.SICHER),
            "steuersatz": feld(mm.group("satz").replace(",", ".") if mm.group("satz") else None,
                               Herkunft.PDF, Status.SICHER if mm.group("satz") else Status.UNSICHER),
        })


def _extract_summen(model: InvoiceModel, text: str) -> None:
    netto = parse_german_decimal(_search(r"Nettosumme\s+([\d.]+,\d{2})", text))
    m_steuer = re.search(r"Steuer\s+[\d.]+,\d{2}\s+(\d{1,2},\d{2})\s+([\d.]+,\d{2})", text)
    satz = m_steuer.group(1).replace(",", ".") if m_steuer else None
    steuer = parse_german_decimal(m_steuer.group(2)) if m_steuer else None
    brutto = parse_german_decimal(_search(r"Summe\s+([\d.]+,\d{2})", text))

    s = model.summen
    s["nettobetrag"] = feld(format_decimal(netto), Herkunft.PDF, Status.SICHER) if netto is not None \
        else fehlend("Nettosumme nicht erkannt - bitte ergaenzen.")
    s["steuerbetrag"] = feld(format_decimal(steuer), Herkunft.PDF, Status.SICHER) if steuer is not None \
        else fehlend("Steuerbetrag nicht erkannt - bitte ergaenzen.")
    s["bruttobetrag"] = feld(format_decimal(brutto), Herkunft.PDF, Status.SICHER) if brutto is not None \
        else fehlend("Bruttosumme nicht erkannt - bitte ergaenzen.")
    if satz:
        s["steuersatz"] = feld(satz, Herkunft.PDF, Status.SICHER)


def extract_pdf(path: str | Path, min_chars: int = 40) -> PdfResult:
    """Liest die PDF-Textebene via pdfplumber; leer/zu kurz -> needs_ocr."""
    p = Path(path)
    try:
        import pdfplumber
    except ImportError as exc:  # pragma: no cover - nur ausserhalb des Containers
        raise RuntimeError("pdfplumber ist nicht installiert (nur im Container verfuegbar).") from exc

    text_parts: list[str] = []
    with pdfplumber.open(str(p)) as pdf:
        for page in pdf.pages:
            text_parts.append(page.extract_text() or "")
    raw_text = "\n".join(text_parts)

    if len(raw_text.strip()) < min_chars:
        # Keine brauchbare Textebene -> Scan/Bild: OCR uebernimmt (prueffpflichtig).
        return PdfResult(model=None, needs_ocr=True, raw_text=raw_text)

    return PdfResult(model=extract_pdf_text(raw_text, p.name), needs_ocr=False, raw_text=raw_text)
