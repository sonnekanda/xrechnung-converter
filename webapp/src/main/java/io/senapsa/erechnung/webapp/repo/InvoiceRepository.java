package io.senapsa.erechnung.webapp.repo;

import io.senapsa.erechnung.webapp.domain.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findAllByOrderByCreatedAtDesc();

    /**
     * Rechnungen im Datumsbereich (Rechnungsdatum). Rechnungen ohne Datum
     * (noch nicht erkannt/unvollständig) werden immer mitgeführt, damit sie
     * nicht aus der Übersicht verschwinden.
     */
    @Query("""
            SELECT i FROM Invoice i
            WHERE i.rechnungsdatum IS NULL
               OR (i.rechnungsdatum >= :von AND i.rechnungsdatum <= :bis)
            ORDER BY i.createdAt DESC
            """)
    List<Invoice> findForDateRange(@Param("von") LocalDate von, @Param("bis") LocalDate bis);
}
