package io.senapsa.erechnung.webapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Lueckenloses Audit-Log ueber alle Verarbeitungsschritte je Rechnung. */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(nullable = false)
    private String action;

    @Column(columnDefinition = "text")
    private String detail;

    @Column(name = "actor")
    private String actor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AuditEvent() {
    }

    public AuditEvent(UUID invoiceId, String action, String detail, String actor) {
        this.invoiceId = invoiceId;
        this.action = action;
        this.detail = detail;
        this.actor = actor;
    }

    public UUID getId() {
        return id;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public String getAction() {
        return action;
    }

    public String getDetail() {
        return detail;
    }

    public String getActor() {
        return actor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
