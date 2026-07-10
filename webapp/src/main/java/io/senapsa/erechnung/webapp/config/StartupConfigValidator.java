package io.senapsa.erechnung.webapp.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fail-Fast-Prüfung sicherheitskritischer Konfiguration beim Start
 * (Entwicklungsrichtlinie 3.2.5 / 8.2). Pflicht-Secrets ohne brauchbaren Wert
 * lassen den Start fehlschlagen, statt unsicher weiterzulaufen.
 */
@Component
public class StartupConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(StartupConfigValidator.class);

    private final String jwtSecret;
    private final String adminPassword;

    public StartupConfigValidator(@Value("${erechnung.jwt.secret:}") String jwtSecret,
                                  @Value("${erechnung.admin.password:}") String adminPassword) {
        this.jwtSecret = jwtSecret;
        this.adminPassword = adminPassword;
    }

    @PostConstruct
    void validate() {
        if (jwtSecret == null || jwtSecret.isBlank() || jwtSecret.length() < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET fehlt oder ist zu kurz (mind. 32 zufällige Zeichen erforderlich). "
                    + "Bitte in der .env setzen.");
        }
        if (jwtSecret.contains("change-me")) {
            throw new IllegalStateException(
                    "JWT_SECRET verwendet noch den Platzhalter aus .example.env. "
                    + "Bitte einen eigenen, zufälligen Wert setzen.");
        }
        if (adminPassword == null || adminPassword.isBlank() || adminPassword.contains("change-me")) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD fehlt oder ist der Platzhalter. Bitte in der .env setzen.");
        }
        log.info("Konfigurationsprüfung bestanden (Pflicht-Secrets gesetzt).");
    }
}
