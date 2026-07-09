-- Basisschema: Benutzer, Rechnungen, Stammdaten, Audit-Log.
-- Versionierung ueber Flyway (weitere Migrationen: V2__..., V3__...).

CREATE TABLE app_user (
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    nav_expanded  BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE recipient (
    id                  UUID PRIMARY KEY,
    name                VARCHAR(255) NOT NULL,
    ust_id              VARCHAR(50),
    kundennummer        VARCHAR(50),
    leitweg_id          VARCHAR(100),
    strasse             VARCHAR(255),
    plz                 VARCHAR(20),
    ort                 VARCHAR(255),
    land                VARCHAR(5),
    iban                VARCHAR(50),
    bic                 VARCHAR(20),
    zahlungsbedingungen VARCHAR(255)
);

CREATE INDEX idx_recipient_ust_id ON recipient (ust_id);
CREATE INDEX idx_recipient_kundennummer ON recipient (kundennummer);

CREATE TABLE invoice (
    id                UUID PRIMARY KEY,
    rechnungsnummer   VARCHAR(100),
    pdf_filename      VARCHAR(255),
    csv_filename      VARCHAR(255),
    status            VARCHAR(30)  NOT NULL,
    dokumentart       VARCHAR(30),
    mapping_version   VARCHAR(50),
    needs_ocr         BOOLEAN      NOT NULL DEFAULT FALSE,
    model_json        TEXT,
    ubl_xml_base64    TEXT,
    validation_report TEXT,
    valid             BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_invoice_status ON invoice (status);
CREATE INDEX idx_invoice_created_at ON invoice (created_at DESC);

CREATE TABLE audit_event (
    id         UUID PRIMARY KEY,
    invoice_id UUID,
    action     VARCHAR(100) NOT NULL,
    detail     TEXT,
    actor      VARCHAR(255),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_invoice ON audit_event (invoice_id, created_at);
