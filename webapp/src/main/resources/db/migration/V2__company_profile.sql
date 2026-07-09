-- Eigene Firma (Verkäufer-Stammdaten). Single-Tenant: genau ein Datensatz.
-- Wird in jede Rechnung als Verkäufer/Zahlungsweg automatisch eingefüllt.
CREATE TABLE company_profile (
    id                  UUID PRIMARY KEY,
    name                VARCHAR(255) NOT NULL,
    strasse             VARCHAR(255),
    plz                 VARCHAR(20),
    ort                 VARCHAR(255),
    land                VARCHAR(5)   DEFAULT 'DE',
    ust_id              VARCHAR(50),
    steuernummer        VARCHAR(50),
    kontakt_name        VARCHAR(255),
    email               VARCHAR(255),
    telefon             VARCHAR(50),
    iban                VARCHAR(50),
    bic                 VARCHAR(20),
    zahlungsbedingungen VARCHAR(255)
);

-- IBAN/BIC gehören zum Verkäufer (Zahlungsweg), nicht zum Empfänger.
ALTER TABLE recipient DROP COLUMN IF EXISTS iban;
ALTER TABLE recipient DROP COLUMN IF EXISTS bic;
