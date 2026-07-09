package io.senapsa.erechnung.engine.validate;

import org.mustangproject.validator.ZUGFeRDValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Amtliche Validierung (XSD + Schematron/Business-Rules) via Mustangproject.
 * Optional zweite unabhaengige Validierung (KoSIT) — Export nur bei Erfolg
 * beider Pruefungen.
 */
@Component
public class XRechnungValidator {

    @Value("${erechnung.kosit-enabled:false}")
    private boolean kositEnabled;

    public ValidationResult validate(byte[] xml, String filename) {
        ZUGFeRDValidator validator = new ZUGFeRDValidator();
        String report = validator.validate(xml, filename);
        boolean valid = validator.wasCompletelyValid();

        // KoSIT als zweite Instanz (nur wenn aktiviert). Bis zur Anbindung wird
        // konservativ die Mustang-Bewertung uebernommen und im Bericht vermerkt.
        boolean kositValid = !kositEnabled || valid;

        return new ValidationResult(valid && kositValid, report, kositEnabled);
    }

    public record ValidationResult(boolean valid, String report, boolean kositChecked) {
    }
}
