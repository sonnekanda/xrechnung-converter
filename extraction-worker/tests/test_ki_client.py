"""Tests KI-Fallback (LLM) - ohne Netzwerk, ueber gemockten OpenRouter-Aufruf.

Prueft die Sicherheitsinvarianten des PoC:
  * Standard AUS: ohne KI_ENABLED/Schluessel passiert nichts.
  * KI fuellt NUR fehlende Felder (status=fehlt) - gesicherte Werte bleiben.
  * KI-Werte sind immer herkunft=ki / status=unsicher (pruefpflichtig).
"""
from app import ki_client
from app.ki_client import enrich_with_ki
from app.model import Herkunft, InvoiceModel, Status, feld, fehlend


def _modell_mit_luecke() -> InvoiceModel:
    m = InvoiceModel(mapping_version="test", dokumentart="rechnung")
    m.kopf["rechnungsnummer"] = feld("400009", Herkunft.PDF, Status.SICHER)
    m.kopf["kaeufer_name"] = fehlend("Kaeufer nicht erkannt - bitte ergaenzen.")
    return m


def test_ki_ruehrt_verkaeufer_nicht_an(monkeypatch):
    """Der Verkaeufer ist immer die eigene Firma (Stammdaten) - die KI darf ihn
    nie vorschlagen (sonst Kaeufer/Verkaeufer-Verwechslung wie bei 400009)."""
    assert not any(k.startswith("verkaeufer") for k in ki_client._KI_KOPF_FELDER)

    monkeypatch.setenv("KI_ENABLED", "true")
    monkeypatch.setenv("OPENROUTER_API_KEY", "test-key")
    # Selbst wenn das Modell einen fehlenden Verkaeufer hat und das LLM (falsch)
    # einen liefert, darf das Feld nicht gesetzt werden.
    monkeypatch.setattr(
        ki_client, "_call_openrouter",
        lambda raw, felder, cfg: {"verkaeufer_name": "Kleine Automobile AG"},
    )
    m = _modell_mit_luecke()
    m.kopf["verkaeufer_name"] = fehlend("Verkaeufer nicht erkannt - bitte ergaenzen.")
    enrich_with_ki(m, "Senapsa Test * Teststr. * 65868 Testhausen\nKleine Automobile AG")
    assert m.kopf["verkaeufer_name"]["wert"] is None
    assert m.kopf["verkaeufer_name"]["herkunft"] == Herkunft.MANUELL.value


def test_ki_aus_per_default(monkeypatch):
    """Ohne KI_ENABLED wird nicht ergaenzt und kein Netzwerkaufruf gemacht."""
    monkeypatch.delenv("KI_ENABLED", raising=False)
    monkeypatch.delenv("OPENROUTER_API_KEY", raising=False)
    m = _modell_mit_luecke()
    assert enrich_with_ki(m, "Rechnung an Kleine Automobile AG") == 0
    assert m.kopf["kaeufer_name"]["status"] == Status.FEHLT.value


def test_ki_fuellt_nur_luecken(monkeypatch):
    """Aktiv: gemockte Antwort fuellt das fehlende Feld als ki/unsicher; das
    bereits gesicherte Feld bleibt unangetastet."""
    monkeypatch.setenv("KI_ENABLED", "true")
    monkeypatch.setenv("OPENROUTER_API_KEY", "test-key")
    # Modell liefert auch rechnungsdatum etc. als fehlend -> Modell gibt nur den
    # Kaeufer zurueck; der Rest bleibt fehlt (KI erfindet nichts).
    monkeypatch.setattr(
        ki_client, "_call_openrouter",
        lambda raw, felder, cfg: {"kaeufer_name": "Kleine Automobile AG"},
    )
    m = _modell_mit_luecke()
    ergaenzt = enrich_with_ki(m, "Rechnung an Kleine Automobile AG, Berlin")

    assert ergaenzt == 1
    kaeufer = m.kopf["kaeufer_name"]
    assert kaeufer["wert"] == "Kleine Automobile AG"
    assert kaeufer["herkunft"] == Herkunft.KI.value
    assert kaeufer["status"] == Status.UNSICHER.value   # gelb -> pruefpflichtig
    # Gesichertes Feld unveraendert.
    assert m.kopf["rechnungsnummer"]["herkunft"] == Herkunft.PDF.value
    assert m.kopf["rechnungsnummer"]["status"] == Status.SICHER.value
    assert m.quelle.get("ki_verwendet") is True


def test_ki_fehler_bricht_nicht_ab(monkeypatch):
    """Ein Fehler im KI-Aufruf darf die Extraktion nicht zum Scheitern bringen."""
    monkeypatch.setenv("KI_ENABLED", "true")
    monkeypatch.setenv("OPENROUTER_API_KEY", "test-key")

    def boom(raw, felder, cfg):
        raise RuntimeError("Netzwerkfehler")

    monkeypatch.setattr(ki_client, "_call_openrouter", boom)
    m = _modell_mit_luecke()
    assert enrich_with_ki(m, "irgendein Text") == 0
    assert m.kopf["kaeufer_name"]["status"] == Status.FEHLT.value


def test_ki_ohne_luecke_kein_aufruf(monkeypatch):
    """Sind alle KI-Felder bereits sicher, wird das Modell nicht angefasst."""
    monkeypatch.setenv("KI_ENABLED", "true")
    monkeypatch.setenv("OPENROUTER_API_KEY", "test-key")
    called = {"n": 0}

    def spy(raw, felder, cfg):
        called["n"] += 1
        return {}

    monkeypatch.setattr(ki_client, "_call_openrouter", spy)
    m = InvoiceModel(mapping_version="test", dokumentart="rechnung")
    for key in ki_client._KI_KOPF_FELDER:
        m.kopf[key] = feld("x", Herkunft.CSV, Status.SICHER)
    assert enrich_with_ki(m, "text") == 0
    assert called["n"] == 0


def test_ki_config_ueberschreibt_env(monkeypatch):
    """Vom Job uebergebene DB-Einstellungen haben Vorrang vor der .env:
    env sagt AUS, aber der Job aktiviert KI mit eigenem Schlussel/Modell."""
    monkeypatch.setenv("KI_ENABLED", "false")
    monkeypatch.delenv("OPENROUTER_API_KEY", raising=False)
    gesehen = {}

    def capture(raw, felder, cfg):
        gesehen.update(cfg)
        return {"kaeufer_name": "Kleine Automobile AG"}

    monkeypatch.setattr(ki_client, "_call_openrouter", capture)
    cfg = {"enabled": True, "api_key": "db-key", "model": "anthropic/claude-3.5", "timeout": 15}
    m = _modell_mit_luecke()
    assert enrich_with_ki(m, "Rechnung an Kleine Automobile AG", cfg) == 1
    assert gesehen["api_key"] == "db-key"
    assert gesehen["model"] == "anthropic/claude-3.5"
    assert m.kopf["kaeufer_name"]["herkunft"] == Herkunft.KI.value
