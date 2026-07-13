package io.senapsa.erechnung.webapp.web;

import io.senapsa.erechnung.webapp.domain.Invoice;
import io.senapsa.erechnung.webapp.service.FachpruefungService;
import io.senapsa.erechnung.webapp.service.InvoiceService;
import io.senapsa.erechnung.webapp.service.ModelJson;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final ModelJson modelJson;
    private final io.senapsa.erechnung.webapp.service.ValidationReportSummarizer reportSummarizer;

    public InvoiceController(InvoiceService invoiceService, ModelJson modelJson,
                             io.senapsa.erechnung.webapp.service.ValidationReportSummarizer reportSummarizer) {
        this.invoiceService = invoiceService;
        this.modelJson = modelJson;
        this.reportSummarizer = reportSummarizer;
    }

    @GetMapping
    public List<InvoiceDtos.InvoiceSummary> list(
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate von,
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate bis) {
        return invoiceService.list(von, bis).stream().map(this::toSummary).toList();
    }

    /** Übersichtszeile inkl. Käufer und Bruttobetrag aus dem internen Modell. */
    private InvoiceDtos.InvoiceSummary toSummary(Invoice invoice) {
        String kaeuferName = null;
        String bruttobetrag = null;
        if (invoice.getModelJson() != null) {
            var model = modelJson.parse(invoice.getModelJson());
            kaeuferName = modelJson.wert(model, "kopf", "kaeufer_name");
            bruttobetrag = modelJson.wert(model, "summen", "bruttobetrag");
        }
        return InvoiceDtos.InvoiceSummary.of(invoice, kaeuferName, bruttobetrag);
    }

    @GetMapping("/{id}")
    public InvoiceDtos.InvoiceDetail detail(@PathVariable UUID id) {
        Invoice invoice = invoiceService.get(id);
        return toDetail(invoice);
    }

    /** Korrektur/Bestaetigung eines Feldes im Pruefbildschirm. */
    @PatchMapping("/{id}")
    public InvoiceDtos.InvoiceDetail patch(@PathVariable UUID id,
                                           @RequestBody InvoiceDtos.FieldUpdateRequest req) {
        Invoice invoice = invoiceService.updateField(id, req.block(), req.feld(), req.wert(), req.positionIndex());
        return toDetail(invoice);
    }

    /** Fuegt eine leere Position hinzu (Pflichtfelder rot, manuell auszufuellen). */
    @PostMapping("/{id}/positionen")
    public InvoiceDtos.InvoiceDetail addPosition(@PathVariable UUID id) {
        return toDetail(invoiceService.addPosition(id));
    }

    /** Entfernt die Position am (0-basierten) Index. */
    @DeleteMapping("/{id}/positionen/{index}")
    public InvoiceDtos.InvoiceDetail removePosition(@PathVariable UUID id, @PathVariable int index) {
        return toDetail(invoiceService.removePosition(id, index));
    }

    /** Erzeugt und validiert die XRechnung ("XRechnung erzeugen"). */
    @PostMapping("/{id}/generate")
    public InvoiceDtos.GenerateResponse generate(@PathVariable UUID id) {
        InvoiceService.GenerateOutcome outcome = invoiceService.generate(id);
        List<InvoiceDtos.Issue> issues = outcome.issues().stream()
                .map(i -> new InvoiceDtos.Issue(i.feld(), i.meldung())).toList();
        return new InvoiceDtos.GenerateResponse(outcome.valid(), issues);
    }

    /** Löscht eine Rechnung samt Dateien und Audit-Einträgen. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        invoiceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Gibt eine validierte Rechnung wieder zur Bearbeitung frei. */
    @PostMapping("/{id}/reopen")
    public InvoiceDtos.InvoiceDetail reopen(@PathVariable UUID id) {
        return toDetail(invoiceService.reopen(id));
    }

    /** Übernimmt die aktuellen Firmendaten in die Verkäufer-Felder der Rechnung. */
    @PostMapping("/{id}/verkaeufer-uebernehmen")
    public InvoiceDtos.InvoiceDetail uebernehmeFirma(@PathVariable UUID id) {
        return toDetail(invoiceService.uebernehmeFirma(id));
    }

    /** Export der validierten XRechnung (UBL). 409, wenn nicht validiert. */
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable UUID id) {
        Invoice invoice = invoiceService.get(id);
        byte[] xml = invoiceService.export(id);
        String filename = "rechnung-" +
                (invoice.getRechnungsnummer() != null ? invoice.getRechnungsnummer() : id) + ".xml";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).contentType(MediaType.APPLICATION_XML).body(xml);
    }

    /** ZUGFeRD-Hybrid-PDF (XRechnung eingebettet ins Original-PDF). 409 ohne PDF/vor Prüfung. */
    @GetMapping("/{id}/zugferd")
    public ResponseEntity<byte[]> zugferd(@PathVariable UUID id) {
        Invoice invoice = invoiceService.get(id);
        byte[] pdf = invoiceService.zugferd(id);
        String filename = "rechnung-" +
                (invoice.getRechnungsnummer() != null ? invoice.getRechnungsnummer() : id) + "-zugferd.pdf";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).contentType(MediaType.APPLICATION_PDF).body(pdf);
    }

    @GetMapping("/{id}/validation-report")
    public ResponseEntity<String> validationReport(@PathVariable UUID id) {
        Invoice invoice = invoiceService.get(id);
        String report = invoice.getValidationReport();
        if (report == null) {
            throw new ApiExceptions.NotFound("Fuer diese Rechnung liegt noch kein Validierungsbericht vor.");
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(report);
    }

    /** Menschenlesbare Zusammenfassung des Validierungsberichts (für die Anzeige). */
    @GetMapping("/{id}/validation-report/summary")
    public io.senapsa.erechnung.webapp.service.ValidationReportSummarizer.Summary validationReportSummary(
            @PathVariable UUID id) {
        Invoice invoice = invoiceService.get(id);
        if (invoice.getValidationReport() == null) {
            throw new ApiExceptions.NotFound("Fuer diese Rechnung liegt noch kein Validierungsbericht vor.");
        }
        return reportSummarizer.summarize(invoice);
    }

    private InvoiceDtos.InvoiceDetail toDetail(Invoice invoice) {
        var model = invoice.getModelJson() == null ? null : modelJson.parse(invoice.getModelJson());
        boolean exportReady = false;
        List<InvoiceDtos.Issue> issues = List.of();
        if (model != null) {
            FachpruefungService.Report report = invoiceService.check(invoice);
            exportReady = report.exportReady();
            issues = report.issues().stream()
                    .map(i -> new InvoiceDtos.Issue(i.feld(), i.meldung())).toList();
        }
        return new InvoiceDtos.InvoiceDetail(invoice.getId().toString(), invoice.getStatus().name(),
                invoice.isNeedsOcr(), invoice.isValid(), invoice.getPdfFilename(), invoice.getCsvFilename(),
                model, exportReady, issues, invoice.getValidationReport() != null);
    }
}
