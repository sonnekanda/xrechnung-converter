import { useCallback, useEffect, useState } from "react";
import { useParams, Link, useNavigate } from "react-router-dom";
import { api } from "../api";
import ReportModal from "../components/ReportModal";

const KOPF_LABELS = {
  rechnungsnummer: "Rechnungsnummer",
  rechnungsdatum: "Rechnungsdatum",
  faelligkeit: "Fälligkeit",
  waehrung: "Währung",
  leitweg_id: "Leitweg-ID",
  verkaeufer_name: "Verkäufer",
  verkaeufer_strasse: "Straße (eigene Firma)",
  verkaeufer_plz: "PLZ (eigene Firma)",
  verkaeufer_ort: "Ort (eigene Firma)",
  verkaeufer_telefon: "Telefon (eigene Firma)",
  verkaeufer_ustid: "USt-IdNr (Verkäufer)",
  kaeufer_name: "Käufer",
  kaeufer_strasse: "Straße (Käufer)",
  kaeufer_plz: "PLZ (Käufer)",
  kaeufer_ort: "Ort (Käufer)",
  kaeufer_ustid: "USt-IdNr (Käufer)",
  kaeufer_email: "E-Mail (Käufer)",
  verkaeufer_email: "E-Mail (Verkäufer)",
  bankverbindung_iban: "IBAN",
};
const SUMMEN_LABELS = {
  nettobetrag: "Nettobetrag",
  steuerbetrag: "Steuerbetrag",
  bruttobetrag: "Bruttobetrag",
  steuersatz: "Steuersatz (%)",
};

// Konstante Verkäufer-Daten (aus „Eigene Firma"). Sie wiederholen sich auf jeder
// Rechnung und werden darum in einen eigenen, standardmäßig eingeklappten
// Abschnitt ausgelagert – die restlichen Kopfdaten sind rechnungsspezifisch.
const VERKAEUFER_FELDER = [
  "verkaeufer_name", "verkaeufer_strasse", "verkaeufer_plz", "verkaeufer_ort",
  "verkaeufer_telefon", "verkaeufer_email", "verkaeufer_ustid", "bankverbindung_iban",
];
// Käufer-/Empfänger-Daten – ebenfalls gruppiert, aber standardmäßig aufgeklappt
// (sie ändern sich je Rechnung und sind die häufigste Korrekturstelle).
const KAEUFER_FELDER = [
  "kaeufer_name", "kaeufer_strasse", "kaeufer_plz", "kaeufer_ort",
  "kaeufer_ustid", "kaeufer_email",
];
// Rechnungsbezogene Kopffelder (weder Verkäufer noch Käufer), in Anzeigereihenfolge.
const KOPF_FELDER = ["rechnungsnummer", "rechnungsdatum", "faelligkeit", "waehrung", "leitweg_id"];

function ampelClass(status) {
  return status === "sicher" ? "gruen" : status === "unsicher" ? "gelb" : "rot";
}

// PDF/CSV-Abweichungen, deren Feld aktuell noch unsicher (gelb) ist. Sobald der
// Nutzer ein solches Feld bestätigt oder korrigiert (Status -> sicher), zählt es
// nicht mehr, sodass der Warnhinweis mit dem tatsächlichen Zustand übereinstimmt.
function offeneAbweichungen(model) {
  return (model.abweichungen || []).filter((a) => {
    const field = a.position_index != null
      ? model.positionen?.[a.position_index]?.[a.feld]
      : (model.kopf?.[a.feld] ?? model.summen?.[a.feld]);
    return field?.status === "unsicher";
  });
}

function FieldRow({ label, field, onSave, readOnly, issue }) {
  const [value, setValue] = useState(field?.wert ?? "");
  useEffect(() => { setValue(field?.wert ?? ""); }, [field?.wert]);
  const status = field?.status || "fehlt";
  const dirty = (field?.wert ?? "") !== value;
  // Hinweistext: bevorzugt die Fachprüfungs-Meldung, sonst der Feld-Hinweis.
  const meldung = issue || (status !== "sicher" ? field?.hinweis : null);

  // Speichern, wenn der Wert geändert wurde ODER das Feld noch bestätigt werden
  // muss (gelb/rot). So wird auch ein korrekter gelber Wert per Klick grün.
  async function save() {
    if (dirty || status !== "sicher") await onSave(value);
  }

  return (
    <div className={`field-row ${issue ? "has-issue" : ""}`}>
      <label title={field?.herkunft}>{label}</label>
      <div className="val">
        <span className={`ampel ${issue ? "rot" : ampelClass(status)}`} title={meldung || status} />
        <input
          value={value}
          readOnly={readOnly}
          title={meldung || undefined}
          onChange={(e) => setValue(e.target.value)}
          onBlur={readOnly ? undefined : save}
          onKeyDown={(e) => e.key === "Enter" && e.target.blur()}
          placeholder={status === "fehlt" ? "bitte ergänzen" : ""}
        />
        {field?.herkunft && <span className="herkunft">{field.herkunft}</span>}
        {issue && <span className="issue-marker" title={meldung}>⚠</span>}
        {!readOnly && status !== "sicher" && (
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
      {meldung && <div className="hinweis">{meldung}</div>}
    </div>
  );
}

// Gruppierter, ein-/ausklappbarer Feldblock (Verkäufer, Käufer). Ein offener
// Punkt (offen=true) hält den Abschnitt aufgeklappt, damit ein Problem – z.B.
// ein fehlendes Pflichtfeld – nie hinter einer Kopfzeile verschwindet.
function CollapsibleGroup({ title, summary, offen, defaultOpen, okLabel = "✓ vollständig", children }) {
  const [userOpen, setUserOpen] = useState(defaultOpen);
  const open = userOpen || offen;
  return (
    <div className="collapse-box">
      <button type="button" className="collapse-head" onClick={() => setUserOpen((o) => !o)}
              aria-expanded={open}>
        <span className="chev">{open ? "▾" : "▸"}</span>
        <span>{title}</span>
        <span className="collapse-sum muted">{summary}</span>
        <span className={offen ? "badge-warn" : "badge-ok"}>{offen ? "⚠ unvollständig" : okLabel}</span>
      </button>
      {open && <div className="collapse-body">{children}</div>}
    </div>
  );
}

export default function Pruefbildschirm() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [detail, setDetail] = useState(null);
  const [pdfUrl, setPdfUrl] = useState(null);
  const [error, setError] = useState(null);
  const [genMsg, setGenMsg] = useState(null);
  const [info, setInfo] = useState(null);
  const [busy, setBusy] = useState(false);
  const [zeigeBericht, setZeigeBericht] = useState(false);
  const [toast, setToast] = useState(null);

  // Erfolgs-Toast oben automatisch nach 3 s schließen.
  useEffect(() => {
    if (!toast) return undefined;
    const t = setTimeout(() => setToast(null), 3000);
    return () => clearTimeout(t);
  }, [toast]);

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
    try {
      const updated = await api.patchField(id, block, feld, wert, positionIndex);
      setDetail(updated);
      setGenMsg(null); // eine Änderung verwirft ein evtl. bereits erzeugtes XML
      setError(null);
    } catch (e) {
      setError(e.message);
    }
  }

  async function generate() {
    setBusy(true);
    setGenMsg(null);
    try {
      const res = await api.generate(id);
      await load();
      if (res.valid) setToast("✓ XRechnung geprüft und amtlich validiert.");
      else setGenMsg({ ok: false, issues: res.issues });
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  async function download(path, filename) {
    try {
      await api.download(path, filename);
      await load(); // Status kann sich auf EXPORTIERT geändert haben
    } catch (e) {
      setError(e.message);
    }
  }

  async function reopen() {
    setError(null);
    setGenMsg(null);
    try {
      setDetail(await api.reopen(id));
    } catch (e) {
      setError(e.message);
    }
  }

  // Aktuelle Firmendaten in die Verkäufer-Felder übernehmen (überschreibend).
  async function firmaUebernehmen() {
    setError(null);
    setInfo(null);
    try {
      setDetail(await api.applyCompany(id));
      setGenMsg(null);
      setToast("✓ Verkäuferdaten aus eigener Firma übernommen.");
    } catch (e) {
      setError(e.message);
    }
  }

  async function remove() {
    if (!confirm("Diese Rechnung wirklich löschen? Datei, Daten und XML werden entfernt.")) return;
    try {
      await api.deleteInvoice(id);
      navigate("/");
    } catch (e) {
      setError(e.message);
    }
  }

  // Käufer der aktuellen Rechnung als Empfänger-Stammdaten übernehmen.
  async function saveRecipient(kopf) {
    const w = (f) => kopf[f]?.wert || "";
    const recipient = {
      name: w("kaeufer_name"),
      ustId: w("kaeufer_ustid"),
      kundennummer: w("kaeufer_kundennummer"),
      leitwegId: w("leitweg_id"),
      strasse: w("kaeufer_strasse"),
      plz: w("kaeufer_plz"),
      ort: w("kaeufer_ort"),
      land: w("kaeufer_land") || "DE",
      email: w("kaeufer_email"),
      zahlungsbedingungen: w("zahlungsbedingungen"),
    };
    setInfo(null);
    setError(null);
    try {
      await api.createStammdaten(recipient);
      setInfo(`Empfänger „${recipient.name}" wurde in den Stammdaten gespeichert.`);
    } catch (e) {
      // 409 = bereits vorhanden -> informativ statt als Fehler.
      setInfo(e.message);
    }
  }

  // Nur die anfängliche Ladefehler-Situation ersetzt die ganze Seite; Fehler
  // während der Bearbeitung erscheinen als Banner über dem Formular.
  if (error && !detail) return <div className="panel"><div className="banner err">{error}</div><Link to="/">Zurück</Link></div>;
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
  // Nach der Validierung sind die Felder gesperrt; zum Ändern erst "Daten ändern".
  const readOnly = validiert;
  // Fachprüfungs-Meldungen den Feldern zuordnen (für Hervorhebung + Tooltip).
  const issueByFeld = {};
  (detail.issues || []).forEach((i) => { issueByFeld[i.feld] = i.meldung; });
  const ladeXml = () => download(api.exportPath(id), `rechnung-${kopf.rechnungsnummer?.wert || id}.xml`);

  // Ein Feldblock hat einen offenen Punkt, sobald ein Feld beanstandet oder
  // unsicher ist – dann bleibt der Abschnitt aufgeklappt.
  const gruppeOffen = (felder) =>
    felder.some((f) => issueByFeld[f] || (kopf[f] && kopf[f].status !== "sicher"));
  const zusammenfassung = (nameF, ortF, idF) =>
    [kopf[nameF]?.wert, kopf[ortF]?.wert, kopf[idF]?.wert].filter(Boolean).join(" · ");
  const verkaeuferOffen = gruppeOffen(VERKAEUFER_FELDER);
  const kaeuferOffen = gruppeOffen(KAEUFER_FELDER);

  return (
    <div>
      {toast && (
        <div className="toast" role="status">
          <span>{toast}</span>
          <button className="ghost icon" onClick={() => setToast(null)} aria-label="Schließen">✕</button>
        </div>
      )}

      {/* Feste Aktionsleiste: bleibt beim Scrollen oben; Tasten je nach Status aktiv. */}
      <div className="action-bar">
        <Link to="/" className="icon-back" title="Zurück zur Übersicht" aria-label="Zurück zur Übersicht">←</Link>
        <h2 className="action-title">Prüfen: Rechnung {kopf.rechnungsnummer?.wert || detail.id.slice(0, 8)}</h2>
        <div className="spacer" />
        <button className="small" onClick={generate} disabled={validiert || busy}
                title="Amtlich prüfen (Mustangproject + KoSIT)">
          {busy ? "Prüft…" : "Prüfen"}
        </button>
        <button className="secondary small" onClick={reopen} disabled={detail.status !== "VALIDIERT"}
                title="Zurück zur Bearbeitung – die erzeugte XRechnung wird verworfen">
          Daten ändern
        </button>
        <button className="secondary small" onClick={() => setZeigeBericht(true)} disabled={!detail.hasReport}
                title={detail.hasReport
                  ? "Validierungsbericht anzeigen (auch bei fehlgeschlagener Prüfung)"
                  : "Noch kein Validierungsbericht – zuerst „Prüfen“"}>
          Bericht
        </button>
        <button className="small" onClick={ladeXml} disabled={!validiert}
                title="XRechnung (UBL-XML) herunterladen">
          ⭳ XRechnung
        </button>
        <button className="secondary small"
                onClick={() => download(api.zugferdPath(id), `rechnung-${kopf.rechnungsnummer?.wert || id}-zugferd.pdf`)}
                disabled={!validiert || !detail.pdfFilename}
                title={detail.pdfFilename
                  ? "ZUGFeRD-PDF (XRechnung im Original-PDF eingebettet) herunterladen"
                  : "ZUGFeRD benötigt ein Original-PDF – zu dieser Rechnung liegt nur eine CSV vor"}>
          ⭳ ZUGFeRD
        </button>
      </div>

      {detail.needsOcr && (
        <div className="banner warn">
          Diese Rechnung enthält keine lesbare Textebene und wurde per Texterkennung (OCR) verarbeitet.
          Bitte alle Werte sorgfältig prüfen und bestätigen.
        </div>
      )}
      {offeneAbweichungen(model).length > 0 && (
        <div className="banner warn">
          {offeneAbweichungen(model).length} Feld(er) weichen zwischen PDF und CSV ab (gelb markiert) – bitte prüfen.
        </div>
      )}
      {error && (
        <div className="banner err row" style={{ justifyContent: "space-between" }}>
          <span>{error}</span>
          <button className="ghost" onClick={() => setError(null)}>Schließen</button>
        </div>
      )}
      {info && (
        <div className="banner ok row" style={{ justifyContent: "space-between" }}>
          <span>{info}</span>
          <button className="ghost" onClick={() => setInfo(null)}>Schließen</button>
        </div>
      )}
      {validiert && (
        <div className="banner ok">
          {detail.status === "EXPORTIERT"
            ? "Diese Rechnung wurde exportiert und ist schreibgeschützt."
            : "XRechnung amtlich validiert – die Felder sind schreibgeschützt. Zum Ändern oben „Daten ändern“ wählen (die erzeugte XRechnung wird dann verworfen)."}
        </div>
      )}
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
          {/* Rechnungsbezogene Felder */}
          {KOPF_FELDER.map((f) =>
            kopf[f] !== undefined || f === "leitweg_id" ? (
              <FieldRow key={f} label={KOPF_LABELS[f]} field={kopf[f]} readOnly={readOnly} issue={issueByFeld[f]} onSave={(v) => saveField("kopf", f, v)} />
            ) : null
          )}

          {/* Verkäufer (eigene Firma): konstante Daten, standardmäßig eingeklappt. */}
          <CollapsibleGroup title="Verkäufer (eigene Firma)" okLabel="✓ aus eigener Firma"
                            summary={zusammenfassung("verkaeufer_name", "verkaeufer_ort", "verkaeufer_ustid")}
                            offen={verkaeuferOffen} defaultOpen={false}>
            {VERKAEUFER_FELDER.map((f) => (
              <FieldRow key={f} label={KOPF_LABELS[f]} field={kopf[f]} readOnly={readOnly}
                        issue={issueByFeld[f]} onSave={(v) => saveField("kopf", f, v)} />
            ))}
            {!readOnly && (
              <div className="mt">
                <button className="secondary small" onClick={firmaUebernehmen}>
                  Aus eigener Firma übernehmen
                </button>
                <div className="muted" style={{ marginTop: 4 }}>
                  Überschreibt die Verkäufer-Felder mit den aktuellen Angaben aus
                  „Eigene Firma“ – nützlich für Rechnungen, die vor deren Pflege entstanden.
                </div>
              </div>
            )}
          </CollapsibleGroup>

          {/* Käufer (Empfänger): rechnungsspezifisch, standardmäßig aufgeklappt. */}
          <CollapsibleGroup title="Käufer (Empfänger)"
                            summary={zusammenfassung("kaeufer_name", "kaeufer_ort", "kaeufer_ustid")}
                            offen={kaeuferOffen} defaultOpen>
            {KAEUFER_FELDER.map((f) => (
              <FieldRow key={f} label={KOPF_LABELS[f]} field={kopf[f]} readOnly={readOnly}
                        issue={issueByFeld[f]} onSave={(v) => saveField("kopf", f, v)} />
            ))}
            {kopf.kaeufer_name?.wert && (
              <div className="mt">
                <button className="secondary" onClick={() => saveRecipient(kopf)}>
                  Empfänger in Stammdaten speichern
                </button>
                <div className="muted" style={{ marginTop: 4 }}>
                  Übernimmt Name, USt-IdNr, Kundennummer, Adresse und Leitweg-ID für die
                  automatische Ergänzung künftiger Rechnungen.
                </div>
              </div>
            )}
          </CollapsibleGroup>

          <div className="section-title">Summen</div>
          {Object.keys(SUMMEN_LABELS).map((f) =>
            summen[f] !== undefined ? (
              <FieldRow key={f} label={SUMMEN_LABELS[f]} field={summen[f]} readOnly={readOnly} issue={issueByFeld[f] || (f === "bruttobetrag" ? issueByFeld.summen : undefined)} onSave={(v) => saveField("summen", f, v)} />
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
                  readOnly={readOnly}
                  issue={issueByFeld[`position[${idx + 1}].${f}`]}
                  onSave={(v) => saveField("position", f, v, idx)}
                />
              ))}
            </div>
          ))}

          <div className="mt row" style={{ justifyContent: "space-between" }}>
            <span className={detail.exportReady ? "muted" : "hinweis"} style={{ margin: 0 }}>
              {detail.exportReady ? "Alle Pflichtfelder sind grün." : `${detail.issues.length} Punkt(e) offen`}
            </span>
            <button className="danger" onClick={remove}>🗑 Rechnung löschen</button>
          </div>
        </div>
      </div>

      {zeigeBericht && (
        <ReportModal
          invoice={{ id, rechnungsnummer: kopf.rechnungsnummer?.wert }}
          onClose={() => setZeigeBericht(false)}
        />
      )}
    </div>
  );
}
