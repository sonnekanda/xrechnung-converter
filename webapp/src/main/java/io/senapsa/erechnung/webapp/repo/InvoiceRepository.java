package io.senapsa.erechnung.webapp.repo;

import io.senapsa.erechnung.webapp.domain.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findAllByOrderByCreatedAtDesc();
}
