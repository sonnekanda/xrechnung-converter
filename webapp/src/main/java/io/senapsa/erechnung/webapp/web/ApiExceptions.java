package io.senapsa.erechnung.webapp.web;

/** Fachliche Ausnahmen mit Meldungen in Alltagssprache (keine Stacktraces nach aussen). */
public final class ApiExceptions {

    private ApiExceptions() {
    }

    /** 404 - Ressource nicht gefunden. */
    public static class NotFound extends RuntimeException {
        public NotFound(String message) {
            super(message);
        }
    }

    /** 409 - unzulaessiger Zustandsuebergang (z.B. Export vor Validierung). */
    public static class Conflict extends RuntimeException {
        public Conflict(String message) {
            super(message);
        }
    }

    /** 400/422 - Eingabe unvollstaendig oder fachlich fehlerhaft. */
    public static class BadRequest extends RuntimeException {
        public BadRequest(String message) {
            super(message);
        }
    }
}
