package io.senapsa.erechnung.webapp.config;

import io.senapsa.erechnung.webapp.domain.AppUser;
import io.senapsa.erechnung.webapp.domain.CompanyProfile;
import io.senapsa.erechnung.webapp.domain.Role;
import io.senapsa.erechnung.webapp.repo.AppUserRepository;
import io.senapsa.erechnung.webapp.repo.CompanyProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Legt beim ersten Start den initialen Admin-Benutzer aus der .env an. */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    ApplicationRunner seedAdmin(AppUserRepository users, PasswordEncoder encoder,
                                @Value("${erechnung.admin.email}") String adminEmail,
                                @Value("${erechnung.admin.password}") String adminPassword) {
        return args -> {
            if (users.existsByEmail(adminEmail)) {
                return;
            }
            users.save(new AppUser(adminEmail, encoder.encode(adminPassword), Role.ADMIN));
            log.info("Initialer Admin-Benutzer angelegt: {}", adminEmail);
        };
    }

    /**
     * Legt die eigene Firma beim ersten Start aus den COMPANY_*-Variablen an
     * (optional). Danach ist sie im Admin-Bereich pflegbar.
     */
    @Bean
    ApplicationRunner seedCompany(CompanyProfileRepository companies, Environment env) {
        return args -> {
            String name = env.getProperty("COMPANY_NAME");
            if (name == null || name.isBlank() || companies.count() > 0) {
                return;
            }
            CompanyProfile c = new CompanyProfile();
            c.setName(name);
            c.setStrasse(env.getProperty("COMPANY_STREET"));
            c.setPlz(env.getProperty("COMPANY_ZIP"));
            c.setOrt(env.getProperty("COMPANY_CITY"));
            c.setLand(env.getProperty("COMPANY_COUNTRY", "DE"));
            c.setUstId(env.getProperty("COMPANY_VATID"));
            c.setSteuernummer(env.getProperty("COMPANY_TAXNO"));
            c.setKontaktName(env.getProperty("COMPANY_CONTACT"));
            c.setEmail(env.getProperty("COMPANY_EMAIL"));
            c.setTelefon(env.getProperty("COMPANY_PHONE"));
            c.setIban(env.getProperty("COMPANY_IBAN"));
            c.setBic(env.getProperty("COMPANY_BIC"));
            c.setZahlungsbedingungen(env.getProperty("COMPANY_PAYMENT_TERMS"));
            companies.save(c);
            log.info("Eigene Firma aus .env angelegt: {}", name);
        };
    }
}
