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

### Test

Run `./gradlew test` (PowerShell: `./gradlew.bat test`). Tests check the application responses and verify that `/metrics` exposes JVM metrics and records an HTTP request using Ktor's in-process test server.

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

| Values file              | Replicas | Logging | CPU request / limit | Memory request / limit | Disruption budget    |
|--------------------------|----------|---------|---------------------|------------------------|----------------------|
| `values-test.yaml`       | 1        | DEBUG   | 100m / 200m         | 256Mi / 512Mi          | Disabled             |
| `values-production.yaml` | 3        | INFO    | 500m / 1000m        | 512Mi / 1Gi            | At least 1 available |

Validate and preview of helm charts:

```bash
helm lint charts/hello-world -f charts/hello-world/values-{environment}.yaml
helm template demo charts/hello-world -f charts/hello-world/values-{environment}.yaml
```

### Endpoints

- `GET http://localhost:8080/health` returns `{"status":"healthy"}`.
- `GET http://localhost:8080/` returns `{"message":"Hello, World!","environment":"development"}` with the configured environment name.
- `GET http://localhost:8080/metrics` exposes HTTP request counts/durations and JVM metrics in Prometheus text format through Micrometer. Request metrics appear after requests have been handled. Check with `curl http://localhost:8080/metrics`.

Application responses use JSON; `/metrics` uses Prometheus plain text. Unknown paths return 404 and unsupported methods return 405. Startup and request logs go to standard error.

INFO logs show startup configuration and HTTP requests. DEBUG logs also show logging configuration and endpoint response details.

### GitHub Actions

The workflow in `.github/workflows/build.yaml` runs on pushes to any branch only when application source/tests, Gradle configuration/wrapper, Dockerfile, or `.dockerignore` change. It builds the Docker image with the commit SHA as its tag, runs unit tests through the Dockerfile, and passes the built image as an artifact to the `test` and `security-scan` jobs.

The separate `.github/workflows/helm.yaml` workflow runs only when files under `charts/` or `infrastructure/` change. It lints and renders the test and production Helm configurations independently of the application pipeline.

The `security-scan` job runs alongside `test` after the image build. Trivy scans the same image artifact for OS and application dependency vulnerabilities and fails the workflow if any HIGH or CRITICAL vulnerabilities are found, including those without fixes. Results appear in the job log.

On the default branch only, `publish` waits for `test` and `security-scan` to succeed, loads the same image artifact, and pushes it to `ghcr.io/lyberakis/devops-challenge-dustin` with the commit SHA as its tag. It uses the built-in `GITHUB_TOKEN` with `packages: write`. For this exercise, GHCR is used with public access for a more straightforward approach. In a production-grade setup, artifacts would be stored in a secure registry and only authorized accounts would have access.

After publishing succeeds, `deploy` commits the image SHA to `image.tag` in `charts/hello-world/values-test.yaml`. Feature branches run the applicable checks but do not publish images or change deployment configuration.

Production promotion is manual: select **Actions → Deploy production → Run workflow** on the default branch. The workflow checks the latest successful default-branch Build run and its build, test, scan, publish, and test-deployment jobs. It requires the test values' image tag to match that run, then copies the image configuration to production.

If production already uses the image, no commit is created. If test does not match the latest successful run or the branch changes during the check, promotion fails and can be retried.

```text
App change → build → test + security scan → publish to GHCR → Deploy to test → Deploy to production (manual trigger)
Chart change → Helm validation; Argo CD applies the chart from Git
```

### Infrastructure

The application runs on an Amazon EKS Auto Mode cluster in Frankfurt (`eu-central-1`), with separate `hello-test` and `hello-production` namespaces. Auto Mode manages worker-node provisioning and load balancing. The shared `alb` IngressClass configures internet-facing Application Load Balancers that route traffic to the application pods.

Argo CD runs in the same cluster and deploys the Helm chart from Git. A parent Application manages the test and production Applications, which automatically create their namespaces and synchronize configuration changes. GitHub Actions publishes images to GHCR and updates the image tags in Git; Argo CD applies those changes to EKS. Cluster setup and bootstrap instructions are documented in [DEMO.md](DEMO.md).

Sealed Secret is also installed under the same cluster under its own namespace.

### Secrets Management

This project uses Sealed Secrets to store encrypted secret values in Git. The controller decrypts them into Kubernetes Secrets inside the cluster. For production applications on AWS, AWS Secrets Manager would be a more suitable choice for centralized secret management. 

Applications can access AWS Secrets Manager through IAM roles associated with their Kubernetes ServiceAccounts, with permissions restricted to the secrets each application needs. Secret values are stored and versioned in Secrets Manager rather than Git.

### Observability

The application writes startup and request logs to stderr and exposes HTTP and JVM metrics at `/metrics` through Micrometer. The optional ServiceMonitor scrapes every 30 seconds when enabled; it requires Prometheus configured to discover its labels and namespace.

#### Infrastructure monitoring

| Area                | Signals and purpose                                                                                                                                                                                                                              |
|---------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Cluster and nodes   | Monitor node readiness, CPU and memory usage, disk space/inodes, disk I/O latency, network throughput/errors, and memory/disk pressure.                                                                                                          |
| Pods and containers | Compare actual CPU and memory usage with requests and limits. Monitor CPU throttling, memory approaching limits, OOM kills, and ephemeral storage usage. Track restarts, CrashLoopBackOff, failed probes, Pending pods, and image-pull failures. |
| Deployments         | Compare desired, updated, ready, and available replicas. Detect stalled rollouts and progress-deadline failures. Monitor ArgoCD sync errors, degraded health, and prolonged OutOfSync state.                                                     |

Use the **RED** principle for application health: request **Rate**, **Errors** (especially the HTTP 5xx ratio), and **Duration** (latency, including p95/p99). 

Use the **USE** principle for resources: **Utilization** (CPU, memory, disk/network throughput), **Saturation** (CPU or I/O queues, throttling, and insufficient capacity leaving pods Pending), and **Errors** (disk/network errors and OOM kills).

#### Production tools

- **Metrics:** Prometheus and Grafana dashboards. Collect application metrics through the ServiceMonitor, object state through kube-state-metrics, and node/container usaged.
- **Logging:** Add structured JSON logs with environment, namespace, pod, and request/trace IDs; use INFO in production and DEBUG temporarily. Set retention and access controls, and exclude secrets. Use Elasticsearch as an open-source option to collect the logs and Kibana for visualization.
- **Tracing:** OpenTelemetry for collecting and processing telemetry data of the JVM application.
- **Alerting:** Prometheus rules and Alertmanager for notification routing, grouping, and deduplication.

#### Alerts to add

This is a list of alerts that a production-like application could use. These are conditions to tune against measured traffic and service objectives.

| Alert                    | Condition                                                                                                                                                    |
|--------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Application unavailable  | Check the health status URL of the application.                                                                                                              |
| JVM heap pressure        | Heap used above 90%, or long GC pauses                                                                                                                       |
| Errors                   | Repeated HTTP 5xx errors.                                                                                                                                    |
| Node health and capacity | Node NotReady, persistent MemoryPressure/DiskPressure, or disk/inode availability.                                                                           |
| Container instability    | CrashLoopBackOff for longer periods, multiple restarts in short timeframes, or OOM kills. Warn on memory above 80% of its limit or sustained CPU throttling. |
| Scheduling and rollouts  | Pods Pending or unable to pull images; available replicas below desired.                                                                                     |


### Potential expansions

For multiple people teams, the best CI/CD practice would be to protect the main branch from directly commiting there. This can be done with pull requests of branches to main with required reviewers and required builds prior to merging. 

Apply least-privilege Kubernetes RBAC access policies to users and controllers. Application ServiceAccounts should receive Kubernetes API permissions only when required.

Extend infrastructure as code to AWS resources using Terraform, covering EKS, networking, and IAM alongside the existing Helm and Argo CD manifests.
