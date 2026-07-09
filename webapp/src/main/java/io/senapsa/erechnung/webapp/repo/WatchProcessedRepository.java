package io.senapsa.erechnung.webapp.repo;

import io.senapsa.erechnung.webapp.domain.WatchProcessed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WatchProcessedRepository extends JpaRepository<WatchProcessed, UUID> {
    boolean existsByContentHash(String contentHash);
}
