import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";

const STATUS_LABEL = {
  HOCHGELADEN: "Hochgeladen",
  IN_VERARBEITUNG: "In Verarbeitung",
  PRUEFEN: "Zu prüfen",
  VALIDIERT: "Validiert",
  EXPORTIERT: "Exportiert",
};

export default function Dashboard() {
  const [invoices, setInvoices] = useState([]);
  const [error, setError] = useState(null);

  useEffect(() => {
    let alive = true;
    const load = () => api.invoices().then((d) => alive && setInvoices(d)).catch((e) => setError(e.message));
    load();
    const t = setInterval(load, 3000); // Status/Ampel aktualisieren
    return () => { alive = false; clearInterval(t); };
  }, []);

  async function download(inv) {
    try {
      await api.download(api.exportPath(inv.id), `rechnung-${inv.rechnungsnummer || inv.id}.xml`);
    } catch (e) {
      alert(e.message);
    }
  }

  async function downloadReport(inv) {
    try {
      await api.download(api.reportPath(inv.id), `bericht-${inv.rechnungsnummer || inv.id}.xml`);
    } catch (e) {
      alert(e.message);
    }
  }

  async function remove(inv) {
    const bez = inv.rechnungsnummer ? `Rechnung ${inv.rechnungsnummer}` : "diese Rechnung";
    if (!confirm(`${bez} wirklich löschen? Datei, Daten und XML werden entfernt.`)) return;
    try {
      await api.deleteInvoice(inv.id);
      setInvoices((prev) => prev.filter((i) => i.id !== inv.id));
    } catch (e) {
      alert(e.message);
    }
  }

  return (
    <div className="panel">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h2>Übersicht</h2>
        <Link to="/upload"><button>Rechnung hochladen</button></Link>
      </div>
      {error && <div className="banner err">{error}</div>}
      {invoices.length === 0 ? (
        <p className="muted">Noch keine Rechnungen. Laden Sie eine PDF- und/oder CSV-Datei hoch.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th></th><th>Rechnung</th><th>Dateien</th><th>Status</th><th>Prüfergebnis</th><th></th>
            </tr>
          </thead>
          <tbody>
            {invoices.map((i) => (
              <tr key={i.id}>
                <td><span className={`ampel ${ampelFor(i)}`} /></td>
                <td>
                  <strong>{i.rechnungsnummer || "—"}</strong>
                  {i.dokumentart && i.dokumentart !== "rechnung" && (
                    <div className="muted">{i.dokumentart}</div>
                  )}
                </td>
                <td className="muted">
                  {i.pdfFilename && <div>PDF{i.needsOcr ? " (OCR nötig)" : ""}</div>}
                  {i.csvFilename && <div>CSV</div>}
                  {i.source === "WATCH" && <div title="aus Ordner-Automatik">📂 Ordner</div>}
                </td>
                <td><span className={`status-pill status-${i.status}`}>{STATUS_LABEL[i.status] || i.status}</span></td>
                <td className="muted">{i.valid ? "gültig" : "—"}</td>
                <td className="row" style={{ gap: 6 }}>
                  {["PRUEFEN", "IN_VERARBEITUNG", "HOCHGELADEN"].includes(i.status) && (
                    <Link to={`/pruefen/${i.id}`}><button className="secondary">Prüfen</button></Link>
                  )}
                  {["VALIDIERT", "EXPORTIERT"].includes(i.status) && (
                    <>
                      <Link to={`/pruefen/${i.id}`}><button className="secondary">Ansehen</button></Link>
                      <button onClick={() => download(i)}>XML</button>
                      <button className="secondary" title="Validierungsbericht"
                              onClick={() => downloadReport(i)}>Bericht</button>
                    </>
                  )}
                  <button className="ghost" title="Löschen" onClick={() => remove(i)}>🗑</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

function ampelFor(i) {
  if (i.status === "VALIDIERT" || i.status === "EXPORTIERT") return "gruen";
  if (i.status === "PRUEFEN") return "gelb";
  if (i.status === "IN_VERARBEITUNG" || i.status === "HOCHGELADEN") return "gelb";
  return "rot";
}
