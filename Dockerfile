# registry-1.docker.io times out from this network. DaoCloud mirrors the official images.
ARG TEMURIN_REGISTRY=docker.m.daocloud.io/library

# Build the Spring Boot jar with the project's Maven wrapper (Java 17).
FROM ${TEMURIN_REGISTRY}/eclipse-temurin:17-jdk AS build
WORKDIR /src

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src
RUN chmod +x mvnw \
    && ./mvnw -B -DskipTests package \
    && find target -maxdepth 1 -type f -name 'bank-account-*.jar' -exec cp {} /src/application.jar \;

# Run the packaged jar on a JRE-only image.
FROM ${TEMURIN_REGISTRY}/eclipse-temurin:17-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app

COPY --from=build /src/application.jar /app/application.jar
RUN chown app:app /app/application.jar

USER app
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
