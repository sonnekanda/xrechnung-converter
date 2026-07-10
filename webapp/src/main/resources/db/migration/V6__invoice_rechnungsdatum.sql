-- Rechnungsdatum als eigene Spalte (aus dem internen Modell befüllt), für
-- Anzeige, Sortierung und Datumsfilter in der Übersicht.
ALTER TABLE invoice ADD COLUMN IF NOT EXISTS rechnungsdatum DATE;
CREATE INDEX IF NOT EXISTS idx_invoice_rechnungsdatum ON invoice (rechnungsdatum);
