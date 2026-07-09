package io.senapsa.erechnung.webapp.repo;

import io.senapsa.erechnung.webapp.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    List<AuditEvent> findAllByInvoiceIdOrderByCreatedAtAsc(UUID invoiceId);
}
