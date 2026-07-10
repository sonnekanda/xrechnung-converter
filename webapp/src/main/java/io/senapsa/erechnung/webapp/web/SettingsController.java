package io.senapsa.erechnung.webapp.web;

import io.senapsa.erechnung.webapp.service.AppSettingsService;
import io.senapsa.erechnung.webapp.service.LicenseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Zeigt die (nicht-geheime) Betriebskonfiguration read-only an (A-15,
 * „Konfiguration über .env und Admin-Bereich"). Secrets werden nicht ausgegeben.
 * Nur Rolle ADMIN (Pfad /admin/**).
 */
@RestController
@RequestMapping("/api/v1/admin/settings")
public class SettingsController {

    private final LicenseService license;
    private final AppSettingsService appSettings;
    private final String brandName;
    private final String productTitle;
    private final String primaryColor;
    private final long maxUploadMb;
    private final boolean kositEnabled;
    private final String roundingTolerance;

    public SettingsController(LicenseService license, AppSettingsService appSettings,
                              @Value("${erechnung.brand.name:senapsa}") String brandName,
                              @Value("${erechnung.brand.product-title:eRechnung-Konverter}") String productTitle,
                              @Value("${erechnung.brand.primary-color:#0B5FFF}") String primaryColor,
                              @Value("${erechnung.max-upload-mb:25}") long maxUploadMb,
                              @Value("${erechnung.kosit-enabled:false}") boolean kositEnabled,
                              @Value("${erechnung.rounding-tolerance:0.02}") String roundingTolerance) {
        this.license = license;
        this.appSettings = appSettings;
        this.brandName = brandName;
        this.productTitle = productTitle;
        this.primaryColor = primaryColor;
        this.maxUploadMb = maxUploadMb;
        this.kositEnabled = kositEnabled;
        this.roundingTolerance = roundingTolerance;
    }

    @GetMapping
    public Settings get() {
        return new Settings(brandName, productTitle, primaryColor, maxUploadMb,
                kositEnabled, appSettings.get().isWatchEnabled(), roundingTolerance, license.status());
    }

    public record Settings(String brandName, String productTitle, String primaryColor,
                           long maxUploadMb, boolean kositEnabled, boolean watchEnabled,
                           String roundingTolerance, LicenseService.Status license) {
    }
}
