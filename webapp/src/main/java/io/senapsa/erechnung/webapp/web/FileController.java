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

import java.util.ArrayList;
import java.util.List;
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

    /**
     * Upload von PDF und/oder CSV, einzeln oder als Mehrfach-Upload. Dateien mit
     * gleichem Basisnamen (400009.pdf + 400009.csv) werden je Rechnung gruppiert;
     * es wird eine Statuszeile je entstandener Rechnung zurückgegeben.
     */
    @PostMapping("/upload")
    public ResponseEntity<List<InvoiceDtos.StatusResponse>> upload(
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "pdf", required = false) MultipartFile pdf,
            @RequestParam(value = "csv", required = false) MultipartFile csv) {
        List<MultipartFile> all = new ArrayList<>();
        if (files != null) {
            all.addAll(files);
        }
        if (pdf != null) {
            all.add(pdf);
        }
        if (csv != null) {
            all.add(csv);
        }
        List<InvoiceDtos.StatusResponse> result = uploadService.handleMultiUpload(all).stream()
                .map(i -> new InvoiceDtos.StatusResponse(i.getId().toString(), i.getStatus().name(),
                        i.isNeedsOcr(), i.getPdfFilename(), i.getCsvFilename()))
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/status")
    public InvoiceDtos.StatusResponse status(@PathVariable UUID id) {
        Invoice invoice = invoiceService.get(id);
        return new InvoiceDtos.StatusResponse(invoice.getId().toString(), invoice.getStatus().name(),
                invoice.isNeedsOcr(), invoice.getPdfFilename(), invoice.getCsvFilename());
    }
}
