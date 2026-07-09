package io.senapsa.erechnung.webapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.senapsa.erechnung.webapp.domain.Invoice;
import io.senapsa.erechnung.webapp.domain.InvoiceStatus;
import io.senapsa.erechnung.webapp.repo.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Asynchrone Extraktion ueber die Redis-Job-Queue. Die webapp legt Jobs in
 * {@code q:extraction}; der extraction-worker schreibt Ergebnisse nach
 * {@code result:{id}} und meldet fertige IDs in {@code q:extraction:done}.
 * Ein Poller uebernimmt die Ergebnisse in die Statusmaschine.
 */
@Service
public class ExtractionQueueService {

    private static final Logger log = LoggerFactory.getLogger(ExtractionQueueService.class);
    private static final String Q_IN = "q:extraction";
    private static final String Q_DONE = "q:extraction:done";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final InvoiceRepository invoices;
    private final StammdatenAutofillService autofill;
    private final ModelJson modelJson;
    private final AuditService audit;

    public ExtractionQueueService(StringRedisTemplate redis, ObjectMapper mapper,
                                  InvoiceRepository invoices, StammdatenAutofillService autofill,
                                  ModelJson modelJson, AuditService audit) {
        this.redis = redis;
        this.mapper = mapper;
        this.invoices = invoices;
        this.autofill = autofill;
        this.modelJson = modelJson;
        this.audit = audit;
    }

    public void enqueue(Invoice invoice, String pdfPath, String csvPath) {
        try {
            String payload = mapper.writeValueAsString(Map.of(
                    "invoice_id", invoice.getId().toString(),
                    "pdf_path", pdfPath == null ? "" : pdfPath,
                    "csv_path", csvPath == null ? "" : csvPath));
            redis.opsForList().leftPush(Q_IN, payload);
            audit.log(invoice.getId(), "EXTRAKTION_EINGEREIHT", "Job in Queue gelegt");
        } catch (Exception e) {
            throw new IllegalStateException("Extraktionsjob konnte nicht eingereiht werden", e);
        }
    }

    /** Uebernimmt fertige Extraktionsergebnisse (kurzes Poll-Intervall). */
    @Scheduled(fixedDelayString = "${erechnung.extraction-poll-ms:1500}")
    public void collectResults() {
        String invoiceId;
        while ((invoiceId = redis.opsForList().rightPop(Q_DONE)) != null) {
            handleResult(invoiceId);
        }
    }

    private void handleResult(String invoiceId) {
        String resultJson = redis.opsForValue().get("result:" + invoiceId);
        if (resultJson == null) {
            log.warn("Kein Ergebnis fuer Rechnung {}", invoiceId);
            return;
        }
        Invoice invoice = invoices.findById(UUID.fromString(invoiceId)).orElse(null);
        if (invoice == null) {
            return;
        }
        try {
            JsonNode result = mapper.readTree(resultJson);
            boolean needsOcr = result.path("needs_ocr").asBoolean(false);
            JsonNode model = result.path("model");
            invoice.setNeedsOcr(needsOcr);
            if (model.isObject()) {
                var obj = (com.fasterxml.jackson.databind.node.ObjectNode) model;
                autofill.apply(obj);
                invoice.setModelJson(modelJson.write(obj));
                invoice.setRechnungsnummer(modelJson.wert(obj, "kopf", "rechnungsnummer"));
                invoice.setDokumentart(obj.path("dokumentart").asText(null));
                invoice.setMappingVersion(obj.path("mapping_version").asText(null));
            }
            invoice.setStatus(InvoiceStatus.PRUEFEN);
            invoice.touch();
            invoices.save(invoice);
            audit.log(invoice.getId(), "EXTRAKTION_FERTIG",
                    "needs_ocr=" + needsOcr + ", zur Pruefung bereit");
        } catch (Exception e) {
            log.error("Ergebnis fuer Rechnung {} konnte nicht verarbeitet werden", invoiceId, e);
        }
    }
}
