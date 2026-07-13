"""Optionaler KI-Fallback (LLM) zum Erkennen fehlender Kopf-/Summenfelder.

Proof-of-Concept ueber OpenRouter (ein OpenAI-kompatibler Chat-Endpunkt, der
viele Modelle buendelt). Der Schritt ist standardmaessig AUS und wird nur aktiv,
wenn ``KI_ENABLED=true`` gesetzt und ein ``OPENROUTER_API_KEY`` vorhanden ist.

Grundsaetze (bewusst konservativ, weil es um Finanzdaten geht):
  * KI ist nie massgeblich: sie fuellt ausschliesslich Felder, die die
    deterministischen Pfade (CSV/PDF/OCR) NICHT liefern konnten (status=fehlt).
  * Jeder KI-Wert wird ``herkunft=ki`` / ``status=unsicher`` markiert - landet
    also im Pruefbildschirm im Gelb und wird nie automatisch bestaetigt/
    exportiert (der Kern-Sicherheitsinvariant der Anwendung bleibt erhalten).
  * Datenschutz: nur der bereits extrahierte Rechnungs-Text wird gesendet, und
    nur wenn der Betreiber KI ausdruecklich aktiviert (Opt-in). Fuer produktive
    DSGVO-Nutzung waere ein EU-/self-hosted Modell zu waehlen - hier PoC.
  * Robust: jeder Fehler (kein Schluessel, Timeout, unparsbare Antwort) fuehrt
    dazu, dass NICHTS ergaenzt wird. Der KI-Schritt darf die Extraktion nie
    zum Scheitern bringen.
"""
from __future__ import annotations

import json
import logging
import os
import re
from typing import Any

from .model import Herkunft, InvoiceModel, Status

log = logging.getLogger("extraction-worker")

_OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
_DEFAULT_MODEL = "openai/gpt-4o-mini"

# Nur diese Kopf-/Summenfelder darf die KI ergaenzen. Bewusst eng gehalten:
# gut aus Freitext ableitbare, klar benannte Felder - keine Positionen, keine
# Betraege, die aus der CSV stammen muessten.
#
# WICHTIG: KEINE Verkaeufer-Felder. Der Verkaeufer ist bei diesen ausgehenden
# Abas-Rechnungen immer die eigene Firma (Single-Tenant) und wird verlaesslich
# aus den Stammdaten/CompanyProfile gefuellt - nicht aus dem Belegtext. Im
# Abas-Layout ist optisch der KAEUFER prominent (Adressfenster), der Verkaeufer
# nur die kleine Absenderzeile; ein LLM verwechselt das leicht (setzt den Kaeufer
# faelschlich als Verkaeufer). Zudem wuerde ein KI-Wert (status=unsicher) das
# Stammdaten-Autofill blockieren, das nur echte Luecken (status=fehlt) fuellt.
_KI_KOPF_FELDER = {
    "rechnungsnummer": "Rechnungsnummer/Belegnummer",
    "rechnungsdatum": "Rechnungsdatum (ISO 8601, JJJJ-MM-TT)",
    "faelligkeit": "Faelligkeitsdatum (ISO 8601, JJJJ-MM-TT)",
    "kaeufer_name": "Name des Rechnungsempfaengers/Kaeufers",
    "kaeufer_strasse": "Strasse und Hausnummer des Kaeufers",
    "kaeufer_plz": "Postleitzahl des Kaeufers",
    "kaeufer_ort": "Ort des Kaeufers",
    "kaeufer_ustid": "USt-IdNr des Kaeufers",
}


def _env_truthy(name: str) -> bool:
    return os.getenv(name, "false").strip().lower() in ("1", "true", "yes")


def resolve_config(override: dict | None = None) -> dict:
    """Effektive KI-Konfiguration: Werte aus der webapp-DB (per Job uebergeben)
    haben Vorrang, sonst die .env des Workers (Fallback fuer den HTTP-Pfad)."""
    override = override or {}
    enabled = override.get("enabled")
    if enabled is None:
        enabled = _env_truthy("KI_ENABLED")
    api_key = (override.get("api_key") or os.getenv("OPENROUTER_API_KEY") or "").strip()
    model = (override.get("model") or os.getenv("KI_MODEL") or _DEFAULT_MODEL).strip()
    try:
        timeout = float(override.get("timeout") or os.getenv("KI_TIMEOUT", "30"))
    except (TypeError, ValueError):
        timeout = 30.0
    return {"enabled": bool(enabled), "api_key": api_key, "model": model, "timeout": timeout}


def ki_available(config: dict | None = None) -> bool:
    """KI-Fallback ist nur aktiv, wenn eingeschaltet UND ein Schluessel vorhanden."""
    cfg = config if config and "enabled" in config else resolve_config(config)
    return cfg["enabled"] and bool(cfg["api_key"])


def _candidates(model: InvoiceModel, correct_ocr: bool) -> dict[str, tuple[str, str | None]]:
    """Bearbeitbare Kopf-Felder als ``{feld: (beschreibung, aktueller_wert)}``.

    Immer dabei: leere/rote Felder (``aktueller_wert=None`` -> extrahieren). Bei
    ``correct_ocr`` zusaetzlich Felder mit OCR-Herkunft (Wert = aktuelle, oft
    fehlerhafte OCR-Lesung -> von der KI korrigieren lassen). Verlaessliche
    Quellen (pdf/csv/stammdaten/manuell) werden nie angetastet."""
    out: dict[str, tuple[str, str | None]] = {}
    for key, beschreibung in _KI_KOPF_FELDER.items():
        f = model.kopf.get(key)
        leer = (not f) or f.get("wert") in (None, "") or f.get("status") == Status.FEHLT.value
        if leer:
            out[key] = (beschreibung, None)
        elif correct_ocr and f.get("herkunft") == Herkunft.OCR.value:
            out[key] = (beschreibung, str(f.get("wert")))
    return out


def _call_openrouter(raw_text: str, felder: dict[str, tuple[str, str | None]], cfg: dict) -> dict[str, Any]:
    """Fragt das Modell nach genau den angefragten Feldern. Liefert {} bei Fehler.

    Felder ohne aktuellen Wert werden extrahiert; Felder mit (OCR-)Wert sollen
    korrigiert werden, falls die OCR sie offensichtlich falsch gelesen hat."""
    import requests  # nur im Container benoetigt

    zeilen = []
    for k, (beschr, aktuell) in felder.items():
        if aktuell is None:
            zeilen.append(f"- {k}: {beschr}")
        else:
            zeilen.append(f"- {k}: {beschr} (OCR las: \"{aktuell}\" - nur offensichtliche "
                          "OCR-Lesefehler korrigieren, sonst exakt diesen Wert zurueckgeben)")
    feld_liste = "\n".join(zeilen)
    system = (
        "Du extrahierst und korrigierst Felder aus deutschen Rechnungen. Antworte "
        "AUSSCHLIESSLICH mit einem JSON-Objekt, dessen Schluessel exakt die "
        "angefragten Feldnamen sind. Gib einen Wert nur an, wenn er eindeutig im "
        "Text steht; sonst null. Bei angegebenem OCR-Wert nur klare Lesefehler "
        "beheben (z.B. Buchstabe O statt Ziffer 0). Erfinde nichts."
    )
    user = (
        f"Bearbeite aus folgendem Rechnungstext diese Felder:\n{feld_liste}\n\n"
        f"--- Rechnungstext ---\n{raw_text[:6000]}"
    )
    payload = {
        "model": cfg["model"],
        "temperature": 0,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
        "response_format": {"type": "json_object"},
    }
    headers = {
        "Authorization": f"Bearer {cfg['api_key']}",
        "Content-Type": "application/json",
        # OpenRouter-Attribution (optional, White-Label-neutral).
        "X-Title": "senapsa-erechnung",
    }
    resp = requests.post(_OPENROUTER_URL, json=payload, headers=headers, timeout=cfg["timeout"])
    resp.raise_for_status()
    content = resp.json()["choices"][0]["message"]["content"]
    return _parse_json_object(content)


def _parse_json_object(content: str) -> dict[str, Any]:
    """Robustes Parsen: akzeptiert auch in ```json ... ``` gewickelte Antworten."""
    content = content.strip()
    try:
        return json.loads(content)
    except json.JSONDecodeError:
        mm = re.search(r"\{.*\}", content, re.DOTALL)
        if mm:
            try:
                return json.loads(mm.group(0))
            except json.JSONDecodeError:
                return {}
    return {}


def enrich_with_ki(model: InvoiceModel, raw_text: str, config: dict | None = None,
                   correct_ocr: bool = False) -> int:
    """Ergaenzt fehlende und (optional) korrigiert OCR-Kopf-Felder per LLM.

    ``config`` sind die von der webapp uebergebenen Laufzeit-Einstellungen
    (DB-gepflegt); ohne ``config`` gilt die .env des Workers. ``correct_ocr``
    aktiviert die Nachkorrektur OCR-gelesener Werte (nur fuer Scan-Belege sinnvoll,
    da die PDF-Textebene verlaesslich ist). Idempotent-sicher: fasst nur leere oder
    OCR-Felder an; alle geschriebenen Werte werden ``herkunft=ki`` / ``status=
    unsicher`` markiert (prueffpflichtig). Gibt die Zahl geaenderter Felder zurueck.
    """
    cfg = resolve_config(config)
    if not ki_available(cfg) or not raw_text or not raw_text.strip():
        return 0
    felder = _candidates(model, correct_ocr)
    if not felder:
        return 0

    log.info("KI-Fallback: pruefe %d Kandidatenfelder (correct_ocr=%s, modell=%s)",
             len(felder), correct_ocr, cfg["model"])
    try:
        werte = _call_openrouter(raw_text, felder, cfg)
    except Exception:  # noqa: BLE001 - KI darf die Extraktion nie zum Scheitern bringen
        log.exception("KI-Fallback fehlgeschlagen - Extraktion laeuft ohne KI weiter")
        return 0

    geaendert = 0
    for key, (_beschr, aktuell) in felder.items():
        wert = werte.get(key)
        if wert in (None, ""):
            continue
        wert = str(wert).strip()
        # OCR-Wert unveraendert bestaetigt -> nicht anfassen (bleibt herkunft=ocr).
        if aktuell is not None and wert == str(aktuell).strip():
            continue
        korrektur = aktuell is not None
        model.kopf[key] = {
            "wert": wert,
            "herkunft": Herkunft.KI.value,
            "status": Status.UNSICHER.value,
            "confidence": 0.5,
            "hinweis": ("Von der KI korrigiert (OCR-Lesefehler) - bitte pruefen und bestaetigen."
                        if korrektur else
                        "Von der KI vorgeschlagen - bitte pruefen und bestaetigen."),
        }
        geaendert += 1

    if geaendert:
        model.quelle["ki_verwendet"] = True
        log.info("KI-Fallback hat %d Felder ergaenzt/korrigiert", geaendert)
    return geaendert
