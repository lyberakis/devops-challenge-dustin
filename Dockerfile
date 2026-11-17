FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /build

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle/wrapper/ gradle/wrapper/
RUN chmod +x gradlew
COPY src/ src/
RUN ./gradlew --no-daemon test installDist

FROM eclipse-temurin:25-jre-alpine AS runtime
RUN apk add --no-cache curl && addgroup -S -g 10001 app && adduser -S -D -H -u 10001 -G app app
WORKDIR /app

COPY --from=build /build/build/install/devops-challenge-dustin/lib/ ./lib/

USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-cp", "/app/lib/*", "MainKt"]
