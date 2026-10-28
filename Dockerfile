# syntax=docker/dockerfile:1

# ---- build: the same Maven Wrapper as local development --------------------------------------------------
# Tests need PostgreSQL, so they run in CI (`./mvnw verify`), not in the image build.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
# Dependencies in their own layer: a code-only change does not download the world again.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/mini-erp-*.jar extract --layers --launcher --destination target/extracted

# ---- runtime: JRE only, unprivileged, layered ----------------------------------------------------------
# Temurin images ship fontconfig, which JasperReports needs for font metrics (the PDF fonts come from the jar).
FROM eclipse-temurin:21-jre
ARG VERSION=dev
ARG REVISION=unknown
LABEL org.opencontainers.image.title="mini-erp" \
      org.opencontainers.image.description="Mini ERP: vendas, estoque e relatórios" \
      org.opencontainers.image.source="https://github.com/wasdevv/minierp" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}" \
      org.opencontainers.image.licenses="MIT"
RUN useradd --system --uid 10001 --home-dir /app --shell /usr/sbin/nologin minierp
WORKDIR /app
# Spring Boot layers, least to most often changed, so a release usually ships only the "application" layer.
COPY --from=build /app/target/extracted/dependencies/ ./
COPY --from=build /app/target/extracted/spring-boot-loader/ ./
COPY --from=build /app/target/extracted/snapshot-dependencies/ ./
COPY --from=build /app/target/extracted/application/ ./
USER 10001
EXPOSE 8080
# Container-aware heap, fail fast on OOM (the orchestrator restarts), and no stray writes outside /tmp.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp"
# No curl in the JRE image: bash's /dev/tcp asks the actuator directly.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=4 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /actuator/health/readiness HTTP/1.0\r\n\r\n" >&3 && grep -q "UP" <&3'
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
