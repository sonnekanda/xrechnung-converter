package io.senapsa.erechnung.engine.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.senapsa.erechnung.engine.generate.XRechnungGenerator;
import io.senapsa.erechnung.engine.mapping.InternalModelMapper;
import io.senapsa.erechnung.engine.validate.XRechnungValidator;
import org.mustangproject.Invoice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

/**
 * Interne API der invoice-engine (nur im Netz sic-net erreichbar).
 * Erzeugt XRechnung-UBL aus dem internen Modell und validiert amtlich.
 */
@RestController
@RequestMapping("/engine")
public class EngineController {

    private static final Logger log = LoggerFactory.getLogger(EngineController.class);

    private final InternalModelMapper mapper;
    private final XRechnungGenerator generator;
    private final XRechnungValidator validator;

    public EngineController(InternalModelMapper mapper, XRechnungGenerator generator,
                            XRechnungValidator validator) {
        this.mapper = mapper;
        this.generator = generator;
        this.validator = validator;
    }

    /**
     * Erzeugt UBL-XML aus dem internen Modell und validiert es sofort amtlich.
     * Der Aufrufer (webapp) gibt nur bei {@code valid=true} zum Export frei.
     */
    @PostMapping("/generate")
    public ResponseEntity<GenerateResponse> generate(@RequestBody GenerateRequest request) throws Exception {
        JsonNode model = request.model();
        String number = model.path("kopf").path("rechnungsnummer").path("wert").asText("?");
        log.info("Generiere XRechnung UBL fuer Rechnung {}", number);

        Invoice invoice = mapper.toInvoice(model);
        byte[] ubl = generator.generateUbl(invoice);

        String filename = "rechnung-" + number + ".xml";
        XRechnungValidator.ValidationResult vr = validator.validate(ubl, filename);
        log.info("Validierung Rechnung {}: valid={} (kosit={})", number, vr.valid(), vr.kositChecked());

        GenerateResponse body = new GenerateResponse(
                "UBL",
                Base64.getEncoder().encodeToString(ubl),
                vr.valid(),
                vr.kositChecked(),
                vr.report());
        return ResponseEntity.ok(body);
    }

    /** Validiert eine bereits erzeugte XML-Datei (Base64) erneut. */
    @PostMapping("/validate")
    public ResponseEntity<ValidateResponse> validate(@RequestBody ValidateRequest request) {
        byte[] xml = Base64.getDecoder().decode(request.xmlBase64());
        XRechnungValidator.ValidationResult vr = validator.validate(xml, request.filename());
        return ResponseEntity.ok(new ValidateResponse(vr.valid(), vr.kositChecked(), vr.report()));
    }

    public record GenerateRequest(JsonNode model, String syntax) {
    }

    public record GenerateResponse(String syntax, String xmlBase64, boolean valid,
                                   boolean kositChecked, String report) {
    }

    public record ValidateRequest(String xmlBase64, String filename) {
    }

    public record ValidateResponse(boolean valid, boolean kositChecked, String report) {
    }
}
