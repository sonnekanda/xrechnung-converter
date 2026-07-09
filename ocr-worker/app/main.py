"""ocr-worker: Texterkennung fuer gescannte PDFs ohne Textebene.

Nutzt ocrmypdf/Tesseract (deutsche Modelle), legt eine durchsuchbare Textebene
an und liefert den erkannten Text zurueck. OCR-Ergebnisse sind grundsaetzlich
prueffpflichtig; die Bewertung als "unsicher" erfolgt im extraction-worker,
unabhaengig von der Confidence.
"""
from __future__ import annotations

import logging
import os
import subprocess
import tempfile
from pathlib import Path

from fastapi import FastAPI, File, UploadFile
from fastapi.responses import JSONResponse

logging.basicConfig(
    level=os.getenv("LOGLEVEL", "INFO"),
    format='{"ts":"%(asctime)s","level":"%(levelname)s","svc":"ocr-worker","msg":"%(message)s"}',
)
log = logging.getLogger("ocr-worker")

app = FastAPI(title="senapsa ocr-worker", version="1.0.0", docs_url="/docs")


@app.get("/health")
def health() -> dict:
    return {"status": "UP", "service": "ocr-worker"}


@app.post("/ocr")
async def ocr(pdf: UploadFile = File(...)) -> JSONResponse:
    """Fuehrt OCR aus und liefert den erkannten Text."""
    with tempfile.TemporaryDirectory() as tmp:
        src = Path(tmp) / (pdf.filename or "scan.pdf")
        out = Path(tmp) / "ocr.pdf"
        src.write_bytes(pdf.file.read())
        try:
            subprocess.run(
                ["ocrmypdf", "--force-ocr", "-l", "deu", "--output-type", "pdf",
                 str(src), str(out)],
                check=True, capture_output=True, timeout=300,
            )
            text = _extract_text(out)
            return JSONResponse({"text": text, "engine": "tesseract-deu"})
        except subprocess.CalledProcessError as exc:
            log.error("OCR fehlgeschlagen: %s", exc.stderr.decode(errors="replace")[:400])
            return JSONResponse({"text": "", "engine": "tesseract-deu", "error": "OCR fehlgeschlagen"},
                                status_code=422)


def _extract_text(pdf_path: Path) -> str:
    import pdfplumber
    parts: list[str] = []
    with pdfplumber.open(str(pdf_path)) as doc:
        for page in doc.pages:
            parts.append(page.extract_text() or "")
    return "\n".join(parts)
