import { useEffect, useState } from "react";
import { api } from "../api";

export default function OrdnerAutomatik() {
  const [s, setS] = useState(null);
  const [msg, setMsg] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    api.watchSettings().then(setS).catch((e) => setError(e.message));
  }, []);

  function set(k, v) { setS({ ...s, [k]: v }); }

  async function save(e) {
    e.preventDefault();
    setMsg(null); setError(null);
    try {
      setS(await api.saveWatchSettings(s));
      setMsg("Einstellungen gespeichert.");
    } catch (err) { setError(err.message); }
  }

  if (error && !s) return <div className="panel"><div className="banner err">{error}</div></div>;
  if (!s) return <div className="panel">Wird geladen…</div>;

  return (
    <div className="panel" style={{ maxWidth: 640 }}>
      <h2>Ordner-Automatik</h2>
      <p className="muted">
        Rechnungen aus dem Eingangsordner werden automatisch verarbeitet – exakt wie beim
        Hochladen. Nur vollständig gültige Rechnungen werden automatisch als XRechnung
        exportiert; unsichere landen im Prüfbildschirm.
      </p>
      {msg && <div className="banner ok">{msg}</div>}
      {error && <div className="banner err">{error}</div>}

      <form onSubmit={save}>
        <label className="row" style={{ gap: 10, cursor: "pointer" }}>
          <input type="checkbox" style={{ width: "auto" }} checked={s.watchEnabled}
                 onChange={(e) => set("watchEnabled", e.target.checked)} />
          <span><strong>Automatik aktiv</strong> – Eingangsordner überwachen</span>
        </label>

        <fieldset disabled={!s.watchEnabled} style={{ border: "none", padding: 0, margin: "12px 0 0", opacity: s.watchEnabled ? 1 : 0.5 }}>
          <div className="section-title">Ordner</div>
          <p className="muted" style={{ marginTop: 0 }}>
            Basis-Ordner werden beim Deployment gemountet (read-only). Optional ein Unterordner:
          </p>
          <div className="field-row">
            <label>Eingang (Basis)</label>
            <div className="val"><input value={s.baseInputDir} readOnly /></div>
          </div>
          <div className="field-row">
            <label>Eingang – Unterordner</label>
            <div className="val"><input value={s.watchInputSubdir}
              onChange={(e) => set("watchInputSubdir", e.target.value)} placeholder="(optional)" /></div>
          </div>
          <div className="field-row">
            <label>Ausgang (Basis)</label>
            <div className="val"><input value={s.baseOutputDir} readOnly /></div>
          </div>
          <div className="field-row">
            <label>Ausgang – Unterordner</label>
            <div className="val"><input value={s.watchOutputSubdir}
              onChange={(e) => set("watchOutputSubdir", e.target.value)} placeholder="(optional)" /></div>
          </div>
          <div className="muted">Effektiv: {s.effectiveInputDir} → {s.effectiveOutputDir}</div>

          <div className="section-title">Verhalten</div>
          <div className="field-row">
            <label>Prüfintervall (Sekunden)</label>
            <div className="val"><input type="number" min="1" value={s.watchIntervalSeconds}
              onChange={(e) => set("watchIntervalSeconds", Number(e.target.value))} /></div>
          </div>
          <div className="field-row">
            <label>Wartezeit / Settle (Sek.)</label>
            <div className="val"><input type="number" min="0" value={s.watchSettleSeconds}
              onChange={(e) => set("watchSettleSeconds", Number(e.target.value))} /></div>
          </div>
          <p className="muted" style={{ marginTop: 0 }}>
            Die Settle-Zeit lässt zusammengehörige PDF/CSV-Dateien „ankommen", bevor
            verarbeitet wird – so wird ein Paar nicht in zwei Rechnungen zerlegt.
          </p>
          <label className="row" style={{ gap: 10, cursor: "pointer", marginTop: 8 }}>
            <input type="checkbox" style={{ width: "auto" }} checked={s.autoConfirmYellow}
                   onChange={(e) => set("autoConfirmYellow", e.target.checked)} />
            <span>Gelbe Felder automatisch bestätigen (für echten Auto-Export)</span>
          </label>
          <p className="hinweis" style={{ margin: "2px 0 0 0" }}>
            OCR-Werte bleiben ausgenommen und weiterhin prüfpflichtig – Scan-Rechnungen
            werden nicht automatisch exportiert.
          </p>
        </fieldset>

        <div className="mt"><button type="submit">Speichern</button></div>
      </form>
    </div>
  );
}
