import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";

const STATUS_LABEL = {
  HOCHGELADEN: "Hochgeladen",
  IN_VERARBEITUNG: "In Verarbeitung",
  PRUEFEN: "Zu prüfen",
  VALIDIERT: "Validiert",
  EXPORTIERT: "Exportiert",
};
const STATUS_RANK = { HOCHGELADEN: 0, IN_VERARBEITUNG: 1, PRUEFEN: 2, VALIDIERT: 3, EXPORTIERT: 4 };

function monthRange() {
  // Lokale Datumsgrenzen ohne UTC-Verschiebung bilden.
  const now = new Date();
  const y = now.getFullYear();
  const m = now.getMonth();
  const pad = (n) => String(n).padStart(2, "0");
  const lastDay = new Date(y, m + 1, 0).getDate();
  return { von: `${y}-${pad(m + 1)}-01`, bis: `${y}-${pad(m + 1)}-${pad(lastDay)}` };
}

function deDatum(iso) {
  if (!iso) return "—";
  const [y, m, d] = iso.split("-");
  return `${d}.${m}.${y}`;
}

export default function Dashboard() {
  const [invoices, setInvoices] = useState([]);
  const [error, setError] = useState(null);
  const [range, setRange] = useState(monthRange());
  const [alle, setAlle] = useState(false);
  const [sort, setSort] = useState({ key: "rechnungsdatum", dir: "desc" });
  const [selected, setSelected] = useState(() => new Set());

  useEffect(() => {
    let alive = true;
    const load = () =>
      api.invoices(alle ? null : range.von, alle ? null : range.bis)
        .then((d) => alive && setInvoices(d))
        .catch((e) => setError(e.message));
    load();
    const t = setInterval(load, 3000); // Status/Ampel aktuell halten
    return () => { alive = false; clearInterval(t); };
  }, [range, alle]);

  function toggleSort(key) {
    setSort((s) => (s.key === key ? { key, dir: s.dir === "asc" ? "desc" : "asc" } : { key, dir: "asc" }));
  }

  const sorted = useMemo(() => {
    const val = (i) => {
      if (sort.key === "status") return STATUS_RANK[i.status] ?? -1;
      if (sort.key === "rechnungsnummer") {
        const n = Number(i.rechnungsnummer);
        return Number.isNaN(n) ? (i.rechnungsnummer || "") : n;
      }
      return i[sort.key]; // rechnungsdatum (ISO), createdAt
    };
    const arr = [...invoices];
    arr.sort((a, b) => {
      const va = val(a), vb = val(b);
      // leere Werte immer ans Ende
      if (va == null || va === "") return 1;
      if (vb == null || vb === "") return -1;
      const cmp = va > vb ? 1 : va < vb ? -1 : 0;
      return sort.dir === "asc" ? cmp : -cmp;
    });
    return arr;
  }, [invoices, sort]);

  async function download(inv) {
    try { await api.download(api.exportPath(inv.id), `rechnung-${inv.rechnungsnummer || inv.id}.xml`); }
    catch (e) { alert(e.message); }
  }
  async function downloadReport(inv) {
    try { await api.download(api.reportPath(inv.id), `bericht-${inv.rechnungsnummer || inv.id}.xml`); }
    catch (e) { alert(e.message); }
  }
  async function remove(inv) {
    const bez = inv.rechnungsnummer ? `Rechnung ${inv.rechnungsnummer}` : "diese Rechnung";
    if (!confirm(`${bez} wirklich löschen? Datei, Daten und XML werden entfernt.`)) return;
    try {
      await api.deleteInvoice(inv.id);
      setInvoices((prev) => prev.filter((i) => i.id !== inv.id));
      setSelected((s) => { const n = new Set(s); n.delete(inv.id); return n; });
    } catch (e) { alert(e.message); }
  }

  function toggleOne(id) {
    setSelected((s) => { const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n; });
  }

  async function removeSelected() {
    const ids = [...selected];
    if (ids.length === 0) return;
    if (!confirm(`${ids.length} Rechnung(en) wirklich löschen? Dateien, Daten und XML werden entfernt.`)) return;
    const res = await Promise.allSettled(ids.map((id) => api.deleteInvoice(id)));
    const failed = res.filter((r) => r.status === "rejected").length;
    setInvoices((prev) => prev.filter((i) => !selected.has(i.id)));
    setSelected(new Set());
    if (failed > 0) alert(`${failed} Rechnung(en) konnten nicht gelöscht werden.`);
  }

  const allSelected = sorted.length > 0 && sorted.every((i) => selected.has(i.id));
  function toggleAll() {
    setSelected(allSelected ? new Set() : new Set(sorted.map((i) => i.id)));
  }

  const Th = ({ label, k }) => (
    <th className="sortable" onClick={() => toggleSort(k)}>
      {label}{sort.key === k && <span className="sort-arrow">{sort.dir === "asc" ? "▲" : "▼"}</span>}
    </th>
  );

  return (
    <div className="panel">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h2>Übersicht</h2>
        <Link to="/upload"><button>Rechnung hochladen</button></Link>
      </div>

      <div className="toolbar">
        <div className="field">
          <label>Von (Rechnungsdatum)</label>
          <input type="date" value={range.von} disabled={alle}
                 onChange={(e) => setRange({ ...range, von: e.target.value })} />
        </div>
        <div className="field">
          <label>Bis</label>
          <input type="date" value={range.bis} disabled={alle}
                 onChange={(e) => setRange({ ...range, bis: e.target.value })} />
        </div>
        <button className="secondary small" onClick={() => { setAlle(false); setRange(monthRange()); }}>
          Dieser Monat
        </button>
        <label className="row" style={{ gap: 6, cursor: "pointer" }}>
          <input type="checkbox" style={{ width: "auto" }} checked={alle}
                 onChange={(e) => setAlle(e.target.checked)} />
          <span className="muted">Alle anzeigen</span>
        </label>
        {selected.size > 0 && (
          <button className="danger small" onClick={removeSelected}>🗑 {selected.size} löschen</button>
        )}
        <div className="spacer" />
        <span className="muted">{sorted.length} Rechnung(en)</span>
      </div>

      {error && <div className="banner err">{error}</div>}
      {sorted.length === 0 ? (
        <p className="muted">Keine Rechnungen im gewählten Zeitraum.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th><input type="checkbox" checked={allSelected} onChange={toggleAll}
                         title="Alle auswählen" /></th>
              <th></th>
              <Th label="Rechnung" k="rechnungsnummer" />
              <Th label="Rechnungsdatum" k="rechnungsdatum" />
              <th>Dateien</th>
              <Th label="Status" k="status" />
              <th>Prüfergebnis</th>
              <th>Aktionen</th>
            </tr>
          </thead>
          <tbody>
            {sorted.map((i) => (
              <tr key={i.id} className={selected.has(i.id) ? "selected" : ""}>
                <td><input type="checkbox" checked={selected.has(i.id)}
                           onChange={() => toggleOne(i.id)} /></td>
                <td><span className={`ampel ${ampelFor(i)}`} /></td>
                <td>
                  <strong>{i.rechnungsnummer || "—"}</strong>
                  {i.dokumentart && i.dokumentart !== "rechnung" && (
                    <div className="muted">{i.dokumentart}</div>
                  )}
                </td>
                <td>{deDatum(i.rechnungsdatum)}</td>
                <td className="muted">
                  {i.pdfFilename && <div>PDF{i.needsOcr ? " (OCR nötig)" : ""}</div>}
                  {i.csvFilename && <div>CSV</div>}
                  {i.source === "WATCH" && <div title="aus Ordner-Automatik">📂 Ordner</div>}
                </td>
                <td><span className={`status-pill status-${i.status}`}>{STATUS_LABEL[i.status] || i.status}</span></td>
                <td className="muted">{i.valid ? "gültig" : "—"}</td>
                <td className="row" style={{ gap: 6 }}>
                  {["PRUEFEN", "IN_VERARBEITUNG", "HOCHGELADEN"].includes(i.status) && (
                    <Link to={`/pruefen/${i.id}`}><button className="secondary small">Prüfen</button></Link>
                  )}
                  {["VALIDIERT", "EXPORTIERT"].includes(i.status) && (
                    <>
                      <Link to={`/pruefen/${i.id}`}><button className="secondary small">Ansehen</button></Link>
                      <button className="small" onClick={() => download(i)}>XML</button>
                      <button className="secondary small" title="Validierungsbericht"
                              onClick={() => downloadReport(i)}>Bericht</button>
                    </>
                  )}
                  <button className="danger small" title="Löschen" onClick={() => remove(i)}>🗑</button>
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
  if (i.status === "PRUEFEN" || i.status === "IN_VERARBEITUNG" || i.status === "HOCHGELADEN") return "gelb";
  return "rot";
}
