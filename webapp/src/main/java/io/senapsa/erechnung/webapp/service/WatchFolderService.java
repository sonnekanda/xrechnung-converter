package io.senapsa.erechnung.webapp.service;

import io.senapsa.erechnung.webapp.domain.Invoice;
import io.senapsa.erechnung.webapp.domain.WatchProcessed;
import io.senapsa.erechnung.webapp.repo.WatchProcessedRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Ordner-Automatik (A-16, "Kann"): überwacht den Eingangsordner im konfigurierbaren
 * Intervall und verarbeitet neue Dateien über EXAKT denselben Weg wie ein Upload
 * (kein Sonderpfad). PDF+CSV werden über den gleichen Dateinamen zugeordnet.
 *
 * Nur vollständig validierte Rechnungen werden automatisch als XRechnung in den
 * Ausgangsordner exportiert; unsichere/unvollständige landen im normalen
 * Prüfbildschirm. Verarbeitete Eingangsdateien werden nach erledigt/fehlerhaft
 * verschoben; identische Wiederablage führt (per Inhalts-Hash) nicht zu
 * Doppelverarbeitung.
 */
@Service
public class WatchFolderService {

    private static final Logger log = LoggerFactory.getLogger(WatchFolderService.class);

    private final boolean enabled;
    private final Path inputDir;
    private final Path outputDir;
    private final UploadService uploadService;
    private final InvoiceService invoiceService;
    private final WatchProcessedRepository processed;
    private final AuditService audit;

    public WatchFolderService(@Value("${erechnung.watch.enabled:false}") boolean enabled,
                              @Value("${erechnung.watch.input-dir:/data/watch/in}") String inputDir,
                              @Value("${erechnung.watch.output-dir:/data/watch/out}") String outputDir,
                              UploadService uploadService, InvoiceService invoiceService,
                              WatchProcessedRepository processed, AuditService audit) {
        this.enabled = enabled;
        this.inputDir = Path.of(inputDir);
        this.outputDir = Path.of(outputDir);
        this.uploadService = uploadService;
        this.invoiceService = invoiceService;
        this.processed = processed;
        this.audit = audit;
    }

    /** Zyklischer Scan des Eingangsordners. Intervall aus der .env (Sekunden). */
    @Scheduled(fixedDelayString = "${erechnung.watch.interval-seconds:60}000",
            initialDelayString = "${erechnung.watch.interval-seconds:60}000")
    public void scan() {
        if (!enabled) {
            return;
        }
        try {
            Files.createDirectories(inputDir);
            Files.createDirectories(outputDir);
            Files.createDirectories(inputDir.resolve(".done"));
            Files.createDirectories(inputDir.resolve(".error"));

            // Dateien nach Basisnamen gruppieren (PDF+CSV über gleichen Namen).
            Map<String, String[]> groups = new TreeMap<>(); // base -> [pdfName, csvName]
            try (Stream<Path> files = Files.list(inputDir)) {
                files.filter(Files::isRegularFile).forEach(p -> {
                    String name = p.getFileName().toString();
                    String lower = name.toLowerCase(Locale.ROOT);
                    String base = name.substring(0, name.lastIndexOf('.') < 0 ? name.length() : name.lastIndexOf('.'));
                    String[] g = groups.computeIfAbsent(base, k -> new String[2]);
                    if (lower.endsWith(".pdf")) {
                        g[0] = name;
                    } else if (lower.endsWith(".csv")) {
                        g[1] = name;
                    }
                });
            }
            for (var entry : groups.entrySet()) {
                if (entry.getValue()[0] != null || entry.getValue()[1] != null) {
                    ingestGroup(entry.getKey(), entry.getValue()[0], entry.getValue()[1]);
                }
            }
        } catch (IOException e) {
            log.error("Ordner-Automatik: Scan fehlgeschlagen", e);
        }
    }

    private void ingestGroup(String base, String pdfName, String csvName) {
        try {
            byte[] pdf = pdfName != null ? Files.readAllBytes(inputDir.resolve(pdfName)) : null;
            byte[] csv = csvName != null ? Files.readAllBytes(inputDir.resolve(csvName)) : null;
            String hash = sha256(pdf, csv);
            if (processed.existsByContentHash(hash)) {
                // Identische Datei bereits verarbeitet -> nicht erneut verarbeiten,
                // aber aus dem Eingang räumen.
                moveToDone(base);
                return;
            }
            processed.save(new WatchProcessed(hash, base));
            Invoice invoice = uploadService.ingest(pdfName, pdf, csvName, csv, "WATCH", base);
            log.info("Ordner-Automatik: {} eingelesen als Rechnung {}", base, invoice.getId());
        } catch (Exception e) {
            log.error("Ordner-Automatik: Verarbeitung von {} fehlgeschlagen", base, e);
            moveToError(base);
        }
    }

    /**
     * Nach abgeschlossener Extraktion aufgerufen (aus dem Queue-Ergebnis) für
     * Rechnungen aus der Ordner-Automatik: nur bei vollständiger Gültigkeit
     * automatisch exportieren, sonst zur manuellen Prüfung belassen.
     */
    public void autoProcess(Invoice invoice) {
        String base = invoice.getWatchBasename();
        try {
            FachpruefungService.Report report = invoiceService.check(invoice);
            if (!report.exportReady()) {
                audit.log(invoice.getId(), "AUTO_PRUEFUNG_OFFEN",
                        report.issues().size() + " offene Punkte - manuelle Prüfung nötig");
                moveToError(base);
                return; // bleibt im Prüfbildschirm
            }
            InvoiceService.GenerateOutcome outcome = invoiceService.generate(invoice.getId());
            if (!outcome.valid()) {
                moveToError(base);
                return;
            }
            byte[] xml = invoiceService.export(invoice.getId());
            // Frisch laden: Validierungsbericht wurde in eigener Transaktion gesetzt.
            writeOutput(invoiceService.get(invoice.getId()), xml);
            moveToDone(base);
            log.info("Ordner-Automatik: Rechnung {} automatisch exportiert", invoice.getRechnungsnummer());
        } catch (Exception e) {
            log.error("Ordner-Automatik: Auto-Export für {} fehlgeschlagen", base, e);
            moveToError(base);
        }
    }

    private void writeOutput(Invoice invoice, byte[] xml) throws IOException {
        String name = invoice.getRechnungsnummer() != null ? invoice.getRechnungsnummer()
                : invoice.getId().toString();
        Files.write(outputDir.resolve("rechnung-" + name + ".xml"), xml);
        if (invoice.getValidationReport() != null) {
            Files.writeString(outputDir.resolve("rechnung-" + name + "-bericht.xml"),
                    invoice.getValidationReport());
        }
    }

    private void moveToDone(String base) {
        moveSources(base, ".done");
    }

    private void moveToError(String base) {
        moveSources(base, ".error");
    }

    private void moveSources(String base, String targetSub) {
        if (base == null) {
            return;
        }
        for (String ext : new String[]{".pdf", ".csv"}) {
            Path src = inputDir.resolve(base + ext);
            if (Files.exists(src)) {
                try {
                    Files.move(src, inputDir.resolve(targetSub).resolve(base + ext),
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    log.warn("Ordner-Automatik: {} konnte nicht nach {} verschoben werden", src, targetSub);
                }
            }
        }
    }

    private static String sha256(byte[] pdf, byte[] csv) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            if (pdf != null) {
                md.update(pdf);
            }
            if (csv != null) {
                md.update(csv);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
