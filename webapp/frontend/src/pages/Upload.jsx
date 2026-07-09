import { useRef, useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { api } from "../api";

export default function Upload() {
  const navigate = useNavigate();
  const [drag, setDrag] = useState(false);
  const [files, setFiles] = useState([]); // Liste ausgewählter Dateien
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [results, setResults] = useState(null);
  const inputRef = useRef();

  function accept(fileList) {
    const next = [...files];
    for (const f of fileList) {
      const name = f.name.toLowerCase();
      if ((name.endsWith(".pdf") || name.endsWith(".csv")) && !next.some((x) => x.name === f.name)) {
        next.push(f);
      }
    }
    setFiles(next);
  }

  function onDrop(e) {
    e.preventDefault();
    setDrag(false);
    accept(e.dataTransfer.files);
  }

  function removeFile(name) {
    setFiles(files.filter((f) => f.name !== name));
  }

  async function submit() {
    if (files.length === 0) {
      setError("Bitte wählen Sie mindestens eine PDF- oder CSV-Datei.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const res = await api.upload(files);
      if (res.length === 1) {
        navigate(`/pruefen/${res[0].id}`); // eine Rechnung -> direkt zur Prüfung
      } else {
        setResults(res); // mehrere -> Übersicht der angelegten Rechnungen
        setFiles([]);
      }
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  if (results) {
    return (
      <div className="panel" style={{ maxWidth: 640 }}>
        <h2>{results.length} Rechnungen hochgeladen</h2>
        <p className="muted">Die Dateien werden ausgelesen. Prüfen Sie die Rechnungen anschließend.</p>
        <table>
          <thead><tr><th>Datei(en)</th><th>Status</th><th></th></tr></thead>
          <tbody>
            {results.map((r) => (
              <tr key={r.id}>
                <td>{[r.pdfFilename, r.csvFilename].filter(Boolean).join(" + ")}</td>
                <td className="muted">{r.needsOcr ? "OCR nötig" : "in Verarbeitung"}</td>
                <td><Link to={`/pruefen/${r.id}`}><button className="secondary">Prüfen</button></Link></td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="mt row">
          <Link to="/"><button>Zur Übersicht</button></Link>
          <button className="ghost" onClick={() => setResults(null)}>Weitere hochladen</button>
        </div>
      </div>
    );
  }

  return (
    <div className="panel" style={{ maxWidth: 640 }}>
      <h2>Rechnungen hochladen</h2>
      <p className="muted">
        Laden Sie eine oder mehrere Abas-Rechnungen als PDF und/oder CSV hoch. Dateien mit
        gleichem Namen (z. B. 400009.pdf + 400009.csv) gehören zu einer Rechnung; das PDF führt,
        die CSV dient der Gegenprüfung.
      </p>
      {error && <div className="banner err">{error}</div>}
      <div
        className={`upload-zone ${drag ? "drag" : ""}`}
        onDragOver={(e) => { e.preventDefault(); setDrag(true); }}
        onDragLeave={() => setDrag(false)}
        onDrop={onDrop}
        onClick={() => inputRef.current?.click()}
      >
        <input ref={inputRef} type="file" accept=".pdf,.csv" multiple hidden
               onChange={(e) => accept(e.target.files)} />
        <div style={{ fontSize: 30 }}>⭱</div>
        <div>Dateien hierher ziehen oder klicken zum Auswählen</div>
        <div className="muted">Erlaubt: PDF, CSV · Mehrfachauswahl möglich</div>
      </div>

      {files.length > 0 && (
        <div className="mt">
          {files.map((f) => (
            <div key={f.name} className="row" style={{ justifyContent: "space-between" }}>
              <span><span className="ampel gruen" /> {f.name}</span>
              <button className="ghost" onClick={() => removeFile(f.name)}>Entfernen</button>
            </div>
          ))}
        </div>
      )}

      <div className="mt row">
        <button onClick={submit} disabled={busy}>{busy ? "Wird hochgeladen…" : "Hochladen & prüfen"}</button>
        {files.length > 0 && <button className="ghost" onClick={() => setFiles([])}>Zurücksetzen</button>}
      </div>
    </div>
  );
}
