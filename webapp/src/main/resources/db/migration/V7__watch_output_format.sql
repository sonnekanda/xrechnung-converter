-- Ordner-Automatik: Zielformat des Auto-Exports (XRechnung-XML, ZUGFeRD-PDF oder beides).
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS watch_output_format VARCHAR(20) NOT NULL DEFAULT 'XRECHNUNG';
