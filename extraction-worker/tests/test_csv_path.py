"""Tests des CSV-Datenpfads gegen die anonymisierten Abas-Musterexporte.

Prueft insbesondere die Summen-Pseudozeilen (Artikel 9/6/1), Kodierungs- und
Header-Erkennung sowie die Gutschrift-Klassifikation.
"""
from pathlib import Path

import pytest

from app.csv_path import parse_csv_file

SAMPLES = Path(__file__).resolve().parents[2] / "aufgabe" / "Musterrechnung CSV"


def _model(name: str) -> dict:
    return parse_csv_file(SAMPLES / name).to_dict()


def test_400009_summen_aus_pseudozeilen():
    m = _model("400009.csv")
    assert m["kopf"]["rechnungsnummer"]["wert"] == "400009"
    assert m["dokumentart"] == "rechnung"
    # Summen stammen aus den Pseudozeilen 9/6/1 - verifiziert gegen das PDF.
    assert m["summen"]["nettobetrag"]["wert"] == "99210.00"
    assert m["summen"]["steuerbetrag"]["wert"] == "18849.90"
    assert m["summen"]["bruttobetrag"]["wert"] == "118059.90"
    # Genau eine echte Position (100003), Summenzeilen sind KEINE Positionen.
    arts = [p["artikelnummer"]["wert"] for p in m["positionen"]]
    assert arts == ["100003"]
    assert m["summen"]["steuersatz"]["wert"] == "19"


def test_400010_ohne_kopfzeile():
    # 400010 hat keine Header-Zeile -> positionsbasiertes Mapping muss greifen.
    m = _model("400010.csv")
    assert m["kopf"]["rechnungsnummer"]["wert"] == "400010"
    assert m["quelle"]["csv_encoding"].startswith("utf-8")
    assert m["summen"]["bruttobetrag"]["wert"] == "58976.40"


def test_400013_gutschrift_erkannt():
    m = _model("400013.csv")
    assert m["dokumentart"] == "gutschrift"
    assert m["summen"]["bruttobetrag"]["wert"] == "-58976.40"


def test_400014_cp1252_und_zwei_positionen():
    m = _model("400014.csv")
    assert m["quelle"]["csv_encoding"] == "cp1252"
    arts = [p["artikelnummer"]["wert"] for p in m["positionen"]]
    assert arts == ["100005", "100004"]
    assert m["summen"]["nettobetrag"]["wert"] == "69195.00"
    assert m["summen"]["bruttobetrag"]["wert"] == "82342.05"


def test_csv_positionen_ohne_menge_sind_rot():
    # Der Abas-CSV-Export liefert je Position keine Menge/Beschreibung -> fehlt.
    m = _model("400009.csv")
    pos = m["positionen"][0]
    assert pos["menge"]["status"] == "fehlt"
    assert pos["beschreibung"]["status"] == "fehlt"
    assert pos["gesamtpreis"]["status"] == "sicher"


if __name__ == "__main__":
    raise SystemExit(pytest.main([__file__, "-v"]))
