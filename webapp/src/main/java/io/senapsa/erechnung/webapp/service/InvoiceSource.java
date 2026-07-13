package io.senapsa.erechnung.webapp.service;

import java.util.List;

/**
 * Vorbereitete Schnittstelle für aktive <b>Pull-Quellen</b> von Rechnungen –
 * externe Systeme, die die Anwendung selbst abfragt (im Gegensatz zum
 * Upload, der vom Benutzer <i>gepusht</i> wird).
 *
 * <p>Alle Wege enden im gemeinsamen Verarbeitungspfad
 * {@link UploadService#ingest(String, byte[], String, byte[], String, String)} –
 * es gibt bewusst keinen Sonderpfad je Quelle. Aktiv sind heute zwei Wege:
 * <ul>
 *   <li><b>Upload</b> (Push, benutzergesteuert) – {@link UploadService}</li>
 *   <li><b>Ordner-Automatik</b> (Pull, pollt einen Übergabeordner) – {@link WatchFolderService}</li>
 * </ul>
 *
 * <p>Diese Schnittstelle verallgemeinert den Pull-Fall für <em>weitere</em>
 * Quellen (z.&nbsp;B. direkte Abas-ERP-Anbindung per REST). Sie ist gemäß
 * Entwicklungsrichtlinie „ab Start vorgesehen, Umsetzung als eigener Baustein":
 * eine Implementierung liefert nur die Rohdokumente einer Rechnung; das Anlegen,
 * Speichern und Einreihen in die Extraktion übernimmt unverändert
 * {@code UploadService.ingest(...)}. Weitere ERP-Systeme sind laut Aufgabenscope
 * ausdrücklich „Später".
 *
 * @see AbasApiInvoiceSource vorbereiteter, noch nicht implementierter Abas-Adapter
 */
public interface InvoiceSource {

    /**
     * Stabiler Bezeichner der Quelle; wird als {@code Invoice.source} gespeichert
     * (analog zu {@code "UPLOAD"} / {@code "WATCH"}), damit die Herkunft jeder
     * Rechnung nachvollziehbar bleibt.
     */
    String id();

    /** Für Menschen lesbarer Name (Admin-Oberfläche, Protokoll). */
    String displayName();

    /**
     * Ob die Quelle jetzt abgefragt werden darf (aktiviert, konfiguriert,
     * erreichbar). Ein künftiger generischer Scheduler ruft {@link #poll()} nur,
     * wenn dies {@code true} ist. Solange eine Quelle nur vorbereitet ist, gibt
     * sie {@code false} zurück und wird nie gepollt.
     */
    boolean available();

    /**
     * Holt neu bereitstehende Rechnungen als Rohdokumente ab. Jede Gruppe wird
     * anschließend über {@code UploadService.ingest(...)} in denselben
     * Verarbeitungs- und Prüfweg gegeben wie ein Upload.
     */
    List<SourceDocumentGroup> poll();

    /**
     * Rohdokumente einer einzelnen Rechnung – exakt das Material, das
     * {@link UploadService#ingest(String, byte[], String, byte[], String, String)}
     * benötigt. PDF und/oder CSV können {@code null} sein (mind. eines gesetzt).
     *
     * @param basename gemeinsamer Basisname zur PDF/CSV-Zuordnung (wie beim Upload)
     */
    record SourceDocumentGroup(String basename,
                               String pdfName, byte[] pdfBytes,
                               String csvName, byte[] csvBytes) {
    }
}
