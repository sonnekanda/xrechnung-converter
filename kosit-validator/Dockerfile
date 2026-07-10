# syntax=docker/dockerfile:1
# KoSIT-Validator als eigenständiger HTTP-Daemon (zweite unabhängige Validierung).
# Enthält die offizielle XRechnung-Prüfkonfiguration; isolierter Classpath.
FROM eclipse-temurin:21-jre-jammy AS runtime

# Gepinnte Versionen (reproduzierbar).
ARG VALIDATOR_URL="https://github.com/itplr-kosit/validator/releases/download/v1.6.2/validator-1.6.2-standalone.jar"
ARG CONFIG_URL="https://github.com/itplr-kosit/validator-configuration-xrechnung/releases/download/v2026-01-31/xrechnung-3.0.2-validator-configuration-2026-01-31.zip"

RUN set -eux; \
    for i in 1 2 3; do apt-get update && break || sleep 5; done; \
    apt-get upgrade -y; \
    apt-get install -y --no-install-recommends curl gosu unzip; \
    rm -rf /var/lib/apt/lists/*; \
    groupadd -r app && useradd -r -g app -u 10001 app; \
    mkdir -p /opt/kosit; \
    curl -fsSL -o /opt/kosit/validator.jar "$VALIDATOR_URL"; \
    curl -fsSL -o /tmp/config.zip "$CONFIG_URL"; \
    unzip -q /tmp/config.zip -d /opt/kosit; \
    rm /tmp/config.zip; \
    chown -R app:app /opt/kosit

COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

EXPOSE 8092
# Der Daemon kompiliert beim Start die Schematron-Regeln -> großzügige start_period.
HEALTHCHECK --interval=15s --timeout=5s --retries=10 --start-period=180s \
    CMD curl -fsS http://localhost:8092/server/health || exit 1
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["java", "-jar", "/opt/kosit/validator.jar", \
     "-s", "/opt/kosit/scenarios.xml", "-r", "/opt/kosit", \
     "-D", "-H", "0.0.0.0", "-P", "8092"]
