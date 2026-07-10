package io.senapsa.erechnung.webapp.service;

import io.senapsa.erechnung.webapp.domain.AuditEvent;
import io.senapsa.erechnung.webapp.repo.AuditEventRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditEventRepository repo;

    public AuditService(AuditEventRepository repo) {
        this.repo = repo;
    }

    public void log(UUID invoiceId, String action, String detail) {
        var ctx = SecurityContextHolder.getContext().getAuthentication();
        String actor = ctx != null ? ctx.getName() : "system";
        repo.save(new AuditEvent(invoiceId, action, detail, actor));
    }

    /** Sicherheitsrelevantes Ereignis ohne Rechnungsbezug (Login, Admin-Änderung). */
    public void logSecurity(String action, String detail, String actor) {
        repo.save(new AuditEvent(null, action, detail, actor));
    }

    public List<AuditEvent> forInvoice(UUID invoiceId) {
        return repo.findAllByInvoiceIdOrderByCreatedAtAsc(invoiceId);
    }

    public void deleteForInvoice(UUID invoiceId) {
        repo.deleteAllByInvoiceId(invoiceId);
    }
}
