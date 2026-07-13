package io.senapsa.erechnung.webapp.web;

import io.senapsa.erechnung.webapp.domain.AppSettings;
import io.senapsa.erechnung.webapp.service.AppSettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Laufzeit-Einstellungen des KI-Assistenten (nur ADMIN). Ueberschreibt die
 * .env-Vorgaben und speichert sie in der DB; bei jeder Extraktion werden die
 * effektiven Werte an den extraction-worker uebergeben (kein Neustart noetig).
 *
 * Der API-Schluessel wird nie an das Frontend zurueckgegeben – die Ansicht
 * meldet nur, OB ein Schluessel hinterlegt ist. Ein leeres Schluesselfeld beim
 * Speichern laesst den bestehenden Schluessel unveraendert.
 */
@RestController
@RequestMapping("/api/v1/admin/ki-assistent")
public class KiSettingsController {

    private final AppSettingsService service;

    public KiSettingsController(AppSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public View get() {
        return view(service.get());
    }

    @PutMapping
    public View save(@RequestBody Input input) {
        AppSettings s = service.saveKi(input.enabled(), input.model(),
                input.timeoutSeconds(), input.apiKey());
        return view(s);
    }

    private View view(AppSettings s) {
        return new View(s.isKiEnabled(), s.getKiModel(), s.getKiTimeoutSeconds(),
                !s.getKiApiKey().isBlank());
    }

    /** Eingabe des Formulars. {@code apiKey} ist optional (leer = unveraendert). */
    public record Input(boolean enabled, String model, int timeoutSeconds, String apiKey) {
    }

    /** Ansicht ohne Schluesselwert – nur {@code apiKeySet} verraet, ob einer da ist. */
    public record View(boolean enabled, String model, int timeoutSeconds, boolean apiKeySet) {
    }
}
