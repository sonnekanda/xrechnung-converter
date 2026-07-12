import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth.jsx";
import ReportModal from "../components/ReportModal";

// Die eigene Firma muss so weit gepflegt sein, dass die amtliche Prüfung nicht
// an fehlenden Verkäufer-Angaben scheitert (Name, Postanschrift + eine
// Steuerkennung – spiegelt die Fachprüfung wider).
function firmaUnvollstaendig(c) {
  if (!c) return true;
  const leer = (v) => !v || !String(v).trim();
  return leer(c.name) || leer(c.strasse) || leer(c.plz) || leer(c.ort)
    || leer(c.telefon) || leer(c.email)
    || (leer(c.ustId) && leer(c.steuernummer));
}

const STATUS_LABEL = {
  HOCHGELADEN: "Hochgeladen",
  IN_VERARBEITUNG: "In Verarbeitung",
  PRUEFEN: "Zu prüfen",
  VALIDIERT: "Validiert",
  EXPORTIERT: "Exportiert",
};
const STATUS_RANK = { HOCHGELADEN: 0, IN_VERARBEITUNG: 1, PRUEFEN: 2, VALIDIERT: 3, EXPORTIERT: 4 };

const MONATE = ["Januar", "Februar", "März", "April", "Mai", "Juni",
  "Juli", "August", "September", "Oktober", "November", "Dezember"];

// Lokale Monatsgrenzen (YYYY-MM-01 … Monatsletzter) ohne UTC-Verschiebung.
// new Date(y, m, 1) normalisiert Überläufe (m=-1 → Dez Vorjahr, m=12 → Jan Folgejahr).
function monthRangeOf(y, m) {
  const d = new Date(y, m, 1);
  const yy = d.getFullYear();
  const mm = d.getMonth();
  const pad = (n) => String(n).padStart(2, "0");
  const lastDay = new Date(yy, mm + 1, 0).getDate();
  return { von: `${yy}-${pad(mm + 1)}-01`, bis: `${yy}-${pad(mm + 1)}-${pad(lastDay)}` };
}

function monthRange() {
  const now = new Date();
  return monthRangeOf(now.getFullYear(), now.getMonth());
}

// Beschriftung des aktuell gewählten Monats aus dem Von-Datum ableiten.
function monthLabel(vonIso) {
  const [y, m] = vonIso.split("-").map(Number);
  return `${MONATE[m - 1]} ${y}`;
}

function deDatum(iso) {
  if (!iso) return "—";
  const [y, m, d] = iso.split("-");
  return `${d}.${m}.${y}`;
}

export default function Dashboard() {
  const { isAdmin } = useAuth();
  const [invoices, setInvoices] = useState([]);
  const [firmaHinweis, setFirmaHinweis] = useState(false);
  const [hinweisZu, setHinweisZu] = useState(false);
  const [error, setError] = useState(null);

  // Einmaliger Einrichtungs-Hinweis: ist die eigene Firma noch nicht (vollständig)
  // hinterlegt, schlägt jede amtliche Prüfung fehl – ohne Firmendaten kann keine
  // gültige XRechnung entstehen. Nur für Admins (nur sie können es beheben) und
  // nur solange unvollständig; der Hinweis verschwindet von selbst, sobald die
  // Firma gepflegt ist.
  useEffect(() => {
    if (!isAdmin) return;
    api.company()
      .then((c) => setFirmaHinweis(firmaUnvollstaendig(c)))
      .catch(() => {});
  }, [isAdmin]);
  const [range, setRange] = useState(monthRange());
  const [alle, setAlle] = useState(false);
  const [sort, setSort] = useState({ key: "rechnungsdatum", dir: "desc" });
  const [selected, setSelected] = useState(() => new Set());
  const [bericht, setBericht] = useState(null); // Rechnung für den Bericht-Dialog

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

  // Um einen Monat vor-/zurückblättern, ausgehend vom Von-Datum.
  function shiftMonth(delta) {
    setAlle(false);
    const [y, m] = range.von.split("-").map(Number);
    setRange(monthRangeOf(y, m - 1 + delta));
  }

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
  async function downloadZugferd(inv) {
    try { await api.download(api.zugferdPath(inv.id), `rechnung-${inv.rechnungsnummer || inv.id}-zugferd.pdf`); }
    catch (e) { alert(e.message); }
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

      {firmaHinweis && !hinweisZu && (
        <div className="banner warn row" style={{ justifyContent: "space-between" }}>
          <span>
            Die Angaben zur <strong>eigenen Firma</strong> sind noch nicht vollständig
            hinterlegt. Ohne sie schlägt die amtliche Prüfung fehl – bitte zuerst die
            Firmendaten ergänzen. <Link to="/firma">Zu den Firmendaten →</Link>
          </span>
          <button className="ghost icon" onClick={() => setHinweisZu(true)}
                  aria-label="Hinweis schließen" title="Schließen">✕</button>
        </div>
      )}

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
        <div className="month-nav">
          <button className="secondary small icon" title="Voriger Monat" aria-label="Voriger Monat"
                  disabled={alle} onClick={() => shiftMonth(-1)}>‹</button>
          <span className="month-label muted">{alle ? "Alle Monate" : monthLabel(range.von)}</span>
          <button className="secondary small icon" title="Nächster Monat" aria-label="Nächster Monat"
                  disabled={alle} onClick={() => shiftMonth(1)}>›</button>
        </div>
        <button className="secondary small" title="Zum aktuellen Monat springen"
                onClick={() => { setAlle(false); setRange(monthRange()); }}>
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
                      {i.pdfFilename && (
                        <button className="secondary small" title="ZUGFeRD-PDF (XRechnung im Original-PDF) herunterladen"
                                onClick={() => downloadZugferd(i)}>ZUGFeRD</button>
                      )}
                      <button className="secondary small" title="Validierungsbericht anzeigen"
                              onClick={() => setBericht(i)}>Bericht</button>
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {bericht && <ReportModal invoice={bericht} onClose={() => setBericht(null)} />}
    </div>
  );
}

function ampelFor(i) {
  if (i.status === "VALIDIERT" || i.status === "EXPORTIERT") return "gruen";
  if (i.status === "PRUEFEN" || i.status === "IN_VERARBEITUNG" || i.status === "HOCHGELADEN") return "gelb";
  return "rot";
}
