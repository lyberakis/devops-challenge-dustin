## Kotlin application

A minimal Kotlin application using Ktor. Requires JDK 25. Resolves dependencies with Gradle.

### Run in IntelliJ

1. Open `build.gradle.kts` as a Gradle project
2. Select JDK 25 as the project SDK and Gradle JVM.
3. Let Gradle finish syncing, then run `main` in `src/main/kotlin/Main.kt`.

### Run from a terminal

PowerShell: `./gradlew.bat run`

Git Bash/Linux/macOS: `./gradlew run`

Build a runnable distribution with `./gradlew installDist`. The launcher and libraries are written to `build/install/devops-challenge-dustin/`.

### Docker

To run the app locally, start Docker and execute:

```bash
docker build -t devops-challenge-dustin:local .
docker run --rm --name devops-challenge-dustin -p 8080:8080 -e ENVIRONMENT=test -e LOG_LEVEL=INFO devops-challenge-dustin:local
```

In another terminal, inspect the health check:

```bash
docker inspect --format '{{.State.Health.Status}}' devops-challenge-dustin
```

The build stage runs tests and creates the application distribution. The runtime uses an Alpine Java 25 JRE, runs as UID/GID 10001, and contains only application libraries and runtime tools. Application files are owned by root so the app user cannot modify them.

### Kubernetes with Helm

The application chart is in `charts/hello-world`. It provides a Deployment, a ClusterIP Service, an Ingress, a ConfigMap, and a ServiceAccount. Pods run as non-root with a read-only filesystem and a writable `/tmp`.

| Values file | Replicas | Logging | CPU request / limit | Memory request / limit | Disruption budget    |
| --- | --- | --- |---------------| --- |----------------------|
| `values-test.yaml` | 1 | DEBUG | 100m / 200m | 256Mi / 512Mi | Disabled             |
| `values-production.yaml` | 3 | INFO | 500m / 1000m | 512Mi / 1Gi | At least 1 available |

Validate and preview of helm charts:

```bash
helm lint charts/hello-world -f charts/hello-world/values-{environment}.yaml
helm template demo charts/hello-world -f charts/hello-world/values-{environment}.yaml
```

### Test

Run `./gradlew test` (PowerShell: `./gradlew.bat test`). The test checks both endpoints' status codes, JSON content type, and response bodies using Ktor's in-process test server.

### Configuration

Set environment variables before starting:

| Variable | Default | Purpose |
| --- | --- | --- |
| `ENVIRONMENT` | `development` | Environment name returned by `/` |
| `HOST` | `0.0.0.0` | Address to listen on |
| `PORT` | `8080` | HTTP port |
| `LOG_LEVEL` | `INFO` | Logging verbosity: `DEBUG`, `INFO`, `ERROR` |

PowerShell example: `$env:ENVIRONMENT = "test"`

Git Bash/Linux/macOS example: `export ENVIRONMENT=test`

### Endpoints

- `GET http://localhost:8080/health` returns `{"status":"healthy"}`.
- `GET http://localhost:8080/` returns `{"message":"Hello, World!","environment":"development"}` with the configured environment name.

Responses use JSON content type. Unknown paths return 404 and unsupported methods return 405. Startup and request logs go to standard error.

INFO logs show startup configuration and HTTP requests. DEBUG logs also show logging configuration and endpoint response details. To enable them, set `$env:LOG_LEVEL = "DEBUG"` in PowerShell or `export LOG_LEVEL=DEBUG` before starting the app.
