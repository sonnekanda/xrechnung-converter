package io.senapsa.erechnung.webapp.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liefert das White-Label-Branding aus der .env. Keine OSS-Namen; nur die
 * konfigurierten senapsa-Werte sind fuer den Kunden sichtbar.
 */
@RestController
@RequestMapping("/api/v1/branding")
public class BrandingController {

    private final String name;
    private final String productTitle;
    private final String primaryColor;

    public BrandingController(@Value("${erechnung.brand.name:senapsa}") String name,
                             @Value("${erechnung.brand.product-title:eRechnung-Konverter}") String productTitle,
                             @Value("${erechnung.brand.primary-color:#0B5FFF}") String primaryColor) {
        this.name = name;
        this.productTitle = productTitle;
        this.primaryColor = primaryColor;
    }

    @GetMapping
    public Branding branding() {
        return new Branding(name, productTitle, primaryColor);
    }

    public record Branding(String name, String productTitle, String primaryColor) {
    }
}
