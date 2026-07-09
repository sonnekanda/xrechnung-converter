import { useEffect, useState } from "react";
import { api } from "../api";

const EMPTY = { email: "", password: "", role: "BENUTZER" };

export default function Benutzer() {
  const [users, setUsers] = useState([]);
  const [form, setForm] = useState(EMPTY);
  const [error, setError] = useState(null);

  const load = () => api.users().then(setUsers).catch((e) => setError(e.message));
  useEffect(() => { load(); }, []);

  async function create(e) {
    e.preventDefault();
    setError(null);
    try {
      await api.createUser(form);
      setForm(EMPTY);
      load();
    } catch (err) { setError(err.message); }
  }

  async function remove(u) {
    if (!confirm(`Benutzer ${u.email} löschen?`)) return;
    try {
      await api.deleteUser(u.id);
      load();
    } catch (e) { alert(e.message); }
  }

  return (
    <div className="panel" style={{ maxWidth: 640 }}>
      <h2>Benutzerverwaltung</h2>
      <p className="muted">Benutzer anlegen und Rollen vergeben. Rolle „Admin" sieht zusätzlich
        Stammdaten, Eigene Firma, Benutzer und Einstellungen.</p>
      {error && <div className="banner err">{error}</div>}

      <form className="row" style={{ flexWrap: "wrap", gap: 10, alignItems: "flex-end" }} onSubmit={create}>
        <div style={{ width: 220 }}>
          <label className="muted">E-Mail</label>
          <input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required />
        </div>
        <div style={{ width: 180 }}>
          <label className="muted">Passwort</label>
          <input type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} required />
        </div>
        <div style={{ width: 140 }}>
          <label className="muted">Rolle</label>
          <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>
            <option value="BENUTZER">Benutzer</option>
            <option value="ADMIN">Admin</option>
          </select>
        </div>
        <button type="submit">Anlegen</button>
      </form>

      <table className="mt">
        <thead><tr><th>E-Mail</th><th>Rolle</th><th></th></tr></thead>
        <tbody>
          {users.map((u) => (
            <tr key={u.id}>
              <td>{u.email}</td>
              <td>{u.role === "ADMIN" ? "Admin" : "Benutzer"}</td>
              <td><button className="ghost" onClick={() => remove(u)}>Löschen</button></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
