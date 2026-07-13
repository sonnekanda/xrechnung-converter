"""Orchestrierung des Extraktions-Datenpfads.

Regeln (Kern-Arbeitsannahmen):
  * CSV vorhanden -> CSV ist massgeblich (strukturierte Abas-Daten). Da die CSV
                     nicht alle Felder liefert (z.B. Menge/Einzelpreis/
                     Beschreibung je Position, Rechnungsdatum, Adresse), fuellt
                     die PDF-Textebene bzw. - bei Scans - OCR diese Luecken auf.
                     Die CSV hat Vorrang: vorhandene CSV-Werte werden nie
                     ueberschrieben. Gegenpruefung nur gegen die zuverlaessige
                     PDF-Textebene (Abweichung = gelb); gegen OCR wird NICHT
                     gegengeprueft, da fehleranfaellig (z.B. "DEO0001" statt
                     "DE00001"). OCR-Werte bleiben unsicher/pflicht.
  * nur PDF mit Textebene  -> PDF fuehrt.
  * nur PDF ohne Textebene -> needs_ocr (ocr-worker, Ergebnis prueffpflichtig).

Gutschriften/Stornos werden erkannt und als nicht exportierbar markiert
(Scope "Spaeter").
"""
from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from pathlib import Path

from .csv_path import parse_csv_bytes
from .ki_client import enrich_with_ki
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


# Felder, die die CSV aus dem Abas-Export nicht liefert, aber eine PDF-
# Textebene enthaelt -> zum Auffuellen der CSV-Luecken (keine Gegenpruefung).
_MERGE_KOPF = ["rechnungsdatum", "faelligkeit", "leistungszeitraum",
               "kaeufer_strasse", "kaeufer_plz", "kaeufer_ort", "kaeufer_ustid"]
_MERGE_POS = ["beschreibung", "menge", "einheit", "einzelpreis", "steuersatz"]


def _is_empty(f: dict | None) -> bool:
    """Feld gilt als leer/zu ergaenzen, wenn ohne Wert oder als 'fehlt' markiert."""
    return (not f) or f.get("wert") in (None, "") or f.get("status") == Status.FEHLT.value


def crosscheck(csv_model: InvoiceModel, pdf_model: InvoiceModel) -> None:
    """Gegenpruefung gemeinsamer Felder von CSV und PDF. Da die CSV massgeblich
    ist, wird bei Abweichung das CSV-Feld gelb markiert (der Nutzer entscheidet).
    Faengt u.a. falsch gepaarte PDF/CSV-Dateien ab."""
    def compare(csv_block: dict, pdf_block: dict, feld_name: str, pos_index=None):
        fc = csv_block.get(feld_name)
        fp = pdf_block.get(feld_name)
        if not fc or not fp:
            return
        if fc.get("wert") is None or fp.get("wert") is None:
            return
        if _values_differ(fc["wert"], fp["wert"]):
            fc["status"] = Status.UNSICHER.value
            fc["hinweis"] = (
                f"PDF und CSV weichen ab (CSV: {fc['wert']}, PDF: {fp['wert']}) - "
                "bitte pruefen, welcher Wert stimmt."
            )
            csv_model.abweichungen.append({
                "feld": feld_name, "pdf_wert": fp["wert"],
                "csv_wert": fc["wert"], "position_index": pos_index,
            })

    for name in _CROSSCHECK_KOPF:
        compare(csv_model.kopf, pdf_model.kopf, name)
    for name in _CROSSCHECK_SUMMEN:
        compare(csv_model.summen, pdf_model.summen, name)

    # Positionen anhand Artikelnummer abgleichen (Gesamtpreis).
    pdf_by_art = {p["artikelnummer"]["wert"]: p for p in pdf_model.positionen
                  if p.get("artikelnummer", {}).get("wert")}
    for i, p in enumerate(csv_model.positionen):
        art = p.get("artikelnummer", {}).get("wert")
        if art in pdf_by_art:
            compare(p, pdf_by_art[art], "gesamtpreis", pos_index=i)


def merge_fill(base: InvoiceModel, extra: InvoiceModel, *, reliable: bool = True) -> None:
    """Fuellt leere/fehlende Felder in ``base`` (CSV) aus ``extra`` (PDF-Textebene
    oder OCR). Vorhandene, gesicherte CSV-Werte bleiben unangetastet - die CSV
    bleibt massgeblich, ``extra`` liefert nur, was die CSV nicht enthaelt.

    ``reliable`` unterscheidet die Quelle: Bei einer PDF-Textebene (True) darf der
    explizit ausgewiesene Steuersatz den nur abgeleiteten CSV-Satz ersetzen; bei
    OCR (False) werden ausschliesslich echte Luecken gefuellt - unsichere OCR-
    Werte (herkunft=ocr) bleiben pruefpflichtig und ueberschreiben nichts, was die
    CSV bereits verlaesslich liefert."""
    for name in _MERGE_KOPF:
        if _is_empty(base.kopf.get(name)) and not _is_empty(extra.kopf.get(name)):
            base.kopf[name] = dict(extra.kopf[name])

    # Dokument-Steuersatz: aus CSV nur abgeleitet (unsicher) - der explizit in
    # einer PDF-Textebene ausgewiesene Satz ist verlaesslicher (OCR nicht).
    if reliable:
        csv_satz = base.summen.get("steuersatz")
        if (_is_empty(csv_satz) or (csv_satz and csv_satz.get("status") == Status.UNSICHER.value)) \
                and not _is_empty(extra.summen.get("steuersatz")):
            base.summen["steuersatz"] = dict(extra.summen["steuersatz"])

    # Positionen nach Artikelnummer zusammenfuehren.
    extra_by_art = {p["artikelnummer"]["wert"]: p for p in extra.positionen
                    if p.get("artikelnummer", {}).get("wert")}
    for pos in base.positionen:
        src = extra_by_art.get(pos.get("artikelnummer", {}).get("wert"))
        if not src:
            continue
        for name in _MERGE_POS:
            if _is_empty(pos.get(name)) and not _is_empty(src.get(name)):
                pos[name] = dict(src[name])


def run_extraction(pdf_path: str | Path | None = None,
                   csv_path: str | Path | None = None,
                   ki_config: dict | None = None) -> ExtractionOutcome:
    """Fuehrt beide Datenpfade zusammen und liefert ein internes Modell.

    ``ki_config`` sind die von der webapp uebergebenen KI-Einstellungen (DB);
    fehlt es, greift die .env des Workers (HTTP-Pfad)."""
    csv_model: InvoiceModel | None = None
    if csv_path:
        csv_model = parse_csv_bytes(Path(csv_path).read_bytes(), Path(csv_path).name)

    # PDF-Textebene lesen, sofern vorhanden. Wichtig: liegt eine CSV vor, wird
    # NICHT ge-OCR-t - die CSV liefert die verlaesslichen Daten.
    pdf_res: PdfResult | None = extract_pdf(pdf_path) if pdf_path else None

    # --- Fall 1: CSV vorhanden -> CSV ist massgeblich, PDF/OCR fuellt Luecken. -
    if csv_model is not None:
        csv_model.quelle["pdf_datei"] = Path(pdf_path).name if pdf_path else None
        used_ocr = False
        if pdf_res and pdf_res.model:
            # Zuverlaessige Textebene: gemeinsame Felder gegenpruefen (Abweichung
            # = gelb) und danach die CSV-Luecken aus dem PDF auffuellen.
            crosscheck(csv_model, pdf_res.model)
            merge_fill(csv_model, pdf_res.model, reliable=True)
        elif pdf_res and pdf_res.needs_ocr and ocr_available():
            # Scan: OCR liefert nur die Felder, die der CSV fehlen (Menge,
            # Einzelpreis, Beschreibung ...). KEINE Gegenpruefung gegen OCR - das
            # ist zu fehleranfaellig und wuerde die massgebliche CSV faelschlich
            # gelb markieren. OCR-Werte bleiben herkunft=ocr/unsicher (pflicht).
            ocr_model = run_ocr(pdf_path)
            if ocr_model is not None:
                merge_fill(csv_model, ocr_model, reliable=False)
                used_ocr = True
        csv_model.quelle["ocr_verwendet"] = used_ocr
        # KI-Fallback: nur echte Luecken (status=fehlt), aus der PDF-Textebene.
        # Ergaenzt herkunft=ki/unsicher -> immer pruefpflichtig, nie Auto-Export.
        enrich_with_ki(csv_model, pdf_res.raw_text if pdf_res else "", ki_config)
        _flag_gutschrift(csv_model)
        return ExtractionOutcome(model=csv_model, needs_ocr=used_ocr,
                                 raw_text=pdf_res.raw_text if pdf_res else "")

    # --- Fall 2: nur PDF -----------------------------------------------------
    if pdf_res:
        if pdf_res.needs_ocr:
            # Keine Textebene und keine CSV -> OCR (falls verfuegbar), prueffpflichtig.
            ocr_model = run_ocr(pdf_path) if ocr_available() else None
            if ocr_model is not None:
                _flag_gutschrift(ocr_model)
                return ExtractionOutcome(model=ocr_model, needs_ocr=True, raw_text=pdf_res.raw_text)
            # OCR nicht moeglich: leeres Geruest -> manuell erfassbar, damit der
            # Pruefbildschirm immer erreichbar ist (kein Haengenbleiben).
            return ExtractionOutcome(model=_empty_skeleton(Path(pdf_path).name, needs_ocr=True),
                                     needs_ocr=True, raw_text=pdf_res.raw_text)
        lead = pdf_res.model
        enrich_with_ki(lead, pdf_res.raw_text, ki_config)
        _flag_gutschrift(lead)
        return ExtractionOutcome(model=lead, raw_text=pdf_res.raw_text)

    # --- Fall 3: weder CSV noch PDF ------------------------------------------
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
