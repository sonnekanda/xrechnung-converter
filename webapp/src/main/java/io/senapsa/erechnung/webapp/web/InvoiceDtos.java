package io.senapsa.erechnung.webapp.web;

import com.fasterxml.jackson.databind.JsonNode;
import io.senapsa.erechnung.webapp.domain.Invoice;

import java.time.Instant;
import java.util.List;

/** DTOs der Rechnungs-API. */
public final class InvoiceDtos {

    private InvoiceDtos() {
    }

    /** Zeile in der Uebersicht/Dashboard. */
    public record InvoiceSummary(String id, String rechnungsnummer, String status,
                                 String dokumentart, boolean needsOcr, boolean valid,
                                 String pdfFilename, String csvFilename, String source,
                                 Instant createdAt) {
        public static InvoiceSummary of(Invoice i) {
            return new InvoiceSummary(i.getId().toString(), i.getRechnungsnummer(),
                    i.getStatus().name(), i.getDokumentart(), i.isNeedsOcr(), i.isValid(),
                    i.getPdfFilename(), i.getCsvFilename(), i.getSource(), i.getCreatedAt());
        }
    }

    /** Detailansicht fuer den Pruefbildschirm inkl. internem Modell und Ampel. */
    public record InvoiceDetail(String id, String status, boolean needsOcr, boolean valid,
                                String pdfFilename, String csvFilename,
                                JsonNode model, boolean exportReady,
                                List<Issue> issues) {
    }

    public record Issue(String feld, String meldung) {
    }

    public record StatusResponse(String id, String status, boolean needsOcr) {
    }

    public record FieldUpdateRequest(String block, String feld, String wert, Integer positionIndex) {
    }

    public record GenerateResponse(boolean valid, List<Issue> issues) {
    }
}
