package io.senapsa.erechnung.webapp.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Leitet Client-seitige Routen der Single-Page-App (z.B. /ordner-automatik,
 * /pruefen/{id}) auf die index.html weiter, damit Deep-Links und Neuladen (F5)
 * funktionieren. Nur Pfade ohne Punkt (also keine Dateien wie *.js/*.css) und
 * ohne /api- bzw. /actuator-Präfix werden weitergeleitet; statische Dateien und
 * die REST-Controller haben Vorrang.
 */
@Controller
public class SpaForwardController {

    @GetMapping(value = {"/{path:[^.]*}", "/{p1}/{path:[^.]*}", "/{p1}/{p2}/{path:[^.]*}"})
    public String forward() {
        return "forward:/index.html";
    }
}
