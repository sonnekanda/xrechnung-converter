package io.senapsa.erechnung.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * invoice-engine: kapselt Mustangproject (Generierung XRechnung UBL) und die
 * amtliche Validierung (XSD + Schematron). White-Label — nach aussen nur die
 * senapsa-API, keine OSS-Namen.
 */
@SpringBootApplication
public class EngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(EngineApplication.class, args);
    }
}
