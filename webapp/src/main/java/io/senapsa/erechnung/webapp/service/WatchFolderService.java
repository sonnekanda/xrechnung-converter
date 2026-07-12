package io.senapsa.erechnung.webapp.service;

import io.senapsa.erechnung.webapp.domain.AppSettings;
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
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Ordner-Automatik (A-16): überwacht den Eingangsordner und verarbeitet neue
 * Dateien über EXAKT denselben Weg wie ein Upload (kein Sonderpfad). Alle
 * Parameter (an/aus, Intervall, Unterordner, Settle-Zeit, Auto-Bestätigen)
 * kommen zur Laufzeit aus den {@link AppSettings} und sind im Admin-Bereich
 * ohne Neustart änderbar. Basis-Mountpfade stammen aus der .env.
 *
 * PDF+CSV werden über den gleichen Basisnamen zugeordnet; eine Gruppe wird erst
 * verarbeitet, wenn ihre Dateien „stabil" sind (Settle-Zeit) – so gehen zeitlich
 * versetzt geschriebene PDF/CSV-Paare nicht auseinander. Nur vollständig gültige
 * Rechnungen werden automatisch exportiert; unsichere landen im Prüfbildschirm.
 */
@Service
public class WatchFolderService {

    private static final Logger log = LoggerFactory.getLogger(WatchFolderService.class);
    private static final long TICK_MS = 3000; // Basis-Takt; Intervall regelt die Einstellung

    private final Path baseInput;
    private final Path baseOutput;
    private final AppSettingsService settings;
    private final UploadService uploadService;
    private final InvoiceService invoiceService;
    private final WatchProcessedRepository processed;
    private final AuditService audit;

    private volatile Instant lastScan = Instant.EPOCH;

    public WatchFolderService(@Value("${erechnung.watch.input-dir:/data/watch/in}") String inputDir,
                              @Value("${erechnung.watch.output-dir:/data/watch/out}") String outputDir,
                              AppSettingsService settings, UploadService uploadService,
                              InvoiceService invoiceService, WatchProcessedRepository processed,
                              AuditService audit) {
        this.baseInput = Path.of(inputDir);
        this.baseOutput = Path.of(outputDir);
        this.settings = settings;
        this.uploadService = uploadService;
        this.invoiceService = invoiceService;
        this.processed = processed;
        this.audit = audit;
    }

    /** Basis-Takt; scannt nur, wenn aktiviert und das konfigurierte Intervall vorbei ist. */
    @Scheduled(fixedDelay = TICK_MS, initialDelay = TICK_MS)
    public void tick() {
        AppSettings s;
        try {
            s = settings.get();
        } catch (Exception e) {
            return; // z.B. während Migration/Start
        }
        if (!s.isWatchEnabled()) {
            return;
        }
        if (Instant.now().isBefore(lastScan.plusSeconds(s.getWatchIntervalSeconds()))) {
            return;
        }
        lastScan = Instant.now();
        scan(s);
    }

    private void scan(AppSettings s) {
        Path input = baseInput.resolve(s.getWatchInputSubdir());
        try {
            Files.createDirectories(input);
            Files.createDirectories(input.resolve(".processing"));
            Files.createDirectories(input.resolve(".done"));
            Files.createDirectories(input.resolve(".error"));

            Map<String, String[]> groups = new TreeMap<>(); // base -> [pdfName, csvName]
            try (Stream<Path> files = Files.list(input)) {
                files.filter(Files::isRegularFile).forEach(p -> {
                    String name = p.getFileName().toString();
                    String lower = name.toLowerCase(Locale.ROOT);
                    int dot = name.lastIndexOf('.');
                    String base = name.substring(0, dot < 0 ? name.length() : dot);
                    String[] g = groups.computeIfAbsent(base, k -> new String[2]);
                    if (lower.endsWith(".pdf")) {
                        g[0] = name;
                    } else if (lower.endsWith(".csv")) {
                        g[1] = name;
                    }
                });
            }
            for (var e : groups.entrySet()) {
                if ((e.getValue()[0] != null || e.getValue()[1] != null)
                        && isSettled(input, e.getValue(), s.getWatchSettleSeconds())) {
                    ingestGroup(input, e.getKey(), e.getValue()[0], e.getValue()[1]);
                }
            }
        } catch (IOException e) {
            log.error("Ordner-Automatik: Scan fehlgeschlagen ({})", input, e);
        }
    }

    /** Gruppe gilt als stabil, wenn ihre jüngste Datei älter als die Settle-Zeit ist. */
    private boolean isSettled(Path input, String[] group, int settleSeconds) {
        long newest = 0;
        for (String name : group) {
            if (name == null) {
                continue;
            }
            try {
                newest = Math.max(newest, Files.getLastModifiedTime(input.resolve(name)).toMillis());
            } catch (IOException ignored) {
                return false;
            }
        }
        return newest > 0 && (System.currentTimeMillis() - newest) >= settleSeconds * 1000L;
    }

    private void ingestGroup(Path input, String base, String pdfName, String csvName) {
        try {
            byte[] pdf = pdfName != null ? Files.readAllBytes(input.resolve(pdfName)) : null;
            byte[] csv = csvName != null ? Files.readAllBytes(input.resolve(csvName)) : null;
            String hash = sha256(pdf, csv);
            if (processed.existsByContentHash(hash)) {
                // Erneut abgelegtes Duplikat: räumen, aber sichtbar machen (Log + Audit).
                log.info("Ordner-Automatik: '{}' bereits verarbeitet (identischer Inhalt) - nicht erneut eingelesen, nach .done verschoben", base);
                audit.logSecurity("ORDNER_DUPLIKAT",
                        "'" + base + "' bereits verarbeitet (identischer Inhalt) - nicht erneut eingelesen, nach .done verschoben",
                        "ordner-automatik");
                move(input, input.resolve(".done"), base);
                return;
            }
            processed.save(new WatchProcessed(hash, base));
            Invoice invoice = uploadService.ingest(pdfName, pdf, csvName, csv, "WATCH", base);
            // Sofort aus dem Eingang nehmen, damit der nächste Scan die Dateien
            // nicht erneut aufgreift (das endgültige Ablegen erfolgt in autoProcess).
            move(input, input.resolve(".processing"), base);
            log.info("Ordner-Automatik: {} eingelesen als Rechnung {}", base, invoice.getId());
        } catch (Exception e) {
            log.error("Ordner-Automatik: Verarbeitung von {} fehlgeschlagen", base, e);
            move(input, input.resolve(".error"), base);
        }
    }

    /**
     * Nach abgeschlossener Extraktion aufgerufen: bei aktivem Auto-Bestätigen
     * werden gelbe Nicht-OCR-Felder bestätigt; nur vollständig gültige Rechnungen
     * werden automatisch exportiert, sonst zur manuellen Prüfung belassen.
     */
    public void autoProcess(Invoice invoice) {
        AppSettings s = settings.get();
        Path input = baseInput.resolve(s.getWatchInputSubdir());
        Path processing = input.resolve(".processing");
        String base = invoice.getWatchBasename();
        try {
            if (s.isAutoConfirmYellow()) {
                invoiceService.autoConfirmNonOcr(invoice.getId());
            }
            Invoice fresh = invoiceService.get(invoice.getId());
            FachpruefungService.Report report = invoiceService.check(fresh);
            if (!report.exportReady()) {
                audit.log(invoice.getId(), "AUTO_PRUEFUNG_OFFEN",
                        report.issues().size() + " offene Punkte - manuelle Prüfung nötig");
                move(processing, input.resolve(".error"), base);
                return;
            }
            InvoiceService.GenerateOutcome outcome = invoiceService.generate(invoice.getId());
            if (!outcome.valid()) {
                move(processing, input.resolve(".error"), base);
                return;
            }
            byte[] xml = invoiceService.export(invoice.getId());
            writeOutput(s, invoiceService.get(invoice.getId()), xml);
            move(processing, input.resolve(".done"), base);
            log.info("Ordner-Automatik: Rechnung {} automatisch exportiert", invoice.getRechnungsnummer());
        } catch (Exception e) {
            log.error("Ordner-Automatik: Auto-Export für {} fehlgeschlagen", base, e);
            move(processing, input.resolve(".error"), base);
        }
    }

    private void writeOutput(AppSettings s, Invoice invoice, byte[] xml) throws IOException {
        Path output = baseOutput.resolve(s.getWatchOutputSubdir());
        Files.createDirectories(output);
        String name = invoice.getRechnungsnummer() != null ? invoice.getRechnungsnummer()
                : invoice.getId().toString();

        String format = s.getWatchOutputFormat();
        boolean wantXml = !"ZUGFERD".equals(format);            // XRECHNUNG oder BEIDE
        boolean wantZugferd = "ZUGFERD".equals(format) || "BEIDE".equals(format);
        boolean xmlWritten = false;

        if (wantXml) {
            writeXml(output, name, invoice, xml);
            xmlWritten = true;
        }
        if (wantZugferd) {
            if (invoice.getPdfFilename() != null) {
                try {
                    byte[] pdf = invoiceService.zugferd(invoice.getId());
                    Files.write(output.resolve("rechnung-" + name + "-zugferd.pdf"), pdf);
                } catch (Exception e) {
                    // ZUGFeRD ist Best-Effort: bei Fehler wenigstens die XML sichern.
                    log.warn("Ordner-Automatik: ZUGFeRD für {} fehlgeschlagen", name, e);
                    if (!xmlWritten) {
                        writeXml(output, name, invoice, xml);
                    }
                }
            } else if (!xmlWritten) {
                // ZUGFeRD gewünscht, aber kein Original-PDF -> XML als Ausweich.
                log.info("Ordner-Automatik: {} ohne Original-PDF -> ZUGFeRD nicht möglich, XML geschrieben", name);
                writeXml(output, name, invoice, xml);
            }
        }
    }

    private void writeXml(Path output, String name, Invoice invoice, byte[] xml) throws IOException {
        Files.write(output.resolve("rechnung-" + name + ".xml"), xml);
        if (invoice.getValidationReport() != null) {
            Files.writeString(output.resolve("rechnung-" + name + "-bericht.xml"),
                    invoice.getValidationReport());
        }
    }

    /**
     * Entfernt die Rest-Dateien einer gelöschten Rechnung aus den Service-
     * Ordnern des Eingangs (.processing/.done/.error), damit sie keinen
     * Speicher belegen. Die exportierten Ausgabedateien bleiben unberührt.
     */
    public void deleteInputTails(String base) {
        if (base == null) {
            return;
        }
        AppSettings s;
        try {
            s = settings.get();
        } catch (Exception e) {
            return;
        }
        Path input = baseInput.resolve(s.getWatchInputSubdir());
        for (String sub : new String[]{".processing", ".done", ".error"}) {
            for (String ext : new String[]{".pdf", ".csv"}) {
                Path f = input.resolve(sub).resolve(base + ext);
                try {
                    Files.deleteIfExists(f);
                } catch (IOException e) {
                    log.warn("Ordner-Automatik: Rest-Datei {} konnte nicht gelöscht werden", f);
                }
            }
        }
    }

    /** Verschiebt die zu {@code base} gehörenden Dateien (.pdf/.csv) von einem
     *  Verzeichnis in ein anderes. */
    private void move(Path fromDir, Path toDir, String base) {
        if (base == null) {
            return;
        }
        for (String ext : new String[]{".pdf", ".csv"}) {
            Path src = fromDir.resolve(base + ext);
            if (Files.exists(src)) {
                try {
                    Files.createDirectories(toDir);
                    Files.move(src, toDir.resolve(base + ext), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    log.warn("Ordner-Automatik: {} konnte nicht nach {} verschoben werden", src, toDir);
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
