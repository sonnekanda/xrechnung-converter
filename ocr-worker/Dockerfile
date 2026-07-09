# syntax=docker/dockerfile:1
FROM python:3.12-slim AS runtime
ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1 PIP_NO_CACHE_DIR=1
# OCR-Systemabhaengigkeiten: Tesseract (deutsch), Ghostscript, qpdf.
RUN set -eux; \
    for i in 1 2 3; do apt-get update && break || sleep 5; done; \
    apt-get install -y --no-install-recommends \
        curl gosu tesseract-ocr tesseract-ocr-deu ghostscript qpdf unpaper pngquant; \
    rm -rf /var/lib/apt/lists/*; \
    groupadd -r app && useradd -r -g app -u 10001 app; \
    mkdir -p /data && chown app:app /data
WORKDIR /app
COPY requirements.txt .
RUN pip install --upgrade pip && pip install -r requirements.txt
COPY app ./app
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh
EXPOSE 8001
HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=30s \
    CMD curl -fsS http://localhost:8001/health || exit 1
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
