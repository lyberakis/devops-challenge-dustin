FROM eclipse-temurin:25-jdk-alpine-3.24 AS build
WORKDIR /build

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle/wrapper/ gradle/wrapper/
RUN chmod +x gradlew
COPY src/ src/
RUN ./gradlew --no-daemon test installDist

FROM eclipse-temurin:25-jre-alpine-3.24 AS runtime
RUN addgroup -S -g 10001 app && adduser -S -D -H -u 10001 -G app app
WORKDIR /app

COPY --from=build /build/build/install/devops-challenge-dustin/lib/ ./lib/

USER 10001:10001
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 CMD wget -qO- "http://127.0.0.1:${PORT:-8080}/health" || exit 1

ENTRYPOINT ["java", "-cp", "/app/lib/*", "MainKt"]
