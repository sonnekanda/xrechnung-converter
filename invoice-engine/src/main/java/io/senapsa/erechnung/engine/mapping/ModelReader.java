package io.senapsa.erechnung.engine.mapping;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Bequemer, defensiver Zugriff auf das interne Rechnungsmodell (JSON).
 * Jedes Feld hat die Form {@code {"wert": ..., "herkunft": ..., "status": ...}}.
 */
public final class ModelReader {

    private final JsonNode root;

    public ModelReader(JsonNode root) {
        this.root = root;
    }

    /** Rohwert eines Feldes aus einem Block (kopf/summen) oder null. */
    public String str(String block, String field) {
        JsonNode f = wert(root.path(block).path(field));
        return f.isMissingNode() || f.isNull() ? null : f.asText();
    }

    public BigDecimal dec(String block, String field) {
        String s = str(block, field);
        return s == null || s.isBlank() ? null : new BigDecimal(s);
    }

    /** ISO-Datum (yyyy-MM-dd) -> Date. */
    public Date date(String block, String field) {
        String s = str(block, field);
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(s);
        } catch (ParseException e) {
            return null;
        }
    }

    public JsonNode positionen() {
        return root.path("positionen");
    }

    public String dokumentart() {
        return root.path("dokumentart").asText("unbekannt");
    }

    /** Wert innerhalb einer Position. */
    public static String posStr(JsonNode pos, String field) {
        JsonNode f = wert(pos.path(field));
        return f.isMissingNode() || f.isNull() ? null : f.asText();
    }

    public static BigDecimal posDec(JsonNode pos, String field) {
        String s = posStr(pos, field);
        return s == null || s.isBlank() ? null : new BigDecimal(s);
    }

    private static JsonNode wert(JsonNode fieldNode) {
        return fieldNode.path("wert");
    }
}
