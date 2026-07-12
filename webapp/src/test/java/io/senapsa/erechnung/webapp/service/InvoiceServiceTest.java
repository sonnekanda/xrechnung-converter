package io.senapsa.erechnung.webapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.senapsa.erechnung.webapp.domain.Invoice;
import io.senapsa.erechnung.webapp.domain.InvoiceStatus;
import io.senapsa.erechnung.webapp.repo.InvoiceRepository;
import io.senapsa.erechnung.webapp.repo.WatchProcessedRepository;
import io.senapsa.erechnung.webapp.web.ApiExceptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Gates der Statusmaschine in {@link InvoiceService} (A-12, K-04): die
 * Exportsperre ist technisch nicht umgehbar – falsche Zustände führen zu 409
 * (Conflict), Übergänge passieren nur auf den erlaubten Pfaden.
 */
@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    private static final String MODEL = """
            {"dokumentart":"rechnung",
             "kopf":{"rechnungsnummer":{"wert":"400009","herkunft":"csv","status":"sicher"}},
             "summen":{},
             "positionen":[]}
            """;

    @Mock InvoiceRepository invoices;
    @Mock FachpruefungService fachpruefung;
    @Mock GenerationService generation;
    @Mock AuditService audit;
    @Mock WatchProcessedRepository watchProcessed;
    @Mock WatchFolderService watchFolder;

    @TempDir Path dataDir;

    private InvoiceService service;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new InvoiceService(invoices, new ModelJson(new ObjectMapper()),
                fachpruefung, generation, audit, watchProcessed, watchFolder,
                dataDir.toString());
        lenient().when(invoices.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Invoice invoice(InvoiceStatus status) {
        Invoice invoice = new Invoice();
        invoice.setStatus(status);
        invoice.setModelJson(MODEL);
        when(invoices.findById(id)).thenReturn(Optional.of(invoice));
        return invoice;
    }

    // --- Export (K-04: nur validiert, sonst 409) ------------------------------

    @Nested
    class Export {

        @Test
        void vor_der_validierung_409() {
            invoice(InvoiceStatus.PRUEFEN);
            assertThatThrownBy(() -> service.export(id))
                    .isInstanceOf(ApiExceptions.Conflict.class);
        }

        @Test
        void ohne_erzeugte_xml_409_auch_bei_validiert() {
            invoice(InvoiceStatus.VALIDIERT); // kein ublXmlBase64 gesetzt
            assertThatThrownBy(() -> service.export(id))
                    .isInstanceOf(ApiExceptions.Conflict.class);
        }

        @Test
        void erstdownload_liefert_xml_und_setzt_exportiert() {
            Invoice inv = invoice(InvoiceStatus.VALIDIERT);
            inv.setUblXmlBase64(Base64.getEncoder().encodeToString("<Invoice/>".getBytes()));
            byte[] xml = service.export(id);
            assertThat(new String(xml)).isEqualTo("<Invoice/>");
            assertThat(inv.getStatus()).isEqualTo(InvoiceStatus.EXPORTIERT);
            verify(invoices).save(inv);
        }

        @Test
        void erneuter_download_bleibt_exportiert_ohne_neuen_uebergang() {
            Invoice inv = invoice(InvoiceStatus.EXPORTIERT);
            inv.setUblXmlBase64(Base64.getEncoder().encodeToString("<Invoice/>".getBytes()));
            byte[] xml = service.export(id);
            assertThat(new String(xml)).isEqualTo("<Invoice/>");
            assertThat(inv.getStatus()).isEqualTo(InvoiceStatus.EXPORTIERT);
            verify(invoices, never()).save(any());
        }

        @Test
        void unbekannte_rechnung_404() {
            when(invoices.findById(id)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.export(id))
                    .isInstanceOf(ApiExceptions.NotFound.class);
        }
    }

    // --- Feldänderung (Sperre nach Export, Invalidierung nach Validierung) ----

    @Nested
    class UpdateField {

        @Test
        void exportierte_rechnung_ist_schreibgeschuetzt() {
            invoice(InvoiceStatus.EXPORTIERT);
            assertThatThrownBy(() -> service.updateField(id, "kopf", "rechnungsnummer", "X", null))
                    .isInstanceOf(ApiExceptions.Conflict.class);
        }

        @Test
        void aenderung_vor_der_pruefphase_409() {
            invoice(InvoiceStatus.IN_VERARBEITUNG);
            assertThatThrownBy(() -> service.updateField(id, "kopf", "rechnungsnummer", "X", null))
                    .isInstanceOf(ApiExceptions.Conflict.class);
        }

        @Test
        void aenderung_nach_validierung_verwirft_xml_und_geht_zurueck_zu_pruefen() {
            Invoice inv = invoice(InvoiceStatus.VALIDIERT);
            inv.setUblXmlBase64("xml");
            inv.setValidationReport("report");
            inv.setValid(true);

            service.updateField(id, "kopf", "rechnungsnummer", "400010", null);

            assertThat(inv.getStatus()).isEqualTo(InvoiceStatus.PRUEFEN);
            assertThat(inv.getUblXmlBase64()).isNull();
            assertThat(inv.getValidationReport()).isNull();
            assertThat(inv.isValid()).isFalse();
        }

        @Test
        void anzeigespalten_werden_mitgefuehrt() {
            Invoice inv = invoice(InvoiceStatus.PRUEFEN);
            service.updateField(id, "kopf", "rechnungsnummer", "400010", null);
            service.updateField(id, "kopf", "rechnungsdatum", "2026-07-12", null);
            assertThat(inv.getRechnungsnummer()).isEqualTo("400010");
            assertThat(inv.getRechnungsdatum()).isEqualTo(java.time.LocalDate.of(2026, 7, 12));
        }
    }

    // --- Generierung (zwei Gates: Fachprüfung, dann Engine) --------------------

    @Nested
    class Generate {

        @Test
        void vor_der_pruefphase_409() {
            invoice(InvoiceStatus.HOCHGELADEN);
            assertThatThrownBy(() -> service.generate(id))
                    .isInstanceOf(ApiExceptions.Conflict.class);
            verifyNoInteractions(generation);
        }

        @Test
        void fachpruefung_offen_blockiert_ohne_engine_aufruf() {
            Invoice inv = invoice(InvoiceStatus.PRUEFEN);
            when(fachpruefung.pruefe(any())).thenReturn(new FachpruefungService.Report(false,
                    List.of(new FachpruefungService.Issue("leitweg_id", "fehlt"))));

            InvoiceService.GenerateOutcome outcome = service.generate(id);

            assertThat(outcome.valid()).isFalse();
            assertThat(outcome.issues()).hasSize(1);
            assertThat(inv.getStatus()).isEqualTo(InvoiceStatus.PRUEFEN);
            verifyNoInteractions(generation); // Engine läuft erst nach grüner Fachprüfung
        }

        @Test
        void erfolgreiche_validierung_setzt_validiert_und_speichert_xml() {
            Invoice inv = invoice(InvoiceStatus.PRUEFEN);
            when(fachpruefung.pruefe(any())).thenReturn(new FachpruefungService.Report(true, List.of()));
            when(generation.generateAndValidate(any())).thenReturn(
                    new GenerationService.EngineResult("UBL", "eGVtbA==", true, true, "<report/>"));

            InvoiceService.GenerateOutcome outcome = service.generate(id);

            assertThat(outcome.valid()).isTrue();
            assertThat(inv.getStatus()).isEqualTo(InvoiceStatus.VALIDIERT);
            assertThat(inv.getUblXmlBase64()).isEqualTo("eGVtbA==");
            assertThat(inv.getValidationReport()).isEqualTo("<report/>");
            assertThat(inv.isValid()).isTrue();
        }

        @Test
        void engine_ablehnung_bleibt_pruefen_mit_bericht() {
            Invoice inv = invoice(InvoiceStatus.PRUEFEN);
            when(fachpruefung.pruefe(any())).thenReturn(new FachpruefungService.Report(true, List.of()));
            when(generation.generateAndValidate(any())).thenReturn(
                    new GenerationService.EngineResult("UBL", "eGVtbA==", false, true, "<report/>"));

            InvoiceService.GenerateOutcome outcome = service.generate(id);

            assertThat(outcome.valid()).isFalse();
            assertThat(inv.getStatus()).isEqualTo(InvoiceStatus.PRUEFEN);
            assertThat(inv.isValid()).isFalse();
            // Bericht bleibt einsehbar, auch wenn die Prüfung fehlschlug.
            assertThat(inv.getValidationReport()).isEqualTo("<report/>");
        }
    }

    // --- Wieder freigeben -------------------------------------------------------

    @Nested
    class Reopen {

        @Test
        void exportierte_rechnung_bleibt_gesperrt() {
            invoice(InvoiceStatus.EXPORTIERT);
            assertThatThrownBy(() -> service.reopen(id))
                    .isInstanceOf(ApiExceptions.Conflict.class);
        }

        @Test
        void validierte_rechnung_geht_zurueck_zu_pruefen_und_verwirft_xml() {
            Invoice inv = invoice(InvoiceStatus.VALIDIERT);
            inv.setUblXmlBase64("xml");
            inv.setValid(true);

            service.reopen(id);

            assertThat(inv.getStatus()).isEqualTo(InvoiceStatus.PRUEFEN);
            assertThat(inv.getUblXmlBase64()).isNull();
            assertThat(inv.isValid()).isFalse();
        }

        @Test
        void in_pruefen_ist_reopen_ein_noop() {
            Invoice inv = invoice(InvoiceStatus.PRUEFEN);
            service.reopen(id);
            assertThat(inv.getStatus()).isEqualTo(InvoiceStatus.PRUEFEN);
            verify(invoices, never()).save(any());
        }
    }

    // --- ZUGFeRD (gleiche Gates wie Export + Original-PDF nötig) ---------------

    @Nested
    class Zugferd {

        @Test
        void vor_der_validierung_409() {
            invoice(InvoiceStatus.PRUEFEN);
            assertThatThrownBy(() -> service.zugferd(id))
                    .isInstanceOf(ApiExceptions.Conflict.class);
        }

        @Test
        void ohne_original_pdf_409() {
            invoice(InvoiceStatus.VALIDIERT); // pdfFilename = null (reiner CSV-Fall)
            assertThatThrownBy(() -> service.zugferd(id))
                    .isInstanceOf(ApiExceptions.Conflict.class)
                    .hasMessageContaining("Original-PDF");
        }

        @Test
        void fehlende_pdf_datei_404() {
            Invoice inv = invoice(InvoiceStatus.VALIDIERT);
            inv.setPdfFilename("400009.pdf"); // Datei existiert im TempDir nicht
            assertThatThrownBy(() -> service.zugferd(id))
                    .isInstanceOf(ApiExceptions.NotFound.class);
        }
    }

    // --- Löschen räumt Watch-Vermerke -------------------------------------------

    @Nested
    class Delete {

        @Test
        void watch_rechnung_gibt_dedup_vermerk_und_restdateien_frei() {
            Invoice inv = invoice(InvoiceStatus.PRUEFEN);
            inv.setWatchBasename("400009");

            service.delete(id);

            verify(watchProcessed).deleteByBasename("400009");
            verify(watchFolder).deleteInputTails("400009");
            verify(invoices).delete(inv);
            verify(audit).deleteForInvoice(id);
        }

        @Test
        void upload_rechnung_ohne_watch_bezug_loescht_nur_sich_selbst() {
            Invoice inv = invoice(InvoiceStatus.PRUEFEN); // watchBasename = null

            service.delete(id);

            verifyNoInteractions(watchProcessed, watchFolder);
            verify(invoices).delete(inv);
        }
    }

    // --- Auto-Bestätigen (Ordner-Automatik): OCR bleibt prüfpflichtig -----------

    @Nested
    class AutoConfirm {

        private static final String GEMISCHT = """
                {"dokumentart":"rechnung",
                 "kopf":{
                   "rechnungsnummer":{"wert":"400009","herkunft":"csv","status":"unsicher"},
                   "rechnungsdatum":{"wert":"2026-07-02","herkunft":"ocr","status":"unsicher"},
                   "waehrung":{"wert":"EUR","herkunft":"pdf","status":"sicher"}},
                 "summen":{},
                 "positionen":[{"menge":{"wert":"2","herkunft":"ocr","status":"unsicher"}}]}
                """;

        @Test
        void bestaetigt_gelbe_felder_aber_niemals_ocr() {
            Invoice inv = invoice(InvoiceStatus.PRUEFEN);
            inv.setModelJson(GEMISCHT);

            service.autoConfirmNonOcr(id);

            var model = new ModelJson(new ObjectMapper()).parse(inv.getModelJson());
            assertThat(model.at("/kopf/rechnungsnummer/status").asText()).isEqualTo("sicher");
            assertThat(model.at("/kopf/rechnungsdatum/status").asText()).isEqualTo("unsicher"); // OCR!
            assertThat(model.at("/kopf/waehrung/status").asText()).isEqualTo("sicher");
            assertThat(model.at("/positionen/0/menge/status").asText()).isEqualTo("unsicher"); // OCR!
        }

        @Test
        void ausserhalb_der_pruefphase_keine_aenderung() {
            Invoice inv = invoice(InvoiceStatus.VALIDIERT);
            inv.setModelJson(GEMISCHT);

            service.autoConfirmNonOcr(id);

            assertThat(inv.getModelJson()).isEqualTo(GEMISCHT);
            verify(invoices, never()).save(any());
        }
    }
}
