package io.senapsa.erechnung.webapp.web;

import io.senapsa.erechnung.webapp.domain.CompanyProfile;
import io.senapsa.erechnung.webapp.service.CompanyProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verwaltung der eigenen Firma (Verkäufer). Nur Rolle ADMIN (Pfad /admin/**).
 * Diese Daten werden automatisch als Verkäufer/Zahlungsweg in jede Rechnung
 * eingefüllt.
 */
@RestController
@RequestMapping("/api/v1/admin/company")
public class CompanyController {

    private final CompanyProfileService service;

    public CompanyController(CompanyProfileService service) {
        this.service = service;
    }

    @GetMapping
    public CompanyProfile get() {
        return service.find().orElseGet(CompanyProfile::new);
    }

    @PutMapping
    public CompanyProfile save(@RequestBody CompanyProfile input) {
        return service.save(input);
    }
}
