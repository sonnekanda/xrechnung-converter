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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    /**
     * Mehrfach-Upload: beliebig viele PDF-/CSV-Dateien. Zusammengehörige Dateien
     * werden über den gleichen Basisnamen zu je einer Rechnung gruppiert
     * (400009.pdf + 400009.csv -> eine Rechnung).
     */
    @Transactional
    public List<Invoice> handleMultiUpload(List<MultipartFile> files) {
        List<MultipartFile> nonEmpty = files == null ? List.of()
                : files.stream().filter(f -> !isEmpty(f)).toList();
        if (nonEmpty.isEmpty()) {
            throw new ApiExceptions.BadRequest("Bitte wählen Sie mindestens eine PDF- oder CSV-Datei.");
        }

        // Basisname -> [pdf, csv]; Reihenfolge stabil halten.
        Map<String, MultipartFile[]> groups = new LinkedHashMap<>();
        for (MultipartFile f : nonEmpty) {
            String name = f.getOriginalFilename() == null ? "" : f.getOriginalFilename();
            String lower = name.toLowerCase(Locale.ROOT);
            boolean pdf = lower.endsWith(".pdf");
            boolean csv = lower.endsWith(".csv");
            if (!pdf && !csv) {
                throw new ApiExceptions.BadRequest("Es sind nur PDF- und CSV-Dateien erlaubt: " + name);
            }
            if (f.getSize() > maxBytes) {
                throw new ApiExceptions.BadRequest("Die Datei \"" + name + "\" ist zu groß.");
            }
            String base = name.substring(0, name.lastIndexOf('.') < 0 ? name.length() : name.lastIndexOf('.'));
            MultipartFile[] g = groups.computeIfAbsent(base, k -> new MultipartFile[2]);
            g[pdf ? 0 : 1] = f;
        }

        List<Invoice> created = new ArrayList<>();
        try {
            for (MultipartFile[] g : groups.values()) {
                created.add(ingest(nameOf(g[0]), bytesOf(g[0]), nameOf(g[1]), bytesOf(g[1]), "UPLOAD", null));
            }
        } catch (IOException e) {
            throw new ApiExceptions.BadRequest("Eine Datei konnte nicht gespeichert werden. Bitte erneut versuchen.");
        }
        return created;
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
