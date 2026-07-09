package io.senapsa.erechnung.webapp.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.senapsa.erechnung.webapp.domain.Recipient;
import io.senapsa.erechnung.webapp.repo.RecipientRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Fuellt fehlende Pflichtfelder im internen Modell automatisch aus den
 * Empfaenger-Stammdaten (Autofill). Zuordnung ueber USt-IdNr, sonst Kundennummer.
 * Gesetzte Werte erhalten {@code herkunft = "stammdaten"} und Status "sicher".
 */
@Service
public class StammdatenAutofillService {

    private final RecipientRepository recipients;
    private final ModelJson modelJson;

    public StammdatenAutofillService(RecipientRepository recipients, ModelJson modelJson) {
        this.recipients = recipients;
        this.modelJson = modelJson;
    }

    /** Ergaenzt das Modell und liefert true, wenn ein passender Empfaenger gefunden wurde. */
    public boolean apply(ObjectNode model) {
        String ustId = modelJson.wert(model, "kopf", "kaeufer_ustid");
        String kundennummer = modelJson.wert(model, "kopf", "kaeufer_kundennummer");

        Optional<Recipient> match = Optional.empty();
        if (ustId != null) {
            match = recipients.findFirstByUstId(ustId);
        }
        if (match.isEmpty() && kundennummer != null) {
            match = recipients.findFirstByKundennummer(kundennummer);
        }
        if (match.isEmpty()) {
            return false;
        }

        Recipient r = match.get();
        fillIfMissing(model, "leitweg_id", r.getLeitwegId());
        fillIfMissing(model, "bankverbindung_iban", r.getIban());
        fillIfMissing(model, "bankverbindung_bic", r.getBic());
        fillIfMissing(model, "zahlungsbedingungen", r.getZahlungsbedingungen());
        fillIfMissing(model, "kaeufer_name", r.getName());
        fillIfMissing(model, "kaeufer_strasse", r.getStrasse());
        fillIfMissing(model, "kaeufer_plz", r.getPlz());
        fillIfMissing(model, "kaeufer_ort", r.getOrt());
        fillIfMissing(model, "kaeufer_land", r.getLand());
        return true;
    }

    private void fillIfMissing(ObjectNode model, String field, String value) {
        if (value != null && !value.isBlank() && modelJson.fehlt(model, "kopf", field)) {
            modelJson.set(model, "kopf", field, value, "stammdaten", "sicher");
        }
    }
}
