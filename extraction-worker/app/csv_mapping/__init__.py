"""Versioniertes Abas-CSV-Mapping (A-03, A-13).

Der Abas-CSV-Export ist ein denormalisierter Dump: ``;``-getrennt, eine Zeile je
Datensatz, Kopf-/Summenfelder auf jeder Zeile wiederholt. Dieselbe ``Artikel``-
Spalte mischt echte Positionen mit Summen-Pseudozeilen (siehe ``abas_v1``).

Mappings sind versioniert; die angewandte Version wird im internen Modell als
``mapping_version`` protokolliert, damit Spezifikations-Updates ohne Kern-
Deployment nachvollziehbar sind.
"""
from __future__ import annotations

from . import abas_v1

# Registry: version-id -> Mapping-Modul. Neue Abas-Exportvarianten kommen als
# eigene Version hinzu; Bestandsdaten bleiben reproduzierbar.
REGISTRY = {
    abas_v1.VERSION: abas_v1,
}

DEFAULT_VERSION = abas_v1.VERSION


def get_mapping(version: str = DEFAULT_VERSION):
    if version not in REGISTRY:
        raise KeyError(f"Unbekannte Mapping-Version: {version}")
    return REGISTRY[version]
