package io.senapsa.erechnung.webapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;

/** Kleine Helfer zum Lesen/Schreiben von Feldern im internen Modell (JSON). */
@Component
public class ModelJson {

    private final ObjectMapper mapper;

    public ModelJson(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectNode parse(String json) {
        try {
            return (ObjectNode) mapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Internes Modell ist kein gueltiges JSON", e);
        }
    }

    public String write(JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("Internes Modell konnte nicht serialisiert werden", e);
        }
    }

    /** Wert eines Feldes in einem Block (kopf/summen) oder null. */
    public String wert(JsonNode model, String block, String field) {
        JsonNode w = model.path(block).path(field).path("wert");
        return w.isMissingNode() || w.isNull() ? null : w.asText();
    }

    /** Herkunft eines Feldes (pdf/csv/ocr/ki/stammdaten/manuell/berechnet) oder null. */
    public String herkunft(JsonNode model, String block, String field) {
        JsonNode h = model.path(block).path(field).path("herkunft");
        return h.isMissingNode() || h.isNull() ? null : h.asText();
    }

    /** true, wenn das Feld fehlt (nicht vorhanden, ohne Wert oder Status "fehlt"). */
    public boolean fehlt(JsonNode model, String block, String field) {
        JsonNode f = model.path(block).path(field);
        if (f.isMissingNode()) {
            return true;
        }
        JsonNode w = f.path("wert");
        return w.isMissingNode() || w.isNull() || w.asText().isBlank()
                || "fehlt".equals(f.path("status").asText());
    }

    /** Setzt/ueberschreibt ein Feld im Block. */
    public void set(ObjectNode model, String block, String field,
                    String wert, String herkunft, String status) {
        JsonNode existing = model.get(block);
        ObjectNode blockNode = existing instanceof ObjectNode on ? on : model.putObject(block);
        writeField(blockNode, field, wert, herkunft, status);
    }

    /** Setzt/ueberschreibt ein Feld innerhalb einer Position. */
    public void setPosition(ObjectNode model, int index, String field,
                            String wert, String herkunft, String status) {
        JsonNode positionen = model.get("positionen");
        if (!(positionen instanceof com.fasterxml.jackson.databind.node.ArrayNode arr)
                || index < 0 || index >= arr.size()
                || !(arr.get(index) instanceof ObjectNode pos)) {
            throw new IllegalArgumentException("Position " + (index + 1) + " existiert nicht.");
        }
        writeField(pos, field, wert, herkunft, status);
    }

    private void writeField(ObjectNode container, String field, String wert,
                            String herkunft, String status) {
        ObjectNode f = container.putObject(field);
        f.put("wert", wert);
        f.put("herkunft", herkunft);
        f.put("status", status);
    }

    /**
     * Haengt eine leere Position an und liefert deren Index. Die uebergebenen
     * Pflichtfelder werden als "fehlt" (rot) angelegt, damit der Nutzer sie im
     * Pruefbildschirm ausfuellt; die Zeilennummern werden fortlaufend vergeben.
     */
    public int addPosition(ObjectNode model, List<String> pflichtfelder) {
        ArrayNode arr = model.get("positionen") instanceof ArrayNode a ? a : model.putArray("positionen");
        ObjectNode pos = arr.addObject();
        writeField(pos, "artikelnummer", null, "manuell", "fehlt");
        for (String f : pflichtfelder) {
            writeField(pos, f, null, "manuell", "fehlt");
        }
        renumberPositionen(arr);
        return arr.size() - 1;
    }

    /** Entfernt die Position am Index und nummeriert die restlichen neu durch. */
    public void removePosition(ObjectNode model, int index) {
        if (!(model.get("positionen") instanceof ArrayNode arr)
                || index < 0 || index >= arr.size()) {
            throw new IllegalArgumentException("Position " + (index + 1) + " existiert nicht.");
        }
        arr.remove(index);
        renumberPositionen(arr);
    }

    /** Setzt die reine Zeilennummer ("position") jeder Position auf 1..N. */
    private void renumberPositionen(ArrayNode arr) {
        for (int i = 0; i < arr.size(); i++) {
            if (arr.get(i) instanceof ObjectNode pos) {
                writeField(pos, "position", String.valueOf(i + 1), "manuell", "sicher");
            }
        }
    }
}
