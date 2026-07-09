# syntax=docker/dockerfile:1
# --- Build-Stufe ------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Erst nur die POM fuer besseres Dependency-Caching.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

# --- Laufzeit-Stufe ---------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime
RUN set -eux; \
    for i in 1 2 3; do apt-get update && break || sleep 5; done; \
    apt-get install -y --no-install-recommends curl gosu; \
    rm -rf /var/lib/apt/lists/*; \
    groupadd -r app && useradd -r -g app -u 10001 app; \
    mkdir -p /data && chown app:app /data
WORKDIR /app
COPY --from=build /build/target/invoice-engine.jar ./invoice-engine.jar
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh
EXPOSE 8090
HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=60s \
    CMD curl -fsS http://localhost:8090/actuator/health || exit 1
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["java", "-jar", "/app/invoice-engine.jar"]
