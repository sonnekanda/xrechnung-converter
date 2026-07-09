package io.senapsa.erechnung.webapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Eigene Firma (Verkäufer). Single-Tenant: es existiert genau ein Datensatz.
 * Wird als Verkäufer/Zahlungsweg automatisch in jede Rechnung eingefüllt.
 */
@Entity
@Table(name = "company_profile")
public class CompanyProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String strasse;
    private String plz;
    private String ort;
    private String land = "DE";

    @Column(name = "ust_id")
    private String ustId;

    private String steuernummer;

    @Column(name = "kontakt_name")
    private String kontaktName;

    private String email;
    private String telefon;
    private String iban;
    private String bic;

    @Column(name = "zahlungsbedingungen")
    private String zahlungsbedingungen;

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getUstId() {
        return ustId;
    }

    public void setUstId(String ustId) {
        this.ustId = ustId;
    }

    public String getSteuernummer() {
        return steuernummer;
    }

    public void setSteuernummer(String steuernummer) {
        this.steuernummer = steuernummer;
    }

    public String getKontaktName() {
        return kontaktName;
    }

    public void setKontaktName(String kontaktName) {
        this.kontaktName = kontaktName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefon() {
        return telefon;
    }

    public void setTelefon(String telefon) {
        this.telefon = telefon;
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
