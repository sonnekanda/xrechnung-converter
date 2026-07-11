package io.senapsa.erechnung.webapp.service;

import io.senapsa.erechnung.webapp.domain.AppSettings;
import io.senapsa.erechnung.webapp.repo.AppSettingsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verwaltet die Laufzeit-Einstellungen (Single-Tenant: ein Datensatz). Beim
 * ersten Zugriff werden die Defaults aus der .env übernommen (WATCH_*), danach
 * ist alles im Admin-Bereich pflegbar.
 */
@Service
public class AppSettingsService {

    private final AppSettingsRepository repo;
    private final boolean envWatchEnabled;
    private final int envWatchInterval;

    public AppSettingsService(AppSettingsRepository repo,
                              @Value("${erechnung.watch.enabled:false}") boolean envWatchEnabled,
                              @Value("${erechnung.watch.interval-seconds:60}") int envWatchInterval) {
        this.repo = repo;
        this.envWatchEnabled = envWatchEnabled;
        this.envWatchInterval = envWatchInterval;
    }

    @Transactional
    public AppSettings get() {
        return repo.findFirstByOrderById().orElseGet(() -> {
            AppSettings s = new AppSettings();
            s.setWatchEnabled(envWatchEnabled);
            s.setWatchIntervalSeconds(envWatchInterval);
            return repo.save(s);
        });
    }

    @Transactional
    public AppSettings save(AppSettings input) {
        AppSettings s = get();
        s.setWatchEnabled(input.isWatchEnabled());
        s.setWatchInputSubdir(sanitize(input.getWatchInputSubdir()));
        s.setWatchOutputSubdir(sanitize(input.getWatchOutputSubdir()));
        s.setWatchIntervalSeconds(input.getWatchIntervalSeconds());
        s.setWatchSettleSeconds(input.getWatchSettleSeconds());
        s.setAutoConfirmYellow(input.isAutoConfirmYellow());
        s.setWatchOutputFormat(input.getWatchOutputFormat());
        return repo.save(s);
    }

    /** Verhindert Pfad-Ausbruch: relativer Unterordner ohne ".." und ohne führende Slashes. */
    private static String sanitize(String subdir) {
        if (subdir == null) {
            return "";
        }
        String s = subdir.replace("\\", "/").trim();
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        if (s.contains("..")) {
            throw new IllegalArgumentException("Unzulässiger Ordnername.");
        }
        return s;
    }
}
