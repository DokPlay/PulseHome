FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /workspace

ENV MAVEN_OPTS="--sun-misc-unsafe-memory-access=allow"

COPY pom.xml ./
COPY telemetry/pom.xml telemetry/pom.xml
COPY telemetry/collector/pom.xml telemetry/collector/pom.xml
COPY telemetry/aggregator/pom.xml telemetry/aggregator/pom.xml
COPY telemetry/analyzer/pom.xml telemetry/analyzer/pom.xml
COPY telemetry/serialization/pom.xml telemetry/serialization/pom.xml
COPY telemetry/serialization/avro-schemas/pom.xml telemetry/serialization/avro-schemas/pom.xml
COPY telemetry/serialization/proto-schemas/pom.xml telemetry/serialization/proto-schemas/pom.xml
COPY infra/pom.xml infra/pom.xml
COPY infra/hub-router-stub/pom.xml infra/hub-router-stub/pom.xml

RUN mvn -B -DskipTests dependency:go-offline

COPY telemetry telemetry
COPY infra infra

RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre AS runtime-base

WORKDIR /app

USER root

RUN apt-get update \
    && apt-get install --no-install-recommends -y curl netcat-openbsd \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /app \
    && if command -v useradd >/dev/null 2>&1; then useradd --system --create-home --home-dir /app pulsehome; fi

USER pulsehome

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow", "-jar", "/app/app.jar"]

FROM runtime-base AS collector
COPY --from=build --chown=pulsehome:pulsehome /workspace/telemetry/collector/target/collector-*.jar /app/app.jar
EXPOSE 8080

FROM runtime-base AS aggregator
COPY --from=build --chown=pulsehome:pulsehome /workspace/telemetry/aggregator/target/aggregator-*.jar /app/app.jar

FROM runtime-base AS analyzer
COPY --from=build --chown=pulsehome:pulsehome /workspace/telemetry/analyzer/target/analyzer-*.jar /app/app.jar

FROM runtime-base AS hub-router-stub
COPY --from=build --chown=pulsehome:pulsehome /workspace/infra/hub-router-stub/target/hub-router-stub-*.jar /app/app.jar
EXPOSE 59090
