-- KI-Assistent: Laufzeit-Einstellungen des optionalen LLM-Fallbacks im
-- extraction-worker, im Admin-Bereich pflegbar (Single-Tenant: derselbe eine
-- app_settings-Datensatz). Die .env liefert nur die Erst-Vorgaben; danach ist
-- die DB massgeblich und wird bei jeder Extraktion an den Worker uebergeben.
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS ki_enabled         BOOLEAN      NOT NULL DEFAULT FALSE;
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS ki_model           VARCHAR(120) NOT NULL DEFAULT 'openai/gpt-4o-mini';
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS ki_api_key         VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS ki_timeout_seconds INT          NOT NULL DEFAULT 30;
