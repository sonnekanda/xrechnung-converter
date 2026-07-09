package io.senapsa.erechnung.webapp.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;

/**
 * Wandelt Ausnahmen in verstaendliche Meldungen (Alltagssprache) um.
 * Keine Stacktraces/technischen Details nach aussen.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiExceptions.NotFound.class)
    public ResponseEntity<ApiError> notFound(ApiExceptions.NotFound ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ApiExceptions.Conflict.class)
    public ResponseEntity<ApiError> conflict(ApiExceptions.Conflict ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ApiExceptions.BadRequest.class)
    public ResponseEntity<ApiError> badRequest(ApiExceptions.BadRequest ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException ex) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE,
                "Die Datei ist zu gross. Bitte bleiben Sie unter dem erlaubten Limit.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> generic(Exception ex) {
        // Technische Details nur ins Log, nicht an den Benutzer.
        log.error("Unerwarteter Fehler", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Es ist ein unerwarteter Fehler aufgetreten. Bitte versuchen Sie es erneut.");
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), message, Instant.now()));
    }

    public record ApiError(int status, String meldung, Instant zeitpunkt) {
    }
}
