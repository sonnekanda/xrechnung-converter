import { useEffect, useState } from "react";
import { api } from "../api";

const FIELDS = [
  ["name", "Firmenname *", "text"],
  ["strasse", "Straße", "text"],
  ["plz", "PLZ", "text"],
  ["ort", "Ort", "text"],
  ["land", "Land", "text"],
  ["ustId", "USt-IdNr", "text"],
  ["steuernummer", "Steuernummer", "text"],
  ["kontaktName", "Ansprechpartner", "text"],
  ["email", "E-Mail", "email"],
  ["telefon", "Telefon", "text"],
  ["iban", "IBAN", "text"],
  ["bic", "BIC", "text"],
  ["zahlungsbedingungen", "Zahlungsbedingungen", "text"],
];

export default function Firma() {
  const [form, setForm] = useState(null);
  const [msg, setMsg] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    api.company().then((c) => setForm(c || {})).catch((e) => setError(e.message));
  }, []);

  async function save(e) {
    e.preventDefault();
    setMsg(null);
    setError(null);
    try {
      const saved = await api.saveCompany(form);
      setForm(saved);
      setMsg("Firmendaten gespeichert.");
    } catch (err) {
      setError(err.message);
    }
  }

  if (!form) return <div className="panel">Wird geladen…</div>;

  return (
    <div className="panel" style={{ maxWidth: 720 }}>
      <h2>Eigene Firma (Verkäufer)</h2>
      <p className="muted">
        Diese Angaben werden automatisch als Verkäufer und Zahlungsweg in jede Rechnung
        eingefüllt – so müssen Kontakt und IBAN nicht bei jeder Rechnung neu erfasst werden.
      </p>
      {msg && <div className="banner ok">{msg}</div>}
      {error && <div className="banner err">{error}</div>}
      <form onSubmit={save}>
        <div className="row" style={{ flexWrap: "wrap", gap: 12 }}>
          {FIELDS.map(([k, label, type]) => (
            <div key={k} style={{ width: 220 }}>
              <label className="muted">{label}</label>
              <input
                type={type}
                value={form[k] ?? ""}
                onChange={(e) => setForm({ ...form, [k]: e.target.value })}
                required={k === "name"}
              />
            </div>
          ))}
        </div>
        <div className="mt">
          <button type="submit">Speichern</button>
        </div>
      </form>
    </div>
  );
}
