package io.senapsa.erechnung.webapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Eine hochgeladene Rechnung samt internem Modell (als JSON-Text), Status und
 * Generierungs-/Validierungsergebnis. Das interne Modell ist die einzige
 * Zwischenschicht; die Zieldatei (UBL) wird ausschliesslich daraus erzeugt.
 */
@Entity
@Table(name = "invoice")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "rechnungsnummer")
    private String rechnungsnummer;

    @Column(name = "pdf_filename")
    private String pdfFilename;

    @Column(name = "csv_filename")
    private String csvFilename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status = InvoiceStatus.HOCHGELADEN;

    @Column(name = "dokumentart")
    private String dokumentart;

    @Column(name = "mapping_version")
    private String mappingVersion;

    @Column(name = "needs_ocr", nullable = false)
    private boolean needsOcr = false;

    /** Internes Rechnungsmodell als JSON-Text. */
    @Column(name = "model_json", columnDefinition = "text")
    private String modelJson;

    /** Erzeugte XRechnung (UBL) als Base64. */
    @Column(name = "ubl_xml_base64", columnDefinition = "text")
    private String ublXmlBase64;

    /** Amtlicher Validierungsbericht (XML-Text). */
    @Column(name = "validation_report", columnDefinition = "text")
    private String validationReport;

    @Column(name = "valid", nullable = false)
    private boolean valid = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public String getRechnungsnummer() {
        return rechnungsnummer;
    }

    public void setRechnungsnummer(String rechnungsnummer) {
        this.rechnungsnummer = rechnungsnummer;
    }

    public String getPdfFilename() {
        return pdfFilename;
    }

    public void setPdfFilename(String pdfFilename) {
        this.pdfFilename = pdfFilename;
    }

    public String getCsvFilename() {
        return csvFilename;
    }

    public void setCsvFilename(String csvFilename) {
        this.csvFilename = csvFilename;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(InvoiceStatus status) {
        this.status = status;
    }

    public String getDokumentart() {
        return dokumentart;
    }

    public void setDokumentart(String dokumentart) {
        this.dokumentart = dokumentart;
    }

    public String getMappingVersion() {
        return mappingVersion;
    }

    public void setMappingVersion(String mappingVersion) {
        this.mappingVersion = mappingVersion;
    }

    public boolean isNeedsOcr() {
        return needsOcr;
    }

    public void setNeedsOcr(boolean needsOcr) {
        this.needsOcr = needsOcr;
    }

    public String getModelJson() {
        return modelJson;
    }

    public void setModelJson(String modelJson) {
        this.modelJson = modelJson;
    }

    public String getUblXmlBase64() {
        return ublXmlBase64;
    }

    public void setUblXmlBase64(String ublXmlBase64) {
        this.ublXmlBase64 = ublXmlBase64;
    }

    public String getValidationReport() {
        return validationReport;
    }

    public void setValidationReport(String validationReport) {
        this.validationReport = validationReport;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
