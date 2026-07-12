package io.senapsa.erechnung.webapp.web;

import io.senapsa.erechnung.webapp.domain.Recipient;
import io.senapsa.erechnung.webapp.repo.RecipientRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Verwaltung der Empfaenger-Stammdaten (nur Rolle ADMIN, siehe SecurityConfig). */
@RestController
@RequestMapping("/api/v1/stammdaten")
public class StammdatenController {

    private final RecipientRepository recipients;

    public StammdatenController(RecipientRepository recipients) {
        this.recipients = recipients;
    }

    @GetMapping
    public List<Recipient> list() {
        return recipients.findAll();
    }

    @PostMapping
    public Recipient create(@RequestBody Recipient recipient) {
        // Eindeutigkeit über den Zuordnungsschlüssel (USt-IdNr bzw. Kundennummer),
        // damit keine Doppel-Empfänger entstehen.
        String ustId = recipient.getUstId();
        if (ustId != null && !ustId.isBlank() && recipients.findFirstByUstId(ustId).isPresent()) {
            throw new ApiExceptions.Conflict(
                    "Ein Empfänger mit dieser USt-IdNr ist bereits vorhanden. Bitte den bestehenden Eintrag bearbeiten.");
        }
        String kundennummer = recipient.getKundennummer();
        if (kundennummer != null && !kundennummer.isBlank()
                && recipients.findFirstByKundennummer(kundennummer).isPresent()) {
            throw new ApiExceptions.Conflict(
                    "Ein Empfänger mit dieser Kundennummer ist bereits vorhanden. Bitte den bestehenden Eintrag bearbeiten.");
        }
        return recipients.save(recipient);
    }

    @PutMapping("/{id}")
    public Recipient update(@PathVariable UUID id, @RequestBody Recipient input) {
        Recipient existing = recipients.findById(id)
                .orElseThrow(() -> new ApiExceptions.NotFound("Stammdatensatz wurde nicht gefunden."));
        // Eindeutigkeit des Zuordnungsschlüssels sichern – aber die eigene Zeile
        // ausnehmen, damit ein Speichern ohne Schlüsseländerung nicht blockiert.
        String ustId = input.getUstId();
        if (ustId != null && !ustId.isBlank()) {
            recipients.findFirstByUstId(ustId)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> { throw new ApiExceptions.Conflict(
                            "Ein anderer Empfänger mit dieser USt-IdNr ist bereits vorhanden."); });
        }
        String kundennummer = input.getKundennummer();
        if (kundennummer != null && !kundennummer.isBlank()) {
            recipients.findFirstByKundennummer(kundennummer)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> { throw new ApiExceptions.Conflict(
                            "Ein anderer Empfänger mit dieser Kundennummer ist bereits vorhanden."); });
        }
        existing.setName(input.getName());
        existing.setUstId(input.getUstId());
        existing.setKundennummer(input.getKundennummer());
        existing.setLeitwegId(input.getLeitwegId());
        existing.setStrasse(input.getStrasse());
        existing.setPlz(input.getPlz());
        existing.setOrt(input.getOrt());
        existing.setLand(input.getLand());
        existing.setEmail(input.getEmail());
        existing.setZahlungsbedingungen(input.getZahlungsbedingungen());
        return recipients.save(existing);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        recipients.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
