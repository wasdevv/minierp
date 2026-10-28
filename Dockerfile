# Build: the same Maven Wrapper as local development. Tests need a database, so they run outside the image
# build (./mvnw verify, or CI).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

# Run: JRE only, as an unprivileged user. Temurin images ship fontconfig, which JasperReports needs for
# font metrics even though the PDF fonts come from the jar.
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --home-dir /app minierp
WORKDIR /app
COPY --from=build /app/target/mini-erp-*.jar /app/app.jar
USER minierp
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
