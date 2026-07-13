"""HTTP-Schnittstelle des extraction-worker + optionaler Redis-Consumer.

Der Dienst ist White-Label und liefert ausschliesslich in das interne Modell.
Er kann synchron (HTTP ``/extract``) oder asynchron (Redis-Queue) betrieben werden.
"""
from __future__ import annotations

import json
import logging
import os
import tempfile
import threading
from pathlib import Path

from fastapi import FastAPI, File, UploadFile
from fastapi.responses import JSONResponse

from . import __version__
from .pipeline import run_extraction

logging.basicConfig(
    level=os.getenv("LOGLEVEL", "INFO"),
    format='{"ts":"%(asctime)s","level":"%(levelname)s","svc":"extraction-worker","msg":"%(message)s"}',
)
log = logging.getLogger("extraction-worker")

app = FastAPI(title="senapsa extraction-worker", version=__version__, docs_url="/docs")


@app.get("/health")
def health() -> dict:
    return {"status": "UP", "service": "extraction-worker", "version": __version__}


@app.post("/extract")
async def extract(pdf: UploadFile | None = File(default=None),
                  csv: UploadFile | None = File(default=None)) -> JSONResponse:
    """Nimmt PDF und/oder CSV entgegen und liefert das interne Rechnungsmodell."""
    with tempfile.TemporaryDirectory() as tmp:
        pdf_path = _save(tmp, pdf) if pdf else None
        csv_path = _save(tmp, csv) if csv else None
        outcome = run_extraction(pdf_path=pdf_path, csv_path=csv_path)

    body = {
        "needs_ocr": outcome.needs_ocr,
        "model": outcome.model.to_dict() if outcome.model else None,
    }
    return JSONResponse(body)


def _save(tmp: str, upload: UploadFile) -> str:
    dest = Path(tmp) / (upload.filename or "upload.bin")
    dest.write_bytes(upload.file.read())
    return str(dest)


# --- Optionaler asynchroner Betrieb ueber Redis-Queue ------------------------
def _redis_consumer() -> None:  # pragma: no cover - Integrationspfad
    import redis  # lokal importiert, damit Tests ohne Redis laufen

    r = redis.Redis(
        host=os.getenv("REDIS_HOST", "redis"),
        port=int(os.getenv("REDIS_PORT", "6379")),
        password=os.getenv("REDIS_PASSWORD") or None,
        decode_responses=True,
    )
    log.info("Redis-Consumer gestartet (Queue q:extraction)")
    while True:
        _, payload = r.brpop("q:extraction")  # blockiert bis Job vorliegt
        job = json.loads(payload)
        try:
            outcome = run_extraction(pdf_path=job.get("pdf_path"), csv_path=job.get("csv_path"),
                                     ki_config=job.get("ki"))
            result = {
                "invoice_id": job["invoice_id"],
                "needs_ocr": outcome.needs_ocr,
                "model": outcome.model.to_dict() if outcome.model else None,
            }
            r.set(f"result:{job['invoice_id']}", json.dumps(result), ex=3600)
            r.lpush("q:extraction:done", job["invoice_id"])
            log.info("Extraktion fertig fuer %s (needs_ocr=%s)", job["invoice_id"], outcome.needs_ocr)
        except Exception:  # noqa: BLE001 - Job darf den Consumer nicht killen
            log.exception("Extraktion fehlgeschlagen fuer %s", job.get("invoice_id"))
            r.lpush("q:extraction:failed", job.get("invoice_id", "?"))


@app.on_event("startup")
def _maybe_start_consumer() -> None:  # pragma: no cover
    if os.getenv("REDIS_HOST"):
        threading.Thread(target=_redis_consumer, daemon=True).start()
