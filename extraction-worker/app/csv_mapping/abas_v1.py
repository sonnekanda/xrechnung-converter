"""Abas-CSV-Mapping, Version ``abas-v1``.

Spaltenreihenfolge ermittelt aus den Musterexporten (400009/400010/400013/400014).
Zuordnung erfolgt **positionsbasiert** (Index), da die Kopfzeile mehrfach gleiche
Spaltennamen enthaelt (``Artikel`` 3x, ``Anfangstext`` mehrfach) und in einigen
Exporten (400010) ganz fehlt. Namen dienen nur der Verifikation.

Zentrale Besonderheit — Summen-Pseudozeilen:
    Die Spalte ``Artikel`` enthaelt neben echten (6-stelligen) Artikelnummern die
    Codes 9/6/1, die KEINE Positionen sind, sondern Dokumentsummen:
        "9" -> Nettosumme, "6" -> Steuerbetrag, "1" -> Bruttosumme.
    Verifiziert gegen die zugehoerigen PDFs (z.B. 400009: netto 99.210,00 /
    Steuer 18.849,90 / brutto 118.059,90).
"""
from __future__ import annotations

VERSION = "abas-v1"

# --- Spaltenindizes (0-basiert) ---------------------------------------------
IDENTNUMMER = 0            # Rechnungsnummer
RECHNUNGSART = 2           # "Rechnung" | "Kaufmaennische Gutschrift" ...
ARTIKEL = 4                # Artikelnummer bzw. Summen-Pseudocode
SELLER_USTID = 7           # Betriebseigene USt-IdNr
BUYER_USTID = 8            # USt-IdNr des Rechnungsempfaengers
ADRESSE_KUNDE = 9          # "Name;Strasse" (eingebettete Semikola, gequotet)
NAME_KUNDE = 28
KUNDENNUMMER = 29
SKONTOBEZUG = 46           # Skontobezugsbetrag (= Bruttobetrag)
POSITIONSWERT = 47         # Wert der jeweiligen Zeile (Position ODER Summe)
WAEHRUNG = 48              # Erfassungswaehrung, z.B. EUR
PLZ_KUNDE = 70
BRUTTOSUMME = 76           # Bruttosumme aller Positionen (Dokumentsumme)
NETTOSUMME = 78            # Nettosumme aller Positionen (Dokumentsumme)

MIN_COLUMNS = 79           # Zeile gilt nur mit mind. so vielen Spalten als gueltig

# Erste Kopfspalte zur Header-Erkennung.
HEADER_FIRST_CELL = "Identnummer"

# Summen-Pseudozeilen: Artikel-Code -> Zielfeld im Summenblock.
SUMMARY_CODES: dict[str, str] = {
    "9": "nettosumme",
    "6": "steuer",
    "1": "bruttosumme",
}

# Rechnungsart -> Dokumentart des internen Modells.
DOKUMENTART = {
    "rechnung": "rechnung",
    "kaufmaennische gutschrift": "gutschrift",
    "kaufmännische gutschrift": "gutschrift",
    "gutschrift": "gutschrift",
    "storno": "storno",
    "stornorechnung": "storno",
}


def dokumentart_from_rechnungsart(rechnungsart: str | None) -> str:
    if not rechnungsart:
        return "unbekannt"
    return DOKUMENTART.get(rechnungsart.strip().lower(), "unbekannt")


def is_summary_code(artikel: str | None) -> bool:
    return (artikel or "").strip() in SUMMARY_CODES
