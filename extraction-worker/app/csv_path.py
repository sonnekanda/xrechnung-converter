"""CSV-Datenpfad: Abas-CSV -> internes Rechnungsmodell (herkunft = csv).

Vollwertiger zweiter Datenpfad. Liegt nur eine CSV vor, wird die Rechnung
vollstaendig hieraus verarbeitet; Felder, die der Abas-Export nicht enthaelt
(z.B. Menge/Beschreibung je Position, Rechnungsdatum, Leitweg-ID), werden als
``fehlt``/``unsicher`` markiert und spaeter aus Stammdaten oder manuell ergaenzt.
"""
from __future__ import annotations

import csv
import io
from decimal import Decimal
from pathlib import Path

from . import csv_mapping
from .csv_mapping import abas_v1 as m
from .model import Herkunft, InvoiceModel, Status, fehlend, feld
from .normalize import format_decimal, parse_german_decimal


def _decode(raw: bytes) -> tuple[str, str]:
    """Kodierung robust bestimmen: UTF-8 bevorzugt, sonst CP1252 (Abas-Altdaten)."""
    for enc in ("utf-8-sig", "utf-8"):
        try:
            return raw.decode(enc), enc
        except UnicodeDecodeError:
            continue
    return raw.decode("cp1252", errors="replace"), "cp1252"


def _split_name_street(adresse: str | None) -> tuple[str | None, str | None]:
    if not adresse:
        return None, None
    parts = [p.strip() for p in adresse.split(";")]
    name = parts[0] if parts else None
    street = parts[1] if len(parts) > 1 else None
    return (name or None), (street or None)


def parse_csv_bytes(raw: bytes, quelle_name: str, version: str = csv_mapping.DEFAULT_VERSION) -> InvoiceModel:
    text, encoding = _decode(raw)
    mapping = csv_mapping.get_mapping(version)
    model = InvoiceModel(mapping_version=mapping.VERSION)
    model.quelle = {"csv_datei": quelle_name, "csv_encoding": encoding}

    rows = list(csv.reader(io.StringIO(text), delimiter=";"))
    if rows and rows[0] and rows[0][0].strip() == mapping.HEADER_FIRST_CELL:
        rows = rows[1:]  # Kopfzeile vorhanden (fehlt z.B. in 400010) -> ueberspringen
    data = [r for r in rows if len(r) >= mapping.MIN_COLUMNS]
    if not data:
        model.summen["_fehler"] = fehlend(
            "Die CSV-Datei enthaelt keine lesbaren Rechnungszeilen. Bitte Datei pruefen."
        )
        return model

    first = data[0]
    rechnungsart = first[mapping.RECHNUNGSART]
    model.dokumentart = mapping.dokumentart_from_rechnungsart(rechnungsart)

    _build_kopf(model, first, mapping)
    positions, sums = _split_rows(data, mapping)
    _build_positions(model, positions, mapping)
    _build_summen(model, first, sums, positions, mapping)
    return model


def parse_csv_file(path: str | Path, version: str = csv_mapping.DEFAULT_VERSION) -> InvoiceModel:
    p = Path(path)
    return parse_csv_bytes(p.read_bytes(), p.name, version)


# --- Bausteine ---------------------------------------------------------------

def _build_kopf(model: InvoiceModel, first: list[str], mapping) -> None:
    name, street = _split_name_street(first[mapping.ADRESSE_KUNDE])
    k = model.kopf
    k["rechnungsnummer"] = feld(first[mapping.IDENTNUMMER].strip() or None,
                                Herkunft.CSV, Status.SICHER)
    k["rechnungsart"] = feld(first[mapping.RECHNUNGSART].strip() or None,
                             Herkunft.CSV, Status.SICHER)
    k["waehrung"] = feld(first[mapping.WAEHRUNG].strip() or None, Herkunft.CSV, Status.SICHER)
    k["verkaeufer_ustid"] = feld(first[mapping.SELLER_USTID].strip() or None,
                                 Herkunft.CSV, Status.SICHER)
    k["kaeufer_name"] = feld((first[mapping.NAME_KUNDE].strip() or name),
                             Herkunft.CSV, Status.SICHER)
    k["kaeufer_strasse"] = feld(street, Herkunft.CSV,
                                Status.SICHER if street else Status.FEHLT)
    k["kaeufer_plz"] = feld(first[mapping.PLZ_KUNDE].strip() or None,
                            Herkunft.CSV, Status.SICHER if first[mapping.PLZ_KUNDE].strip() else Status.FEHLT)
    k["kaeufer_kundennummer"] = feld(first[mapping.KUNDENNUMMER].strip() or None,
                                     Herkunft.CSV, Status.SICHER)
    buyer_ustid = first[mapping.BUYER_USTID].strip()
    k["kaeufer_ustid"] = feld(buyer_ustid or None, Herkunft.CSV,
                              Status.SICHER if buyer_ustid else Status.FEHLT)

    # Felder, die der Abas-CSV-Export nicht liefert -> Pflichtfelder rot bzw.
    # spaeter aus Stammdaten (Autofill) ergaenzt.
    k["rechnungsdatum"] = fehlend("Rechnungsdatum steht nicht in der CSV - bitte ergaenzen.")
    k["leistungszeitraum"] = fehlend("Leistungszeitraum fehlt - bitte ergaenzen.")
    k["faelligkeit"] = fehlend("Faelligkeitsdatum fehlt - bitte ergaenzen.")
    k["leitweg_id"] = fehlend("Leitweg-ID fehlt - wird aus den Stammdaten des Empfaengers uebernommen oder bitte ergaenzen.")
    k["zahlungsbedingungen"] = fehlend("Zahlungsbedingungen fehlen - bitte ergaenzen.")
    k["bankverbindung"] = fehlend("Bankverbindung fehlt - wird aus den Stammdaten uebernommen oder bitte ergaenzen.")


def _split_rows(data: list[list[str]], mapping) -> tuple[list[list[str]], dict[str, Decimal]]:
    """Trennt echte Positionen von Summen-Pseudozeilen (Artikel 9/6/1)."""
    positions: list[list[str]] = []
    sums: dict[str, Decimal] = {}
    for row in data:
        art = row[mapping.ARTIKEL].strip()
        if mapping.is_summary_code(art):
            wert = parse_german_decimal(row[mapping.POSITIONSWERT])
            if wert is not None:
                sums[mapping.SUMMARY_CODES[art]] = wert
        else:
            positions.append(row)
    return positions, sums


def _build_positions(model: InvoiceModel, positions: list[list[str]], mapping) -> None:
    for idx, row in enumerate(positions, start=1):
        gesamt = parse_german_decimal(row[mapping.POSITIONSWERT])
        pos = {
            "position": feld(idx, Herkunft.CSV, Status.SICHER),
            "artikelnummer": feld(row[mapping.ARTIKEL].strip() or None, Herkunft.CSV, Status.SICHER),
            "gesamtpreis": feld(format_decimal(gesamt), Herkunft.CSV,
                                Status.SICHER if gesamt is not None else Status.FEHLT),
            # Der Abas-CSV-Export enthaelt je Position keine Menge/Einheit/
            # Einzelpreis/Beschreibung -> Pflichtangaben rot (aus PDF/manuell).
            "beschreibung": fehlend("Artikelbeschreibung fehlt in der CSV - bitte ergaenzen."),
            "menge": fehlend("Menge fehlt in der CSV - bitte ergaenzen."),
            "einheit": fehlend("Einheit fehlt in der CSV - bitte ergaenzen."),
            "einzelpreis": fehlend("Einzelpreis fehlt in der CSV - bitte ergaenzen."),
        }
        model.positionen.append(pos)


def _build_summen(model: InvoiceModel, first: list[str], sums: dict[str, Decimal],
                  positions: list[list[str]], mapping) -> None:
    netto = sums.get("nettosumme") or parse_german_decimal(first[mapping.NETTOSUMME])
    brutto = sums.get("bruttosumme") or parse_german_decimal(first[mapping.BRUTTOSUMME])
    steuer = sums.get("steuer")
    if steuer is None and netto is not None and brutto is not None:
        steuer = brutto - netto

    # Gegenpruefung: Summe der Positionswerte == Nettosumme?
    pos_sum = sum((parse_german_decimal(r[mapping.POSITIONSWERT]) or Decimal(0)) for r in positions)
    netto_status = Status.SICHER
    netto_hinweis = None
    if netto is not None and abs(pos_sum - netto) > Decimal("0.02"):
        netto_status = Status.UNSICHER
        netto_hinweis = "Summe der Positionen weicht von der Nettosumme ab - bitte pruefen."

    s = model.summen
    s["nettobetrag"] = feld(format_decimal(netto), Herkunft.CSV, netto_status, hinweis=netto_hinweis) \
        if netto is not None else fehlend("Nettosumme fehlt - bitte ergaenzen.")
    s["steuerbetrag"] = feld(format_decimal(steuer),
                             Herkunft.CSV if "steuer" in sums else Herkunft.BERECHNET,
                             Status.SICHER) if steuer is not None else fehlend("Steuerbetrag fehlt.")
    s["bruttobetrag"] = feld(format_decimal(brutto), Herkunft.CSV, Status.SICHER) \
        if brutto is not None else fehlend("Bruttosumme fehlt - bitte ergaenzen.")

    # Steuersatz ableiten (Dokumentebene); je Position nicht in CSV enthalten.
    if netto and steuer is not None and netto != 0:
        satz = (steuer / netto * 100).quantize(Decimal("1"))
        s["steuersatz"] = feld(str(satz), Herkunft.BERECHNET, Status.UNSICHER,
                               hinweis="Steuersatz aus Netto/Steuer abgeleitet - bitte bestaetigen.")
