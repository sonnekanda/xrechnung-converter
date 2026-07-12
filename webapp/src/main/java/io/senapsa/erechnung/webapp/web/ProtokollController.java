package io.senapsa.erechnung.webapp.web;

import io.senapsa.erechnung.webapp.domain.AuditEvent;
import io.senapsa.erechnung.webapp.domain.Invoice;
import io.senapsa.erechnung.webapp.repo.AuditEventRepository;
import io.senapsa.erechnung.webapp.repo.InvoiceRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Protokoll-Ansicht (Audit-Log) für den Admin-Bereich: die jüngsten Ereignisse
 * über alle Rechnungen und Systemvorgänge (Anmeldungen, Ordner-Automatik,
 * Löschungen). Beantwortet die Betreiber-Frage „was ist passiert?" ohne
 * Datenbank-Zugriff.
 */
@RestController
@RequestMapping("/api/v1/admin/protokoll")
public class ProtokollController {

    private final AuditEventRepository events;
    private final InvoiceRepository invoices;

    public ProtokollController(AuditEventRepository events, InvoiceRepository invoices) {
        this.events = events;
        this.invoices = invoices;
    }

    @GetMapping
    public List<Eintrag> list() {
        List<AuditEvent> latest = events.findTop200ByOrderByCreatedAtDesc();
        // Rechnungsnummern zur Anzeige auflösen (gelöschte Rechnungen bleiben ohne).
        List<UUID> ids = latest.stream().map(AuditEvent::getInvoiceId)
                .filter(Objects::nonNull).distinct().toList();
        Map<UUID, String> nummern = invoices.findAllById(ids).stream()
                .filter(i -> i.getRechnungsnummer() != null)
                .collect(Collectors.toMap(Invoice::getId, Invoice::getRechnungsnummer));
        return latest.stream()
                .map(e -> new Eintrag(e.getCreatedAt(), e.getAction(), e.getDetail(), e.getActor(),
                        e.getInvoiceId() == null ? null : e.getInvoiceId().toString(),
                        e.getInvoiceId() == null ? null : nummern.get(e.getInvoiceId())))
                .toList();
    }

    public record Eintrag(Instant zeitpunkt, String aktion, String detail, String benutzer,
                          String invoiceId, String rechnungsnummer) {
    }
}
