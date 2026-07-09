import { useEffect, useState } from "react";
import { api } from "../api";

const EMPTY = { name: "", ustId: "", kundennummer: "", leitwegId: "", strasse: "", plz: "", ort: "", land: "DE", iban: "", bic: "", zahlungsbedingungen: "" };
const FIELDS = [
  ["name", "Name"], ["ustId", "USt-IdNr"], ["kundennummer", "Kundennummer"], ["leitwegId", "Leitweg-ID"],
  ["strasse", "Straße"], ["plz", "PLZ"], ["ort", "Ort"], ["land", "Land"], ["iban", "IBAN"], ["bic", "BIC"],
  ["zahlungsbedingungen", "Zahlungsbedingungen"],
];

export default function Stammdaten() {
  const [rows, setRows] = useState([]);
  const [form, setForm] = useState(EMPTY);
  const [error, setError] = useState(null);

  const load = () => api.stammdaten().then(setRows).catch((e) => setError(e.message));
  useEffect(() => { load(); }, []);

  async function save(e) {
    e.preventDefault();
    setError(null);
    try {
      await api.createStammdaten(form);
      setForm(EMPTY);
      load();
    } catch (err) { setError(err.message); }
  }

  async function remove(id) {
    if (!confirm("Diesen Empfänger löschen?")) return;
    await api.deleteStammdaten(id);
    load();
  }

  return (
    <div className="panel">
      <h2>Empfänger-Stammdaten</h2>
      <p className="muted">
        Hinterlegte Leitweg-ID, Bankverbindung und Zahlungsbedingungen werden beim Auslesen
        automatisch in fehlende Pflichtfelder eingefüllt (Zuordnung über USt-IdNr oder Kundennummer).
      </p>
      {error && <div className="banner err">{error}</div>}

      <form className="row" style={{ flexWrap: "wrap", gap: 8, alignItems: "flex-end" }} onSubmit={save}>
        {FIELDS.map(([k, label]) => (
          <div key={k} style={{ width: 160 }}>
            <label className="muted">{label}</label>
            <input value={form[k]} onChange={(e) => setForm({ ...form, [k]: e.target.value })}
                   required={k === "name"} />
          </div>
        ))}
        <button type="submit">Hinzufügen</button>
      </form>

      <table className="mt">
        <thead>
          <tr><th>Name</th><th>USt-IdNr</th><th>Kundennr.</th><th>Leitweg-ID</th><th>IBAN</th><th></th></tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id}>
              <td>{r.name}</td><td>{r.ustId}</td><td>{r.kundennummer}</td><td>{r.leitwegId}</td><td>{r.iban}</td>
              <td><button className="ghost" onClick={() => remove(r.id)}>Löschen</button></td>
            </tr>
          ))}
          {rows.length === 0 && <tr><td colSpan="6" className="muted">Noch keine Stammdaten angelegt.</td></tr>}
        </tbody>
      </table>
    </div>
  );
}
