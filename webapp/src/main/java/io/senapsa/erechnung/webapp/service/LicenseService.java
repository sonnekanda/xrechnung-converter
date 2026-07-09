package io.senapsa.erechnung.webapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Vorbereitete Schnittstelle zum Lizenzcontroller (Entwicklungsrichtlinie:
 * „ab Start vorsehen, Durchsetzung vor Produktivgang").
 *
 * Ist die Durchsetzung deaktiviert (Default), sind alle Funktionen erlaubt.
 * Der Durchsetzungspunkt {@link #isFeatureAllowed(String)} ist bereits im Code
 * vorhanden und kann später gegen den realen Lizenzcontroller unter
 * {@code LICENSE_CONTROLLER_URL} geführt werden – ohne Umbau der Aufrufstellen.
 */
@Service
public class LicenseService {

    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);

    private final boolean enforcement;
    private final String controllerUrl;
    private final boolean keyConfigured;

    public LicenseService(@Value("${erechnung.license.enforcement:false}") boolean enforcement,
                          @Value("${erechnung.license.controller-url:}") String controllerUrl,
                          @Value("${erechnung.license.key:}") String key) {
        this.enforcement = enforcement;
        this.controllerUrl = controllerUrl;
        this.keyConfigured = key != null && !key.isBlank();
        if (enforcement) {
            log.info("Lizenz-Durchsetzung aktiv (Controller: {})",
                    controllerUrl.isBlank() ? "nicht konfiguriert" : controllerUrl);
        }
    }

    /**
     * Durchsetzungspunkt für den erlaubten Funktionsumfang. Ohne aktivierte
     * Durchsetzung ist alles erlaubt; mit Durchsetzung würde hier der
     * Lizenzcontroller befragt (bis zur Anbindung konservativ: erlaubt, wenn ein
     * Lizenzschlüssel hinterlegt ist).
     */
    public boolean isFeatureAllowed(String feature) {
        if (!enforcement) {
            return true;
        }
        return keyConfigured; // Platzhalter bis zur Controller-Anbindung
    }

    public Status status() {
        return new Status(enforcement, !controllerUrl.isBlank(), keyConfigured);
    }

    public record Status(boolean enforcement, boolean controllerConfigured, boolean keyConfigured) {
    }
}
