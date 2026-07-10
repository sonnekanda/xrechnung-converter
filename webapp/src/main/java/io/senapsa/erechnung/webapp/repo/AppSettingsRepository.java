package io.senapsa.erechnung.webapp.repo;

import io.senapsa.erechnung.webapp.domain.AppSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppSettingsRepository extends JpaRepository<AppSettings, UUID> {
    Optional<AppSettings> findFirstByOrderById();
}
