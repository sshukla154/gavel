# Operations guide

Everything you need to go from a clean machine to a fully running Gavel stack.

Three operating modes are covered in order of complexity:

| Mode | When to use |
|---|---|
| [Mode 1 — Local JVM](#mode-1--local-jvm) | Day-to-day development; fastest iteration cycle |
| [Mode 2 — Docker Compose](#mode-2--docker-compose-full-stack) | Run the full observability stack alongside the service |
| [Mode 3 — Kubernetes](#mode-3--kubernetes-kind--helm--argocd) | Validate Helm chart, ArgoCD GitOps, and production-like config |

---

## Part 1 — Tool installation

### 1.1 Required tools

| Tool | Min version | Purpose |
|---|---|---|
| JDK 21 | 21.0+ | Compile and run Java source |
| Maven | 3.9+ | Build, test, package |
| Docker Desktop | 4.x | Containers, Compose, kind node images |
| Git | 2.40+ | Version control |
| kind | 0.24+ | Local Kubernetes cluster (Mode 3) |
| kubectl | 1.31+ | Kubernetes CLI (Mode 3) |
| Helm | 3.16+ | Chart install / upgrade (Mode 3) |
| ArgoCD CLI | 2.13+ | GitOps sync and app management (Mode 3) |
| curl | any | Smoke-test HTTP endpoints |

### 1.2 Installation — Windows (Scoop)

[Scoop](https://scoop.sh) is recommended on Windows. Install it first if you don't have it:

```powershell
Set-ExecutionPolicy RemoteSigned -Scope CurrentUser
irm get.scoop.sh | iex
```

Then install all tools in one shot:

```powershell
# Core build tools
scoop install git maven

# JDK 21 (Zulu is recommended; Temurin and Corretto also work)
scoop bucket add java
scoop install zulu21-jdk

# Docker Desktop — download from https://www.docker.com/products/docker-desktop
# Install manually; Scoop's Docker package does not include Desktop.

# Kubernetes tools
scoop install kind kubectl helm

# ArgoCD CLI
scoop bucket add extras
scoop install argocd

# curl (usually pre-installed on Windows 10+)
curl --version
```

### 1.3 Installation — macOS (Homebrew)

```bash
brew install git maven openjdk@21 kind kubectl helm argocd curl

# Link OpenJDK 21 so the system picks it up
sudo ln -sfn $(brew --prefix)/opt/openjdk@21/libexec/openjdk.jdk \
  /Library/Java/JavaVirtualMachines/openjdk-21.jdk
```

Docker Desktop for Mac: download from [docker.com](https://www.docker.com/products/docker-desktop).

---

## Part 2 — Configuration

### 2.1 JAVA_HOME (Windows)

This project requires Java 21. If your machine has multiple JDKs (common with Scoop), the
system default may be an older version. Verify first:

```powershell
java -version     # should print openjdk 21...
mvn -version      # should print Java version: 21
```

If either prints the wrong version, override for the current PowerShell session:

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\scoop\apps\zulu21-jdk\current"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version     # now prints 21
```

To make the override permanent, add the two lines above to your PowerShell profile
(`$PROFILE`) or set `JAVA_HOME` in System → Advanced → Environment Variables.

### 2.2 JAVA_HOME (macOS)

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH=$JAVA_HOME/bin:$PATH
java -version     # openjdk 21
```

Add the two `export` lines to `~/.zshrc` (or `~/.bashrc`) to make them permanent.

### 2.3 Docker Desktop settings

Kind needs enough memory to run a Kubernetes control plane. Open Docker Desktop →
Settings → Resources and set:

- **CPUs:** 4 minimum
- **Memory:** 6 GB minimum (8 GB recommended)

Apply and restart Docker Desktop before creating a kind cluster.

### 2.4 Verify all tools

Run this block once after installation. Every command should print a version, not an error:

```bash
java -version        # openjdk 21...
mvn -version         # Apache Maven 3.9.x ... Java version: 21
docker version       # Client/Server version lines
docker compose version  # Docker Compose version v2.x
kind version         # kind v0.24.x
kubectl version --client  # Client Version: v1.31.x
helm version         # version.BuildInfo{Version:"v3.16.x"...}
argocd version       # argocd: v2.13.x
curl --version       # curl 8.x
```

---

## Part 3 — Clone and build

```bash
git clone https://github.com/sshukla154/gavel.git
cd gavel
mvn clean verify
```

`mvn clean verify` runs the full build: compilation → unit tests → integration tests
(Testcontainers spins up a temporary Postgres container). Docker Desktop must be running.
Expect ~3 minutes on a cold Maven cache, ~1 minute warm.

Expected output ends with:

```
[INFO] BUILD SUCCESS
```

---

## Mode 1 — Local JVM

Run auction-service directly on the JVM against Docker Compose infrastructure.
Best for active development — no image build cycle.

### Start

**Step 1 — Start the infrastructure stack**

```bash
docker compose up -d
```

Wait until all containers are healthy:

```bash
docker compose ps
```

All containers should show `healthy` or `running`. The postgres health check takes ~10s.

**Step 2 — Start auction-service**

```bash
mvn -pl services/auction-service -am spring-boot:run
```

The `-am` flag builds `gavel-common` first. Flyway runs migrations on startup, then Tomcat
binds on port 8081. Look for:

```
Started AuctionApplication in X.XXX seconds
```

### Verify

Keycloak is now part of the stack (added Phase 1.1): `/api/v1/ping` and every other `/api/**` route require a valid JWT with the `BIDDER` role, and `/actuator/**` other than `/actuator/health/**` requires an authenticated (any-role) JWT. Plain unauthenticated curl no longer succeeds against those endpoints.

```bash
# Ping endpoint without a token now returns 401 — this is expected
curl -i http://localhost:8081/api/v1/ping

# Overall health — public, expect {"status":"UP"}
curl http://localhost:8081/actuator/health

# Liveness probe — public, expect {"status":"UP"}
curl http://localhost:8081/actuator/health/liveness

# Readiness probe — public, expect {"status":"UP"}
curl http://localhost:8081/actuator/health/readiness

# Prometheus metrics — requires an authenticated JWT too (401 without one);
# see infra/prometheus/prometheus.yaml, which currently scrapes this endpoint
# unauthenticated and will itself get 401 (tracked as a known gap, not a doc issue)
curl http://localhost:8081/actuator/prometheus | head -20
```

To exercise `/api/v1/ping` with a real token, get one from Keycloak's `bidder` user (realm `gavel`, client `gavel-spa`) or simply drive it through the Angular UI at `localhost:4200`, which attaches the JWT automatically. See DEVELOPMENT.md.

Verify the database is being written to:

```bash
docker compose exec postgres psql -U postgres -d hello_db \
  -c "SELECT count(*) FROM visits;"
```

Call `/api/v1/ping` several times. The count should increment on every call.

### Stop

```bash
# Stop auction-service: Ctrl+C in the terminal running spring-boot:run

# Stop the infrastructure stack (data is preserved)
docker compose down

# Full reset — wipe all data volumes
docker compose down -v
```

---

## Mode 2 — Docker Compose (full stack)

Run the containerised auction-service image alongside the infra stack. Use this to test
Docker builds and production-like environment variables before pushing to CI.

### Start

**Step 1 — Build the image locally**

```bash
# Must be run from the repo root (where the Dockerfile lives)
docker build -t ghcr.io/sshukla154/gavel/auction-service:local .
```

**Step 2 — Start the infra stack**

```bash
docker compose up -d
```

**Step 3 — Run the service container**

```bash
docker run --rm \
  --name auction-service \
  --network gavel_gavel-net \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://gavel-postgres:5432/hello_db \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=postgres \
  -e KEYCLOAK_ISSUER_URI=http://gavel-keycloak:8080/realms/gavel \
  -e KAFKA_BOOTSTRAP_SERVERS=gavel-kafka:9092 \
  -e OTEL_EXPORTER_OTLP_ENDPOINT=http://gavel-otel-collector:4318 \
  -e SPRING_PROFILES_ACTIVE=local \
  -p 8081:8081 \
  ghcr.io/sshukla154/gavel/auction-service:local
```

The service joins the `gavel_gavel-net` Docker network so it can reach `gavel-postgres`,
`gavel-keycloak`, `gavel-kafka`, and `gavel-otel-collector` by container name. `KEYCLOAK_ISSUER_URI`
and `KAFKA_BOOTSTRAP_SERVERS` default to `localhost` addresses inside `application.yaml` — inside
a container on `gavel_gavel-net` those defaults don't resolve, so both must be overridden as shown.

### Verify

Same curl commands as Mode 1 apply. Additionally, verify the container is healthy:

```bash
docker inspect auction-service --format '{{.State.Health.Status}}'
# expect: healthy  (after the 45s start-period)
```

Check that the service shows up in Prometheus:

1. Open [http://localhost:9090/targets](http://localhost:9090/targets)
2. The `auction-service` job should show **UP**

Verify traces appear in Grafana:

1. Open [http://localhost:3000](http://localhost:3000) → Explore → Datasource: **Tempo**
2. Search tab → Service Name: `auction-service` → Run query
3. A trace should appear for each `/api/v1/ping` call

### Stop

```bash
# Stop the service container (Ctrl+C, or in another terminal:)
docker stop auction-service

# Stop the infra stack
docker compose down

# Full reset
docker compose down -v
```

---

## Mode 3 — Kubernetes (kind + Helm + ArgoCD)

Deploy through the full GitOps pipeline locally. ArgoCD watches the git repository and
applies the Helm chart automatically. Use this to validate Kubernetes config, liveness probes,
and the CI GitOps loop end-to-end.

### 3.1 One-time cluster setup

These steps only need to be done once per machine (or after `kind delete cluster`).

**Step 1 — Add the bitnami Helm repo**

```bash
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update
```

**Step 2 — Create the kind cluster**

```bash
kind create cluster --config k8s/kind/cluster-config.yaml
```

Verify the cluster is up:

```bash
kubectl cluster-info --context kind-gavel
kubectl get nodes
# NAME                  STATUS   ROLES           AGE   VERSION
# gavel-control-plane   Ready    control-plane   Xs    v1.31.x
```

**Step 3 — Install ArgoCD**

```bash
kubectl create namespace argocd
kubectl apply -n argocd \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
```

Wait for all ArgoCD pods to be running:

```bash
kubectl wait --for=condition=ready pod --all -n argocd --timeout=180s
```

**Step 4 — Log in to ArgoCD**

```bash
# Retrieve the generated admin password
argocd admin initial-password -n argocd

# Port-forward the ArgoCD API server
kubectl port-forward svc/argocd-server -n argocd 8080:443 &

# Log in (accept the self-signed cert when prompted)
argocd login localhost:8080 --username admin --insecure
```

Open the ArgoCD UI at [https://localhost:8080](https://localhost:8080) (self-signed cert — proceed anyway).

### 3.2 Load the image into kind

kind nodes are isolated from the host Docker daemon. Load the image so ArgoCD can deploy
it without pulling from GHCR:

```bash
# Option A — use the already-built local image from Mode 2
docker tag ghcr.io/sshukla154/gavel/auction-service:local \
           ghcr.io/sshukla154/gavel/auction-service:master
kind load docker-image ghcr.io/sshukla154/gavel/auction-service:master --name gavel

# Option B — pull the published branch image
docker pull ghcr.io/sshukla154/gavel/auction-service:master
kind load docker-image ghcr.io/sshukla154/gavel/auction-service:master --name gavel
```

### 3.3 Deploy via ArgoCD

```bash
# Deploy PostgreSQL first (auction-service depends on it)
kubectl apply -f k8s/argocd/postgres-app.yaml

# Wait for postgres to be ready (takes ~60s on first start)
kubectl wait --for=condition=ready pod \
  -l app.kubernetes.io/name=postgresql -n gavel --timeout=120s

# Deploy auction-service
kubectl apply -f k8s/argocd/auction-service-app.yaml
```

Watch ArgoCD sync:

```bash
argocd app list
# NAME             CLUSTER     NAMESPACE  STATUS   HEALTH
# postgres         in-cluster  gavel      Synced   Healthy
# auction-service  in-cluster  gavel      Synced   Healthy
```

### 3.4 Verify

**Wait for the pod to be ready:**

```bash
kubectl wait --for=condition=ready pod \
  -l app.kubernetes.io/name=auction-service -n gavel --timeout=120s
```

**Check pod and deployment status:**

```bash
kubectl get pods -n gavel
# NAME                               READY   STATUS    RESTARTS
# auction-service-xxxxxxxxx-xxxxx    1/1     Running   0
# postgres-postgresql-0              1/1     Running   0

kubectl get deployment auction-service -n gavel
# NAME              READY   UP-TO-DATE   AVAILABLE
# auction-service   1/1     1            1
```

**Inspect pod events and logs (if a pod fails to start):**

```bash
kubectl describe pod -l app.kubernetes.io/name=auction-service -n gavel
kubectl logs -l app.kubernetes.io/name=auction-service -n gavel --tail=50
```

**Smoke-test the endpoint:**

```bash
kubectl port-forward svc/auction-service -n gavel 8081:8081 &

curl -i http://localhost:8081/api/v1/ping
# 401 Unauthorized without a Keycloak JWT — expected since Phase 1.1 (see Mode 1's Verify section above).
# Keycloak is not deployed into the kind cluster (Phase 2 debt), so there is currently no
# in-cluster way to obtain a token for this endpoint; the liveness/readiness probes below
# remain the practical smoke test for Mode 3.

curl http://localhost:8081/actuator/health/liveness
# {"status":"UP"}

curl http://localhost:8081/actuator/health/readiness
# {"status":"UP"}
```

**Verify the Kubernetes Secret was created:**

```bash
kubectl get secret auction-service-secret -n gavel
kubectl describe secret auction-service-secret -n gavel
```

**Verify liveness and readiness probes are passing (events log):**

```bash
kubectl get events -n gavel --sort-by='.lastTimestamp' | tail -10
# Should show no Liveness/Readiness probe failures
```

### 3.5 Force ArgoCD sync (optional)

ArgoCD polls git every 3 minutes. To trigger an immediate sync:

```bash
argocd app sync auction-service
argocd app sync postgres
```

### 3.6 Stop and tear down

**Stop port-forwards:**

```bash
# Kill background port-forwards started with &
kill %1 %2   # adjust job numbers as needed
# Or find them:
jobs
```

**Remove the ArgoCD Applications (and the resources they manage):**

```bash
kubectl delete -f k8s/argocd/auction-service-app.yaml
kubectl delete -f k8s/argocd/postgres-app.yaml
```

**Delete the entire cluster:**

```bash
kind delete cluster --name gavel
```

**Remove the bitnami repo (optional cleanup):**

```bash
helm repo remove bitnami
```

---

## Part 4 — Observability verification

This applies when the Docker Compose infra stack is running (Modes 1 and 2).

### Grafana dashboards

1. Open [http://localhost:3000](http://localhost:3000) — credentials: `admin` / `admin`
2. Go to **Dashboards → Gavel → auction-service**
3. The dashboard should show:
   - Request rate (req/s)
   - P99 latency (ms)
   - JVM heap usage (MB)
   - Total visit count (counter)

If the dashboard is blank, the service is not running or has not received any traffic yet.
Hit `/api/v1/ping` a few times, then refresh.

### Prometheus targets

Open [http://localhost:9090/targets](http://localhost:9090/targets).

| Job | Expected state | Notes |
|---|---|---|
| `auction-service` | UP | Only UP when the service is running on port 8081 |
| `otel-collector` | UP | Always UP when the Compose stack is running |

### Distributed traces (Tempo)

1. Grafana → Explore → Datasource: **Tempo**
2. Query type: **Search** → Service Name: `auction-service`
3. Each `/api/v1/ping` call should produce one trace with a single `PingController.ping` span

### Logs (Loki)

1. Grafana → Explore → Datasource: **Loki**
2. Query: `{job="auction-service"}`
3. Log lines appear in plain-text format (local profile) or ECS JSON (prod profile)

---

## Part 5 — Port reference

| Service | Mode | Host port | Notes |
|---|---|---|---|
| auction-service | 1, 2 | 8081 | HTTP, actuator, metrics |
| PostgreSQL | 1, 2 | 5432 | `hello_db` (auction-service) + `bids_db` (bid-service), user: postgres |
| Kafka | 1, 2 | 9092 | Bid command/event bus (not deployed to Mode 3 — Phase 2 debt) |
| Keycloak | 1, 2 | 8180 | OAuth2/OIDC, realm `gavel` (not deployed to Mode 3 — Phase 2 debt) |
| bid-service | 1, 2 | 8082 | Bid ledger API (not deployed to Mode 3 — Phase 2 debt) |
| UI (nginx) | 1, 2 | 4200 | Angular SPA (not deployed to Mode 3 — Phase 2 debt) |
| OTel Collector | 1, 2 | 4317 | OTLP gRPC |
| OTel Collector | 1, 2 | 4318 | OTLP HTTP |
| OTel Collector | 1, 2 | 8889 | Prometheus scrape endpoint |
| Prometheus | 1, 2 | 9090 | Web UI + query API |
| Grafana | 1, 2 | 3000 | Dashboards |
| Tempo | 1, 2 | 3200 | Trace HTTP API |
| Loki | 1, 2 | 3100 | Log HTTP API |
| auction-service (k8s) | 3 | 8081 | Via `kubectl port-forward` |
| ArgoCD server | 3 | 8080 | Via `kubectl port-forward` |

---

## Part 6 — Quick reference cheat sheet

### Mode 1 — Local JVM

```bash
docker compose up -d                                              # start infra (postgres, kafka, keycloak, bid-service, ui, observability)
mvn -pl services/auction-service -am spring-boot:run             # start service
curl http://localhost:8081/actuator/health                       # verify (unauthenticated); /api/v1/ping now needs a Keycloak JWT
docker compose down                                              # stop infra
```

### Mode 2 — Docker container

```bash
docker compose up -d                                             # start infra
docker build -t ghcr.io/sshukla154/gavel/auction-service:local .  # build image
docker run --rm --name auction-service \
  --network gavel_gavel-net \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://gavel-postgres:5432/hello_db \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=postgres \
  -e KEYCLOAK_ISSUER_URI=http://gavel-keycloak:8080/realms/gavel \
  -e KAFKA_BOOTSTRAP_SERVERS=gavel-kafka:9092 \
  -p 8081:8081 \
  ghcr.io/sshukla154/gavel/auction-service:local                 # start service
curl http://localhost:8081/actuator/health                       # verify (unauthenticated)
docker stop auction-service && docker compose down               # stop all
```

### Mode 3 — Kubernetes

```bash
kind create cluster --config k8s/kind/cluster-config.yaml                          # create cluster
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml  # install argocd
kubectl wait --for=condition=ready pod --all -n argocd --timeout=180s              # wait for argocd
kind load docker-image ghcr.io/sshukla154/gavel/auction-service:master --name gavel  # load image
kubectl apply -f k8s/argocd/postgres-app.yaml                                      # deploy postgres
kubectl apply -f k8s/argocd/auction-service-app.yaml                               # deploy service
kubectl port-forward svc/auction-service -n gavel 8081:8081 &                      # expose
curl http://localhost:8081/actuator/health/liveness                                # verify (no Keycloak in-cluster yet)
kind delete cluster --name gavel                                                   # destroy
```

### Useful diagnostics

```bash
# Live application logs
docker compose logs -f auction-service    # Mode 2 container
kubectl logs -f -l app.kubernetes.io/name=auction-service -n gavel  # Mode 3

# Database row count
docker compose exec postgres psql -U postgres -d hello_db -c "SELECT count(*) FROM visits;"

# Restart a single Compose service
docker compose restart otel-collector

# Re-sync ArgoCD app immediately
argocd app sync auction-service

# Show all Kubernetes events sorted by time
kubectl get events -n gavel --sort-by='.lastTimestamp'
```
