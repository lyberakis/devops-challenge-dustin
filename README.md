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

### Metrics collection

The ServiceMonitor scrapes `/metrics` through the Service's named `http` port every 30 seconds. It selects the application's Service in the same namespace.

Requires Prometheus Operator and its ServiceMonitor CRD before enabling this resource.

### Test

Run `./gradlew test` (PowerShell: `./gradlew.bat test`). Tests check the application responses and verify that `/metrics` exposes JVM metrics and records an HTTP request using Ktor's in-process test server.

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
- `GET http://localhost:8080/metrics` exposes HTTP request counts/durations and JVM metrics in Prometheus text format through Micrometer. Request metrics appear after requests have been handled. Check with `curl http://localhost:8080/metrics`.

Application responses use JSON; `/metrics` uses Prometheus plain text. Unknown paths return 404 and unsupported methods return 405. Startup and request logs go to standard error.

INFO logs show startup configuration and HTTP requests. DEBUG logs also show logging configuration and endpoint response details. To enable them, set `$env:LOG_LEVEL = "DEBUG"` in PowerShell or `export LOG_LEVEL=DEBUG` before starting the app.

### GitHub Actions

The workflow in `.github/workflows/build.yaml` runs on pushes to any branch only when application source/tests, Gradle configuration/wrapper, Dockerfile, or `.dockerignore` change. It builds the Docker image with the commit SHA as its tag, runs unit tests through the Dockerfile, and passes the built image as an artifact to the `test` and `security-scan` jobs.

The separate `.github/workflows/helm.yaml` workflow runs only when files under `charts/` change. It lints and renders the test and production Helm configurations independently of the application pipeline.

The `security-scan` job runs alongside `test` after the image build. Trivy scans the same image artifact for OS and application dependency vulnerabilities and fails the workflow if any HIGH or CRITICAL vulnerabilities are found, including those without fixes. Results appear in the job log.

On the default branch only, `publish` waits for `test` and `security-scan` to succeed, loads the same image artifact, and pushes it to `ghcr.io/lyberakis/devops-challenge-dustin` with the commit SHA as its tag. It uses the built-in `GITHUB_TOKEN` with `packages: write`; no additional registry secret is needed in GitHub Actions.

After publishing succeeds, `deploy` commits the image SHA to `image.tag` in `charts/hello-world/values-test.yaml`. Feature branches run the applicable checks but do not publish images or change deployment configuration.

# Sealed Secrets

## 1. Install the controller

```bash
helm repo add sealed-secrets https://bitnami-labs.github.io/sealed-secrets
helm repo update
helm upgrade --install sealed-secrets sealed-secrets/sealed-secrets --version 2.22.0 --namespace kube-system --set fullnameOverride=sealed-secrets-controller --wait --timeout 5m
```

## 2. Seal a secret

Set the environment to `test` or `production`, then fetch the cluster's public certificate:

```bash
APP_ENV=test
mkdir -p .secrets
kubeseal --controller-name=sealed-secrets-controller --controller-namespace=kube-system --fetch-cert > .secrets/sealed-secrets.pem
```

Enter the API key at the hidden prompt and encrypt it:

```bash
read -rsp 'API key: ' APP_API_KEY
printf '%s' "$APP_API_KEY" | kubeseal --raw --scope strict --cert .secrets/sealed-secrets.pem --namespace "hello-$APP_ENV" --name hello-world-secrets > ".secrets/api-key-$APP_ENV.encrypted"
unset APP_API_KEY
```

After successful encryption, copy the ciphertext into `sealedSecret.encryptedData.API_KEY` in `charts/hello-world/values-test-secrets.yaml` or `values-production-secrets.yaml`, and set `enabled: true`. Seal separately for each environment. The namespace (`hello-test` or `hello-production`), Secret name (`hello-world-secrets`), and cluster must match the deployment.

For a more user-friendly usage, a UI is also available https://github.com/bakito/sealed-secrets-web to avoid regularly sealing of secrets in a terminal.
