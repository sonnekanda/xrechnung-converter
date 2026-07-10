-- Laufzeit-Einstellungen der Ordner-Automatik (Single-Tenant: ein Datensatz),
-- im Admin-Bereich pflegbar ohne Neustart. Die Basis-Mountpfade bleiben in der
-- .env/Compose; hier werden nur (Unter-)Ordner und Verhalten konfiguriert.
CREATE TABLE app_settings (
    id                     UUID PRIMARY KEY,
    watch_enabled          BOOLEAN NOT NULL DEFAULT FALSE,
    watch_input_subdir     VARCHAR(255) NOT NULL DEFAULT '',
    watch_output_subdir    VARCHAR(255) NOT NULL DEFAULT '',
    watch_interval_seconds INT     NOT NULL DEFAULT 60,
    watch_settle_seconds   INT     NOT NULL DEFAULT 10,
    auto_confirm_yellow    BOOLEAN NOT NULL DEFAULT FALSE
);
