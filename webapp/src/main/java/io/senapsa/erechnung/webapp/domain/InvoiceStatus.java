package io.senapsa.erechnung.webapp.domain;

/**
 * Zustaende der Rechnung. Export ist ausschliesslich im Zustand
 * {@link #VALIDIERT} moeglich (technisch nicht umgehbar).
 */
public enum InvoiceStatus {
    HOCHGELADEN,
    IN_VERARBEITUNG,
    PRUEFEN,
    VALIDIERT,
    EXPORTIERT;

    /** Erlaubte Uebergaenge der Statusmaschine. */
    public boolean canTransitionTo(InvoiceStatus target) {
        return switch (this) {
            case HOCHGELADEN -> target == IN_VERARBEITUNG;
            case IN_VERARBEITUNG -> target == PRUEFEN;
            // Aus "pruefen" kann validiert werden; Validierungsfehler bleibt "pruefen".
            case PRUEFEN -> target == VALIDIERT || target == PRUEFEN;
            // Validierungsfehler beim erneuten Lauf -> zurueck zu "pruefen".
            case VALIDIERT -> target == EXPORTIERT || target == PRUEFEN;
            case EXPORTIERT -> false;
        };
    }
}
