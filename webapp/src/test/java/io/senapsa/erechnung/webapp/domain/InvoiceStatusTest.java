package io.senapsa.erechnung.webapp.domain;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vollständige Übergangsmatrix der Statusmaschine (A-12). Die Exportsperre
 * stützt sich darauf, dass NUR die hier erlaubten Übergänge möglich sind –
 * jede Änderung an der Maschine muss diesen Test bewusst anfassen.
 */
class InvoiceStatusTest {

    private static final Map<InvoiceStatus, Set<InvoiceStatus>> ERLAUBT = Map.of(
            InvoiceStatus.HOCHGELADEN, EnumSet.of(InvoiceStatus.IN_VERARBEITUNG),
            InvoiceStatus.IN_VERARBEITUNG, EnumSet.of(InvoiceStatus.PRUEFEN),
            // Validierungsfehler bleibt "prüfen" (Selbstübergang erlaubt).
            InvoiceStatus.PRUEFEN, EnumSet.of(InvoiceStatus.VALIDIERT, InvoiceStatus.PRUEFEN),
            // Erneuter Lauf kann fehlschlagen -> zurück zu "prüfen".
            InvoiceStatus.VALIDIERT, EnumSet.of(InvoiceStatus.EXPORTIERT, InvoiceStatus.PRUEFEN),
            InvoiceStatus.EXPORTIERT, EnumSet.noneOf(InvoiceStatus.class));

    @Test
    void uebergangsmatrix_vollstaendig() {
        for (InvoiceStatus von : InvoiceStatus.values()) {
            for (InvoiceStatus nach : InvoiceStatus.values()) {
                boolean erwartet = ERLAUBT.get(von).contains(nach);
                assertThat(von.canTransitionTo(nach))
                        .as("%s -> %s", von, nach)
                        .isEqualTo(erwartet);
            }
        }
    }

    @Test
    void exportiert_ist_endzustand() {
        for (InvoiceStatus ziel : InvoiceStatus.values()) {
            assertThat(InvoiceStatus.EXPORTIERT.canTransitionTo(ziel))
                    .as("EXPORTIERT -> %s muss gesperrt sein", ziel)
                    .isFalse();
        }
    }
}
