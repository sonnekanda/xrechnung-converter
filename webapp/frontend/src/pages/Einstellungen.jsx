import { useEffect, useState } from "react";
import { api } from "../api";

export default function Einstellungen() {
  const [s, setS] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    api.settings().then(setS).catch((e) => setError(e.message));
  }, []);

  if (error) return <div className="panel"><div className="banner err">{error}</div></div>;
  if (!s) return <div className="panel">Wird geladen…</div>;

  const rows = [
    ["Marke", s.brandName],
    ["Produkttitel", s.productTitle],
    ["Primärfarbe", s.primaryColor],
    ["Max. Uploadgröße", `${s.maxUploadMb} MB`],
    ["Rundungstoleranz", s.roundingTolerance],
    ["Zweite Validierung (KoSIT)", s.kositEnabled ? "aktiv" : "aus"],
    ["Ordner-Automatik", s.watchEnabled ? "aktiv" : "aus"],
    ["Lizenz-Durchsetzung", s.license.enforcement ? "aktiv" : "aus"],
    ["Lizenzcontroller konfiguriert", s.license.controllerConfigured ? "ja" : "nein"],
    ["Lizenzschlüssel hinterlegt", s.license.keyConfigured ? "ja" : "nein"],
  ];

  return (
    <div className="panel" style={{ maxWidth: 560 }}>
      <h2>Einstellungen</h2>
      <p className="muted">
        Betriebskonfiguration (schreibgeschützt). Änderungen erfolgen über die
        zentrale <code>.env</code> und wirken nach einem Neustart des Stacks. Secrets
        werden hier nicht angezeigt.
      </p>
      <table>
        <tbody>
          {rows.map(([k, v]) => (
            <tr key={k}><th style={{ width: 260 }}>{k}</th><td>{String(v)}</td></tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
