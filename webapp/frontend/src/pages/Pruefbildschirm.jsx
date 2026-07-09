import { useCallback, useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import { api } from "../api";

const KOPF_LABELS = {
  rechnungsnummer: "Rechnungsnummer",
  rechnungsdatum: "Rechnungsdatum",
  faelligkeit: "Fälligkeit",
  waehrung: "Währung",
  leitweg_id: "Leitweg-ID",
  verkaeufer_name: "Verkäufer",
  verkaeufer_ustid: "USt-IdNr (Verkäufer)",
  kaeufer_name: "Käufer",
  kaeufer_strasse: "Straße (Käufer)",
  kaeufer_plz: "PLZ (Käufer)",
  kaeufer_ort: "Ort (Käufer)",
  kaeufer_ustid: "USt-IdNr (Käufer)",
  bankverbindung_iban: "IBAN",
};
const SUMMEN_LABELS = {
  nettobetrag: "Nettobetrag",
  steuerbetrag: "Steuerbetrag",
  bruttobetrag: "Bruttobetrag",
  steuersatz: "Steuersatz (%)",
};

function ampelClass(status) {
  return status === "sicher" ? "gruen" : status === "unsicher" ? "gelb" : "rot";
}

function FieldRow({ label, field, onSave }) {
  const [value, setValue] = useState(field?.wert ?? "");
  useEffect(() => { setValue(field?.wert ?? ""); }, [field?.wert]);
  const status = field?.status || "fehlt";
  const dirty = (field?.wert ?? "") !== value;

  // Speichern, wenn der Wert geändert wurde ODER das Feld noch bestätigt werden
  // muss (gelb/rot). So wird auch ein korrekter gelber Wert per Klick grün.
  async function save() {
    if (dirty || status !== "sicher") await onSave(value);
  }

  return (
    <div className="field-row">
      <label title={field?.herkunft}>{label}</label>
      <div className="val">
        <span className={`ampel ${ampelClass(status)}`} title={status} />
        <input
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onBlur={save}
          onKeyDown={(e) => e.key === "Enter" && e.target.blur()}
          placeholder={status === "fehlt" ? "bitte ergänzen" : ""}
        />
        {field?.herkunft && <span className="herkunft">{field.herkunft}</span>}
        {status !== "sicher" && (
          <button
            type="button"
            className="confirm-btn"
            title="Wert bestätigen"
            onMouseDown={(e) => e.preventDefault()}
            onClick={() => onSave(value)}
          >
            ✓
          </button>
        )}
      </div>
      {status !== "sicher" && field?.hinweis && <div className="hinweis">{field.hinweis}</div>}
    </div>
  );
}

export default function Pruefbildschirm() {
  const { id } = useParams();
  const [detail, setDetail] = useState(null);
  const [pdfUrl, setPdfUrl] = useState(null);
  const [error, setError] = useState(null);
  const [genMsg, setGenMsg] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => api.invoice(id).then(setDetail).catch((e) => setError(e.message)), [id]);

  useEffect(() => {
    load();
    // Solange noch extrahiert wird (kein Modell), kurz nachladen.
    const t = setInterval(() => {
      api.invoice(id).then((d) => { setDetail(d); if (d.model) clearInterval(t); }).catch(() => {});
    }, 1500);
    return () => clearInterval(t);
  }, [id, load]);

  useEffect(() => {
    if (detail?.pdfFilename) api.blobUrl(api.pdfPath(id)).then(setPdfUrl).catch(() => {});
  }, [detail?.pdfFilename, id]);

  async function saveField(block, feld, wert, positionIndex = null) {
    const updated = await api.patchField(id, block, feld, wert, positionIndex);
    setDetail(updated);
  }

  async function generate() {
    setBusy(true);
    setGenMsg(null);
    try {
      const res = await api.generate(id);
      await load();
      if (res.valid) setGenMsg({ ok: true, text: "XRechnung wurde erzeugt und amtlich validiert." });
      else setGenMsg({ ok: false, issues: res.issues });
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  async function download(path, filename) {
    try { await api.download(path, filename); } catch (e) { alert(e.message); }
  }

  if (error) return <div className="panel"><div className="banner err">{error}</div><Link to="/">Zurück</Link></div>;
  if (!detail) return <div className="panel">Wird geladen…</div>;

  if (!detail.model) {
    return (
      <div className="panel">
        <h2>Rechnung wird verarbeitet…</h2>
        <p className="muted">Die Datei wird ausgelesen. Der Prüfbildschirm öffnet sich automatisch.</p>
      </div>
    );
  }

  const { model } = detail;
  const kopf = model.kopf || {};
  const summen = model.summen || {};
  const positionen = model.positionen || [];
  const validiert = detail.status === "VALIDIERT" || detail.status === "EXPORTIERT";

  return (
    <div>
      <div className="row" style={{ justifyContent: "space-between", marginBottom: 12 }}>
        <h2 style={{ margin: 0 }}>Prüfen: Rechnung {kopf.rechnungsnummer?.wert || detail.id.slice(0, 8)}</h2>
        <Link to="/"><button className="ghost">Zurück zur Übersicht</button></Link>
      </div>

      {detail.needsOcr && (
        <div className="banner warn">
          Diese Rechnung enthält keine lesbare Textebene und wurde per Texterkennung (OCR) verarbeitet.
          Bitte alle Werte sorgfältig prüfen und bestätigen.
        </div>
      )}
      {model.abweichungen?.length > 0 && (
        <div className="banner warn">
          {model.abweichungen.length} Feld(er) weichen zwischen PDF und CSV ab (gelb markiert) – bitte prüfen.
        </div>
      )}
      {genMsg?.ok && <div className="banner ok">{genMsg.text}</div>}
      {genMsg && !genMsg.ok && (
        <div className="banner err">
          Noch nicht exportierbar:
          <ul style={{ margin: "6px 0 0" }}>
            {genMsg.issues.map((it, k) => <li key={k}>{it.meldung}</li>)}
          </ul>
        </div>
      )}

      <div className="pruef">
        <div className="pdf">
          {pdfUrl ? (
            <embed src={pdfUrl} type="application/pdf" />
          ) : (
            <div className="panel muted">Kein Original-PDF vorhanden (CSV-Datenpfad).</div>
          )}
        </div>

        <div className="panel">
          <div className="section-title">Kopfdaten</div>
          {Object.keys(KOPF_LABELS).map((f) =>
            kopf[f] !== undefined || ["leitweg_id", "kaeufer_name", "verkaeufer_name", "bankverbindung_iban"].includes(f) ? (
              <FieldRow key={f} label={KOPF_LABELS[f]} field={kopf[f]} onSave={(v) => saveField("kopf", f, v)} />
            ) : null
          )}

          <div className="section-title">Summen</div>
          {Object.keys(SUMMEN_LABELS).map((f) =>
            summen[f] !== undefined ? (
              <FieldRow key={f} label={SUMMEN_LABELS[f]} field={summen[f]} onSave={(v) => saveField("summen", f, v)} />
            ) : null
          )}

          <div className="section-title">Positionen ({positionen.length})</div>
          {positionen.map((pos, idx) => (
            <div key={idx} className="panel" style={{ margin: "8px 0", padding: 12 }}>
              <strong>Position {idx + 1}: {pos.artikelnummer?.wert || ""}</strong>
              {["beschreibung", "menge", "einheit", "einzelpreis", "gesamtpreis", "steuersatz"].map((f) => (
                <FieldRow
                  key={f}
                  label={f}
                  field={pos[f]}
                  onSave={(v) => saveField("position", f, v, idx)}
                />
              ))}
            </div>
          ))}

          <div className="mt row" style={{ justifyContent: "space-between" }}>
            <span className={detail.exportReady ? "muted" : "hinweis"} style={{ margin: 0 }}>
              {detail.exportReady ? "Alle Pflichtfelder sind grün." : `${detail.issues.length} Punkt(e) offen`}
            </span>
            <div className="row">
              {validiert && (
                <>
                  <button className="secondary" onClick={() => download(api.reportPath(id), `bericht-${id}.xml`)}>
                    Validierungsbericht
                  </button>
                  <button onClick={() => download(api.exportPath(id), `rechnung-${kopf.rechnungsnummer?.wert || id}.xml`)}>
                    XRechnung herunterladen
                  </button>
                </>
              )}
              {!validiert && (
                <button onClick={generate} disabled={busy}>
                  {busy ? "Wird erzeugt…" : "XRechnung erzeugen"}
                </button>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
