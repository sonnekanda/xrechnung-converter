package io.senapsa.erechnung.engine.validate;

import org.mustangproject.validator.ZUGFeRDValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Doppelte amtliche Validierung. Erste Instanz: Mustangproject (XSD + Schematron)
 * in-process. Zweite unabhaengige Instanz: der KoSIT-Validator als eigener Dienst
 * mit der offiziellen XRechnung-Pruefkonfiguration (HTTP-Daemon, isolierter
 * Classpath). Export ist nur zulaessig, wenn BEIDE Pruefungen bestehen.
 *
 * KoSIT-Antwort: HTTP 200 = akzeptiert, 406 = abgelehnt, sonst Verarbeitungs-
 * fehler. Ist KoSIT aktiviert, aber nicht erreichbar, gilt die Rechnung als
 * nicht gueltig (Fail-Safe: es wird kein "gueltig" vorgetaeuscht).
 */
@Component
public class XRechnungValidator {

    private static final Logger log = LoggerFactory.getLogger(XRechnungValidator.class);

    @Value("${erechnung.kosit-enabled:false}")
    private boolean kositEnabled;

    @Value("${erechnung.kosit-url:http://kosit-validator:8092/}")
    private String kositUrl;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    public ValidationResult validate(byte[] xml, String filename) {
        // 1) Mustangproject
        ZUGFeRDValidator mustang = new ZUGFeRDValidator();
        String mustangReport = mustang.validate(xml, filename);
        boolean mustangValid = mustang.wasCompletelyValid();

        if (!kositEnabled) {
            return new ValidationResult(mustangValid, mustangReport, false);
        }

        // 2) KoSIT-Dienst
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(kositUrl))
                    .header("Content-Type", "application/xml")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(xml))
                    .build();
            HttpResponse<byte[]> resp = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            int sc = resp.statusCode();
            String kositReport = new String(resp.body(), StandardCharsets.UTF_8);

            boolean acceptable = sc == 200;
            boolean processed = sc == 200 || sc == 406;
            if (!processed) {
                log.warn("KoSIT-Verarbeitungsfehler (HTTP {})", sc);
                return new ValidationResult(false, mustangReport, true,
                        "Die zweite Validierung (KoSIT) meldete einen Verarbeitungsfehler.");
            }
            boolean valid = mustangValid && acceptable;
            // Ablehnenden Bericht bevorzugen, sonst den KoSIT-Bericht ausgeben.
            String report = !acceptable ? kositReport : (!mustangValid ? mustangReport : kositReport);
            log.info("Validierung {}: Mustang={}, KoSIT={} (HTTP {})", filename, mustangValid, acceptable, sc);
            return new ValidationResult(valid, report, true);
        } catch (Exception e) {
            log.error("KoSIT-Dienst nicht erreichbar unter {}", kositUrl, e);
            return new ValidationResult(false, mustangReport, true,
                    "Die zweite Validierung (KoSIT) ist derzeit nicht erreichbar.");
        }
    }

    public record ValidationResult(boolean valid, String report, boolean kositChecked, String hinweis) {
        public ValidationResult(boolean valid, String report, boolean kositChecked) {
            this(valid, report, kositChecked, null);
        }
    }
}
