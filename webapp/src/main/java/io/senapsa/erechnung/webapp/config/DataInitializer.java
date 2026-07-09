package io.senapsa.erechnung.webapp.config;

import io.senapsa.erechnung.webapp.domain.AppUser;
import io.senapsa.erechnung.webapp.domain.Role;
import io.senapsa.erechnung.webapp.repo.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
}
