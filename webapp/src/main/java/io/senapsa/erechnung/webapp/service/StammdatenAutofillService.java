package io.senapsa.erechnung.webapp.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.senapsa.erechnung.webapp.domain.CompanyProfile;
import io.senapsa.erechnung.webapp.domain.Recipient;
import io.senapsa.erechnung.webapp.repo.RecipientRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Fuellt fehlende Pflichtfelder im internen Modell automatisch:
 *  - Verkaeufer/Zahlungsweg aus der eigenen Firma (Single-Tenant),
 *  - Empfaenger-Felder + Leitweg-ID aus den Empfaenger-Stammdaten
 *    (Zuordnung ueber USt-IdNr, sonst Kundennummer).
 * Gesetzte Werte erhalten {@code herkunft = "stammdaten"} und Status "sicher".
 */
@Service
public class StammdatenAutofillService {

    private final RecipientRepository recipients;
    private final CompanyProfileService companyProfile;
    private final ModelJson modelJson;

    public StammdatenAutofillService(RecipientRepository recipients,
                                     CompanyProfileService companyProfile, ModelJson modelJson) {
        this.recipients = recipients;
        this.companyProfile = companyProfile;
        this.modelJson = modelJson;
    }

    /** Ergaenzt das Modell (Verkaeufer + Empfaenger) und liefert true bei Empfaenger-Treffer. */
    public boolean apply(ObjectNode model) {
        applyCompany(model);
        return applyRecipient(model);
    }

    /** Verkaeufer/Zahlungsweg aus der eigenen Firma. */
    private void applyCompany(ObjectNode model) {
        CompanyProfile c = companyProfile.find().orElse(null);
        if (c == null) {
            return;
        }
        fillIfMissing(model, "verkaeufer_name", c.getName());
        fillIfMissing(model, "verkaeufer_strasse", c.getStrasse());
        fillIfMissing(model, "verkaeufer_plz", c.getPlz());
        fillIfMissing(model, "verkaeufer_ort", c.getOrt());
        fillIfMissing(model, "verkaeufer_land", c.getLand());
        fillIfMissing(model, "verkaeufer_ustid", c.getUstId());
        fillIfMissing(model, "verkaeufer_steuernummer", c.getSteuernummer());
        fillIfMissing(model, "verkaeufer_kontakt_name", c.getKontaktName());
        fillIfMissing(model, "verkaeufer_email", c.getEmail());
        fillIfMissing(model, "verkaeufer_telefon", c.getTelefon());
        fillIfMissing(model, "bankverbindung_iban", c.getIban());
        fillIfMissing(model, "bankverbindung_bic", c.getBic());
        fillIfMissing(model, "zahlungsbedingungen", c.getZahlungsbedingungen());
    }

    /** Empfaenger-Felder + Leitweg-ID aus den Empfaenger-Stammdaten. */
    private boolean applyRecipient(ObjectNode model) {
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
        fillIfMissing(model, "zahlungsbedingungen", r.getZahlungsbedingungen());
        fillIfMissing(model, "kaeufer_name", r.getName());
        fillIfMissing(model, "kaeufer_strasse", r.getStrasse());
        fillIfMissing(model, "kaeufer_plz", r.getPlz());
        fillIfMissing(model, "kaeufer_ort", r.getOrt());
        fillIfMissing(model, "kaeufer_land", r.getLand());
        fillIfMissing(model, "kaeufer_email", r.getEmail());
        return true;
    }

    private void fillIfMissing(ObjectNode model, String field, String value) {
        if (value != null && !value.isBlank() && modelJson.fehlt(model, "kopf", field)) {
            modelJson.set(model, "kopf", field, value, "stammdaten", "sicher");
        }
    }
}
