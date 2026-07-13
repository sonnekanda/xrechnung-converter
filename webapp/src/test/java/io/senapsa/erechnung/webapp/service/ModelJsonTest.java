package io.senapsa.erechnung.webapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelJsonTest {

    private final ModelJson modelJson = new ModelJson(new ObjectMapper());

    private ObjectNode modellMit(int positionen) {
        ObjectNode m = new ObjectMapper().createObjectNode();
        var arr = m.putArray("positionen");
        for (int i = 0; i < positionen; i++) {
            ObjectNode p = arr.addObject();
            p.putObject("artikelnummer").put("wert", "1000" + i).put("herkunft", "csv").put("status", "sicher");
            p.putObject("position").put("wert", i + 1).put("herkunft", "csv").put("status", "sicher");
        }
        return m;
    }

    @Test
    void addPosition_legt_pflichtfelder_als_fehlt_an() {
        ObjectNode m = modellMit(1);
        int idx = modelJson.addPosition(m, List.of("beschreibung", "menge", "gesamtpreis"));

        assertThat(idx).isEqualTo(1);
        var neu = m.get("positionen").get(1);
        assertThat(neu.path("beschreibung").path("status").asText()).isEqualTo("fehlt");
        assertThat(neu.path("menge").path("herkunft").asText()).isEqualTo("manuell");
        // Zeilennummer wird fortlaufend vergeben.
        assertThat(neu.path("position").path("wert").asText()).isEqualTo("2");
    }

    @Test
    void addPosition_ohne_bestehendes_array_erstellt_es() {
        ObjectNode m = new ObjectMapper().createObjectNode();
        int idx = modelJson.addPosition(m, List.of("beschreibung"));
        assertThat(idx).isEqualTo(0);
        assertThat(m.path("positionen")).hasSize(1);
    }

    @Test
    void removePosition_entfernt_und_nummeriert_neu() {
        ObjectNode m = modellMit(3);
        modelJson.removePosition(m, 0); // erste entfernen

        var arr = m.get("positionen");
        assertThat(arr).hasSize(2);
        // Verbliebene Positionen sind neu durchnummeriert (1, 2).
        assertThat(arr.get(0).path("position").path("wert").asText()).isEqualTo("1");
        assertThat(arr.get(1).path("position").path("wert").asText()).isEqualTo("2");
        // Es blieb die zweite/dritte Position (Artikel 10001/10002).
        assertThat(arr.get(0).path("artikelnummer").path("wert").asText()).isEqualTo("10001");
    }

    @Test
    void removePosition_bei_ungueltigem_index_wirft() {
        ObjectNode m = modellMit(1);
        assertThatThrownBy(() -> modelJson.removePosition(m, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
