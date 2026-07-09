package io.senapsa.erechnung.webapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Fachliche Pruefung des internen Modells: Pflichtfelder, Summenlogik,
 * Rundungstoleranz. Ergebnis steuert die Exportsperre (kein Export bei Fehlern
 * oder unbestaetigten/unsicheren Feldern). Meldungen in Alltagssprache.
 */
@Service
public class FachpruefungService {

    // Pflichtfelder Kopf (Minimum fuer eine gueltige XRechnung / B2G).
    private static final List<String> PFLICHT_KOPF = List.of(
            "rechnungsnummer", "rechnungsdatum", "waehrung",
            "verkaeufer_name", "kaeufer_name", "leitweg_id");
    private static final List<String> PFLICHT_SUMMEN = List.of(
            "nettobetrag", "steuerbetrag", "bruttobetrag");
    private static final List<String> PFLICHT_POSITION = List.of(
            "beschreibung", "menge", "einheit", "einzelpreis", "gesamtpreis", "steuersatz");

    private final ModelJson modelJson;
    private final BigDecimal toleranz;

    public FachpruefungService(ModelJson modelJson,
                               @Value("${erechnung.rounding-tolerance:0.02}") String tolerance) {
        this.modelJson = modelJson;
        this.toleranz = new BigDecimal(tolerance);
    }

    public Report pruefe(JsonNode model) {
        List<Issue> issues = new ArrayList<>();

        String dokumentart = model.path("dokumentart").asText("unbekannt");
        if (!"rechnung".equals(dokumentart)) {
            issues.add(new Issue("dokumentart",
                    "Nur normale Rechnungen koennen exportiert werden. Gutschriften/Stornos sind derzeit nicht vorgesehen."));
        }

        for (String f : PFLICHT_KOPF) {
            checkField(model, "kopf", f, issues);
        }
        for (String f : PFLICHT_SUMMEN) {
            checkField(model, "summen", f, issues);
        }

        JsonNode positionen = model.path("positionen");
        if (!positionen.isArray() || positionen.isEmpty()) {
            issues.add(new Issue("positionen", "Es wurde keine Position erkannt - bitte mindestens eine Position ergaenzen."));
        } else {
            for (int i = 0; i < positionen.size(); i++) {
                JsonNode pos = positionen.get(i);
                for (String f : PFLICHT_POSITION) {
                    checkPosition(pos, i + 1, f, issues);
                }
            }
        }

        checkSummen(model, issues);

        boolean exportReady = issues.isEmpty();
        return new Report(exportReady, issues);
    }

    private void checkField(JsonNode model, String block, String field, List<Issue> issues) {
        JsonNode f = model.path(block).path(field);
        String status = f.path("status").asText("fehlt");
        if (modelJson.fehlt(model, block, field)) {
            issues.add(new Issue(field, hinweisOr(f, "„" + label(field) + "\" fehlt - bitte ergaenzen.")));
        } else if ("unsicher".equals(status)) {
            issues.add(new Issue(field, hinweisOr(f, "„" + label(field) + "\" ist unsicher - bitte pruefen und bestaetigen.")));
        }
    }

    private void checkPosition(JsonNode pos, int nr, String field, List<Issue> issues) {
        JsonNode f = pos.path(field);
        JsonNode w = f.path("wert");
        boolean missing = w.isMissingNode() || w.isNull() || w.asText().isBlank()
                || "fehlt".equals(f.path("status").asText());
        if (missing) {
            issues.add(new Issue("position[" + nr + "]." + field,
                    "Position " + nr + ": „" + label(field) + "\" fehlt - bitte ergaenzen."));
        } else if ("unsicher".equals(f.path("status").asText())) {
            issues.add(new Issue("position[" + nr + "]." + field,
                    "Position " + nr + ": „" + label(field) + "\" ist unsicher - bitte bestaetigen."));
        }
    }

    private void checkSummen(JsonNode model, List<Issue> issues) {
        BigDecimal netto = dec(modelJson.wert(model, "summen", "nettobetrag"));
        BigDecimal steuer = dec(modelJson.wert(model, "summen", "steuerbetrag"));
        BigDecimal brutto = dec(modelJson.wert(model, "summen", "bruttobetrag"));
        if (netto != null && steuer != null && brutto != null) {
            BigDecimal diff = netto.add(steuer).subtract(brutto).abs();
            if (diff.compareTo(toleranz) > 0) {
                issues.add(new Issue("summen",
                        "Netto + Steuer ergeben nicht den Bruttobetrag (Abweichung " + diff + "). Bitte Betraege pruefen."));
            }
        }
    }

    private static BigDecimal dec(String s) {
        try {
            return s == null ? null : new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String hinweisOr(JsonNode field, String fallback) {
        String h = field.path("hinweis").asText(null);
        return (h != null && !h.isBlank()) ? h : fallback;
    }

    private static String label(String field) {
        return switch (field) {
            case "rechnungsnummer" -> "Rechnungsnummer";
            case "rechnungsdatum" -> "Rechnungsdatum";
            case "waehrung" -> "Waehrung";
            case "verkaeufer_name" -> "Verkaeufer";
            case "kaeufer_name" -> "Kaeufer";
            case "leitweg_id" -> "Leitweg-ID";
            case "nettobetrag" -> "Nettobetrag";
            case "steuerbetrag" -> "Steuerbetrag";
            case "bruttobetrag" -> "Bruttobetrag";
            case "beschreibung" -> "Beschreibung";
            case "menge" -> "Menge";
            case "einheit" -> "Einheit";
            case "einzelpreis" -> "Einzelpreis";
            case "gesamtpreis" -> "Gesamtpreis";
            case "steuersatz" -> "Steuersatz";
            default -> field;
        };
    }

    public record Issue(String feld, String meldung) {
    }

    public record Report(boolean exportReady, List<Issue> issues) {
    }
}
