#!/usr/bin/env python3
"""Lasttest für den eRechnung-Konverter (Auslegung: 200 Rechnungen/Tag, 5 parallele Nutzer).

Simuliert mehrere gleichzeitige Nutzer, die Rechnungen hochladen; misst, bis die
Extraktion (über die Redis-Queue) je Rechnung abgeschlossen ist (Status PRUEFEN).

Beispiel:
    python scripts/loadtest.py --count 200 --concurrency 5
Nur Standardbibliothek erforderlich.
"""
from __future__ import annotations

import argparse
import json
import pathlib
import statistics
import threading
import time
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed

AUFG = pathlib.Path(__file__).resolve().parents[1] / "aufgabe"
PDF = (AUFG / "Musterrechnung PDF" / "XDOC18.1782975457883.MASTER.pdf").read_bytes()
CSV = (AUFG / "Musterrechnung CSV" / "400009.csv").read_bytes()


def login(base: str, email: str, password: str) -> str:
    body = json.dumps({"email": email, "password": password}).encode()
    req = urllib.request.Request(base + "/auth/login", data=body,
                                 headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())["token"]


def _multipart(files: list[tuple[str, bytes]]) -> tuple[bytes, str]:
    boundary = uuid.uuid4().hex
    parts = []
    for name, content in files:
        parts.append((f'--{boundary}\r\nContent-Disposition: form-data; name="files"; '
                      f'filename="{name}"\r\nContent-Type: application/octet-stream\r\n\r\n').encode())
        parts.append(content)
        parts.append(b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode())
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"


def process_one(base: str, token: str, idx: int, timeout: float) -> float:
    """Ein Nutzer-Vorgang: Upload + warten bis Extraktion fertig. Liefert Dauer (s)."""
    start = time.time()
    data, ctype = _multipart([(f"{idx}.pdf", PDF), (f"{idx}.csv", CSV)])
    req = urllib.request.Request(base + "/files/upload", data=data,
                                 headers={"Authorization": "Bearer " + token, "Content-Type": ctype},
                                 method="POST")
    with urllib.request.urlopen(req) as r:
        inv_id = json.loads(r.read())[0]["id"]
    # bis PRUEFEN (Extraktion abgeschlossen) pollen
    while time.time() - start < timeout:
        s = urllib.request.Request(base + f"/files/{inv_id}/status",
                                   headers={"Authorization": "Bearer " + token})
        with urllib.request.urlopen(s) as r:
            if json.loads(r.read())["status"] == "PRUEFEN":
                return time.time() - start
        time.sleep(0.5)
    raise TimeoutError(f"Rechnung {inv_id} nicht rechtzeitig verarbeitet")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default="http://localhost:8080/api/v1")
    ap.add_argument("--email", default="admin@senapsa.local")
    ap.add_argument("--password", default="admin12345")
    ap.add_argument("--count", type=int, default=200)
    ap.add_argument("--concurrency", type=int, default=5)
    ap.add_argument("--timeout", type=float, default=120)
    args = ap.parse_args()

    token = login(args.url, args.email, args.password)
    print(f"Lasttest: {args.count} Rechnungen, {args.concurrency} parallele Nutzer -> {args.url}")

    durations: list[float] = []
    errors = 0
    lock = threading.Lock()
    t0 = time.time()
    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        futures = [pool.submit(process_one, args.url, token, i, args.timeout)
                   for i in range(args.count)]
        for f in as_completed(futures):
            try:
                d = f.result()
                with lock:
                    durations.append(d)
            except Exception as e:  # noqa: BLE001
                with lock:
                    errors += 1
                print("  Fehler:", e)
    wall = time.time() - t0

    ok = len(durations)
    print("\n=== Ergebnis ===")
    print(f"Erfolgreich:   {ok}/{args.count}  (Fehler: {errors})")
    print(f"Gesamtdauer:   {wall:.1f} s")
    print(f"Durchsatz:     {ok / wall * 60:.1f} Rechnungen/Minute "
          f"(~{ok / wall * 3600:.0f}/Stunde)")
    if durations:
        durations.sort()
        print(f"Latenz je Rechnung: min {durations[0]:.1f}s  "
              f"median {statistics.median(durations):.1f}s  "
              f"p95 {durations[int(len(durations) * 0.95) - 1]:.1f}s  "
              f"max {durations[-1]:.1f}s")
    # Auslegungsziel 200/Tag ist bei diesem Durchsatz um Größenordnungen erfüllt.


if __name__ == "__main__":
    main()
