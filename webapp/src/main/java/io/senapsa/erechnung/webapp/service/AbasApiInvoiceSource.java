package io.senapsa.erechnung.webapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Vorbereitete – aber bewusst <b>noch nicht implementierte</b> – Pull-Quelle für
 * die direkte Anbindung eines Abas-ERP per REST-API (analog zur vorbereiteten
 * {@link LicenseService Lizenzcontroller-Schnittstelle}).
 *
 * <p>Hintergrund: Das Produkt existiert gerade, weil ältere Abas-Versionen kein
 * XML erzeugen – die tragende Anbindung ist daher dateibasiert (Upload und
 * Ordner-Automatik über einen Übergabeordner). Eine direkte REST-Anbindung ist
 * laut Aufgabenscope „Später" (weitere ERP-Systeme / erweiterte Anbindung). Diese
 * Klasse hält den Platz frei, damit ein späterer Baustein nur {@link #poll()}
 * gegen den realen Abas-Client führen muss – ohne Umbau des Verarbeitungspfads:
 * die abgeholten Dokumente laufen unverändert durch
 * {@link UploadService#ingest(String, byte[], String, byte[], String, String)}.
 *
 * <p>Als reiner Bean ohne {@code @Scheduled} ist die Klasse zur Laufzeit inert:
 * niemand ruft {@link #poll()}, sie startet nichts und ändert kein Verhalten.
 */
@Service
public class AbasApiInvoiceSource implements InvoiceSource {

    /** Herkunftskennzeichen für {@code Invoice.source} (neben "UPLOAD"/"WATCH"). */
    public static final String SOURCE_ID = "ABAS_API";

    private static final Logger log = LoggerFactory.getLogger(AbasApiInvoiceSource.class);

    private final boolean enabled;
    private final String baseUrl;
    private final boolean apiKeyConfigured;

    public AbasApiInvoiceSource(@Value("${erechnung.source.abas.enabled:false}") boolean enabled,
                                @Value("${erechnung.source.abas.base-url:}") String baseUrl,
                                @Value("${erechnung.source.abas.api-key:}") String apiKey) {
        this.enabled = enabled;
        this.baseUrl = baseUrl == null ? "" : baseUrl;
        this.apiKeyConfigured = apiKey != null && !apiKey.isBlank();
        if (enabled) {
            // Sichtbar machen, dass die Schnittstelle konfiguriert, aber noch nicht
            // implementiert ist – kein stiller "aktiv, tut aber nichts"-Zustand.
            log.warn("Abas-API-Quelle ist per Konfiguration aktiviert, aber noch nicht "
                    + "implementiert (Schnittstelle vorbereitet, Umsetzung = eigener Baustein). "
                    + "Es wird nichts abgerufen.");
        }
    }

    @Override
    public String id() {
        return SOURCE_ID;
    }

    @Override
    public String displayName() {
        return "Abas-ERP (REST-API)";
    }

    /**
     * Immer {@code false}: Auch bei gesetzter Konfiguration ist der Abruf bis zur
     * Implementierung des Abas-Clients nicht verfügbar. So wird die Quelle von
     * einem etwaigen generischen Scheduler nie gepollt.
     */
    @Override
    public boolean available() {
        return false;
    }

    @Override
    public List<SourceDocumentGroup> poll() {
        throw new UnsupportedOperationException(
                "Abas-API-Import ist vorbereitet, aber noch nicht implementiert "
                + "(eigener Baustein; siehe erechnung.source.abas.* in der .env).");
    }

    /** Statusabbild für spätere Admin-Anzeige/Diagnose (analog LicenseService.status). */
    public Status status() {
        return new Status(enabled, !baseUrl.isBlank(), apiKeyConfigured, false);
    }

    /**
     * @param implemented ob der Abruf tatsächlich umgesetzt ist (aktuell {@code false})
     */
    public record Status(boolean enabled, boolean baseUrlConfigured,
                         boolean apiKeyConfigured, boolean implemented) {
    }
}
