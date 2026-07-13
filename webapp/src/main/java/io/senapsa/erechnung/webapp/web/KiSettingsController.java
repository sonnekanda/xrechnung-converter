package io.senapsa.erechnung.webapp.web;

import io.senapsa.erechnung.webapp.domain.AppSettings;
import io.senapsa.erechnung.webapp.service.AppSettingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Laufzeit-Einstellungen des KI-Assistenten (nur ADMIN).
 *
 * Ein Master-Schalter {@code override} entscheidet, ob die hier gepflegten Werte
 * die .env-/Installations-Vorgaben ueberschreiben. Ist er aus, gelten die
 * Standardwerte (der extraction-worker nutzt seine eigene .env) – der Nutzer
 * kann jederzeit gefahrlos dorthin zurueck, unabhaengig vom Inhalt der Felder.
 *
 * Der API-Schluessel wird nie an das Frontend zurueckgegeben – die Ansicht
 * meldet nur, OB ein Schluessel hinterlegt ist. Ein leeres Schluesselfeld beim
 * Speichern laesst den bestehenden Schluessel unveraendert. Zusaetzlich werden
 * die .env-Standardwerte read-only mitgeliefert, damit die Oberflaeche im
 * Aus-Zustand zeigen kann, was tatsaechlich gilt.
 */
@RestController
@RequestMapping("/api/v1/admin/ki-assistent")
public class KiSettingsController {

    private final AppSettingsService service;
    private final boolean defaultEnabled;
    private final String defaultModel;
    private final int defaultTimeoutSeconds;
    private final boolean defaultApiKeySet;

    public KiSettingsController(AppSettingsService service,
                               @Value("${erechnung.ki.enabled:false}") boolean defaultEnabled,
                               @Value("${erechnung.ki.model:openai/gpt-4o-mini}") String defaultModel,
                               @Value("${erechnung.ki.timeout-seconds:30}") int defaultTimeoutSeconds,
                               @Value("${erechnung.ki.api-key:}") String defaultApiKey) {
        this.service = service;
        this.defaultEnabled = defaultEnabled;
        this.defaultModel = defaultModel;
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
        this.defaultApiKeySet = defaultApiKey != null && !defaultApiKey.isBlank();
    }

    @GetMapping
    public View get() {
        return view(service.get());
    }

    @PutMapping
    public View save(@RequestBody Input input) {
        AppSettings s = service.saveKi(input.override(), input.enabled(), input.model(),
                input.timeoutSeconds(), input.apiKey());
        return view(s);
    }

    private View view(AppSettings s) {
        return new View(s.isKiOverride(), s.isKiEnabled(), s.getKiModel(),
                s.getKiTimeoutSeconds(), !s.getKiApiKey().isBlank(),
                defaultEnabled, defaultModel, defaultTimeoutSeconds, defaultApiKeySet);
    }

    /**
     * Eingabe des Formulars. {@code apiKey} ist optional (leer = unveraendert);
     * die Detailwerte gelten nur bei {@code override = true}.
     */
    public record Input(boolean override, boolean enabled, String model,
                        int timeoutSeconds, String apiKey) {
    }

    /**
     * Ansicht ohne Schluesselwert. {@code apiKeySet} verraet nur, ob einer da ist;
     * die {@code default*}-Felder zeigen die geltenden .env-Standardwerte.
     */
    public record View(boolean override, boolean enabled, String model,
                       int timeoutSeconds, boolean apiKeySet,
                       boolean defaultEnabled, String defaultModel,
                       int defaultTimeoutSeconds, boolean defaultApiKeySet) {
    }
}
