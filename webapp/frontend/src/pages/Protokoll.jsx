import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";

// Sprechende Beschriftungen für die Audit-Aktionen (Alltagssprache).
const AKTION_LABEL = {
  HOCHGELADEN: "Hochgeladen",
  EXTRAKTION_EINGEREIHT: "Verarbeitung gestartet",
  EXTRAKTION_FERTIG: "Daten ausgelesen",
  FELD_KORRIGIERT: "Feld korrigiert",
  AUTO_BESTAETIGT: "Automatisch bestätigt",
  AUTO_PRUEFUNG_OFFEN: "Automatik: Prüfung nötig",
  FACHPRUEFUNG_OFFEN: "Prüfung offen",
  AENDERUNG_NACH_VALIDIERUNG: "Geändert nach Prüfung",
  VALIDIERT: "Amtlich validiert",
  VALIDIERUNG_FEHLGESCHLAGEN: "Validierung fehlgeschlagen",
  ZUR_BEARBEITUNG: "Zur Bearbeitung freigegeben",
  EXPORTIERT: "Exportiert",
  RECHNUNG_GELOESCHT: "Rechnung gelöscht",
  ORDNER_DUPLIKAT: "Ordner: Duplikat übersprungen",
  BENUTZER_ANGELEGT: "Benutzer angelegt",
  BENUTZER_GELOESCHT: "Benutzer gelöscht",
  LOGIN_OK: "Anmeldung",
  LOGIN_FEHLGESCHLAGEN: "Anmeldung fehlgeschlagen",
  LOGIN_GESPERRT: "Konto gesperrt",
};

function zeit(iso) {
  const d = new Date(iso);
  return d.toLocaleDateString("de-DE") + " " + d.toLocaleTimeString("de-DE");
}

export default function Protokoll() {
  const [eintraege, setEintraege] = useState(null);
  const [error, setError] = useState(null);
  const [filter, setFilter] = useState("");

  const load = () =>
    api.protokoll().then(setEintraege).catch((e) => setError(e.message));

  useEffect(() => { load(); }, []);

  const gefiltert = useMemo(() => {
    if (!eintraege) return [];
    const q = filter.trim().toLowerCase();
    if (!q) return eintraege;
    return eintraege.filter((e) =>
      [e.aktion, AKTION_LABEL[e.aktion], e.detail, e.benutzer, e.rechnungsnummer]
        .some((v) => v && v.toLowerCase().includes(q)));
  }, [eintraege, filter]);

  if (error) return <div className="panel"><div className="banner err">{error}</div></div>;
  if (!eintraege) return <div className="panel">Wird geladen…</div>;

  return (
    <div className="panel">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h2>Protokoll</h2>
        <button className="secondary small" onClick={load}>Aktualisieren</button>
      </div>
      <p className="muted">
        Die letzten {eintraege.length} Ereignisse über alle Rechnungen und Systemvorgänge
        (Anmeldungen, Ordner-Automatik, Löschungen). Der Verlauf einer gelöschten Rechnung
        wird mit ihr entfernt; das Löschen selbst bleibt protokolliert.
      </p>
      <div className="toolbar">
        <div className="field" style={{ flex: 1, maxWidth: 340 }}>
          <label>Filter (Aktion, Rechnung, Benutzer, Text)</label>
          <input value={filter} onChange={(e) => setFilter(e.target.value)}
                 placeholder="z. B. Duplikat, 400009, admin…" />
        </div>
        <div className="spacer" />
        <span className="muted">{gefiltert.length} Einträge</span>
      </div>

      {gefiltert.length === 0 ? (
        <p className="muted">Keine Einträge.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th style={{ whiteSpace: "nowrap" }}>Zeitpunkt</th>
              <th>Aktion</th>
              <th>Rechnung</th>
              <th>Details</th>
              <th>Benutzer</th>
            </tr>
          </thead>
          <tbody>
            {gefiltert.map((e, i) => (
              <tr key={i}>
                <td className="muted" style={{ whiteSpace: "nowrap" }}>{zeit(e.zeitpunkt)}</td>
                <td>{AKTION_LABEL[e.aktion] || e.aktion}</td>
                <td>
                  {e.rechnungsnummer
                    ? <Link to={`/pruefen/${e.invoiceId}`}>{e.rechnungsnummer}</Link>
                    : (e.invoiceId ? <span className="muted">{e.invoiceId.slice(0, 8)}</span> : "—")}
                </td>
                <td className="muted">{e.detail}</td>
                <td className="muted">{e.benutzer || "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
