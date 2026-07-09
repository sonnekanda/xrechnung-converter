# syntax=docker/dockerfile:1
# --- Build-Stufe: Abhaengigkeiten in ein venv ------------------------------
FROM python:3.12-slim AS build
ENV PIP_NO_CACHE_DIR=1 PYTHONDONTWRITEBYTECODE=1
WORKDIR /build
RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"
COPY requirements.txt .
RUN pip install --upgrade pip && pip install -r requirements.txt

# --- Laufzeit-Stufe --------------------------------------------------------
FROM python:3.12-slim AS runtime
ENV PATH="/opt/venv/bin:$PATH" \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1
# curl fuer Healthcheck, gosu fuer sicheren User-Wechsel im entrypoint.
RUN set -eux; \
    for i in 1 2 3; do apt-get update && break || sleep 5; done; \
    apt-get install -y --no-install-recommends curl gosu; \
    rm -rf /var/lib/apt/lists/*; \
    groupadd -r app && useradd -r -g app -u 10001 app; \
    mkdir -p /data && chown app:app /data
COPY --from=build /opt/venv /opt/venv
WORKDIR /app
COPY app ./app
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh
EXPOSE 8000
HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=30s \
    CMD curl -fsS http://localhost:8000/health || exit 1
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
