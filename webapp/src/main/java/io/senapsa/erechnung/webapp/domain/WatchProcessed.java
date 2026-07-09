package io.senapsa.erechnung.webapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Vermerk verarbeiteter Eingangsdateien (Schutz vor Doppelverarbeitung). */
@Entity
@Table(name = "watch_processed")
public class WatchProcessed {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "content_hash", nullable = false, unique = true)
    private String contentHash;

    private String basename;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected WatchProcessed() {
    }

    public WatchProcessed(String contentHash, String basename) {
        this.contentHash = contentHash;
        this.basename = basename;
    }

    public UUID getId() {
        return id;
    }

    public String getContentHash() {
        return contentHash;
    }
}
