package io.senapsa.erechnung.webapp.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

/**
 * Liefert das White-Label-Branding aus der .env. Keine OSS-Namen; nur die
 * konfigurierten senapsa-Werte sind fuer den Kunden sichtbar. Ein optionales
 * Logo (BRAND_LOGO_PATH, Datei im Datenvolumen) wird unter /logo ausgeliefert;
 * fehlt die Datei, zeigt die Oberflaeche automatisch den Buchstaben-Avatar.
 */
@RestController
@RequestMapping("/api/v1/branding")
public class BrandingController {

    private final String name;
    private final String productTitle;
    private final String primaryColor;
    private final String logoPath;

    public BrandingController(@Value("${erechnung.brand.name:senapsa}") String name,
                             @Value("${erechnung.brand.product-title:eRechnung-Konverter}") String productTitle,
                             @Value("${erechnung.brand.primary-color:#0B5FFF}") String primaryColor,
                             @Value("${erechnung.brand.logo-path:}") String logoPath) {
        this.name = name;
        this.productTitle = productTitle;
        this.primaryColor = primaryColor;
        this.logoPath = logoPath == null ? "" : logoPath.trim();
    }

    @GetMapping
    public Branding branding() {
        return new Branding(name, productTitle, primaryColor,
                hasLogo() ? "/api/v1/branding/logo" : null);
    }

    /** Konfiguriertes Logo (SVG/PNG/JPG); 404, wenn keines hinterlegt ist. */
    @GetMapping("/logo")
    public ResponseEntity<byte[]> logo() throws IOException {
        if (!hasLogo()) {
            throw new ApiExceptions.NotFound("Es ist kein Logo hinterlegt.");
        }
        Path file = Path.of(logoPath);
        return ResponseEntity.ok()
                .contentType(mediaType(file.getFileName().toString()))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)))
                .body(Files.readAllBytes(file));
    }

    private boolean hasLogo() {
        return !logoPath.isBlank() && Files.isReadable(Path.of(logoPath));
    }

    private static MediaType mediaType(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".svg")) {
            return MediaType.valueOf("image/svg+xml");
        }
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    public record Branding(String name, String productTitle, String primaryColor, String logoUrl) {
    }
}
