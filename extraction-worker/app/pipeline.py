"""Orchestrierung des Extraktions-Datenpfads.

Regeln (Kern-Arbeitsannahmen):
  * PDF vorhanden  -> PDF fuehrt.
  * PDF + CSV      -> PDF fuehrt, CSV dient der Gegenpruefung (Abweichung = gelb).
  * nur CSV        -> vollstaendig aus CSV.
  * PDF ohne Textebene -> needs_ocr (ocr-worker uebernimmt, Ergebnis prueffpflichtig).

Gutschriften/Stornos werden erkannt und als nicht exportierbar markiert
(Scope "Spaeter").
"""
from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from pathlib import Path

from .csv_path import parse_csv_bytes
from .model import InvoiceModel, Status, fehlend
from .ocr_client import ocr_available, run_ocr
from .pdf_path import PdfResult, extract_pdf

# Felder, die zwischen PDF und CSV verglichen werden (Gegenpruefung).
_CROSSCHECK_KOPF = ["rechnungsnummer", "verkaeufer_ustid", "kaeufer_ustid"]
_CROSSCHECK_SUMMEN = ["nettobetrag", "steuerbetrag", "bruttobetrag"]


@dataclass
class ExtractionOutcome:
    model: InvoiceModel | None
    needs_ocr: bool = False
    raw_text: str = ""


def _to_decimal(v) -> Decimal | None:
    try:
        return Decimal(str(v))
    except Exception:
        return None


def _values_differ(a, b) -> bool:
    da, db = _to_decimal(a), _to_decimal(b)
    if da is not None and db is not None:
        return abs(da - db) > Decimal("0.02")
    return (str(a).strip() if a is not None else None) != (str(b).strip() if b is not None else None)


def crosscheck(lead: InvoiceModel, other: InvoiceModel) -> None:
    """Vergleicht ``other`` (CSV) gegen ``lead`` (PDF); Abweichungen -> gelb + Liste."""
    def compare(block_lead: dict, block_other: dict, feld_name: str, pos_index=None):
        f_lead = block_lead.get(feld_name)
        f_other = block_other.get(feld_name)
        if not f_lead or not f_other:
            return
        if f_lead.get("wert") is None or f_other.get("wert") is None:
            return
        if _values_differ(f_lead["wert"], f_other["wert"]):
            f_lead["status"] = Status.UNSICHER.value
            f_lead["hinweis"] = (
                f"PDF und CSV weichen ab (PDF: {f_lead['wert']}, CSV: {f_other['wert']}) - "
                "bitte pruefen, welcher Wert stimmt."
            )
            lead.abweichungen.append({
                "feld": feld_name, "pdf_wert": f_lead["wert"],
                "csv_wert": f_other["wert"], "position_index": pos_index,
            })

    for name in _CROSSCHECK_KOPF:
        compare(lead.kopf, other.kopf, name)
    for name in _CROSSCHECK_SUMMEN:
        compare(lead.summen, other.summen, name)

    # Positionen anhand Artikelnummer abgleichen (Gesamtpreis).
    other_by_art = {p["artikelnummer"]["wert"]: p for p in other.positionen}
    for i, p in enumerate(lead.positionen):
        art = p.get("artikelnummer", {}).get("wert")
        if art in other_by_art:
            compare(p, other_by_art[art], "gesamtpreis", pos_index=i)


def run_extraction(pdf_path: str | Path | None = None,
                   csv_path: str | Path | None = None) -> ExtractionOutcome:
    """Fuehrt beide Datenpfade zusammen und liefert ein internes Modell."""
    pdf_res: PdfResult | None = None
    if pdf_path:
        pdf_res = extract_pdf(pdf_path)
        if pdf_res.needs_ocr:
            # Textebene fehlt -> OCR (falls verfuegbar); Ergebnisse sind unsicher.
            ocr_model = run_ocr(pdf_path) if ocr_available() else None
            if ocr_model is not None:
                if csv_path:
                    crosscheck(ocr_model, parse_csv_bytes(Path(csv_path).read_bytes(), Path(csv_path).name))
                _flag_gutschrift(ocr_model)
                return ExtractionOutcome(model=ocr_model, needs_ocr=True, raw_text=pdf_res.raw_text)
            # OCR nicht verfuegbar: CSV (falls vorhanden) dient als Grundlage.
            if csv_path:
                model = parse_csv_bytes(Path(csv_path).read_bytes(), Path(csv_path).name)
                _flag_gutschrift(model)
                return ExtractionOutcome(model=model, needs_ocr=True, raw_text=pdf_res.raw_text)
            # OCR nicht moeglich und keine CSV: leeres Geruest -> manuell erfassbar,
            # damit der Pruefbildschirm immer erreichbar ist (kein Haengenbleiben).
            return ExtractionOutcome(model=_empty_skeleton(Path(pdf_path).name, needs_ocr=True),
                                     needs_ocr=True, raw_text=pdf_res.raw_text)

    csv_model: InvoiceModel | None = None
    if csv_path:
        csv_model = parse_csv_bytes(Path(csv_path).read_bytes(), Path(csv_path).name)

    if pdf_res and pdf_res.model:
        lead = pdf_res.model
        lead.quelle["csv_datei"] = Path(csv_path).name if csv_path else None
        if csv_model:
            crosscheck(lead, csv_model)  # PDF fuehrt, CSV = Gegenpruefung
        _flag_gutschrift(lead)
        return ExtractionOutcome(model=lead, raw_text=pdf_res.raw_text if pdf_res else "")

    if csv_model:
        _flag_gutschrift(csv_model)
        return ExtractionOutcome(model=csv_model)

    return ExtractionOutcome(model=_empty_skeleton(None, needs_ocr=False))


def _empty_skeleton(pdf_name: str | None, needs_ocr: bool) -> InvoiceModel:
    """Leeres, manuell befuellbares Rechnungsgeruest (alle Pflichtfelder rot)."""
    model = InvoiceModel(mapping_version="manuell", dokumentart="rechnung")
    model.quelle = {"pdf_datei": pdf_name, "pdf_hat_textebene": False, "ocr_verwendet": needs_ocr}
    kopf_pflicht = {
        "rechnungsnummer": "Rechnungsnummer", "rechnungsdatum": "Rechnungsdatum",
        "waehrung": "Waehrung", "verkaeufer_name": "Verkaeufer", "kaeufer_name": "Kaeufer",
        "leitweg_id": "Leitweg-ID",
    }
    for feld_name, label in kopf_pflicht.items():
        model.kopf[feld_name] = fehlend(f"{label} konnte nicht erkannt werden - bitte ergaenzen.")
    for feld_name in ("nettobetrag", "steuerbetrag", "bruttobetrag"):
        model.summen[feld_name] = fehlend("Betrag konnte nicht erkannt werden - bitte ergaenzen.")
    return model


def _flag_gutschrift(model: InvoiceModel) -> None:
    """Gutschrift/Storno sind 'Spaeter' -> markieren, damit Export gesperrt bleibt."""
    if model.dokumentart in ("gutschrift", "storno"):
        model.kopf.setdefault("_hinweis_dokumentart", {})
        model.kopf["_hinweis_dokumentart"] = {
            "wert": model.dokumentart, "herkunft": "berechnet", "status": Status.FEHLT.value,
            "hinweis": "Gutschriften/Stornos werden derzeit nicht als XRechnung exportiert.",
        }
