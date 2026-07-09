import { useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api";

export default function Upload() {
  const navigate = useNavigate();
  const [drag, setDrag] = useState(false);
  const [files, setFiles] = useState({ pdf: null, csv: null });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const inputRef = useRef();

  function accept(fileList) {
    const next = { ...files };
    for (const f of fileList) {
      const name = f.name.toLowerCase();
      if (name.endsWith(".pdf")) next.pdf = f;
      else if (name.endsWith(".csv")) next.csv = f;
    }
    setFiles(next);
  }

  function onDrop(e) {
    e.preventDefault();
    setDrag(false);
    accept(e.dataTransfer.files);
  }

  async function submit() {
    if (!files.pdf && !files.csv) {
      setError("Bitte wählen Sie mindestens eine PDF- oder CSV-Datei.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const res = await api.upload(files.pdf, files.csv);
      // Kurz auf Extraktion warten, dann in den Prüfbildschirm.
      navigate(`/pruefen/${res.id}`);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="panel" style={{ maxWidth: 640 }}>
      <h2>Rechnung hochladen</h2>
      <p className="muted">
        Laden Sie die Abas-Rechnung als PDF und/oder CSV hoch. Gehört beides zusammen,
        führt das PDF; die CSV dient der Gegenprüfung.
      </p>
      {error && <div className="banner err">{error}</div>}
      <div
        className={`upload-zone ${drag ? "drag" : ""}`}
        onDragOver={(e) => { e.preventDefault(); setDrag(true); }}
        onDragLeave={() => setDrag(false)}
        onDrop={onDrop}
        onClick={() => inputRef.current?.click()}
      >
        <input
          ref={inputRef}
          type="file"
          accept=".pdf,.csv"
          multiple
          hidden
          onChange={(e) => accept(e.target.files)}
        />
        <div style={{ fontSize: 30 }}>⭱</div>
        <div>Dateien hierher ziehen oder klicken zum Auswählen</div>
        <div className="muted">Erlaubt: PDF, CSV</div>
      </div>

      <div className="mt">
        {files.pdf && <div className="row"><span className="ampel gruen" /> PDF: {files.pdf.name}</div>}
        {files.csv && <div className="row mt"><span className="ampel gruen" /> CSV: {files.csv.name}</div>}
      </div>

      <div className="mt row">
        <button onClick={submit} disabled={busy}>{busy ? "Wird hochgeladen…" : "Hochladen & prüfen"}</button>
        {(files.pdf || files.csv) && (
          <button className="ghost" onClick={() => setFiles({ pdf: null, csv: null })}>Zurücksetzen</button>
        )}
      </div>
    </div>
  );
}
