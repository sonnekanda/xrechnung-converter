package io.senapsa.erechnung.webapp.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.senapsa.erechnung.webapp.domain.Invoice;
import io.senapsa.erechnung.webapp.domain.InvoiceStatus;
import io.senapsa.erechnung.webapp.repo.InvoiceRepository;
import io.senapsa.erechnung.webapp.web.ApiExceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** Orchestriert Statusmaschine, Generierung, Validierung und Export-Freigabe. */
@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final InvoiceRepository invoices;
    private final ModelJson modelJson;
    private final FachpruefungService fachpruefung;
    private final GenerationService generation;
    private final AuditService audit;

    public InvoiceService(InvoiceRepository invoices, ModelJson modelJson,
                          FachpruefungService fachpruefung, GenerationService generation,
                          AuditService audit) {
        this.invoices = invoices;
        this.modelJson = modelJson;
        this.fachpruefung = fachpruefung;
        this.generation = generation;
        this.audit = audit;
    }

    public Invoice get(UUID id) {
        return invoices.findById(id)
                .orElseThrow(() -> new ApiExceptions.NotFound("Rechnung wurde nicht gefunden."));
    }

    public List<Invoice> list() {
        return invoices.findAllByOrderByCreatedAtDesc();
    }

    /** Fachpruefung ohne Zustandsaenderung (fuer den Pruefbildschirm/Ampel). */
    public FachpruefungService.Report check(Invoice invoice) {
        return fachpruefung.pruefe(modelJson.parse(invoice.getModelJson()));
    }

    /**
     * Korrektur/Bestaetigung eines Feldes im Pruefbildschirm (herkunft = manuell).
     * Eine Aenderung nach der Validierung verwirft die erzeugte XRechnung und
     * setzt die Rechnung auf "pruefen" zurueck - so kann nie eine XML exportiert
     * werden, die nicht zu den aktuellen Daten passt.
     */
    @Transactional
    public Invoice updateField(UUID id, String block, String field, String wert, Integer positionIndex) {
        Invoice invoice = get(id);
        if (invoice.getStatus() == InvoiceStatus.EXPORTIERT) {
            throw new ApiExceptions.Conflict(
                    "Diese Rechnung wurde bereits exportiert und kann nicht mehr geaendert werden.");
        }
        if (invoice.getStatus() != InvoiceStatus.PRUEFEN && invoice.getStatus() != InvoiceStatus.VALIDIERT) {
            throw new ApiExceptions.Conflict("In diesem Bearbeitungsschritt ist die Aenderung nicht moeglich.");
        }
        ObjectNode model = modelJson.parse(invoice.getModelJson());
        if ("position".equals(block) && positionIndex != null) {
            modelJson.setPosition(model, positionIndex, field, wert, "manuell", "sicher");
        } else {
            modelJson.set(model, block, field, wert, "manuell", "sicher");
        }
        invoice.setModelJson(modelJson.write(model));

        if (invoice.getStatus() == InvoiceStatus.VALIDIERT) {
            invoice.setStatus(InvoiceStatus.PRUEFEN);
            invoice.setValid(false);
            invoice.setUblXmlBase64(null);
            invoice.setValidationReport(null);
            audit.log(id, "AENDERUNG_NACH_VALIDIERUNG",
                    "Erzeugte XRechnung verworfen - erneute Erzeugung erforderlich");
        }
        invoice.touch();
        String pos = positionIndex != null ? "[" + (positionIndex + 1) + "]" : "";
        audit.log(id, "FELD_KORRIGIERT", block + pos + "." + field + " = " + wert);
        return invoices.save(invoice);
    }

    /**
     * Erzeugt und validiert die XRechnung. Zwei Gates: (1) Fachpruefung (alle
     * Pflichtfelder gruen, Summen stimmen), (2) amtliche Validierung der Engine.
     * Nur bei Erfolg beider -> Status VALIDIERT.
     */
    @Transactional
    public GenerateOutcome generate(UUID id) {
        Invoice invoice = get(id);
        if (invoice.getStatus() != InvoiceStatus.PRUEFEN && invoice.getStatus() != InvoiceStatus.VALIDIERT) {
            throw new ApiExceptions.Conflict("Die Rechnung ist noch nicht bereit zur Erzeugung.");
        }
        ObjectNode model = modelJson.parse(invoice.getModelJson());

        FachpruefungService.Report report = fachpruefung.pruefe(model);
        if (!report.exportReady()) {
            transition(invoice, InvoiceStatus.PRUEFEN);
            invoices.save(invoice);
            audit.log(id, "FACHPRUEFUNG_OFFEN", report.issues().size() + " offene Punkte");
            return new GenerateOutcome(false, report.issues(), null);
        }

        GenerationService.EngineResult engine = generation.generateAndValidate(model);
        invoice.setUblXmlBase64(engine.xmlBase64());
        invoice.setValidationReport(engine.report());
        invoice.setValid(engine.valid());

        if (engine.valid()) {
            transition(invoice, InvoiceStatus.VALIDIERT);
            audit.log(id, "VALIDIERT", "XRechnung UBL erfolgreich erzeugt und validiert");
        } else {
            transition(invoice, InvoiceStatus.PRUEFEN);
            audit.log(id, "VALIDIERUNG_FEHLGESCHLAGEN", "Engine meldete Fehler");
        }
        invoices.save(invoice);
        List<FachpruefungService.Issue> engineIssues = engine.valid() ? List.of()
                : List.of(new FachpruefungService.Issue("validierung",
                "Die amtliche Pruefung ist fehlgeschlagen. Details finden Sie im Validierungsbericht."));
        return new GenerateOutcome(engine.valid(), engineIssues, engine.report());
    }

    /**
     * Liefert die validierte XRechnung-XML. Voraussetzung: Status VALIDIERT
     * (Erst-Download setzt auf EXPORTIERT) oder bereits EXPORTIERT (wiederholter
     * Download). Vor der Validierung -> 409.
     */
    @Transactional
    public byte[] export(UUID id) {
        Invoice invoice = get(id);
        boolean downloadbar = invoice.getStatus() == InvoiceStatus.VALIDIERT
                || invoice.getStatus() == InvoiceStatus.EXPORTIERT;
        if (!downloadbar || invoice.getUblXmlBase64() == null) {
            throw new ApiExceptions.Conflict(
                    "Diese Rechnung ist noch nicht validiert und kann daher nicht exportiert werden.");
        }
        byte[] xml = Base64.getDecoder().decode(invoice.getUblXmlBase64());
        if (invoice.getStatus() == InvoiceStatus.VALIDIERT) {
            transition(invoice, InvoiceStatus.EXPORTIERT);
            invoices.save(invoice);
            audit.log(id, "EXPORTIERT", "XRechnung heruntergeladen");
        }
        return xml;
    }

    private void transition(Invoice invoice, InvoiceStatus target) {
        if (invoice.getStatus() != target && !invoice.getStatus().canTransitionTo(target)) {
            throw new ApiExceptions.Conflict("Unzulaessiger Statuswechsel.");
        }
        invoice.setStatus(target);
        invoice.touch();
    }

    public record GenerateOutcome(boolean valid, List<FachpruefungService.Issue> issues, String report) {
    }
}
