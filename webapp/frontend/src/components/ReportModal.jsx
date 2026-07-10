import { useEffect, useState } from "react";
import { api } from "../api";

const SCHWERE = {
  fehler: { label: "Fehler", cls: "sev-fehler" },
  warnung: { label: "Warnung", cls: "sev-warnung" },
  hinweis: { label: "Hinweis", cls: "sev-hinweis" },
};

/**
 * Zeigt den Validierungsbericht menschenlesbar in einem Overlay. Der technische
 * XML-Bericht bleibt per Download verfügbar.
 */
export default function ReportModal({ invoice, onClose }) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let alive = true;
    api.reportSummary(invoice.id)
      .then((d) => alive && setData(d))
      .catch((e) => alive && setError(e.message));
    return () => { alive = false; };
  }, [invoice.id]);

  useEffect(() => {
    const onKey = (e) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  async function downloadXml() {
    setBusy(true);
    try {
      await api.download(api.reportPath(invoice.id), `bericht-${invoice.rechnungsnummer || invoice.id}.xml`);
    } catch (e) {
      alert(e.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <div className="modal-head">
          <h3>Validierungsbericht</h3>
          <button className="ghost icon" onClick={onClose} title="Schließen" aria-label="Schließen">✕</button>
        </div>

        {error && <div className="banner err">{error}</div>}
        {!data && !error && <p className="muted">Bericht wird geladen …</p>}

        {data && (
          <>
            <div className={`verdict ${data.gueltig ? "ok" : "bad"}`}>
              <span className="verdict-ico">{data.gueltig ? "✓" : "✕"}</span>
              <div>
                <strong>{data.gueltig ? "Gültige XRechnung" : "Nicht gültig"}</strong>
                <div className="muted">
                  Rechnung {data.rechnungsnummer || "—"} · geprüft nach EN 16931 / XRechnung 3.0
                </div>
              </div>
            </div>

            <div className="counts">
              <span className={`count-pill sev-fehler${data.anzahl.fehler ? " on" : ""}`}>
                {data.anzahl.fehler} Fehler
              </span>
              <span className={`count-pill sev-warnung${data.anzahl.warnung ? " on" : ""}`}>
                {data.anzahl.warnung} Warnungen
              </span>
              <span className={`count-pill sev-hinweis${data.anzahl.hinweis ? " on" : ""}`}>
                {data.anzahl.hinweis} Hinweise
              </span>
            </div>

            {data.meldungen.length === 0 ? (
              <p className="muted">Keine Beanstandungen – die Rechnung erfüllt alle Prüfregeln.</p>
            ) : (
              <ul className="meldungen">
                {data.meldungen.map((m, i) => (
                  <li key={i}>
                    <span className={`sev-tag ${SCHWERE[m.schwere]?.cls || ""}`}>
                      {SCHWERE[m.schwere]?.label || m.schwere}
                    </span>
                    <div className="meldung-body">
                      <div>{m.text}</div>
                      {m.kontext && <div className="muted mono">{m.kontext}</div>}
                    </div>
                  </li>
                ))}
              </ul>
            )}

            <div className="modal-foot">
              <button className="secondary" onClick={downloadXml} disabled={busy}>
                XML herunterladen
              </button>
              <button onClick={onClose}>Schließen</button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
