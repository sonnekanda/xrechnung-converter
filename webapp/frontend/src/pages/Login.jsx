import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth.jsx";

export default function Login() {
  const { login, branding } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const abgelaufen = new URLSearchParams(window.location.search).has("abgelaufen");

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(email, password);
      navigate("/");
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login-wrap">
      <form className="panel login-card" onSubmit={submit}>
        <div className="brand" style={{ padding: 0, marginBottom: 16, color: "var(--ink)" }}>
          {branding.logoUrl
            ? <img className="logo" src={branding.logoUrl} alt={branding.name} />
            : <span className="logo">{(branding.name || "s")[0].toUpperCase()}</span>}
          <span style={{ fontSize: 20, fontWeight: 700 }}>{branding.name}</span>
        </div>
        <h2 style={{ marginTop: 0 }}>Anmeldung</h2>
        <p className="muted">{branding.productTitle}</p>
        {abgelaufen && !error && (
          <div className="banner warn">Ihre Sitzung ist abgelaufen. Bitte melden Sie sich erneut an.</div>
        )}
        {error && <div className="banner err">{error}</div>}
        <label className="muted">E-Mail</label>
        <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoFocus required />
        <div className="spacer-y" />
        <label className="muted">Passwort</label>
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        <div className="mt">
          <button type="submit" disabled={busy} style={{ width: "100%" }}>
            {busy ? "Anmeldung läuft…" : "Anmelden"}
          </button>
        </div>
      </form>
    </div>
  );
}
