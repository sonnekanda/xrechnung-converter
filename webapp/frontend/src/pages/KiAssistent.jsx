import { useEffect, useState } from "react";
import { api } from "../api";

// KI-Assistent: optionaler LLM-Fallback im extraction-worker. Ein Master-Schalter
// "Standardeinstellungen überschreiben" entscheidet, ob die vier Detailwerte
// (aktiv, Schlüssel, Modell, Timeout) verwendet werden. Ist er aus, gelten die
// Vorgaben aus der Installation (.env) – der Nutzer kann jederzeit gefahrlos
// dorthin zurück, egal was in den Feldern steht. Der API-Schlüssel wird vom
// Server nie zurückgegeben; ein leeres Feld lässt den bestehenden unangetastet.
export default function KiAssistent() {
  const [s, setS] = useState(null);
  const [apiKey, setApiKey] = useState("");
  const [msg, setMsg] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    api.kiSettings().then(setS).catch((e) => setError(e.message));
  }, []);

  function set(k, v) { setS({ ...s, [k]: v }); }

  async function save(e) {
    e.preventDefault();
    setMsg(null); setError(null);
    try {
      const saved = await api.saveKiSettings({
        override: s.override,
        enabled: s.enabled,
        model: s.model,
        timeoutSeconds: s.timeoutSeconds,
        apiKey: apiKey,          // leer = Schlüssel unverändert lassen
      });
      setS(saved);
      setApiKey("");
      setMsg("Einstellungen gespeichert.");
    } catch (err) { setError(err.message); }
  }

  if (error && !s) return <div className="panel"><div className="banner err">{error}</div></div>;
  if (!s) return <div className="panel">Wird geladen…</div>;

  const defaultSummary = `${s.defaultEnabled ? "KI aktiv" : "KI aus"}, Modell ${s.defaultModel}, `
    + `Timeout ${s.defaultTimeoutSeconds}s, Schlüssel ${s.defaultApiKeySet ? "hinterlegt" : "nicht hinterlegt"}`;

  return (
    <div className="panel" style={{ maxWidth: 640 }}>
      <h2>KI-Assistent</h2>
      <p className="muted">
        Optionaler KI-Schritt bei der Extraktion: Felder, die aus CSV/PDF/OCR nicht
        erkannt werden konnten, werden per Sprachmodell (über OpenRouter) vorgeschlagen.
        Vorschläge sind immer <strong>gelb</strong> markiert (Quelle „ki") und müssen im
        Prüfbildschirm bestätigt werden – sie werden nie automatisch exportiert.
      </p>
      {msg && <div className="banner ok">{msg}</div>}
      {error && <div className="banner err">{error}</div>}

      <form onSubmit={save}>
        <label className="row" style={{ gap: 10, cursor: "pointer" }}>
          <input type="checkbox" style={{ width: "auto" }} checked={s.override}
                 onChange={(e) => set("override", e.target.checked)} />
          <span><strong>Standardeinstellungen überschreiben</strong></span>
        </label>
        <p className="hinweis" style={{ margin: "2px 0 0 0" }}>
          {s.override
            ? "Die folgenden Werte werden verwendet und ersetzen die Vorgaben aus der Installation."
            : `Aus: Es gelten die Vorgaben aus der Installation (.env) – ${defaultSummary}.`}
        </p>

        <fieldset disabled={!s.override} style={{ border: "none", padding: 0, margin: "12px 0 0", opacity: s.override ? 1 : 0.5 }}>
          <label className="row" style={{ gap: 10, cursor: "pointer" }}>
            <input type="checkbox" style={{ width: "auto" }} checked={s.enabled}
                   onChange={(e) => set("enabled", e.target.checked)} />
            <span><strong>KI-Assistent aktiv</strong> – fehlende Felder per LLM ergänzen</span>
          </label>

          <div className="section-title">OpenRouter</div>
          <div className="field-row">
            <label>API-Schlüssel</label>
            <div className="val">
              <input type="password" autoComplete="off"
                     value={apiKey}
                     placeholder={s.apiKeySet ? "•••••••• (hinterlegt – zum Ändern neuen Schlüssel eingeben)" : "sk-or-…"}
                     onChange={(e) => setApiKey(e.target.value)} />
            </div>
          </div>
          <p className="hinweis" style={{ marginTop: 0 }}>
            Schlüssel von <code>openrouter.ai</code>. Wird verschlüsselt übertragen und
            aus Sicherheitsgründen nie wieder angezeigt. Leer lassen = bestehender Schlüssel bleibt.
          </p>
          <div className="field-row">
            <label>Modell</label>
            <div className="val">
              <input value={s.model}
                     placeholder="openai/gpt-4o-mini"
                     onChange={(e) => set("model", e.target.value)} />
            </div>
          </div>
          <p className="hinweis" style={{ marginTop: 0 }}>
            Modell-ID bei OpenRouter, z.&nbsp;B. <code>openai/gpt-4o-mini</code> oder
            <code>anthropic/claude-3.5-sonnet</code>.
          </p>
          <div className="field-row">
            <label>Timeout (Sekunden)</label>
            <div className="val">
              <input type="number" min="1" value={s.timeoutSeconds}
                     onChange={(e) => set("timeoutSeconds", Number(e.target.value))} />
            </div>
          </div>
          <p className="hinweis" style={{ marginTop: 0 }}>
            Datenschutz: Bei aktiviertem Assistenten wird der Rechnungstext an OpenRouter
            (Drittanbieter) gesendet. Für den Produktivbetrieb ein EU-/self-hosted Modell wählen.
          </p>
        </fieldset>

        <div className="mt"><button type="submit">Speichern</button></div>
      </form>
    </div>
  );
}
