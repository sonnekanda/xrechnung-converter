package io.senapsa.erechnung.webapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Ruft die invoice-engine (Mustangproject) zur Generierung der XRechnung-UBL
 * und amtlichen Validierung auf. White-Label: der Dienst ist nur intern
 * erreichbar; nach aussen erscheint ausschliesslich die senapsa-API.
 */
@Service
public class GenerationService {

    private final RestClient client;

    public GenerationService(@Value("${erechnung.invoice-engine-url}") String engineUrl) {
        this.client = RestClient.builder().baseUrl(engineUrl).build();
    }

    public EngineResult generateAndValidate(JsonNode model) {
        return client.post()
                .uri("/engine/generate")
                .body(Map.of("model", model, "syntax", "UBL"))
                .retrieve()
                .body(EngineResult.class);
    }

    /** Antwort der invoice-engine. */
    public record EngineResult(String syntax, String xmlBase64, boolean valid,
                               boolean kositChecked, String report) {
    }
}
