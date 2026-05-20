# Runbook — local infrastructure

Operations guide for the Docker Compose stack used in local development.

## Stack overview

| Container name | Image | Port(s) | Purpose |
|---|---|---|---|
| gavel-postgres | postgres:16-alpine | 5432 | Primary database |
| gavel-otel-collector | otel/opentelemetry-collector-contrib:0.127.0 | 4317, 4318, 8889 | Telemetry ingestion |
| gavel-prometheus | prom/prometheus:v3.4.0 | 9090 | Metrics storage |
| gavel-grafana | grafana/grafana:12.0.0 | 3000 | Dashboards |
| gavel-tempo | grafana/tempo:2.7.2 | 3200 | Trace storage |
| gavel-loki | grafana/loki:3.4.2 | 3100 | Log aggregation |

## Start and stop

```powershell
# Start all services in the background
docker compose up -d

# Check all containers are healthy
docker compose ps

# View logs for a specific service
docker compose logs -f postgres
docker compose logs -f otel-collector

# Stop all services (data volumes preserved)
docker compose down

# Stop and destroy all data (full reset)
docker compose down -v
```

## Inspect Postgres

```powershell
# Open a psql shell
docker compose exec postgres psql -U postgres -d hello_db

# Useful queries once connected
\dt                          -- list all tables
SELECT count(*) FROM visits; -- verify visit persistence
```

Or connect from any external SQL client using `localhost:5432`, database `hello_db`, username `postgres`, password `postgres`.

## Access Grafana

Open [http://localhost:3000](http://localhost:3000). Default credentials: `admin` / `admin`. Grafana prompts you to change the password on first login — skip it for local use.

Three datasources are pre-provisioned: Prometheus, Tempo, Loki. If any datasource shows red, restart the collector and wait 10 seconds:

```powershell
docker compose restart otel-collector
```

The `auction-service` dashboard is under **Dashboards → Gavel**. It shows request rate, p99 latency, JVM heap, and total visit count.

## Check Prometheus targets

Open [http://localhost:9090/targets](http://localhost:9090/targets).

- `auction-service` scrapes `/actuator/prometheus` on `host.docker.internal:8081`. This target is **UP** only when auction-service is running on the host.
- `otel-collector` scrapes the collector's `:8889` metrics endpoint. This should always be **UP** when the stack is running.

## Query traces in Grafana

1. Open Grafana → Explore → select **Tempo** datasource.
2. Use **Search** tab → Service Name: `auction-service` → Run query.
3. Click any trace to see the span waterfall.

Alternatively, copy a `traceId` from a auction-service log line and paste it directly into Tempo's **TraceQL** tab.

## Common issues

**`docker compose up` fails — port already in use**

Find and kill the process:
```powershell
netstat -ano | findstr 5432   # replace with the conflicting port
taskkill /PID <pid> /F
```

**Postgres container exits immediately**

Check logs: `docker compose logs postgres`. Most likely a volume permissions issue. Fix: `docker compose down -v` then `docker compose up -d`.

**`auction-service` target is DOWN in Prometheus**

`host.docker.internal` only resolves when Docker Desktop is running and auction-service is actually up on port 8081. Start the service first.

**OTel Collector crashes on startup**

Usually a config syntax error. Check: `docker compose logs otel-collector`. Validate the YAML in `infra/otel-collector/otel-collector-config.yaml`.

**Grafana datasource shows red**

Restart the affected backend and Grafana:
```powershell
docker compose restart tempo loki prometheus grafana
```
