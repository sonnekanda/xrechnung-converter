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
 * Laufzeit-Einstellungen der Ordner-Automatik (nur ADMIN). Die Basis-Mountpfade
 * (aus der .env/Compose) werden read-only mitgeliefert; konfigurierbar sind
 * (Unter-)Ordner und Verhalten.
 */
@RestController
@RequestMapping("/api/v1/admin/ordner-automatik")
public class WatchSettingsController {

    private final AppSettingsService service;
    private final String baseInput;
    private final String baseOutput;

    public WatchSettingsController(AppSettingsService service,
                                   @Value("${erechnung.watch.input-dir:/data/watch/in}") String baseInput,
                                   @Value("${erechnung.watch.output-dir:/data/watch/out}") String baseOutput) {
        this.service = service;
        this.baseInput = baseInput;
        this.baseOutput = baseOutput;
    }

    @GetMapping
    public View get() {
        return View.of(service.get(), baseInput, baseOutput);
    }

    @PutMapping
    public View save(@RequestBody AppSettings input) {
        return View.of(service.save(input), baseInput, baseOutput);
    }

    /** Ansicht inkl. read-only Basispfade und der effektiven Ordner. */
    public record View(boolean watchEnabled, String watchInputSubdir, String watchOutputSubdir,
                       int watchIntervalSeconds, int watchSettleSeconds, boolean autoConfirmYellow,
                       String baseInputDir, String baseOutputDir,
                       String effectiveInputDir, String effectiveOutputDir) {
        static View of(AppSettings s, String baseIn, String baseOut) {
            return new View(s.isWatchEnabled(), s.getWatchInputSubdir(), s.getWatchOutputSubdir(),
                    s.getWatchIntervalSeconds(), s.getWatchSettleSeconds(), s.isAutoConfirmYellow(),
                    baseIn, baseOut, join(baseIn, s.getWatchInputSubdir()), join(baseOut, s.getWatchOutputSubdir()));
        }

        private static String join(String base, String sub) {
            return sub == null || sub.isBlank() ? base : base + "/" + sub;
        }
    }
}
