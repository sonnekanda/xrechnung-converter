-- Ordner-Automatik: Herkunft der Rechnung + Schutz vor Doppelverarbeitung.
ALTER TABLE invoice ADD COLUMN IF NOT EXISTS source VARCHAR(20) NOT NULL DEFAULT 'UPLOAD';
ALTER TABLE invoice ADD COLUMN IF NOT EXISTS watch_basename VARCHAR(255);

-- Verarbeitete Eingangsdateien werden per Inhalts-Hash vermerkt, damit die
-- erneute Ablage identischer Dateien nicht zu Doppelverarbeitung fuehrt.
CREATE TABLE watch_processed (
    id           UUID PRIMARY KEY,
    content_hash VARCHAR(64)  NOT NULL UNIQUE,
    basename     VARCHAR(255),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
