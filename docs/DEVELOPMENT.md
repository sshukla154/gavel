# Development guide

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 | Temurin recommended: https://adoptium.net |
| Maven | 3.9+ | Or use `./mvnw` wrapper if added later |
| Docker Desktop | latest stable | Required for Phase 0.2+ (Testcontainers, Compose) |
| kind | 0.24+ | Kubernetes in Docker — Phase 0.4 |
| kubectl | 1.31+ | Phase 0.4 |
| helm | 3.16+ | Phase 0.4 |
| argocd CLI | 2.13+ | Phase 0.4 |
| Node.js | 20 LTS | Angular frontend — Phase 1 |

## Clone and build

```bash
git clone https://github.com/sshukla154/gavel.git
cd gavel
mvn clean verify
```

`mvn clean verify` compiles all modules, runs unit and integration tests (including Testcontainers), and produces the executable jar. Docker Desktop must be running. Expect ~2 minutes on a warm Maven cache.

## Local infrastructure

The full observability stack runs via Docker Compose. Docker Desktop must be running first.

```powershell
# Start all services (Postgres, OTel Collector, Prometheus, Grafana, Tempo, Loki)
docker compose up -d

# Check all containers are healthy
docker compose ps
```

Port map:

| Service | Host port | Notes |
|---|---|---|
| PostgreSQL | 5432 | DB: `hello_db`, user: `postgres`, password: `postgres` |
| OTel Collector | 4317 (gRPC), 4318 (HTTP) | OTLP ingestion |
| Prometheus | 9090 | Targets: http://localhost:9090/targets |
| Grafana | 3000 | Dashboards: http://localhost:3000 (admin / admin) |
| Tempo | 3200 | Trace query API (used by Grafana) |
| Loki | 3100 | Log query API (used by Grafana) |

Connect to Postgres with any SQL client using `localhost:5432`, database `hello_db`, user `postgres`, password `postgres`.

```powershell
# Stop the stack (data volumes are preserved)
docker compose down

# Wipe all data and start clean
docker compose down -v
docker compose up -d
```

## Run hello-service locally

> **Windows note:** the system default `JAVA_HOME` may point at a non-21 JDK. Prefix Maven commands with the override below, or set `JAVA_HOME` permanently in your user environment variables.
> ```powershell
> $env:JAVA_HOME = "$env:USERPROFILE\scoop\apps\zulu21-jdk\current"
> $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
> ```

With the Compose stack running:

```bash
# Start the service — Flyway runs on startup, then Tomcat on port 8081
mvn -pl services/hello-service -am spring-boot:run

# In a second terminal
curl http://localhost:8081/api/v1/ping
curl http://localhost:8081/actuator/health
```

The `-am` flag builds `gavel-common` first so hello-service has its dependency.

Expected ping response:

```json
{"data":{"status":"ok","service":"hello-service","totalVisits":1},"timestamp":"..."}
```

`totalVisits` increments on every call.

## IDE setup (IntelliJ IDEA)

1. **Open**: File → Open → select the root `pom.xml`, open as project.
2. **SDK**: File → Project Structure → SDKs → add JDK 21 if not present. Set Project SDK and language level to 21.
3. **Maven**: IntelliJ auto-imports. If not, right-click `pom.xml` → Maven → Reimport.
4. **Run config**: Right-click `HelloApplication.java` → Run. The embedded Tomcat starts on 8081.
5. **EditorConfig**: IntelliJ respects `.editorconfig` natively — no plugin needed.

## Project structure

```
gavel/
├── pom.xml                              # aggregator + dependency management
├── docker-compose.yaml                  # local infra stack
├── infra/                               # configs for each infra service
│   ├── otel-collector/
│   ├── prometheus/
│   ├── grafana/provisioning/
│   ├── tempo/
│   └── loki/
├── common/                              # gavel-common library
│   └── src/main/java/com/shukla/gavel/common/
│       ├── api/ApiResponse.java
│       └── error/ProblemDetails.java
└── services/
    └── hello-service/
        ├── src/main/java/com/shukla/gavel/hello/
        │   ├── HelloApplication.java
        │   ├── api/                     # PingController, PingResponse
        │   ├── domain/                  # Visit, VisitRepository, VisitService
        │   └── infrastructure/          # TomcatNio2Config
        ├── src/main/resources/
        │   ├── application.yaml
        │   ├── application-local.yaml
        │   ├── logback-spring.xml
        │   └── db/migration/            # Flyway SQL scripts
        └── src/test/java/com/shukla/gavel/hello/
            ├── PingControllerTest.java
            ├── VisitPersistenceIT.java
            └── TestcontainersConfiguration.java
```

## Troubleshooting

**`mvn` not found on PATH (Git Bash / PowerShell)**
- Git Bash: add Maven's `bin/` to `~/.bashrc` or use the full path.
- PowerShell: add to system PATH via Environment Variables, or prefix commands with the full Maven path.

**Port 8081 already in use**
- `netstat -ano | findstr 8081` (PowerShell) to find the PID, then `taskkill /PID <pid> /F`.

**Tests fail — `Could not find a valid Docker environment`**
- Testcontainers needs Docker Desktop running. Start Docker Desktop and re-run `mvn verify`.

**Flyway fails on startup — `relation "visits" does not exist`**
- Ensure the Compose Postgres is up (`docker compose ps`). Flyway runs `V1__create_visits_table.sql` on first startup automatically.

**`Unable to establish loopback connection` on startup (Windows 11 + JDK 21)**
- JDK 21's `WEPollSelectorImpl` uses Unix Domain Socket loopback for its internal NIO pipe. On some Windows 11 Enterprise builds this returns `WSAEINVAL`. The codebase already works around this via `TomcatNio2Config`, which switches Tomcat to the NIO2 connector (IOCP-based, no `Selector.open()`). If you see this error despite the fix, verify that `TomcatNio2Config` is on the classpath and that you're using JDK 21 (not JDK 8 which is the system default on this machine).
