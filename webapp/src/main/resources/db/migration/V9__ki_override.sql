-- KI-Assistent: Master-Schalter "Standardeinstellungen ueberschreiben".
-- FALSE (Default) = es gelten die .env-/Installations-Vorgaben (der Worker nutzt
-- seine eigene .env). TRUE = die in app_settings gepflegten KI-Werte werden bei
-- jeder Extraktion an den Worker uebergeben und ueberschreiben die Vorgaben.
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS ki_override BOOLEAN NOT NULL DEFAULT FALSE;
