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
    private final String hostInput;
    private final String hostOutput;

    public WatchSettingsController(AppSettingsService service,
                                   @Value("${erechnung.watch.input-dir:/data/watch/in}") String baseInput,
                                   @Value("${erechnung.watch.output-dir:/data/watch/out}") String baseOutput,
                                   @Value("${WATCH_HOST_INPUT_DIR:}") String hostInput,
                                   @Value("${WATCH_HOST_OUTPUT_DIR:}") String hostOutput) {
        this.service = service;
        this.baseInput = baseInput;
        this.baseOutput = baseOutput;
        this.hostInput = hostInput;
        this.hostOutput = hostOutput;
    }

    @GetMapping
    public View get() {
        return view(service.get());
    }

    @PutMapping
    public View save(@RequestBody AppSettings input) {
        return view(service.save(input));
    }

    private View view(AppSettings s) {
        return new View(s.isWatchEnabled(), s.getWatchInputSubdir(), s.getWatchOutputSubdir(),
                s.getWatchIntervalSeconds(), s.getWatchSettleSeconds(), s.isAutoConfirmYellow(),
                s.getWatchOutputFormat(),
                baseInput, baseOutput, hostInput, hostOutput,
                join(baseInput, s.getWatchInputSubdir()), join(baseOutput, s.getWatchOutputSubdir()),
                joinHost(hostInput, s.getWatchInputSubdir()), joinHost(hostOutput, s.getWatchOutputSubdir()));
    }

    private static String join(String base, String sub) {
        return sub == null || sub.isBlank() ? base : base + "/" + sub;
    }

    /** Host-Anzeige (Windows-Backslashes), leer wenn kein Host-Ordner gemountet. */
    private static String joinHost(String host, String sub) {
        if (host == null || host.isBlank()) {
            return "";
        }
        String p = (sub == null || sub.isBlank()) ? host : host + "/" + sub;
        return p.replace("/", "\\");
    }

    /**
     * Ansicht inkl. read-only Container-Basispfade, Host-Ordner (falls gemountet)
     * und der effektiven Ordner.
     */
    public record View(boolean watchEnabled, String watchInputSubdir, String watchOutputSubdir,
                       int watchIntervalSeconds, int watchSettleSeconds, boolean autoConfirmYellow,
                       String watchOutputFormat,
                       String baseInputDir, String baseOutputDir,
                       String hostInputDir, String hostOutputDir,
                       String effectiveInputDir, String effectiveOutputDir,
                       String effectiveHostInput, String effectiveHostOutput) {
    }
}
