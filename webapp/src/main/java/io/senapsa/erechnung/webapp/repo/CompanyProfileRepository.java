package io.senapsa.erechnung.webapp.repo;

import io.senapsa.erechnung.webapp.domain.CompanyProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompanyProfileRepository extends JpaRepository<CompanyProfile, UUID> {
    Optional<CompanyProfile> findFirstByOrderByName();
}
