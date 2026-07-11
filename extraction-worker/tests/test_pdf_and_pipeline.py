"""Tests PDF-Datenpfad und Orchestrierung gegen die Abas-Muster-PDFs."""
from pathlib import Path

import pytest

from app.pdf_path import extract_pdf, extract_pdf_text
from app.pipeline import run_extraction

PDF = Path(__file__).resolve().parents[2] / "aufgabe" / "Musterrechnung PDF"
CSV = Path(__file__).resolve().parents[2] / "aufgabe" / "Musterrechnung CSV"

TEXT_PDF = PDF / "XDOC18.1782975457883.MASTER.pdf"   # 400009, mit Textebene
SCAN_PDF = PDF / "400014.pdf"                          # Scan ohne Textebene


def test_pdf_textebene_400009():
    r = extract_pdf(TEXT_PDF)
    assert r.needs_ocr is False
    m = r.model.to_dict()
    assert m["kopf"]["rechnungsnummer"]["wert"] == "400009"
    assert m["kopf"]["rechnungsdatum"]["wert"] == "2026-07-02"
    assert m["kopf"]["faelligkeit"]["wert"] == "2026-07-09"
    pos = m["positionen"][0]
    assert pos["artikelnummer"]["wert"] == "100003"
    assert pos["menge"]["wert"] == "1000.00"
    assert pos["einzelpreis"]["wert"] == "99.21"
    assert pos["gesamtpreis"]["wert"] == "99210.00"
    assert m["summen"]["bruttobetrag"]["wert"] == "118059.90"
    assert m["summen"]["steuersatz"]["wert"] == "19.00"
    # Kaeufer-Adressblock (fuer BR-07: Buyer name BT-44).
    assert m["kopf"]["kaeufer_name"]["wert"] == "Kleine Automobile AG"
    assert m["kopf"]["kaeufer_plz"]["wert"] == "12335"
    assert m["kopf"]["kaeufer_ort"]["wert"] == "Berlin"


def test_scan_ohne_textebene_braucht_ocr():
    r = extract_pdf(SCAN_PDF)
    assert r.needs_ocr is True
    assert r.model is None


def test_pipeline_csv_massgeblich_pdf_fuellt_luecken():
    r = run_extraction(pdf_path=TEXT_PDF, csv_path=CSV / "400009.csv")
    m = r.model.to_dict()
    # PDF und CSV stimmen ueberein -> keine gelben Abweichungen.
    assert m["abweichungen"] == []
    assert r.needs_ocr is False
    # CSV ist massgeblich: strukturierte Felder stammen aus der CSV ...
    assert m["kopf"]["rechnungsnummer"]["herkunft"] == "csv"
    assert m["positionen"][0]["gesamtpreis"]["herkunft"] == "csv"
    # ... waehrend Luecken der CSV aus der PDF-Textebene gefuellt werden.
    assert m["kopf"]["rechnungsdatum"]["herkunft"] == "pdf"
    assert m["kopf"]["rechnungsdatum"]["wert"] == "2026-07-02"
    assert m["positionen"][0]["menge"]["herkunft"] == "pdf"
    assert m["positionen"][0]["menge"]["wert"] == "1000.00"


def test_crosscheck_markiert_csv_feld_bei_abweichung():
    # Kuenstliche Divergenz: Bruttobetrag im PDF-Text manipuliert; die CSV
    # bleibt massgeblich, das (abweichende) CSV-Feld wird gelb markiert.
    from app.csv_path import parse_csv_file
    from app.pipeline import crosscheck
    pdf_model = extract_pdf(TEXT_PDF).model
    csv_model = parse_csv_file(CSV / "400009.csv")
    pdf_model.summen["bruttobetrag"]["wert"] = "999999.00"
    crosscheck(csv_model, pdf_model)
    assert csv_model.summen["bruttobetrag"]["status"] == "unsicher"
    assert any(a["feld"] == "bruttobetrag" for a in csv_model.abweichungen)


def test_scan_mit_csv_ocr_fuellt_luecken_csv_hat_vorrang(monkeypatch):
    # Scan + CSV: OCR liefert die der CSV fehlenden Felder (Menge ...), die CSV
    # behaelt aber Vorrang - fehlerhafte OCR-Werte ueberschreiben sie nicht, und
    # es gibt KEINE Gegenpruefung gegen OCR.
    import app.pipeline as pipeline
    from app.pdf_path import extract_pdf_text
    from app.ocr_client import _tag_ocr

    def fake_ocr(_path):
        text = ("Rechnung Nr. 400014\nvom 05.07.2026\nUnsere USt-IdNr DEO0001\n"
                "100005 3,00 Stück 6.545,00 19,00 19.635,00\nSpezialteil\n"
                "Nettosumme 19.635,00\n")
        m = extract_pdf_text(text, "400014.pdf")
        m.quelle = {"pdf_datei": "400014.pdf", "pdf_hat_textebene": False, "ocr_verwendet": True}
        _tag_ocr(m)
        return m

    monkeypatch.setattr(pipeline, "ocr_available", lambda: True)
    monkeypatch.setattr(pipeline, "run_ocr", fake_ocr)

    r = run_extraction(pdf_path=SCAN_PDF, csv_path=CSV / "400014.csv")
    m = r.model.to_dict()
    assert r.needs_ocr is True
    assert m["quelle"]["ocr_verwendet"] is True
    # CSV hat Vorrang: verkaeufer_ustid bleibt der CSV-Wert (nicht OCR "DEO0001").
    assert m["kopf"]["verkaeufer_ustid"]["herkunft"] == "csv"
    assert m["kopf"]["verkaeufer_ustid"]["wert"] != "DEO0001"
    # Keine Gegenpruefung gegen OCR -> keine Abweichungen.
    assert m["abweichungen"] == []
    # OCR fuellt die CSV-Luecke Menge als unsicher/pflichtig.
    pos = m["positionen"][0]
    assert pos["menge"]["herkunft"] == "ocr"
    assert pos["menge"]["status"] == "unsicher"
    # CSV-eigener Wert bleibt (Gesamtpreis aus CSV).
    assert pos["gesamtpreis"]["herkunft"] == "csv"


if __name__ == "__main__":
    raise SystemExit(pytest.main([__file__, "-v"]))
