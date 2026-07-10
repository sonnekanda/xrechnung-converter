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
}
