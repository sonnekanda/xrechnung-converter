package io.senapsa.erechnung.webapp.repo;

import io.senapsa.erechnung.webapp.domain.WatchProcessed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WatchProcessedRepository extends JpaRepository<WatchProcessed, UUID> {
    boolean existsByContentHash(String contentHash);

    /**
     * Entfernt den Doppelverarbeitungs-Vermerk zu einem Basisnamen. Wird beim
     * Löschen einer Rechnung aufgerufen, damit dieselbe Datei erneut über die
     * Ordner-Automatik eingelesen werden kann.
     */
    void deleteByBasename(String basename);
}
