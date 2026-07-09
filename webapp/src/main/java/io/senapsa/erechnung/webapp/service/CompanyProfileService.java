package io.senapsa.erechnung.webapp.service;

import io.senapsa.erechnung.webapp.domain.CompanyProfile;
import io.senapsa.erechnung.webapp.repo.CompanyProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Verwaltet die eigene Firma (Single-Tenant: genau ein Datensatz). */
@Service
public class CompanyProfileService {

    private final CompanyProfileRepository repo;

    public CompanyProfileService(CompanyProfileRepository repo) {
        this.repo = repo;
    }

    public Optional<CompanyProfile> find() {
        return repo.findFirstByOrderByName();
    }

    /** Legt den einzigen Datensatz an oder aktualisiert ihn. */
    @Transactional
    public CompanyProfile save(CompanyProfile input) {
        CompanyProfile profile = repo.findFirstByOrderByName().orElseGet(CompanyProfile::new);
        profile.setName(input.getName());
        profile.setStrasse(input.getStrasse());
        profile.setPlz(input.getPlz());
        profile.setOrt(input.getOrt());
        profile.setLand(input.getLand() == null || input.getLand().isBlank() ? "DE" : input.getLand());
        profile.setUstId(input.getUstId());
        profile.setSteuernummer(input.getSteuernummer());
        profile.setKontaktName(input.getKontaktName());
        profile.setEmail(input.getEmail());
        profile.setTelefon(input.getTelefon());
        profile.setIban(input.getIban());
        profile.setBic(input.getBic());
        profile.setZahlungsbedingungen(input.getZahlungsbedingungen());
        return repo.save(profile);
    }
}
