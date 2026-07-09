package io.senapsa.erechnung.webapp.service;

import io.senapsa.erechnung.webapp.domain.Invoice;
import io.senapsa.erechnung.webapp.domain.InvoiceStatus;
import io.senapsa.erechnung.webapp.repo.InvoiceRepository;
import io.senapsa.erechnung.webapp.web.ApiExceptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Nimmt Uploads entgegen (PDF und/oder CSV), prueft Typ/Groesse, legt die
 * Rechnung an und reiht die Extraktion in die Queue. Dateien liegen unter /data.
 */
@Service
public class UploadService {

    private final InvoiceRepository invoices;
    private final ExtractionQueueService queue;
    private final AuditService audit;
    private final Path dataDir;
    private final long maxBytes;

    public UploadService(InvoiceRepository invoices, ExtractionQueueService queue, AuditService audit,
                         @Value("${erechnung.data-dir:/data}") String dataDir,
                         @Value("${erechnung.max-upload-mb:25}") long maxUploadMb) {
        this.invoices = invoices;
        this.queue = queue;
        this.audit = audit;
        this.dataDir = Path.of(dataDir, "uploads");
        this.maxBytes = maxUploadMb * 1024 * 1024;
    }

    @Transactional
    public Invoice handleUpload(MultipartFile pdf, MultipartFile csv) {
        if (isEmpty(pdf) && isEmpty(csv)) {
            throw new ApiExceptions.BadRequest("Bitte laden Sie mindestens eine PDF- oder CSV-Datei hoch.");
        }
        validate(pdf, ".pdf", "PDF");
        validate(csv, ".csv", "CSV");
        try {
            return ingest(nameOf(pdf), bytesOf(pdf), nameOf(csv), bytesOf(csv), "UPLOAD", null);
        } catch (IOException e) {
            throw new ApiExceptions.BadRequest("Die Datei konnte nicht gespeichert werden. Bitte erneut versuchen.");
        }
    }

    /**
     * Gemeinsamer Verarbeitungspfad fuer Upload und Ordner-Automatik: legt die
     * Rechnung an, speichert die Dateien unter /data und reiht die Extraktion in
     * dieselbe Queue ein. Kein Sonderpfad je Quelle.
     */
    @Transactional
    public Invoice ingest(String pdfName, byte[] pdfBytes, String csvName, byte[] csvBytes,
                          String source, String watchBasename) throws IOException {
        Invoice invoice = new Invoice();
        invoice.setStatus(InvoiceStatus.HOCHGELADEN);
        invoice.setSource(source);
        invoice.setWatchBasename(watchBasename);
        invoice = invoices.save(invoice);

        Path dir = dataDir.resolve(invoice.getId().toString());
        Files.createDirectories(dir);
        String pdfPath = writeFile(dir, pdfName, pdfBytes);
        String csvPath = writeFile(dir, csvName, csvBytes);
        if (pdfPath != null) {
            invoice.setPdfFilename(pdfName);
        }
        if (csvPath != null) {
            invoice.setCsvFilename(csvName);
        }
        invoice.setStatus(InvoiceStatus.IN_VERARBEITUNG);
        invoice.touch();
        invoices.save(invoice);
        audit.log(invoice.getId(), "HOCHGELADEN",
                "quelle=" + source + ", pdf=" + pdfName + ", csv=" + csvName);
        queue.enqueue(invoice, pdfPath, csvPath);
        return invoice;
    }

    private String writeFile(Path dir, String name, byte[] bytes) throws IOException {
        if (name == null || bytes == null || bytes.length == 0) {
            return null;
        }
        Path target = dir.resolve(name);
        Files.write(target, bytes);
        return target.toString();
    }

    private static String nameOf(MultipartFile f) {
        return isEmpty(f) ? null : f.getOriginalFilename();
    }

    private static byte[] bytesOf(MultipartFile f) throws IOException {
        return isEmpty(f) ? null : f.getBytes();
    }

    private void validate(MultipartFile file, String suffix, String label) {
        if (isEmpty(file)) {
            return;
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(suffix)) {
            throw new ApiExceptions.BadRequest("Es sind nur PDF- und CSV-Dateien erlaubt (" + label + " ungueltig).");
        }
        if (file.getSize() > maxBytes) {
            throw new ApiExceptions.BadRequest("Die Datei ist zu gross. Bitte bleiben Sie unter dem erlaubten Limit.");
        }
    }

    private static boolean isEmpty(MultipartFile f) {
        return f == null || f.isEmpty();
    }
}
