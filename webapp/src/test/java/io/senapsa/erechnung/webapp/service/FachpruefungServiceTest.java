package io.senapsa.erechnung.webapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fachprüfung (A-07): Pflichtfelder, Summenlogik, Arithmetik-Konsistenz,
 * Rundungstoleranz, IBAN-Prüfsumme. Die Prüfung ist das erste Gate der
 * Exportsperre (K-04) – exportReady=false blockiert die Generierung.
 */
class FachpruefungServiceTest {

    private FachpruefungService service;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        service = new FachpruefungService(new ModelJson(mapper), "0.02");
    }

    // --- Modell-Baukasten ----------------------------------------------------

    /** Vollständig gültiges Modell (angelehnt an Musterrechnung 400009). */
    private ObjectNode gueltigesModell() {
        ObjectNode m = mapper.createObjectNode();
        m.put("dokumentart", "rechnung");
        ObjectNode kopf = m.putObject("kopf");
        feld(kopf, "rechnungsnummer", "400009", "sicher");
        feld(kopf, "rechnungsdatum", "2026-07-02", "sicher");
        feld(kopf, "waehrung", "EUR", "sicher");
        feld(kopf, "verkaeufer_name", "Senapsa Test GmbH", "sicher");
        // Eigene Firma (Verkaeufer): Postanschrift + Steuerkennung aus dem
        // Stammdaten-Autofill - Voraussetzung fuer eine gueltige XRechnung.
        feld(kopf, "verkaeufer_strasse", "Musterweg 1", "sicher");
        feld(kopf, "verkaeufer_plz", "10115", "sicher");
        feld(kopf, "verkaeufer_ort", "Berlin", "sicher");
        feld(kopf, "verkaeufer_telefon", "+49 30 1234567", "sicher");
        feld(kopf, "verkaeufer_email", "rechnung@senapsa-test.de", "sicher");
        feld(kopf, "verkaeufer_ustid", "DE123456789", "sicher");
        feld(kopf, "kaeufer_name", "Kleine Automobile AG", "sicher");
        feld(kopf, "leitweg_id", "991-12345-67", "sicher");
        ObjectNode summen = m.putObject("summen");
        feld(summen, "nettobetrag", "100.00", "sicher");
        feld(summen, "steuerbetrag", "19.00", "sicher");
        feld(summen, "bruttobetrag", "119.00", "sicher");
        feld(summen, "steuersatz", "19", "sicher");
        ArrayNode positionen = m.putArray("positionen");
        ObjectNode pos = positionen.addObject();
        feld(pos, "beschreibung", "Gewindestück", "sicher");
        feld(pos, "menge", "2", "sicher");
        feld(pos, "einheit", "Stück", "sicher");
        feld(pos, "einzelpreis", "50.00", "sicher");
        feld(pos, "gesamtpreis", "100.00", "sicher");
        feld(pos, "steuersatz", "19", "sicher");
        return m;
    }

    private static void feld(ObjectNode block, String name, String wert, String status) {
        ObjectNode f = block.putObject(name);
        f.put("wert", wert);
        f.put("herkunft", "pdf");
        f.put("status", status);
    }

    private List<String> felder(FachpruefungService.Report report) {
        return report.issues().stream().map(FachpruefungService.Issue::feld).toList();
    }

    // --- Grundfälle ------------------------------------------------------------

    @Test
    void gueltiges_modell_ist_exportbereit() {
        FachpruefungService.Report report = service.pruefe(gueltigesModell());
        assertThat(report.issues()).isEmpty();
        assertThat(report.exportReady()).isTrue();
    }

    @Test
    void gutschrift_blockiert_export() {
        ObjectNode m = gueltigesModell();
        m.put("dokumentart", "gutschrift");
        FachpruefungService.Report report = service.pruefe(m);
        assertThat(report.exportReady()).isFalse();
        assertThat(felder(report)).contains("dokumentart");
    }

    // --- Pflichtfelder -----------------------------------------------------------

    @Nested
    class Pflichtfelder {

        @Test
        void fehlendes_kopffeld_blockiert() {
            ObjectNode m = gueltigesModell();
            ((ObjectNode) m.get("kopf")).remove("leitweg_id");
            FachpruefungService.Report report = service.pruefe(m);
            assertThat(report.exportReady()).isFalse();
            assertThat(felder(report)).containsExactly("leitweg_id");
            assertThat(report.issues().get(0).meldung()).contains("fehlt");
        }

        @Test
        void leerer_wert_gilt_als_fehlend() {
            ObjectNode m = gueltigesModell();
            feld((ObjectNode) m.get("kopf"), "rechnungsnummer", "", "sicher");
            assertThat(felder(service.pruefe(m))).containsExactly("rechnungsnummer");
        }

        @Test
        void unsicheres_feld_erfordert_bestaetigung() {
            ObjectNode m = gueltigesModell();
            feld((ObjectNode) m.get("kopf"), "rechnungsnummer", "400009", "unsicher");
            FachpruefungService.Report report = service.pruefe(m);
            assertThat(report.exportReady()).isFalse();
            assertThat(report.issues().get(0).meldung()).contains("unsicher");
        }

        @Test
        void eigener_hinweis_wird_bevorzugt() {
            ObjectNode m = gueltigesModell();
            ObjectNode f = (ObjectNode) m.get("kopf").get("leitweg_id");
            f.putNull("wert");
            f.put("status", "fehlt");
            f.put("hinweis", "Leitweg-ID fehlt - wird aus den Stammdaten uebernommen.");
            FachpruefungService.Report report = service.pruefe(m);
            assertThat(report.issues().get(0).meldung())
                    .isEqualTo("Leitweg-ID fehlt - wird aus den Stammdaten uebernommen.");
        }

        @Test
        void ohne_positionen_blockiert() {
            ObjectNode m = gueltigesModell();
            m.putArray("positionen");
            FachpruefungService.Report report = service.pruefe(m);
            assertThat(report.exportReady()).isFalse();
            assertThat(felder(report)).contains("positionen");
        }

        @Test
        void fehlendes_positionsfeld_wird_positionsbezogen_gemeldet() {
            ObjectNode m = gueltigesModell();
            ((ObjectNode) m.get("positionen").get(0)).remove("menge");
            FachpruefungService.Report report = service.pruefe(m);
            assertThat(felder(report)).contains("position[1].menge");
            assertThat(report.issues().get(0).meldung()).contains("Position 1");
        }

        @Test
        void unsicheres_nicht_pflichtfeld_blockiert_ebenfalls() {
            // Der 400014-Fall: Fälligkeit kam per OCR (gelb) und wurde nicht
            // bestätigt - die Prüfung darf trotzdem NICHT durchgehen (K-02).
            ObjectNode m = gueltigesModell();
            feld((ObjectNode) m.get("kopf"), "faelligkeit", "2026-07-22", "unsicher");
            FachpruefungService.Report report = service.pruefe(m);
            assertThat(report.exportReady()).isFalse();
            assertThat(felder(report)).containsExactly("faelligkeit");
            assertThat(report.issues().get(0).meldung()).contains("unsicher");
        }

        @Test
        void unsicheres_nicht_pflichtfeld_in_position_blockiert() {
            ObjectNode m = gueltigesModell();
            feld((ObjectNode) m.get("positionen").get(0), "artikelnummer", "100005", "unsicher");
            FachpruefungService.Report report = service.pruefe(m);
            assertThat(felder(report)).containsExactly("position[1].artikelnummer");
        }

        @Test
        void unsichere_positionsnummer_blockiert_nicht() {
            // "position" ist nur die Zeilennummer (kein Bestätigen-Feld im UI) und
            // darf den Export nicht blockieren, auch wenn OCR sie als unsicher liest.
            ObjectNode m = gueltigesModell();
            feld((ObjectNode) m.get("positionen").get(0), "position", "1", "unsicher");
            assertThat(service.pruefe(m).exportReady()).isTrue();
        }

        @Test
        void interne_marker_und_optionale_fehlende_felder_blockieren_nicht() {
            ObjectNode m = gueltigesModell();
            // interner Marker (beginnt mit "_") wird ignoriert
            feld((ObjectNode) m.get("kopf"), "_hinweis_dokumentart", "x", "unsicher");
            // optionales Feld ohne Wert (rot) blockiert nicht - eine XRechnung
            // ist auch ohne Leistungszeitraum gueltig
            ObjectNode lz = ((ObjectNode) m.get("kopf")).putObject("leistungszeitraum");
            lz.putNull("wert");
            lz.put("status", "fehlt");
            assertThat(service.pruefe(m).exportReady()).isTrue();
        }
    }

    // --- Eigene Firma (Verkaeufer) ---------------------------------------------

    @Nested
    class EigeneFirma {

        @Test
        void fehlende_verkaeufer_adresse_blockiert_feldbezogen() {
            // Frische Installation: eigene Firma nicht gepflegt -> Autofill hat
            // Strasse/PLZ/Ort nicht gesetzt. Frueher blieb alles gruen und erst
            // KoSIT scheiterte; jetzt meldet die Fachpruefung es feldbezogen.
            ObjectNode m = gueltigesModell();
            ObjectNode kopf = (ObjectNode) m.get("kopf");
            kopf.remove("verkaeufer_strasse");
            kopf.remove("verkaeufer_plz");
            kopf.remove("verkaeufer_ort");
            FachpruefungService.Report report = service.pruefe(m);
            assertThat(report.exportReady()).isFalse();
            assertThat(felder(report))
                    .contains("verkaeufer_strasse", "verkaeufer_plz", "verkaeufer_ort");
            assertThat(report.issues().get(0).meldung()).contains("Eigene Firma");
        }

        @Test
        void ohne_ustid_und_ohne_steuernummer_blockiert() {
            ObjectNode m = gueltigesModell();
            ((ObjectNode) m.get("kopf")).remove("verkaeufer_ustid");
            FachpruefungService.Report report = service.pruefe(m);
            assertThat(report.exportReady()).isFalse();
            assertThat(felder(report)).contains("verkaeufer_ustid");
            assertThat(report.issues().get(0).meldung()).contains("Steuernummer");
        }

        @Test
        void fehlender_verkaeufer_kontakt_blockiert() {
            // XRechnung verlangt Verkaeufer-Telefon (BT-42, BR-DE-6) und -E-Mail
            // (BT-43, BR-DE-7). Fehlen sie, scheiterte frueher erst die amtliche
            // Prüfung an leer eingebetteten Kontaktfeldern (Fall 400014).
            ObjectNode m = gueltigesModell();
            ObjectNode kopf = (ObjectNode) m.get("kopf");
            kopf.remove("verkaeufer_telefon");
            kopf.remove("verkaeufer_email");
            FachpruefungService.Report report = service.pruefe(m);
            assertThat(report.exportReady()).isFalse();
            assertThat(felder(report)).contains("verkaeufer_telefon", "verkaeufer_email");
        }

        @Test
        void steuernummer_genuegt_statt_ustid() {
            // BR-CO-26: eine der beiden Kennungen reicht (z.B. Kleinunternehmer).
            ObjectNode m = gueltigesModell();
            ObjectNode kopf = (ObjectNode) m.get("kopf");
            kopf.remove("verkaeufer_ustid");
            feld(kopf, "verkaeufer_steuernummer", "12/345/67890", "sicher");
            assertThat(service.pruefe(m).exportReady()).isTrue();
        }
    }

    // --- Summen & Arithmetik ---------------------------------------------------

    @Nested
    class SummenUndArithmetik {

        @Test
        void netto_plus_steuer_ungleich_brutto_blockiert() {
            ObjectNode m = gueltigesModell();
            feld((ObjectNode) m.get("summen"), "bruttobetrag", "120.00", "sicher");
            FachpruefungService.Report report = service.pruefe(m);
            assertThat(report.exportReady()).isFalse();
            assertThat(felder(report)).contains("summen");
        }

        @Test
        void abweichung_innerhalb_der_toleranz_ist_ok() {
            ObjectNode m = gueltigesModell();
            // 100,00 + 19,00 = 119,00; 119,02 liegt genau AN der Toleranzgrenze (0,02).
            feld((ObjectNode) m.get("summen"), "bruttobetrag", "119.02", "sicher");
            assertThat(service.pruefe(m).exportReady()).isTrue();
        }

        @Test
        void abweichung_knapp_ueber_der_toleranz_blockiert() {
            ObjectNode m = gueltigesModell();
            feld((ObjectNode) m.get("summen"), "bruttobetrag", "119.03", "sicher");
            assertThat(service.pruefe(m).exportReady()).isFalse();
        }

        @Test
        void einzelpreis_mal_menge_ungleich_gesamtpreis_blockiert() {
            ObjectNode m = gueltigesModell();
            // 50,00 × 2 = 100,00 – Gesamtpreis manipuliert. Es wird NICHT still
            // nachgerechnet, sondern gemeldet; der erwartete Wert steht im Text.
            feld((ObjectNode) m.get("positionen").get(0), "gesamtpreis", "90.00", "sicher");
            // Netto passend zur Positionssumme, damit nur EINE Abweichung gemeldet wird.
            feld((ObjectNode) m.get("summen"), "nettobetrag", "90.00", "sicher");
            feld((ObjectNode) m.get("summen"), "steuerbetrag", "17.10", "sicher");
            feld((ObjectNode) m.get("summen"), "bruttobetrag", "107.10", "sicher");
            FachpruefungService.Report report = service.pruefe(m);
            assertThat(felder(report)).containsExactly("position[1].gesamtpreis");
            assertThat(report.issues().get(0).meldung()).contains("Einzelpreis");
        }

        @Test
        void positionssumme_ungleich_netto_blockiert() {
            ObjectNode m = gueltigesModell();
            // Zweite Position hinzufügen -> Positionssumme 200, Netto bleibt 100.
            ObjectNode pos2 = ((ArrayNode) m.get("positionen")).addObject();
            feld(pos2, "beschreibung", "Zweites Teil", "sicher");
            feld(pos2, "menge", "1", "sicher");
            feld(pos2, "einheit", "Stück", "sicher");
            feld(pos2, "einzelpreis", "100.00", "sicher");
            feld(pos2, "gesamtpreis", "100.00", "sicher");
            feld(pos2, "steuersatz", "19", "sicher");
            FachpruefungService.Report report = service.pruefe(m);
            assertThat(felder(report)).contains("nettobetrag");
        }

        @Test
        void steuer_passt_nicht_zum_satz_blockiert() {
            ObjectNode m = gueltigesModell();
            // 100,00 × 19 % = 19,00 – Steuerbetrag manipuliert; Brutto konsistent
            // zur manipulierten Steuer, damit gezielt NUR der Satz-Check anschlägt.
            feld((ObjectNode) m.get("summen"), "steuerbetrag", "25.00", "sicher");
            feld((ObjectNode) m.get("summen"), "bruttobetrag", "125.00", "sicher");
            FachpruefungService.Report report = service.pruefe(m);
            assertThat(felder(report)).containsExactly("steuerbetrag");
        }

        @Test
        void ohne_steuersatz_kein_satz_check() {
            ObjectNode m = gueltigesModell();
            ((ObjectNode) m.get("summen")).remove("steuersatz");
            assertThat(service.pruefe(m).exportReady()).isTrue();
        }
    }

    // --- IBAN -------------------------------------------------------------------

    @Nested
    class Iban {

        @Test
        void gueltige_iban_passiert() {
            ObjectNode m = gueltigesModell();
            feld((ObjectNode) m.get("kopf"), "bankverbindung_iban", "DE89 3704 0044 0532 0130 00", "sicher");
            assertThat(service.pruefe(m).exportReady()).isTrue();
        }

        @Test
        void falsche_pruefsumme_blockiert() {
            ObjectNode m = gueltigesModell();
            feld((ObjectNode) m.get("kopf"), "bankverbindung_iban", "DE89370400440532013001", "sicher");
            FachpruefungService.Report report = service.pruefe(m);
            assertThat(felder(report)).containsExactly("bankverbindung_iban");
        }

        @Test
        void ohne_iban_keine_meldung() {
            // IBAN ist optional; nur WENN angegeben, muss sie stimmen.
            assertThat(service.pruefe(gueltigesModell()).exportReady()).isTrue();
        }

        @Test
        void iban_pruefsumme_direkt() {
            assertThat(FachpruefungService.isValidIban("DE89370400440532013000")).isTrue();
            assertThat(FachpruefungService.isValidIban("DE89 3704 0044 0532 0130 00")).isTrue();
            assertThat(FachpruefungService.isValidIban("DE00370400440532013000")).isFalse();
            assertThat(FachpruefungService.isValidIban("KEINE-IBAN")).isFalse();
            assertThat(FachpruefungService.isValidIban("DE12")).isFalse();
        }
    }
}
