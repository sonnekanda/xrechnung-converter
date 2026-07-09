package io.senapsa.erechnung.webapp.web;

import io.senapsa.erechnung.webapp.domain.Invoice;
import io.senapsa.erechnung.webapp.service.InvoiceService;
import io.senapsa.erechnung.webapp.service.UploadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final UploadService uploadService;
    private final InvoiceService invoiceService;

    public FileController(UploadService uploadService, InvoiceService invoiceService) {
        this.uploadService = uploadService;
        this.invoiceService = invoiceService;
    }

    /** Upload von PDF und/oder CSV (einzeln oder kombiniert). */
    @PostMapping("/upload")
    public ResponseEntity<InvoiceDtos.StatusResponse> upload(
            @RequestParam(value = "pdf", required = false) MultipartFile pdf,
            @RequestParam(value = "csv", required = false) MultipartFile csv) {
        Invoice invoice = uploadService.handleUpload(pdf, csv);
        return ResponseEntity.ok(new InvoiceDtos.StatusResponse(
                invoice.getId().toString(), invoice.getStatus().name(), invoice.isNeedsOcr()));
    }

    @GetMapping("/{id}/status")
    public InvoiceDtos.StatusResponse status(@PathVariable UUID id) {
        Invoice invoice = invoiceService.get(id);
        return new InvoiceDtos.StatusResponse(
                invoice.getId().toString(), invoice.getStatus().name(), invoice.isNeedsOcr());
    }
}
