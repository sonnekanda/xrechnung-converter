import { useEffect, useMemo, useRef, useState } from "react";
import { api } from "../api";

const EMPTY = { name: "", ustId: "", kundennummer: "", leitwegId: "", strasse: "", plz: "", ort: "", land: "DE", email: "", zahlungsbedingungen: "" };
const FIELDS = [
  ["name", "Name"], ["ustId", "USt-IdNr"], ["kundennummer", "Kundennummer"], ["leitwegId", "Leitweg-ID"],
  ["strasse", "Straße"], ["plz", "PLZ"], ["ort", "Ort"], ["land", "Land"],
  ["email", "E-Mail"], ["zahlungsbedingungen", "Zahlungsbedingungen"],
];

// Voll durchsuchbare Felder für den Live-Filter.
const SUCH_FELDER = ["name", "ustId", "kundennummer", "leitwegId", "strasse", "plz", "ort", "land", "email"];

export default function Stammdaten() {
  const [rows, setRows] = useState([]);
  const [q, setQ] = useState("");
  const [selected, setSelected] = useState(() => new Set());
  const [modal, setModal] = useState(null); // { isNew, data } oder null
  const [error, setError] = useState(null);
  const suchRef = useRef(null);

  const load = () => api.stammdaten().then(setRows).catch((e) => setError(e.message));
  useEffect(() => { load(); }, []);

  const gefiltert = useMemo(() => {
    const term = q.trim().toLowerCase();
    if (!term) return rows;
    return rows.filter((r) => SUCH_FELDER.some((f) => (r[f] || "").toLowerCase().includes(term)));
  }, [rows, q]);

  function toggleOne(id) {
    setSelected((s) => { const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n; });
  }
  const alleGewaehlt = gefiltert.length > 0 && gefiltert.every((r) => selected.has(r.id));
  function toggleAll() {
    setSelected(alleGewaehlt ? new Set() : new Set(gefiltert.map((r) => r.id)));
  }

  async function saveRecipient(form) {
    if (modal.isNew) await api.createStammdaten(form);
    else await api.updateStammdaten(form.id, form);
    setModal(null);
    await load();
  }

  async function deleteRecipient(id) {
    if (!confirm("Diesen Empfänger löschen?")) return;
    await api.deleteStammdaten(id);
    setModal(null);
    load();
  }

  async function bulkDelete() {
    const ids = [...selected];
    if (ids.length === 0) return;
    if (!confirm(`${ids.length} Empfänger wirklich löschen?`)) return;
    const res = await Promise.allSettled(ids.map((id) => api.deleteStammdaten(id)));
    const failed = res.filter((r) => r.status === "rejected").length;
    setSelected(new Set());
    await load();
    if (failed > 0) setError(`${failed} Empfänger konnten nicht gelöscht werden.`);
  }

  return (
    <div className="panel">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h2>Empfänger-Stammdaten</h2>
        <button onClick={() => setModal({ isNew: true, data: EMPTY })}>Hinzufügen</button>
      </div>
      <p className="muted">
        Hinterlegte Leitweg-ID, Bankverbindung und Zahlungsbedingungen werden beim Auslesen
        automatisch in fehlende Pflichtfelder eingefüllt (Zuordnung über USt-IdNr oder Kundennummer).
        Zum Bearbeiten auf eine Zeile klicken.
      </p>
      {error && (
        <div className="banner err row" style={{ justifyContent: "space-between" }}>
          <span>{error}</span>
          <button className="ghost" onClick={() => setError(null)}>Schließen</button>
        </div>
      )}

      <div className="toolbar">
        <div className="field" style={{ flex: 1, minWidth: 220 }}>
          <label>Suchen</label>
          <div className="such-feld">
            <input ref={suchRef} value={q} onChange={(e) => setQ(e.target.value)}
                   placeholder="Name, USt-IdNr, Kundennr., Ort …" />
            {q && (
              <button type="button" className="such-clear" title="Filter zurücksetzen"
                      aria-label="Filter zurücksetzen"
                      onClick={() => { setQ(""); suchRef.current?.focus(); }}>✕</button>
            )}
          </div>
        </div>
        {selected.size > 0 && (
          <button className="danger small" onClick={bulkDelete}>🗑 {selected.size} löschen</button>
        )}
        <div className="spacer" />
        <span className="muted">{gefiltert.length} Eintrag/Einträge</span>
      </div>

      <div className="table-scroll-v">
        <table className="stammdaten">
          <thead>
            <tr>
              <th><input type="checkbox" checked={alleGewaehlt} onChange={toggleAll} title="Alle auswählen" /></th>
              {FIELDS.map(([k, label]) => <th key={k}>{label}</th>)}
            </tr>
          </thead>
          <tbody>
            {gefiltert.map((r) => (
              <tr key={r.id} className="klickbar" onClick={() => setModal({ isNew: false, data: { ...EMPTY, ...r } })}
                  title="Bearbeiten">
                <td onClick={(e) => e.stopPropagation()}>
                  <input type="checkbox" checked={selected.has(r.id)} onChange={() => toggleOne(r.id)} />
                </td>
                {FIELDS.map(([k]) => <td key={k}>{r[k] || <span className="muted">—</span>}</td>)}
              </tr>
            ))}
            {gefiltert.length === 0 && (
              <tr><td colSpan={FIELDS.length + 1} className="muted">
                {rows.length === 0 ? "Noch keine Stammdaten angelegt." : "Keine Treffer für den Filter."}
              </td></tr>
            )}
          </tbody>
        </table>
      </div>

      {modal && (
        <StammdatenModal
          isNew={modal.isNew}
          initial={modal.data}
          onSave={saveRecipient}
          onDelete={() => deleteRecipient(modal.data.id)}
          onClose={() => setModal(null)}
        />
      )}
    </div>
  );
}

function StammdatenModal({ isNew, initial, onSave, onDelete, onClose }) {
  const [form, setForm] = useState(initial);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState(null);

  useEffect(() => {
    const onKey = (e) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setErr(null);
    try {
      await onSave(form);
    } catch (ex) {
      setErr(ex.message);
      setBusy(false);
    }
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <div className="modal-head">
          <h3>{isNew ? "Empfänger hinzufügen" : "Empfänger bearbeiten"}</h3>
          <button className="ghost icon" onClick={onClose} title="Schließen" aria-label="Schließen">✕</button>
        </div>
        {err && <div className="banner err">{err}</div>}
        <form onSubmit={submit}>
          <div className="row" style={{ flexWrap: "wrap", gap: 12 }}>
            {FIELDS.map(([k, label]) => (
              <div key={k} style={{ width: k === "zahlungsbedingungen" ? "100%" : 176 }}>
                <label className="muted">{label}{k === "name" ? " *" : ""}</label>
                <input value={form[k] ?? ""} required={k === "name"}
                       onChange={(e) => setForm({ ...form, [k]: e.target.value })} />
              </div>
            ))}
          </div>
          <div className="modal-foot" style={{ justifyContent: "space-between" }}>
            {!isNew ? (
              <button type="button" className="danger" onClick={onDelete}>Löschen</button>
            ) : <span />}
            <div className="row" style={{ gap: 10 }}>
              <button type="button" className="secondary" onClick={onClose}>Abbrechen</button>
              <button type="submit" disabled={busy}>{busy ? "Speichert…" : "Speichern"}</button>
            </div>
          </div>
        </form>
      </div>
    </div>
  );
}
