package io.senapsa.erechnung.webapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Empfaenger-Stammdaten. Fehlende Pflichtfelder des internen Modells werden je
 * Empfaenger automatisch ergaenzt (Autofill), Zuordnung ueber USt-IdNr oder
 * Kundennummer.
 */
@Entity
@Table(name = "recipient")
public class Recipient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** Zuordnungsschluessel: USt-IdNr des Empfaengers. */
    @Column(name = "ust_id")
    private String ustId;

    /** Zuordnungsschluessel: Kundennummer. */
    @Column(name = "kundennummer")
    private String kundennummer;

    @Column(name = "leitweg_id")
    private String leitwegId;

    private String strasse;
    private String plz;
    private String ort;
    private String land = "DE";

    private String iban;
    private String bic;

    @Column(name = "zahlungsbedingungen")
    private String zahlungsbedingungen;

    // Getter/Setter
    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUstId() {
        return ustId;
    }

    public void setUstId(String ustId) {
        this.ustId = ustId;
    }

    public String getKundennummer() {
        return kundennummer;
    }

    public void setKundennummer(String kundennummer) {
        this.kundennummer = kundennummer;
    }

    public String getLeitwegId() {
        return leitwegId;
    }

    public void setLeitwegId(String leitwegId) {
        this.leitwegId = leitwegId;
    }

    public String getStrasse() {
        return strasse;
    }

    public void setStrasse(String strasse) {
        this.strasse = strasse;
    }

    public String getPlz() {
        return plz;
    }

    public void setPlz(String plz) {
        this.plz = plz;
    }

    public String getOrt() {
        return ort;
    }

    public void setOrt(String ort) {
        this.ort = ort;
    }

    public String getLand() {
        return land;
    }

    public void setLand(String land) {
        this.land = land;
    }

    public String getIban() {
        return iban;
    }

    public void setIban(String iban) {
        this.iban = iban;
    }

    public String getBic() {
        return bic;
    }

    public void setBic(String bic) {
        this.bic = bic;
    }

    public String getZahlungsbedingungen() {
        return zahlungsbedingungen;
    }

    public void setZahlungsbedingungen(String zahlungsbedingungen) {
        this.zahlungsbedingungen = zahlungsbedingungen;
    }
}
