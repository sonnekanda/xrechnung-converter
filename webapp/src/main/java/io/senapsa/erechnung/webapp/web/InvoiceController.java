package io.senapsa.erechnung.webapp.web;

import io.senapsa.erechnung.webapp.domain.Invoice;
import io.senapsa.erechnung.webapp.service.FachpruefungService;
import io.senapsa.erechnung.webapp.service.InvoiceService;
import io.senapsa.erechnung.webapp.service.ModelJson;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    public InvoiceController(InvoiceService invoiceService, ModelJson modelJson) {
        this.invoiceService = invoiceService;
        this.modelJson = modelJson;
    }

    @GetMapping
    public List<InvoiceDtos.InvoiceSummary> list() {
        return invoiceService.list().stream().map(InvoiceDtos.InvoiceSummary::of).toList();
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

    /** Erzeugt und validiert die XRechnung ("XRechnung erzeugen"). */
    @PostMapping("/{id}/generate")
    public InvoiceDtos.GenerateResponse generate(@PathVariable UUID id) {
        InvoiceService.GenerateOutcome outcome = invoiceService.generate(id);
        List<InvoiceDtos.Issue> issues = outcome.issues().stream()
                .map(i -> new InvoiceDtos.Issue(i.feld(), i.meldung())).toList();
        return new InvoiceDtos.GenerateResponse(outcome.valid(), issues);
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

    @GetMapping("/{id}/validation-report")
    public ResponseEntity<String> validationReport(@PathVariable UUID id) {
        Invoice invoice = invoiceService.get(id);
        String report = invoice.getValidationReport();
        if (report == null) {
            throw new ApiExceptions.NotFound("Fuer diese Rechnung liegt noch kein Validierungsbericht vor.");
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(report);
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
                model, exportReady, issues);
    }
}
