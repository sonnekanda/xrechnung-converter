package io.senapsa.erechnung.webapp.web;

import io.senapsa.erechnung.webapp.domain.Invoice;
import io.senapsa.erechnung.webapp.service.InvoiceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Liefert das Original-PDF fuer den Pruefbildschirm (Anzeige links). */
@RestController
@RequestMapping("/api/v1/invoices")
public class DocumentController {

    private final InvoiceService invoiceService;
    private final Path uploadsDir;

    public DocumentController(InvoiceService invoiceService,
                              @Value("${erechnung.data-dir:/data}") String dataDir) {
        this.invoiceService = invoiceService;
        this.uploadsDir = Path.of(dataDir, "uploads");
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<Resource> pdf(@PathVariable UUID id) {
        Invoice invoice = invoiceService.get(id);
        if (invoice.getPdfFilename() == null) {
            throw new ApiExceptions.NotFound("Zu dieser Rechnung liegt kein PDF vor.");
        }
        Path file = uploadsDir.resolve(id.toString()).resolve(invoice.getPdfFilename());
        if (!Files.exists(file)) {
            throw new ApiExceptions.NotFound("Die PDF-Datei wurde nicht gefunden.");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .body(new PathResource(file));
    }
}
