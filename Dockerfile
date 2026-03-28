FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /workspace

ENV MAVEN_OPTS="--sun-misc-unsafe-memory-access=allow"

COPY pom.xml ./
COPY telemetry telemetry
COPY infra infra
COPY commerce commerce

RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre AS runtime-base

WORKDIR /app

RUN mkdir -p /app \
    && if command -v useradd >/dev/null 2>&1; then useradd --system --create-home --home-dir /app pulsehome; fi

USER pulsehome

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow", "-jar", "/app/app.jar"]

FROM runtime-base AS collector
COPY --from=build --chown=pulsehome:pulsehome /workspace/telemetry/collector/target/collector-1.0.0-SNAPSHOT.jar /app/app.jar
EXPOSE 8080

FROM runtime-base AS aggregator
COPY --from=build --chown=pulsehome:pulsehome /workspace/telemetry/aggregator/target/aggregator-1.0.0-SNAPSHOT.jar /app/app.jar

FROM runtime-base AS analyzer
COPY --from=build --chown=pulsehome:pulsehome /workspace/telemetry/analyzer/target/analyzer-1.0.0-SNAPSHOT.jar /app/app.jar

FROM runtime-base AS hub-router-stub
COPY --from=build --chown=pulsehome:pulsehome /workspace/infra/hub-router-stub/target/hub-router-stub-1.0.0-SNAPSHOT.jar /app/app.jar
EXPOSE 59090
