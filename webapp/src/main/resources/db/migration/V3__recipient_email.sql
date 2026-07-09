-- Elektronische Adresse des Empfängers (BT-49, XRechnung-Pflicht).
ALTER TABLE recipient ADD COLUMN IF NOT EXISTS email VARCHAR(255);
