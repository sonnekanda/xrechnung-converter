package io.senapsa.erechnung.webapp.repo;

import io.senapsa.erechnung.webapp.domain.Recipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RecipientRepository extends JpaRepository<Recipient, UUID> {
    Optional<Recipient> findFirstByUstId(String ustId);

    Optional<Recipient> findFirstByKundennummer(String kundennummer);
}
