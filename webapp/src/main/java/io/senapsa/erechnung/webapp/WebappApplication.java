package io.senapsa.erechnung.webapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * webapp: UI + API {@code /api/v1}, Authentifizierung/Rollen, Statusmaschine,
 * Stammdaten und Export-Freigabe. Einziger nach aussen exponierter Dienst.
 */
@SpringBootApplication
@EnableScheduling
public class WebappApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebappApplication.class, args);
    }
}
