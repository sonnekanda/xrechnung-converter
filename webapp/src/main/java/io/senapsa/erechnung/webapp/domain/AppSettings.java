package io.senapsa.erechnung.webapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Laufzeit-Einstellungen der Ordner-Automatik (Single-Tenant: genau ein Datensatz).
 * Im Admin-Bereich pflegbar. Basis-Mountpfade kommen aus der .env; hier werden
 * nur (Unter-)Ordner und Verhalten gesteuert.
 */
@Entity
@Table(name = "app_settings")
public class AppSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "watch_enabled", nullable = false)
    private boolean watchEnabled = false;

    @Column(name = "watch_input_subdir", nullable = false)
    private String watchInputSubdir = "";

    @Column(name = "watch_output_subdir", nullable = false)
    private String watchOutputSubdir = "";

    @Column(name = "watch_interval_seconds", nullable = false)
    private int watchIntervalSeconds = 60;

    /** Wartezeit, bis eine neue Datei als „stabil" gilt (Paarung PDF+CSV). */
    @Column(name = "watch_settle_seconds", nullable = false)
    private int watchSettleSeconds = 10;

    /** Gelbe (unsichere) Felder automatisch bestätigen – ausgenommen OCR. */
    @Column(name = "auto_confirm_yellow", nullable = false)
    private boolean autoConfirmYellow = false;

    /** Zielformat des Auto-Exports: XRECHNUNG (XML), ZUGFERD (PDF) oder BEIDE. */
    @Column(name = "watch_output_format", nullable = false)
    private String watchOutputFormat = "XRECHNUNG";

    // --- KI-Assistent (optionaler LLM-Fallback im extraction-worker) ---------

    /**
     * Master-Schalter: die folgenden KI-Werte ueberschreiben die .env-Vorgaben.
     * FALSE = Standardeinstellungen (Worker nutzt seine eigene .env).
     */
    @Column(name = "ki_override", nullable = false)
    private boolean kiOverride = false;

    /** LLM-Fallback fuer fehlende Felder aktiv (nur wirksam bei kiOverride). */
    @Column(name = "ki_enabled", nullable = false)
    private boolean kiEnabled = false;

    /** Modell-ID bei OpenRouter (z.B. "openai/gpt-4o-mini"). */
    @Column(name = "ki_model", nullable = false)
    private String kiModel = "openai/gpt-4o-mini";

    /** OpenRouter-API-Schluessel. Wird nie an das Frontend zurueckgegeben. */
    @Column(name = "ki_api_key", nullable = false)
    private String kiApiKey = "";

    /** Timeout je KI-Aufruf in Sekunden. */
    @Column(name = "ki_timeout_seconds", nullable = false)
    private int kiTimeoutSeconds = 30;

    public UUID getId() {
        return id;
    }

    public boolean isWatchEnabled() {
        return watchEnabled;
    }

    public void setWatchEnabled(boolean watchEnabled) {
        this.watchEnabled = watchEnabled;
    }

    public String getWatchInputSubdir() {
        return watchInputSubdir;
    }

    public void setWatchInputSubdir(String watchInputSubdir) {
        this.watchInputSubdir = watchInputSubdir == null ? "" : watchInputSubdir;
    }

    public String getWatchOutputSubdir() {
        return watchOutputSubdir;
    }

    public void setWatchOutputSubdir(String watchOutputSubdir) {
        this.watchOutputSubdir = watchOutputSubdir == null ? "" : watchOutputSubdir;
    }

    public int getWatchIntervalSeconds() {
        return watchIntervalSeconds;
    }

    public void setWatchIntervalSeconds(int watchIntervalSeconds) {
        this.watchIntervalSeconds = Math.max(1, watchIntervalSeconds);
    }

    public int getWatchSettleSeconds() {
        return watchSettleSeconds;
    }

    public void setWatchSettleSeconds(int watchSettleSeconds) {
        this.watchSettleSeconds = Math.max(0, watchSettleSeconds);
    }

    public boolean isAutoConfirmYellow() {
        return autoConfirmYellow;
    }

    public void setAutoConfirmYellow(boolean autoConfirmYellow) {
        this.autoConfirmYellow = autoConfirmYellow;
    }

    public String getWatchOutputFormat() {
        return watchOutputFormat;
    }

    public void setWatchOutputFormat(String watchOutputFormat) {
        this.watchOutputFormat = normalizeFormat(watchOutputFormat);
    }

    /** Nur bekannte Werte zulassen; alles andere -> XRECHNUNG (sicherer Default). */
    private static String normalizeFormat(String v) {
        if (v == null) {
            return "XRECHNUNG";
        }
        String u = v.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (u) {
            case "ZUGFERD", "BEIDE", "XRECHNUNG" -> u;
            default -> "XRECHNUNG";
        };
    }

    public boolean isKiOverride() {
        return kiOverride;
    }

    public void setKiOverride(boolean kiOverride) {
        this.kiOverride = kiOverride;
    }

    public boolean isKiEnabled() {
        return kiEnabled;
    }

    public void setKiEnabled(boolean kiEnabled) {
        this.kiEnabled = kiEnabled;
    }

    public String getKiModel() {
        return kiModel;
    }

    public void setKiModel(String kiModel) {
        this.kiModel = (kiModel == null || kiModel.isBlank()) ? "openai/gpt-4o-mini" : kiModel.trim();
    }

    public String getKiApiKey() {
        return kiApiKey;
    }

    public void setKiApiKey(String kiApiKey) {
        this.kiApiKey = kiApiKey == null ? "" : kiApiKey.trim();
    }

    public int getKiTimeoutSeconds() {
        return kiTimeoutSeconds;
    }

    public void setKiTimeoutSeconds(int kiTimeoutSeconds) {
        this.kiTimeoutSeconds = Math.max(1, kiTimeoutSeconds);
    }
}
