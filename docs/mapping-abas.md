# Abas-Datenmapping (Version `abas-v1`)

Dieses Dokument beschreibt, wie Abas-Ausgangsrechnungen (PDF/CSV) auf das interne
Rechnungsmodell und damit auf die XRechnung-Pflichtfelder abgebildet werden.
Mappings sind versioniert (A-13); die angewandte Version steht je Rechnung im
Feld `mapping_version`.

## 1. CSV-Struktur

Der Abas-CSV-Export ist ein **denormalisierter Dump**:

- Trennzeichen `;`, eine Zeile je Datensatz; Kopf-/Summenfelder auf jeder Zeile
  wiederholt.
- Freitextfelder mit eingebetteten Semikola sind gequotet
  (`"Kleine Automobile AG;Hamburger Str"`).
- Deutsches Zahlenformat `1.234,56`.

### Format-Eigenheiten (Grund für „versioniertes Mapping")

| Beobachtung | Muster | Behandlung |
|---|---|---|
| Kopfzeile teils vorhanden, teils nicht | 400010 hat **keine** Kopfzeile | Zuordnung **positionsbasiert** (Spaltenindex), nicht über Namen |
| Unterschiedliche Kodierung | 400014 ist **CP1252**, andere UTF-8 | Kodierung wird je Datei erkannt |
| Mehrfach gleiche Spaltennamen | `Artikel` 3×, `Anfangstext` mehrfach | Index-Mapping statt Namens-Mapping |

### Summen-Pseudozeilen (zentral!)

Die Spalte `Artikel` mischt echte Positionen mit **Summenzeilen**. Codes:

| `Artikel` | Bedeutung | Zielfeld im Modell |
|---|---|---|
| 6-stellig (z.B. `100003`) | echte Position | `positionen[]` |
| `9` | Nettosumme | `summen.nettobetrag` |
| `6` | Steuerbetrag | `summen.steuerbetrag` |
| `1` | Bruttosumme | `summen.bruttobetrag` |

Verifiziert gegen die PDFs, z.B. **400009**: Netto 99.210,00 / Steuer 18.849,90 /
Brutto 118.059,90. Ohne diese Regel würden die Summenzeilen fälschlich als
Positionen gezählt.

### Verwendete Spalten (Index, 0-basiert)

| Index | Bedeutung | Modellfeld |
|---|---|---|
| 0 | Identnummer | `kopf.rechnungsnummer` |
| 2 | Rechnungsart | `dokumentart` |
| 4 | Artikel / Summencode | Position bzw. Summe |
| 7 | Betriebseigene USt-IdNr | `kopf.verkaeufer_ustid` |
| 8 | USt-IdNr Empfänger | `kopf.kaeufer_ustid` |
| 9 | Adresse (Kunde) „Name;Straße" | `kopf.kaeufer_name`/`_strasse` |
| 28 | Name (Kunde) | `kopf.kaeufer_name` |
| 29 | Rechnungsempfänger (Kundennr.) | `kopf.kaeufer_kundennummer` |
| 47 | Positionswert | Positions-/Summenwert |
| 48 | Erfassungswährung | `kopf.waehrung` |
| 70 | Postleitzahl (Kunde) | `kopf.kaeufer_plz` |
| 76 | Bruttosumme aller Positionen | Gegenprüfung |
| 78 | Nettosumme aller Positionen | Gegenprüfung |

> **Grenze des CSV-Pfads:** Der Export enthält je Position **keine** Menge,
> Einheit, Einzelpreis oder Beschreibung. Diese Felder werden im CSV-Pfad als
> `fehlt` (rot) markiert und aus dem PDF bzw. manuell ergänzt. Genau dafür ist
> die PDF↔CSV-Kombination gedacht (PDF führt).

## 2. PDF-Struktur

Die Abas-Layouts sind gleichförmig (gut für regelbasierte Erkennung, vgl.
invoice2data-Templates):

```
Rechnung Nr. 400009
vom 02.07.2026
Kunde 20587           Ihre USt-IdNr DE2456673467
...
Pos Artikel   Menge        Einzelpreis  %      Gesamtpreis
    100003     1.000 Stück  99,21               99.210,00
    Gewindestück
Nettosumme                                       99.210,00
Steuer          99.210,00   19,00               18.849,90
Summe                                           118.059,90
Fälligkeit 09.07.2026
```

Erkannt werden Rechnungsnummer, Datum, Fälligkeit, USt-IdNr, Käufer-Adressblock,
Positionen (Menge/Einheit/Einzel-/Gesamtpreis/Steuersatz) und Summen.

Fehlt die Textebene (Scan, z.B. **400014**), erkennt der `extraction-worker`
`needs_ocr` und übergibt an den `ocr-worker`. OCR-Ergebnisse sind grundsätzlich
`unsicher` (gelb) und prüfpflichtig.

## 3. Feldabgleich Abas ↔ XRechnung (Auszug)

| XRechnung (EN 16931) | Quelle | Hinweis |
|---|---|---|
| BT-1 Rechnungsnummer | PDF/CSV | |
| BT-2 Rechnungsdatum | PDF | in CSV nicht enthalten |
| BT-5 Währung | CSV/PDF | EUR |
| BT-10 Leitweg-ID (Buyer reference) | Stammdaten | B2G-Pflicht |
| BT-44 Käufer-Name | PDF/CSV | Pflicht (BR-07) |
| BG-6 Verkäufer-Kontakt | Stammdaten | XRechnung-Pflicht |
| Zahlungsweg (IBAN) | Stammdaten | für Überweisung |
| BG-25 Positionen | PDF (CSV nur teilweise) | Menge/Beschreibung aus PDF |
| Steuerkategorie/-satz | PDF (CSV abgeleitet) | Standard 19 % |

## 4. Versionierung

Neue Abas-Exportvarianten werden als eigene Mapping-Version (`abas-v2`, …) in der
Registry `extraction-worker/app/csv_mapping/` ergänzt; Bestandsdaten bleiben über
`mapping_version` reproduzierbar. Validierungsregeln der Engine sind über die
Mustangproject-/KoSIT-Version gebunden.
